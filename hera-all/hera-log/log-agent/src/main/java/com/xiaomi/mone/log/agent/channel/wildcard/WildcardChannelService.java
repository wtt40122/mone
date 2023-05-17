package com.xiaomi.mone.log.agent.channel.wildcard;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import com.google.common.collect.Maps;
import com.xiaomi.mone.file.*;
import com.xiaomi.mone.log.agent.channel.ChannelDefine;
import com.xiaomi.mone.log.agent.channel.ChannelService;
import com.xiaomi.mone.log.agent.channel.ChannelState;
import com.xiaomi.mone.log.agent.channel.file.MonitorFile;
import com.xiaomi.mone.log.agent.channel.mark.FileUniqueMark;
import com.xiaomi.mone.log.agent.channel.memory.AgentMemoryService;
import com.xiaomi.mone.log.agent.channel.memory.ChannelMemory;
import com.xiaomi.mone.log.agent.channel.memory.UnixFileNode;
import com.xiaomi.mone.log.agent.channel.wildcard.listen.FileChangeListener;
import com.xiaomi.mone.log.agent.channel.wildcard.listen.FileChangeMonitor;
import com.xiaomi.mone.log.agent.channel.wildcard.listen.FileChangeObserver;
import com.xiaomi.mone.log.agent.channel.wildcard.symbol.FileSymbol;
import com.xiaomi.mone.log.agent.common.ChannelUtil;
import com.xiaomi.mone.log.agent.common.ExecutorUtil;
import com.xiaomi.mone.log.agent.export.MsgExporter;
import com.xiaomi.mone.log.agent.filter.FilterChain;
import com.xiaomi.mone.log.agent.input.Input;
import com.xiaomi.mone.log.api.enums.K8sPodTypeEnum;
import com.xiaomi.mone.log.api.enums.LogTypeEnum;
import com.xiaomi.mone.log.api.model.meta.FilterConf;
import com.xiaomi.mone.log.api.model.msg.LineMessage;
import com.xiaomi.mone.log.common.FileUtils;
import com.xiaomi.mone.log.utils.NetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.xiaomi.mone.log.common.Constant.GSON;

/**
 * @author wtt
 * @version 1.0
 * @description 通配符匹配采集文件处理
 * @date 2023/5/6 15:05
 */
@Slf4j
public class WildcardChannelService implements ChannelService {

    private final ChannelDefine channelDefine;

    private final MsgExporter msgExporter;

    private final FilterChain chain;

    private final FileUniqueMark fileUniqueMark;

    private final AgentMemoryService memoryService;

    private ChannelMemory channelMemory;

    private String monitorDirectory;
    private String logPattern;

    private byte[] lock = new byte[0];
    private Map<String, FileSymbol> uniqueFileMap = new ConcurrentHashMap<>();
    private Map<String, String> ipPath = new ConcurrentHashMap<>();
    private Map<String, LogFile> logFileMap = new ConcurrentHashMap<>();
    private Map<String, Future> futureMap = new ConcurrentHashMap<>();
    private List<LineMessage> lineMessageList = new ArrayList<>();
    private Map<String, Long> lastSendTimeFileMap = new HashMap<>();
    private List<FileChangeMonitor> monitorList = new CopyOnWriteArrayList();
    private long logCounts = 0;

    private volatile boolean cancel;

    public WildcardChannelService(ChannelDefine channelDefine, MsgExporter msgExporter, FilterChain chain, FileUniqueMark fileUniqueMark, AgentMemoryService memoryService) {
        this.channelDefine = channelDefine;
        this.msgExporter = msgExporter;
        this.chain = chain;
        this.fileUniqueMark = fileUniqueMark;
        this.memoryService = memoryService;
    }


    @Override
    public void start() {
        Input input = channelDefine.getInput();
        logPattern = input.getLogPattern();
        monitorDirectory = FileUtils.getMonitorDirectory(logPattern);
        //1，找到所有的采集地址
        List<String> filePaths = FileUtils.getFilesByWildcard(logPattern);
        //2.文件地址与文件唯一标识符的对应关系
        buildFileUniqueMap(filePaths);
        //3.获取读取进度(断线重连)
        channelMemoryObtain(channelDefine, filePaths);
        //4.获取每个文件对象所属的机器ip
        Map<String, String> ipPath = getFilepathIp();
        //5.开始采集
        startCollectFile(channelDefine.getChannelId(), channelDefine.getIps(), filePaths);
        memoryService.refreshMemory(channelMemory);
        //6.开启扫描线程
        startFileMonitor();
    }

