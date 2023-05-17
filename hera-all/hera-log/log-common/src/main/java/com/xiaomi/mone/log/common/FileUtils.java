package com.xiaomi.mone.log.common;

import cn.hutool.core.io.FileUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/6 15:39
 */
@Slf4j
public class FileUtils {

    @Getter
    private static final String PATH_WILDCARD = "*";
    //    private static final String SEPARATOR = FileSystems.getDefault().getSeparator();
    private static final String SEPARATOR = "/";

    private FileUtils() {
    }

    /**
     * 分2种
     * 1.最后一级目录是包含*的，即文件是通配符
     * 2.目录是*,后缀是通配符或者不是通配符
     *
     * @param logPattern
     * @return
     */
    public static List<String> getFilesByWildcard(String logPattern) {
        //1.提取目录
        String matchDirectory = getMatchDirectory(logPattern);
        //2.设置文件过滤符
        String fileStorey = StringUtils.substringAfterLast(logPattern, SEPARATOR);
        FileFilter fileFilter = getFileFilter(fileStorey);
        List<File> files = FileUtil.loopFiles(matchDirectory, fileFilter);
        log.info("logPattern:{},match file size:{}", logPattern, files.size());
        return files.stream().map(File::getAbsolutePath).collect(Collectors.toList());
    }

    /**
     * 获取查询文件的目录
     * 获取第一层包含*前边的目录即可
     *
     * @param logPattern
     * @return
     */
    private static String getMatchDirectory(String logPattern) {
        String matchStory = Arrays.stream(logPattern.split(SEPARATOR)).filter(name -> name.contains(PATH_WILDCARD)).findFirst().get();
        return StringUtils.substringBefore(logPattern, matchStory);
    }

    private static FileFilter getFileFilter(String file) {
        Pattern pattern = escapeWildcard(file);
        return pathFile -> pattern.matcher(pathFile.getName()).matches();
    }

    private static Pattern escapeWildcard(String str) {
        try {
            if (!str.contains(PATH_WILDCARD)) {
                str = str + "." + PATH_WILDCARD;
            }
            Pattern pattern = Pattern.compile(str);
            return pattern;
        } catch (Exception e) {
            Pattern pattern = Pattern.compile(str.replace(PATH_WILDCARD, "." + PATH_WILDCARD));
            return pattern;
        }
    }

    public static String getMonitorDirectory(String filePath) {
        try {
            return getMatchDirectory(filePath);
        } catch (Exception e) {
            return StringUtils.substringBeforeLast(filePath, SEPARATOR) + SEPARATOR;
        }
    }

    public static boolean belongToLogPath(String pathWildcard, String filePath) {
        Pattern pattern = Pattern.compile(pathWildcard);
        return pattern.matcher(filePath).find();
    }
}
