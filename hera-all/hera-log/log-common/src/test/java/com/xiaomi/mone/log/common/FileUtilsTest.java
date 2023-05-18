package com.xiaomi.mone.log.common;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/6 15:47
 */
@Slf4j
public class FileUtilsTest {

    @Test
    public void test01() {
        String logPattern = "/home/work/log/*.log";
        List<String> files = FileUtils.getFilesByWildcard(logPattern);
        files.stream().forEach(System.out::println);
        log.info("files size:{}", files.size());
    }

    @Test
    public void test02() {
        String logPattern = "/home/work/*/trace.log";
        List<String> files = FileUtils.getFilesByWildcard(logPattern);
        files.stream().forEach(System.out::println);
        log.info("files size:{}", files.size());
    }

    @Test
    public void test03() {
        String logPattern = "/home/work/*/*.log";
        List<String> files = FileUtils.getFilesByWildcard(logPattern);
        files.stream().forEach(System.out::println);
        log.info("files size:{}", files.size());
    }

    @Test
    public void test04() {
        String logPattern = "/home/work/*/*.log";
        String monitorDirectory = FileUtils.getMonitorDirectory(logPattern);
        Assert.assertEquals("/home/work/", monitorDirectory);

        String logPattern1 = "/home/work/server/*.log";
        String monitorDirectory1 = FileUtils.getMonitorDirectory(logPattern1);
        Assert.assertEquals("/home/work/server/", monitorDirectory1);

        String logPattern2 = "/home/work/server/server.log";
        String monitorDirectory2 = FileUtils.getMonitorDirectory(logPattern2);
        Assert.assertEquals("/home/work/server/", monitorDirectory2);
    }

    @Test
    public void test05(){
        String logPattern = "/home/work/*/*.log";
        String filePath = "/home/work/test/trace.log";
        boolean b = FileUtils.belongToLogPath(logPattern, filePath);
        Assert.assertTrue(b);
    }
}