    private void startFileMonitor() {
        ExecutorUtil.submit(() -> monitorDirectory(new FileChangeListener() {

            @Override
            public void onFileCreate(String file, String uniqueMark) {
                createFile(file, uniqueMark);
            }

            @Override
            public void onFileChange(String file, String uniqueMark) {
                changeFile(file, uniqueMark);
            }

            @Override
            public void onFileDelete(String file, String uniqueMark) {
                deleteFile(file, uniqueMark);
            }
        }));
    }

    private void monitorDirectory(FileChangeListener fileChangeListener) {
        // 默认 遍历文件 间隔时间 5s
        FileChangeMonitor monitor = new FileChangeMonitor(5000);
        log.info("agent monitor files:{}", GSON.toJson(monitorDirectory));
        FileChangeObserver observer = new FileChangeObserver(monitorDirectory);
        observer.addListener(fileChangeListener);
        log.info("## agent monitor file:{}, filePattern:{}", logPattern, monitorDirectory);
        monitor.addObserver(observer);
        try {
            monitor.start();
            log.info("## agent monitor filePattern:{} started", logPattern);
            monitorList.add(monitor);
        } catch (Exception e) {
            log.error(String.format("agent file monitor start err,monitor filePattern:%s", logPattern), e);
        }
    }

    private void deleteFile(String deleteFilePath, String uniqueMark) {
        log.info("deleteFilePath:{}", deleteFilePath);
        if (futureMap.containsKey(deleteFilePath)) {
            futureMap.get(deleteFilePath).cancel(true);
        }
        if (logFileMap.containsKey(deleteFilePath)) {
            logFileMap.get(deleteFilePath).setStop(true);
        }
        if (uniqueFileMap.containsKey(deleteFilePath)) {
            uniqueFileMap.remove(deleteFilePath);
        }
        if (lastSendTimeFileMap.containsKey(deleteFilePath)) {
            lastSendTimeFileMap.remove(deleteFilePath);
        }
    }

    private void changeFile(String filePath, String uniqueMark) {
        long lastModified = new File(filePath).lastModified();
        if (Instant.now().toEpochMilli() - lastModified > 24 * 60 * 60 * 1000) {
            ChannelMemory memory = memoryService.getMemory(channelDefine.getChannelId());
            Long fileMaxPointer = memory.getFileProgressMap().get(filePath).getFileMaxPointer();
            Long currentMaxPointer = 0L;
            try {
                currentMaxPointer = new RandomAccessFile(filePath, "r").length();
            } catch (IOException e) {
                log.error("get currentMaxPointer error,filePath:{}", filePath, e);
            }
            if (fileMaxPointer == currentMaxPointer) {
                //删除当前文件
            }
        }
    }

    private void createFile(String createFilePath, String uniqueMark) {
        log.info("createFilePath:{}", createFilePath);
        //1.判断是否符合规则
        if (FileUtils.belongToLogPath(logPattern, createFilePath)) {
            String existsFileName = uniqueMarkExistsFileName(uniqueMark);
            if (StringUtils.isEmpty(existsFileName)) {
                //判断是否是切割的文件，如果是不需要,否则，需要开启读取文件
                if (!isExclude(createFilePath)) {
                    if (!logFileMap.containsKey(createFilePath)) {
                        readFile("", createFilePath, channelDefine.getChannelId());
                    }
                }
            } else {

            }
        }
    }

    private String uniqueMarkExistsFileName(String uniqueMark) {
        for (Map.Entry<String, FileSymbol> fileSymbolEntry : uniqueFileMap.entrySet()) {
            if (Objects.equals(uniqueMark, fileSymbolEntry.getValue().getFileUniqueMark())) {
                return fileSymbolEntry.getKey();
            }
        }
        return null;
    }

