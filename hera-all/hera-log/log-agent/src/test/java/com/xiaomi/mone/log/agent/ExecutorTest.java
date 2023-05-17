package com.xiaomi.mone.log.agent;

import com.xiaomi.mone.log.agent.common.ExecutorUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/17 16:35
 */
@Slf4j
public class ExecutorTest {

    @Test
    public void testJobDelete() throws InterruptedException, IOException {
        ScheduledFuture<?> lastFileLineScheduledFuture = ExecutorUtil.scheduleAtFixedRate(() -> {
            log.info("test delete");
        }, 30, 30, TimeUnit.SECONDS);
        TimeUnit.MINUTES.sleep(1);
        lastFileLineScheduledFuture.cancel(true);
        System.in.read();
    }
}
