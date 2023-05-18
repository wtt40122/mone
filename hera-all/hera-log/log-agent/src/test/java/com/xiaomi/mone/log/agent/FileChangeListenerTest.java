package com.xiaomi.mone.log.agent;

import com.xiaomi.mone.log.agent.channel.wildcard.listen.FileChangeListener;
import com.xiaomi.mone.log.agent.channel.wildcard.listen.FileChangeMonitor;
import com.xiaomi.mone.log.agent.channel.wildcard.listen.FileChangeObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;

import static com.xiaomi.mone.log.common.Constant.GSON;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/18 15:12
 */
@Slf4j
public class FileChangeListenerTest {

    @Test
    public void testFileChange() throws IOException {
        String monitorDirectory = "/home/work/log/123";

        FileChangeListener fileChangeListener = new FileChangeListener() {

            @Override
            public void onFileCreate(String file, String uniqueMark) {
                log.info("onFileCreate:{},uniqueMark:{}", file, uniqueMark);
            }

            @Override
            public void onFileChange(String file, String uniqueMark) {
                log.info("onFileChange:{},uniqueMark:{}", file, uniqueMark);
            }

            @Override
            public void onFileDelete(String file, String uniqueMark) {
                log.info("onFileDelete:{},uniqueMark:{}", file, uniqueMark);
            }
        };

        // 默认 遍历文件 间隔时间 5s
        FileChangeMonitor monitor = new FileChangeMonitor(5000);
        log.info("agent monitor files:{}", GSON.toJson(monitorDirectory));
        FileChangeObserver observer = new FileChangeObserver(monitorDirectory);
        observer.addListener(fileChangeListener);
        log.info("## agent monitor file:{}, filePattern:{}", monitorDirectory, monitorDirectory);
        monitor.addObserver(observer);
        try {
            monitor.start();
            log.info("## agent monitor filePattern:{} started", monitorDirectory);
        } catch (Exception e) {
            log.error(String.format("agent file monitor start err,monitor filePattern:%s", monitorDirectory), e);
        }
        System.in.read();
    }
}