    private void listenFileChange() {
        for (Map.Entry<String, FileSymbol> entry : uniqueFileMap.entrySet()) {
            if (!Objects.equals(entry.getValue().getFileUniqueMark(), fileUniqueMark.getFileUniqueMark(entry.getKey()))) {
                //说明文件唯一标识变化了,并且超过当前时间已经距离最后一次读取的时间超过1min
                if (Instant.now().toEpochMilli() - entry.getValue().getLastSendTime() > 60 * 1000) {
                    String ip = getFilepathIp().get(entry.getKey());
                    readFile(ip, entry.getKey(), channelDefine.getChannelId());
                }
            } else {
                //文件唯一标识没有变化，但是文件的大小变化了

            }

        }
    }

    private boolean isExclude(String filePath) {
        return false;
    }

    private Map<String, String> getFilepathIp() {
        return new ConcurrentHashMap<>();
    }

    private void startCollectFile(Long channelId, List<String> ips, List<String> filePaths) {
        Map<String, String> ipPathDireMap = new HashMap<>();
        BeanUtil.copyProperties(ipPath, ipPathDireMap);

        for (int i = 0; i < filePaths.size(); i++) {
            String ip = ChannelUtil.queryCurrentCorrectIp(ipPathDireMap, filePaths.get(i), ips);
            readFile(ip, filePaths.get(i), channelId);
        }
    }

    private void readFile(String ip, String filePath, Long channelId) {
        MLog mLog = new MLog();
        if (StringUtils.isNotBlank(this.channelDefine.getInput().getLinePrefix())) {
            mLog.setCustomLinePattern(this.channelDefine.getInput().getLinePrefix());
        }
        String usedIp = StringUtils.isBlank(ip) ? NetUtil.getLocalIp() : ip;

        ReadListener listener = initFileReadListener(mLog, usedIp, filePath);
        Map<String, ChannelMemory.FileProgress> fileProgressMap = channelMemory.getFileProgressMap();
        log.info("fileProgressMap:{}", fileProgressMap);
        LogFile logFile = getLogFile(filePath, listener, fileProgressMap);
        if (null == logFile) {
            log.warn("file:{} marked stop to collect", filePath);
            return;
        }
        //判断文件是否存在
        if (FileUtil.exist(filePath)) {
            stopOldCurrentFileThread(filePath);
            log.info("start to collect file,fileName:{}", filePath);
            logFileMap.put(filePath, logFile);
            Future<?> future = ExecutorUtil.submit(() -> {
                try {
                    logFile.readLine();
                } catch (Exception e) {
                    log.error("logFile read line err,channelId:{},uniqueIp:{},file:{}", channelId, usedIp, fileProgressMap, e);
                }
            });
            futureMap.put(filePath, future);
        } else {
            log.info("file not exist,file:{}", filePath);
        }
    }

    private void stopOldCurrentFileThread(String filePath) {
        LogFile logFile = logFileMap.get(filePath);
        if (null != logFile) {
            logFile.setStop(true);
        }
        Future future = futureMap.get(filePath);
        if (null != future) {
            future.cancel(false);
        }
    }

    private LogFile getLogFile(String filePath, ReadListener listener, Map<String, ChannelMemory.FileProgress> fileProgressMap) {
        long pointer = 0L;
        long lineNumber = 0L;
        ChannelMemory.FileProgress fileProgress = fileProgressMap.get(filePath);
        if (fileProgress != null) {
            if (null != fileProgress.getFinished() && fileProgress.getFinished()) {
                /**
                 * k8s 中 stateful 的pod不用通过finished判断
                 */
                if (StringUtils.isNotBlank(channelDefine.getPodType())) {
                    if (K8sPodTypeEnum.valueOf(channelDefine.getPodType().toUpperCase()) != K8sPodTypeEnum.STATEFUL) {
                        return null;
                    }
                }
            }
            pointer = fileProgress.getPointer();
            lineNumber = fileProgress.getCurrentRowNum();
            //比较inode值是否变化，变化则从头开始读
            UnixFileNode memoryUnixFileNode = fileProgress.getUnixFileNode();
            if (null != memoryUnixFileNode && null != memoryUnixFileNode.getSt_ino()) {
                log.info("memory file inode info,filePath:{},:{}", filePath, GSON.toJson(memoryUnixFileNode));
                //获取当前文件inode信息
                UnixFileNode currentUnixFileNode = ChannelUtil.buildUnixFileNode(filePath);
                if (null != currentUnixFileNode && null != currentUnixFileNode.getSt_ino()) {
                    log.info("current file inode info,filePath:{},file node info:{}", filePath, GSON.toJson(currentUnixFileNode));
                    if (!Objects.equals(memoryUnixFileNode.getSt_ino(), currentUnixFileNode.getSt_ino())) {
                        pointer = 0L;
                        lineNumber = 0L;
                        log.info("read file start from head,filePath:{},memory:{},current:{}", filePath, GSON.toJson(memoryUnixFileNode), GSON.toJson(currentUnixFileNode));
                    }
                }
            }
        }
        return new LogFile(filePath, listener, pointer, lineNumber);
    }

