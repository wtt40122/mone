package com.xiaomi.mone.log.agent.channel.wildcard;

import com.xiaomi.mone.log.agent.channel.ChannelDefine;
import com.xiaomi.mone.log.agent.channel.ChannelService;
import com.xiaomi.mone.log.agent.channel.ChannelState;
import com.xiaomi.mone.log.agent.channel.file.MonitorFile;
import com.xiaomi.mone.log.agent.export.MsgExporter;
import com.xiaomi.mone.log.agent.input.Input;
import com.xiaomi.mone.log.api.model.meta.FilterConf;
import com.xiaomi.mone.log.common.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author wtt
 * @version 1.0
 * @description 通配符匹配采集文件处理
 * @date 2023/5/6 15:05
 */
@Slf4j
public class WildcardChannelService implements ChannelService {

    private final ChannelDefine channelDefine;

    public WildcardChannelService(ChannelDefine channelDefine) {
        this.channelDefine = channelDefine;
    }


    @Override
    public void start() {
        Input input = channelDefine.getInput();
        String logPattern = input.getLogPattern();
        //1，找到所有的采集地址
        List<String> filePaths = FileUtils.getFilesByWildcard(logPattern);
        //2.文件地址与文件唯一标识符的对应关系
        //
    }

    @Override
    public void refresh(ChannelDefine channelDefine, MsgExporter msgExporter) {

    }

    @Override
    public void stopFile(List<String> filePrefixList) {

    }

    @Override
    public ChannelState state() {
        return null;
    }

    @Override
    public String instanceId() {
        return null;
    }

    @Override
    public void filterRefresh(List<FilterConf> confs) {

    }

    @Override
    public void reOpen(String filePath) {

    }

    @Override
    public List<MonitorFile> getMonitorPathList() {
        return null;
    }

    @Override
    public void delayDeletionFinishedFile() {

    }

    @Override
    public void close() {

    }
}
