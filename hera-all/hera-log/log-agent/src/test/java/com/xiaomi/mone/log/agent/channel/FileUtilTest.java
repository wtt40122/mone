package com.xiaomi.mone.log.agent.channel;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.File;
import java.io.FileFilter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/6 15:26
 */
@Slf4j
public class FileUtilTest {

    @Test
    public void findAllFileTest() {
//        String fileName = "/home/work/log";
        String fileName = "/home/work/log/";
        Pattern pattern = Pattern.compile("/*.log");
        FileFilter fileFilter = pathname -> pattern.matcher(pathname.getName()).matches();
        List<File> files = FileUtil.loopFiles(fileName, fileFilter);
        log.info("file size:{}", files.size());
        for (File file : files) {
            log.info("fileName:{}", file.getAbsoluteFile());
        }
    }
}