    private ReadListener initFileReadListener(MLog mLog, String ip, String filePath) {
        AtomicReference<ReadResult> readResult = new AtomicReference<>();
        ReadListener listener = new DefaultReadListener(event -> {
            readResult.set(event.getReadResult());
            if (null == readResult.get()) {
                log.info("empty data");
                return;
            }
            long ct = System.currentTimeMillis();
            readResult.get().getLines().stream()
                    .forEach(l -> {
                        String logType = channelDefine.getInput().getType();
                        LogTypeEnum logTypeEnum = LogTypeEnum.name2enum(logType);
                        // 多行应用日志类型和opentelemetry类型才判断异常堆栈
                        if (LogTypeEnum.APP_LOG_MULTI == logTypeEnum || LogTypeEnum.OPENTELEMETRY == logTypeEnum) {
                            l = mLog.append2(l);
                        } else {
                            // tail 单行模式
                        }
                        if (null != l) {
                            synchronized (lock) {
                                wrapDataToSend(l, readResult, filePath, ip, ct);
                            }
                        } else {
                            log.debug("biz log channelId:{}, not new line:{}", channelDefine.getChannelId(), l);
                        }
                    });

        });

        /**
         * 采集最后一行数据内存中超 10s 没有发送的数据
         */
        ExecutorUtil.scheduleAtFixedRate(() -> {
            Long appendTime = mLog.getAppendTime();
            if (null != appendTime && Instant.now().toEpochMilli() - appendTime > 10 * 1000) {
                String remainMsg = mLog.takeRemainMsg2();
                if (null != remainMsg) {
                    synchronized (lock) {
                        log.info("start send last line,pattern:{},data:{}", filePath, remainMsg);
                        wrapDataToSend(remainMsg, readResult, filePath, ip, Instant.now().toEpochMilli());
                    }
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
        return listener;
    }

    private void wrapDataToSend(String lineMsg, AtomicReference<ReadResult> readResult, String filePath, String localIp, Long ct) {
        LineMessage lineMessage = new LineMessage();
        lineMessage.setMsgBody(lineMsg);
        lineMessage.setPointer(readResult.get().getPointer());
        lineMessage.setLineNumber(readResult.get().getLineNumber());
        lineMessage.setFileName(filePath);
        lineMessage.setProperties(LineMessage.KEY_MQ_TOPIC_TAG, this.channelDefine.getInput().getPatternCode());
        lineMessage.setProperties(LineMessage.KEY_IP, localIp);
        lineMessage.setProperties(LineMessage.KEY_COLLECT_TIMESTAMP, ct.toString());
        String logType = channelDefine.getInput().getType();
        LogTypeEnum logTypeEnum = LogTypeEnum.name2enum(logType);
        if (null != logTypeEnum) {
            lineMessage.setProperties(LineMessage.KEY_MESSAGE_TYPE, logTypeEnum.getType().toString());
        }

        ChannelMemory.FileProgress fileProgress = channelMemory.getFileProgressMap().get(filePath);
        if (null == fileProgress) {
            fileProgress = new ChannelMemory.FileProgress();
            channelMemory.getFileProgressMap().put(filePath, fileProgress);
            channelMemory.getInput().setLogPattern(filePath);
            channelMemory.getInput().setType(logTypeEnum.name());
            channelMemory.getInput().setLogSplitExpress(this.channelDefine.getInput().getLogSplitExpress());
        }
        fileProgress.setCurrentRowNum(readResult.get().getLineNumber());
        fileProgress.setPointer(readResult.get().getPointer());
        if (null != readResult.get().getFileMaxPointer()) {
            fileProgress.setFileMaxPointer(readResult.get().getFileMaxPointer());
        }
        fileProgress.setUnixFileNode(ChannelUtil.buildUnixFileNode(filePath));
        fileProgress.setPodType(channelDefine.getPodType());
        lineMessageList.add(lineMessage);

        uniqueFileMap.get(filePath).setLastSendTime(ct);
        uniqueFileMap.get(filePath).setLineNumber(readResult.get().getLineNumber());

        int batchSize = msgExporter.batchExportSize();
        if (lineMessageList.size() > batchSize) {
            List<LineMessage> subList = lineMessageList.subList(0, batchSize);
            doExport(filePath, subList);
        }
    }

    private void doExport(String filePath, List<LineMessage> subList) {
        try {
            if (CollectionUtils.isEmpty(subList)) {
                return;
            }
            //限流处理
            chain.doFilter();

            long current = System.currentTimeMillis();
            msgExporter.export(subList);
            logCounts += subList.size();
            long lastSendTime = System.currentTimeMillis();
            lastSendTimeFileMap.put(filePath, lastSendTime);
            channelMemory.setCurrentTime(lastSendTime);

            log.info("doExport channelId:{}, send {} message, cost:{}, total send:{}, instanceId:{},", channelDefine.getChannelId(), subList.size(), lastSendTime - current, logCounts, instanceId());
        } catch (Exception e) {
            log.error("doExport Exception:{}", e);
        } finally {
            subList.clear();
        }
    }

    private void buildFileUniqueMap(List<String> filePaths) {
        for (String filePath : filePaths) {
            uniqueFileMap.put(filePath,
                    FileSymbol.builder()
                            .fileUniqueMark(fileUniqueMark.getFileUniqueMark(filePath))
                            .filePath(filePath)
                            .lastSendTime(Instant.now().toEpochMilli())
                            .build());
        }
    }

    private void channelMemoryObtain(ChannelDefine channelDefine, List<String> filePaths) {
        channelMemory = memoryService.getMemory(channelDefine.getChannelId());
        if (null == channelMemory) {
            channelMemory = initChannelMemory(channelDefine.getChannelId(), channelDefine.getInput(), filePaths);
        }
    }

    private ChannelMemory initChannelMemory(Long channelId, Input input, List<String> filePaths) {
        channelMemory = new ChannelMemory();
        channelMemory.setChannelId(channelId);
        channelMemory.setInput(input);
        HashMap<String, ChannelMemory.FileProgress> fileProgressMap = Maps.newHashMap();
        for (String filePath : filePaths) {
            ChannelMemory.FileProgress fileProgress = new ChannelMemory.FileProgress();
            fileProgress.setPointer(0L);
            fileProgress.setCurrentRowNum(0L);
            fileProgress.setUnixFileNode(ChannelUtil.buildUnixFileNode(filePath));
            fileProgress.setPodType(channelDefine.getPodType());
            fileProgressMap.put(filePath, fileProgress);
        }
        channelMemory.setFileProgressMap(fileProgressMap);
        channelMemory.setCurrentTime(System.currentTimeMillis());
        channelMemory.setVersion(ChannelMemory.DEFAULT_VERSION);
        return channelMemory;
    }

    @Override
    public void refresh(ChannelDefine channelDefine, MsgExporter msgExporter) {

    }

    @Override
    public void stopFile(List<String> filePrefixList) {

    }

    @Override
    public ChannelState state() {
        return null;
    }

    @Override
    public String instanceId() {
        return null;
    }

    @Override
    public void filterRefresh(List<FilterConf> confs) {

    }

    @Override
    public void reOpen(String filePath) {

    }

    @Override
    public List<MonitorFile> getMonitorPathList() {
        return null;
    }

    @Override
    public void delayDeletionFinishedFile() {

    }

    @Override
    public void close() {

    }
}
