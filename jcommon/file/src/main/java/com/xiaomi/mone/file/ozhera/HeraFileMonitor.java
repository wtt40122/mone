package com.xiaomi.mone.file.ozhera;

import com.google.common.collect.Lists;
import com.xiaomi.mone.file.common.FileInfo;
import com.xiaomi.mone.file.common.FileInfoCache;
import com.xiaomi.mone.file.common.FileUtils;
import com.xiaomi.mone.file.common.Pair;
import com.xiaomi.mone.file.event.EventListener;
import com.xiaomi.mone.file.event.EventType;
import com.xiaomi.mone.file.event.FileEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * @author goodjava@qq.com
 * @date 2023/9/25 09:55
 */
@Slf4j
public class HeraFileMonitor {

    @Getter
    private ConcurrentHashMap<Object, HeraFile> map = new ConcurrentHashMap<>();

    @Getter
    private ConcurrentHashMap<String, HeraFile> fileMap = new ConcurrentHashMap<>();

    @Setter
    private EventListener listener;

    @Setter
    private volatile boolean stop;

    public HeraFileMonitor() {
        this(TimeUnit.SECONDS.toMillis(30));
    }

    public HeraFileMonitor(long removeTime) {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                List<Pair<String, Object>> remList = Lists.newArrayList();
                long now = System.currentTimeMillis();
                fileMap.values().forEach(it -> {
                    if (now - it.getUtime().get() >= removeTime && now - it.getReadTime().get() >= removeTime) {
                        remList.add(Pair.of(it.getFileName(), it.getFileKey()));
                    }
                });

                remList.forEach(it -> {
                    log.info("remove file:{},fileKey:{}", it.getKey(), it.getValue());
                    fileMap.remove(it.getKey());
                    map.remove(it.getValue());
                    listener.remove(it.getValue());
                });
            } catch (Throwable ex) {
                log.error("remove file error", ex);
            }
        }, 5, 10, TimeUnit.SECONDS);
    }


    public HeraFileMonitor(EventListener listener) {
        this();
        this.listener = listener;
    }

    /**
     * 注册目录监听并初始化文件采集
     *
     * @param path         需要监听的目录，这个目录必须是存在的
     * @param predicate    文件名是否符合指定的规则
     * @param files        如果这个有值，说明已经传过来了刚开始要采集的目录，否则就用当前目录下的文件
     * @param monitorPaths 如果这个有值，则会使用这个目录作为监控目录
     * @throws IOException
     * @throws InterruptedException
     */
    public void reg(String path, Predicate<String> predicate, List<File> files, List<String> monitorPaths) throws IOException, InterruptedException {
        Path directory = Paths.get(path);
        File f = directory.toFile();

        if (!f.exists()) {
            log.info("create directory:{}", directory);
            Files.createDirectories(directory);
        }
        // 如果传入了初始文件列表，则使用这些文件，否则扫描当前目录下的文件
        if (files != null && !files.isEmpty()) {
            files.stream()
                    .filter(it -> predicate.test(it.getPath()))
                    .forEach(this::initFile);
        } else {
            Arrays.stream(Objects.requireNonNull(getFilesFromDirectory(f)))
                    .filter(it -> predicate.test(it.getPath()))
                    .forEach(this::initFile);
        }

        WatchService watchService = FileSystems.getDefault().newWatchService();

        // 如果传入了监控目录列表，则注册这些目录，否则注册当前目录
        if (monitorPaths != null && !monitorPaths.isEmpty()) {
            for (String monitorPath : monitorPaths) {
                Path monitorDir = Paths.get(monitorPath);
                // 注册监听事件，排除修改事件
                monitorDir.register(watchService,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_CREATE);
            }
        } else {
            directory.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_CREATE);
        }
        while (!stop) {
            try {

                WatchKey key = watchService.take();
                try {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path modifiedFile = (Path) event.context();
                        String filePath = String.format("%s%s", path, modifiedFile.getFileName().toString());
                        if (!predicate.test(filePath) || modifiedFile.getFileName().toString().startsWith(".")) {
                            continue;
                        }
                        HeraFile hfile = fileMap.get(filePath);

                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            if (null == hfile) {
                                hfile = initFile(new File(filePath));
                            }
                            modify(hfile);
                        }

                        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            fileMap.remove(filePath);
                            if (null != hfile) {
                                map.remove(hfile.getFileKey());
                                listener.onEvent(FileEvent.builder().type(EventType.delete).fileName(filePath).fileKey(hfile.getFileKey()).build());
                            }
                        }

                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            File file = new File(filePath);
                            Object k = FileUtils.fileKey(file);

                            if (map.containsKey(k)) {
                                log.info("change name " + map.get(k) + "--->" + file);
                                listener.onEvent(FileEvent.builder().fileKey(k).type(EventType.rename).build());
                            } else {
                                log.info("ENTRY_CREATE filePath:{},fileKey:{}", filePath, k);
                                HeraFile hf = HeraFile.builder().file(file).fileKey(k).fileName(filePath).build();
                                map.putIfAbsent(k, hf);
                                fileMap.put(filePath, hf);

                                listener.onEvent(FileEvent.builder().type(EventType.create).fileKey(k).fileName(file.getPath()).build());
                            }
                        }
                    }
                } catch (Exception e1) {
                    log.error("watchService poll events error", e1);
                } finally {
                    key.reset();
                }
            } catch (Exception e) {
                log.error("watchService error", e);
            }
        }
    }

    /**
     * 获取目录下的所有文件
     *
     * @param directory 目录
     * @return 文件数组
     */
    private File[] getFilesFromDirectory(File directory) {
        return directory.listFiles();
    }

    private ReentrantLock lock = new ReentrantLock();

    private HeraFile initFile(File it) {
        if (it.isFile()) {
            String name = it.getName();
            if (name.startsWith(".")) {
                return null;
            }
            lock.lock();
            try {
                Object fileKey = FileUtils.fileKey(it);
                if (map.containsKey(fileKey)) {
                    return map.get(fileKey);
                }
                HeraFile hf = HeraFile.builder().file(it).fileKey(fileKey).fileName(it.getPath()).build();
                FileInfo fi = FileInfoCache.ins().get(fileKey.toString());
                long pointer = 0L;
                if (null != fi) {
                    pointer = fi.getPointer();
                }
                log.info("initFile fileName:{},fileKey:{}", name, fileKey);
                map.put(hf.getFileKey(), hf);
                fileMap.put(hf.getFileName(), hf);
                log.info("initFile hf:{},map size:{},fileMap size:{}", hf, map.size(), fileMap.size());
                this.listener.onEvent(FileEvent.builder()
                        .pointer(pointer)
                        .type(EventType.init)
                        .fileName(hf.getFileName())
                        .fileKey(hf.getFileKey())
                        .build());
                return hf;
            } catch (Exception e) {
                log.error("init file error,fileName:{}", name, e);
            } finally {
                lock.unlock();
            }
        }
        return null;
    }


    private void modify(HeraFile hfile) {
        if (null != hfile) {
            hfile.getUtime().set(System.currentTimeMillis());
            if (hfile.getFile().length() == 0) {
                listener.onEvent(FileEvent.builder().type(EventType.empty).fileName(hfile.getFileName()).fileKey(hfile.getFileKey()).build());
            } else {
                listener.onEvent(FileEvent.builder().type(EventType.modify).build());
            }
        }
    }

    public void stop() {
        this.stop = true;
        listener.stop();
    }

}
