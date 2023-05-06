package com.xiaomi.mone.log.common;

import lombok.extern.slf4j.Slf4j;
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
}
