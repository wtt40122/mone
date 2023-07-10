package com.xiaomi.mone.log.agent.channel.process;

import com.xiaomi.mone.log.agent.channel.ChannelDefine;

import java.util.List;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/7/10 15:45
 */
public class AdditionProcessor implements ConfigProcessor<List<ChannelDefine>> {
    @Override
    public boolean process(List<ChannelDefine> config) {
        return false;
    }
}
