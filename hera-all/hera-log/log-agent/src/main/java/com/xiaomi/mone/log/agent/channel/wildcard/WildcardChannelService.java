package com.xiaomi.mone.log.agent.channel.wildcard;

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
import com.xiaomi.mone.log.api.model.meta.LogPattern;
import com.xiaomi.mone.log.api.model.msg.LineMessage;
import com.xiaomi.mone.log.common.FileUtils;
import com.xiaomi.mone.log.utils.NetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.xiaomi.mone.log.common.Constant.GSON;
import static com.xiaomi.mone.log.common.Constant.SYMBOL_COMMA;

/**
 * @author wtt
 * @version 1.0
 * @description 通配符匹配采集文件处理
 * @date 2023/5/6 15:05
 */
@Slf4j
public class WildcardChannelService implements ChannelService {

    private ChannelDefine channelDefine;

    private MsgExporter msgExporter;

    private final FilterChain chain;

    private final FileUniqueMark fileUniqueMark;

    private final AgentMemoryService memoryService;

    private ChannelMemory channelMemory;

    private String monitorDirectory;
    private String logPattern;

    private byte[] lock = new byte[0];
    private Map<String, FileSymbol> uniqueFileMap = new ConcurrentHashMap<>();
    private Map<String, LogFile> logFileMap = new ConcurrentHashMap<>();
    private Map<String, Future> futureMap = new ConcurrentHashMap<>();

    private List<LineMessage> lineMessageList = new ArrayList<>();
    private long lastSendTime = System.currentTimeMillis();

    private List<FileChangeMonitor> monitorList = new CopyOnWriteArrayList();
    private long logCounts = 0;

    private ScheduledFuture<?> scheduledFuture;

    private Map<String, ScheduledFuture<?>> lastFileLineScheduledFutureMap = new ConcurrentHashMap<>();

    private String instanceId = UUID.randomUUID().toString();

