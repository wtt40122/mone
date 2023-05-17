package com.xiaomi.mone.log.agent.common;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.xiaomi.mone.log.agent.channel.memory.UnixFileNode;
import com.xiaomi.mone.log.api.model.meta.LogPattern;
import com.xiaomi.mone.log.utils.NetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xiaomi.mone.log.common.Constant.*;
import static com.xiaomi.mone.log.common.PathUtils.PATH_WILDCARD;
import static com.xiaomi.mone.log.common.PathUtils.SEPARATOR;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2022/6/23 14:55
 */
@Slf4j
public class ChannelUtil {

    private Gson gson = GSON;

    private ChannelUtil() {

    }

    public static String queryCurrentCorrectIp(Map<String, String> ipPath, String pattern, List<String> ips) {
        if (ips.size() == 1) {
            return ips.get(ips.size() - 1);
        }
        String ip = "";
        try {
            if (!ipPath.isEmpty()) {
                String correctIpKey = ipPath.keySet().stream()
                        .filter(data -> pattern.contains(data))
                        .findFirst().get();
                ip = ipPath.get(correctIpKey);
            }
        } catch (Exception e) {
            ip = NetUtil.getLocalIp();
        }
        return ip;
    }

    public static String queryCurrentCorrectIp(List<LogPattern.IPRel> ips, String filePath) {
        if (ips.size() == 1) {
            return ips.get(ips.size() - 1).getIp();
        }
        String ip = "";
        try {
            if (!ips.isEmpty()) {
                ip = ips.stream()
                        .filter(data -> filePath.contains(data.getKey()))
                        .findFirst().get()
                        .getIp();
            }
        } catch (Exception e) {
            ip = NetUtil.getLocalIp();
        }
        return ip;
    }

    public static List<String> buildLogExpressList(String logPattern) {
        List<String> pathList = Lists.newArrayList();
        for (String filePath : logPattern.split(SYMBOL_COMMA)) {
            String filePrefix = StringUtils.substringBeforeLast(filePath, SEPARATOR);
            String multipleFileNames = StringUtils.substringAfterLast(filePath, SEPARATOR);
            if (filePath.contains(PATH_WILDCARD) && !filePath.contains(SYMBOL_MULTI)) {
                logPattern = logPattern.replaceAll("\\*", SYMBOL_MULTI);
            } else {
                logPattern = Arrays.stream(multipleFileNames.split("\\|"))
                        .map(s -> filePrefix + SEPARATOR + s + SYMBOL_MULTI)
                        .collect(Collectors.joining(DEFAULT_TAIL_SEPARATOR));
            }
            if (!logPattern.endsWith(SYMBOL_MULTI)) {
                logPattern = logPattern + SYMBOL_MULTI;
            }
            pathList.add(logPattern);
        }
        return pathList;
    }

    public static String buildSingleTimeExpress(String filePath) {
        String filePrefix = StringUtils.substringBeforeLast(filePath, SEPARATOR);
        String multipleFileName = StringUtils.substringAfterLast(filePath, SEPARATOR);
        if (!multipleFileName.contains(SYMBOL_MULTI)) {
            multipleFileName = multipleFileName.replaceAll("\\*", SYMBOL_MULTI);
        }
        if (multipleFileName.startsWith(PATH_WILDCARD)) {
            multipleFileName = multipleFileName.replaceFirst("\\*", SYMBOL_MULTI);
        }
        return String.format("%s%s%s", filePrefix, SEPARATOR, multipleFileName);
    }

    /**
     * unix系统文件才可以获取到值，否则为空对象
     *
     * @param filePath
     * @return
     */
    public static UnixFileNode buildUnixFileNode(String filePath) {
        try {
            BasicFileAttributes fileAttributes = Files.readAttributes(Paths.get(filePath), BasicFileAttributes.class);
            if (null != fileAttributes.fileKey()) {
                UnixFileNode unixFileNode = GSON.fromJson(GSON.toJson(fileAttributes.fileKey()), UnixFileNode.class);
                log.debug("origin file path:{},fileNode unixFileNode:{}", filePath, GSON.toJson(unixFileNode));
                return unixFileNode;
            }
        } catch (IOException e) {
            log.info("buildUnixFileNode error,filePath:{}", filePath, e);
        }
        return new UnixFileNode();
    }

}
