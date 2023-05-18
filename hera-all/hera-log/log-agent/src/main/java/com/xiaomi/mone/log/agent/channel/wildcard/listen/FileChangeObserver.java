package com.xiaomi.mone.log.agent.channel.wildcard.listen;

import com.xiaomi.mone.log.agent.channel.mark.DefaultFileUniqueMark;
import com.xiaomi.mone.log.agent.channel.mark.FileUniqueMark;
import com.xiaomi.mone.log.common.FileUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/16 15:51
 */
public class FileChangeObserver implements Serializable {

    private String monitorFileDirectory;

    private Map<String, String> fileINodeMap = new ConcurrentHashMap<>();

    private FileUniqueMark fileUniqueMark = new DefaultFileUniqueMark();

    private final List<FileChangeListener> listeners = new CopyOnWriteArrayList<>();

    public FileChangeObserver(String monitorFileDirectory) {
        this.monitorFileDirectory = monitorFileDirectory;
        buildFileInodeMap();
    }

    private void buildFileInodeMap() {
        fileINodeMap.clear();
        List<String> files = FileUtils.listFilePathNames(monitorFileDirectory);
        for (String file : files) {
            fileINodeMap.put(file, fileUniqueMark.getFileUniqueMark(file));
        }
    }

    public void addListener(final FileChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(final FileChangeListener listener) {
        if (listener != null) {
            while (listeners.remove(listener)) {
            }
        }
    }

    public void checkAndNotify() {
        //查询当前目录下的inode是否新增、删除
        List<String> files = FileUtils.listFilePathNames(monitorFileDirectory);
        Collection<String> fileUniqueList = fileINodeMap.values();
        List<String> uniqueMarkList = new ArrayList<>();
        for (String file : files) {
            String uniqueMark = fileUniqueMark.getFileUniqueMark(file);
            uniqueMarkList.add(uniqueMark);
            if (!fileUniqueList.contains(uniqueMark)) {
                for (FileChangeListener listener : listeners) {
                    listener.onFileCreate(file, uniqueMark);
                }
            }
            for (FileChangeListener listener : listeners) {
                listener.onFileChange(file, uniqueMark);
            }
        }
        for (Map.Entry<String, String> entry : fileINodeMap.entrySet()) {
            if (!uniqueMarkList.contains(entry.getValue())) {
                for (FileChangeListener listener : listeners) {
                    listener.onFileDelete(entry.getKey(), entry.getValue());
                }
            }
        }
        buildFileInodeMap();
    }
}