    private Pattern patternExpress;

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
        buildFileUniqueMap(filePaths, input.getLogSplitExpress());
        //3.获取读取进度(断线重连)
        channelMemoryObtain(channelDefine, filePaths);
        //4.开始采集
        startCollectFile(channelDefine.getChannelId(), filePaths);
        memoryService.refreshMemory(channelMemory);
        //5.开启扫描线程
        startFileMonitor();
        startExportQueueDataThread();
    }

    private void startFileMonitor() {
        ExecutorUtil.submit(() -> monitorDirectory(new FileChangeListener() {

            @Override
            public void onFileCreate(String file, String uniqueMark) {
                if (FileUtils.belongToLogPath(logPattern, file)) {
                    createFile(file, uniqueMark);
                }
            }

            @Override
            public void onFileChange(String file, String uniqueMark) {
                if (FileUtils.belongToLogPath(logPattern, file)) {
                    changeFile(file, uniqueMark);
                }
            }

            @Override
            public void onFileDelete(String file, String uniqueMark) {
                if (FileUtils.belongToLogPath(logPattern, file)) {
                    deleteFile(file, uniqueMark);
                }
            }
        }));
    }

    private void deleteFile(String deleteFilePath, String uniqueMark) {
        log.info("deleteFilePath:{},uniqueMark:{}", deleteFilePath, uniqueMark);
        if (futureMap.containsKey(uniqueMark)) {
            futureMap.get(uniqueMark).cancel(true);
        }
        if (logFileMap.containsKey(uniqueMark)) {
            logFileMap.get(uniqueMark).setStop(true);
        }
        if (uniqueFileMap.containsKey(uniqueMark)) {
            uniqueFileMap.remove(uniqueMark);
        }
        if (lastFileLineScheduledFutureMap.containsKey(uniqueMark)) {
            lastFileLineScheduledFutureMap.get(uniqueMark).cancel(false);
        }
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
            log.info("## agent monitor filePattern:{} started，monitorDirectory：{}", logPattern, monitorDirectory);
            monitorList.add(monitor);
        } catch (Exception e) {
            log.error(String.format("agent file monitor start err,monitor filePattern:%s,monitorDirectory：%s", logPattern, monitorDirectory), e);
        }
    }

    private void changeFile(String filePath, String uniqueMark) {
        //底层做了
    }

    private void createFile(String createFilePath, String uniqueMark) {
        log.info("createFilePath:{},uniqueMark:{}", createFilePath, uniqueMark);
        //判断inode是否存在
        String existsFileName = uniqueMarkExistsFileName(uniqueMark);
        if (StringUtils.isEmpty(existsFileName)) {
            //文件uniqueMark不存在，代表是重新创建了个文件，存在2种情况->主要看文件切割机制
            //1.过了一段时间重新增加了个文件，需要被采集
            //2.被切割出来的文件，不需要被采集
            //判断是否是切割的文件，如果是不需要,否则，需要开启读取文件
            if (!isExclude(createFilePath)) {
                readFile(ChannelUtil.queryCurrentCorrectIp(channelDefine.getIpDirectoryRel(), createFilePath), createFilePath, channelDefine.getChannelId());
            }
        } else {
            //这个情况不可能发生，新增事件不会出现这样的情况
            //如果文件uniqueMark已经存在，代表的是文件被重命名了，不用管，等待修改时被超时停止
            log.info("createFilePath -> file uniqueMark has exist,fileName:{},inode:{}", createFilePath, uniqueMark);
        }
    }

    private String uniqueMarkExistsFileName(String uniqueMark) {
        if (uniqueFileMap.containsKey(uniqueMark)) {
            return uniqueFileMap.get(uniqueMark).getFilePath();
        }
        return null;
    }

    private boolean isExclude(String filePath) {
        if (null == patternExpress) {
            return false;
        }
        return patternExpress.matcher(filePath).find();
    }

    private Map<String, String> getFilepathIp() {
        return new ConcurrentHashMap<>();
    }

    private void startCollectFile(Long channelId, List<String> filePaths) {

        for (int i = 0; i < filePaths.size(); i++) {
            String ip = ChannelUtil.queryCurrentCorrectIp(channelDefine.getIpDirectoryRel(), filePaths.get(i));
            readFile(ip, filePaths.get(i), channelId);
        }
    }

    private void readFile(String ip, String filePath, Long channelId) {
        MLog mLog = new MLog();
        if (StringUtils.isNotBlank(this.channelDefine.getInput().getLinePrefix())) {
            mLog.setCustomLinePattern(this.channelDefine.getInput().getLinePrefix());
        }
        String usedIp = StringUtils.isBlank(ip) ? NetUtil.getLocalIp() : ip;

        String uniqueMark = fileUniqueMark.getFileUniqueMark(filePath);
        ReadListener listener = initFileReadListener(mLog, usedIp, filePath, uniqueMark);
        Map<String, ChannelMemory.FileProgress> fileProgressMap = channelMemory.getFileProgressMap();
        log.info("fileProgressMap:{}", fileProgressMap);
        LogFile logFile = getLogFile(filePath, listener, fileProgressMap);
        if (null == logFile) {
            log.warn("file:{} marked stop to collect", filePath);
            return;
        }
        //判断文件是否存在
        if (FileUtil.exist(filePath)) {
            log.info("start to collect file,fileName:{}", filePath);
            logFileMap.put(uniqueMark, logFile);
            Future<?> future = ExecutorUtil.submit(() -> {
                try {
                    logFile.readLine();
                } catch (Exception e) {
                    log.error("logFile read line err,channelId:{},uniqueIp:{},file:{}", channelId, usedIp, fileProgressMap, e);
                }
            });
            futureMap.put(uniqueMark, future);
        } else {
            log.info("file not exist,file:{}", filePath);
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
                        fileProgress.setPointer(pointer);
                        fileProgress.setFileMaxPointer(pointer);
                        fileProgress.setCurrentRowNum(lineNumber);
                        fileProgress.setUnixFileNode(currentUnixFileNode);
                    }
                }
            }
        }
        return new LogFile(filePath, listener, pointer, lineNumber);
    }

    private ReadListener initFileReadListener(MLog mLog, String ip, String filePath, String uniqueMark) {
        AtomicReference<ReadResult> readResult = new AtomicReference<>();
        ReadListener listener = new DefaultReadListener(event -> {
            readResult.set(event.getReadResult());
            if (null == readResult.get()) {
                log.info("empty data");
                return;
            }
            long ct = System.currentTimeMillis();
            readResult.get().getLines().stream().forEach(l -> {
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
                        wrapDataToSend(l, readResult, filePath, ip, ct, uniqueMark);
                    }
                } else {
                    log.debug("biz log channelId:{}, not new line:{}", channelDefine.getChannelId(), l);
                }
            });

        });

        /**
         * 采集最后一行数据内存中超 10s 没有发送的数据
         */
        ScheduledFuture<?> lastFileLineScheduledFuture = ExecutorUtil.scheduleAtFixedRate(() -> {
            Long appendTime = mLog.getAppendTime();
            if (null != appendTime && Instant.now().toEpochMilli() - appendTime > 10 * 1000) {
                String remainMsg = mLog.takeRemainMsg2();
                if (null != remainMsg) {
                    synchronized (lock) {
                        log.info("start send last line,pattern:{},data:{}", filePath, remainMsg);
                        wrapDataToSend(remainMsg, readResult, filePath, ip, Instant.now().toEpochMilli(), uniqueMark);
                    }
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
        lastFileLineScheduledFutureMap.put(uniqueMark, lastFileLineScheduledFuture);
        return listener;
    }

    private void wrapDataToSend(String lineMsg, AtomicReference<ReadResult> readResult, String filePath, String localIp, Long ct, String uniqueMark) {
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

        uniqueFileMap.putIfAbsent(uniqueMark, FileSymbol.builder().fileUniqueMark(uniqueMark).filePath(filePath).lastSendTime(Instant.now().toEpochMilli()).build());
        uniqueFileMap.get(uniqueMark).setLastSendTime(ct);
        uniqueFileMap.get(uniqueMark).setLineNumber(readResult.get().getLineNumber());

        int batchSize = msgExporter.batchExportSize();
        if (lineMessageList.size() > batchSize) {
            List<LineMessage> subList = lineMessageList.subList(0, batchSize);
            doExport(subList);
        }
    }

    private void doExport(List<LineMessage> subList) {
        try {
            if (CollectionUtils.isEmpty(subList)) {
                return;
            }
            //限流处理
            chain.doFilter();

            long current = System.currentTimeMillis();
            msgExporter.export(subList);
            logCounts += subList.size();
            lastSendTime = System.currentTimeMillis();
            channelMemory.setCurrentTime(lastSendTime);

            log.info("doExport channelId:{}, send {} message, cost:{}, total send:{}, instanceId:{},", channelDefine.getChannelId(), subList.size(), lastSendTime - current, logCounts, instanceId());
        } catch (Exception e) {
            log.error("doExport Exception:{}", e);
        } finally {
            subList.clear();
        }
    }

    private void buildFileUniqueMap(List<String> filePaths, String express) {
        for (String filePath : filePaths) {
            String uniqueMark = fileUniqueMark.getFileUniqueMark(filePath);
            uniqueFileMap.put(uniqueMark, FileSymbol.builder().fileUniqueMark(uniqueMark).filePath(filePath).lastSendTime(Instant.now().toEpochMilli()).build());
        }
        if (StringUtils.isNotEmpty(express)) {
            try {
                patternExpress = Pattern.compile(express);
            } catch (Exception e) {
                log.error("build patternExpress error,express:{}", express, e);
            }
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
        this.channelDefine = channelDefine;
        if (null != msgExporter) {
            synchronized (this.lock) {
                this.msgExporter.close();
                this.msgExporter = msgExporter;
            }
        }
    }

    @Override
    public void stopFile(List<String> filePrefixList) {
        Map<String, ChannelMemory.FileProgress> fileProgressMap = channelMemory.getFileProgressMap();
        if (null == fileProgressMap) {
            fileProgressMap = new HashMap<>();
        }

        for (Iterator<Map.Entry<String, LogFile>> it = logFileMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, LogFile> entry = it.next();
            String filePath = entry.getValue().getFile();
            for (String filePrefix : filePrefixList) {
                if (filePath.startsWith(filePrefix)) {
                    entry.getValue().setStop(true);
                    futureMap.get(entry.getKey()).cancel(false);
                    log.warn("channel:{} stop file:{} success", channelDefine.getChannelId(), filePath);
                    ChannelMemory.FileProgress fileProgress = fileProgressMap.get(filePath);
                    //刷新内存记录，防止agent重启，重新采集该文件
                    if (null != fileProgress) {
                        fileProgress.setFinished(true);
                    }
                    it.remove();
                }
            }
        }
    }

    @Override
    public ChannelState state() {
        ChannelState channelState = new ChannelState();

        channelState.setTailId(this.channelDefine.getChannelId());
        channelState.setTailName(this.channelDefine.getTailName());
        channelState.setAppId(this.channelDefine.getAppId());
        channelState.setAppName(this.channelDefine.getAppName());
        channelState.setLogPattern(this.channelDefine.getInput().getLogPattern());
        channelState.setLogPatternCode(this.channelDefine.getInput().getPatternCode());
        channelState.setIpList(channelDefine.getIpDirectoryRel().stream().map(LogPattern.IPRel::getIp).distinct().collect(Collectors.toList()));

        channelState.setCollectTime(this.channelMemory.getCurrentTime());

        if (channelState.getStateProgressMap() == null) {
            channelState.setStateProgressMap(new HashMap<>(256));
        }
        channelMemory.getFileProgressMap().forEach((pattern, fileProcess) -> {
            if (null != fileProcess.getFinished() && fileProcess.getFinished()) {
                return;
            }
            ChannelState.StateProgress stateProgress = new ChannelState.StateProgress();
            stateProgress.setCurrentFile(pattern);
            stateProgress.setIp(ChannelUtil.queryCurrentCorrectIp(channelDefine.getIpDirectoryRel(), pattern));
            stateProgress.setCurrentRowNum(fileProcess.getCurrentRowNum());
            stateProgress.setPointer(fileProcess.getPointer());
            stateProgress.setFileMaxPointer(fileProcess.getFileMaxPointer());
            channelState.getStateProgressMap().put(pattern, stateProgress);
        });

        channelState.setTotalSendCnt(this.logCounts);
        return channelState;
    }

    @Override
    public String instanceId() {
        return instanceId;
    }

    @Override
    public void filterRefresh(List<FilterConf> confs) {
        try {
            this.chain.loadFilterList(confs);
            this.chain.reset();
        } catch (Exception e) {
            log.error("filter refresh err,new conf:{}", confs, e);
        }
    }

    @Override
    public void reOpen(String filePath) {
        log.info("reOpen file:{}", filePath);
        LogFile logFile = logFileMap.get(fileUniqueMark.getFileUniqueMark(filePath));
        String ip = ChannelUtil.queryCurrentCorrectIp(channelDefine.getIpDirectoryRel(), filePath);
        if (null == logFile) {
            // 新增日志文件
            readFile(ip, filePath, getChannelId());
            log.info("watch new file create for chnnelId:{},ip:{},path:{}", getChannelId(), filePath, ip);
        } else {
            logFile.setReOpen(true);
            log.info("file reOpen: channelId:{},ip:{},path:{}", getChannelId(), ip, filePath);
        }
    }

    @Override
    public List<MonitorFile> getMonitorPathList() {
        return null;
    }

    @Override
    public void delayDeletionFinishedFile() {

    }

    @Override
    public Long getChannelId() {
        return channelDefine.getChannelId();
    }

    @Override
    public void close() {
        log.info("删除当前采集任务，channelId:{}", getChannelId());
        //1.停止日志抓取
        for (LogFile logFile : logFileMap.values()) {
            logFile.setStop(true);
        }
        //2. 停止export
        this.msgExporter.close();
        //3. 刷新缓存
        memoryService.refreshMemory(channelMemory);
        // 停止任务
        if (null != scheduledFuture) {
            scheduledFuture.cancel(false);
        }
        if (null != lastFileLineScheduledFutureMap) {
            for (Map.Entry<String, ScheduledFuture<?>> futureEntry : lastFileLineScheduledFutureMap.entrySet()) {
                futureEntry.getValue().cancel(false);
            }
        }
        for (Future future : futureMap.values()) {
            future.cancel(false);
        }
        for (FileChangeMonitor fileChangeMonitor : monitorList) {
            fileChangeMonitor.stop();
        }
        log.info("stop file monitor,fileName:", logFileMap.keySet().stream().collect(Collectors.joining(SYMBOL_COMMA)));
    }

    private void startExportQueueDataThread() {
        scheduledFuture = ExecutorUtil.scheduleAtFixedRate(() -> {
            // 超过10s 未发送mq消息，才进行异步发送
            if (System.currentTimeMillis() - lastSendTime < 10 * 1000) {
                return;
            }
            synchronized (lock) {
                this.doExport(lineMessageList);
            }
        }, 10, 7, TimeUnit.SECONDS);
    }

}
