package com.xiaomi.mone.log.agent.channel.mark;

import com.xiaomi.mone.log.agent.channel.memory.UnixFileNode;
import com.xiaomi.mone.log.agent.common.ChannelUtil;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/8 10:56
 */
public class DefaultFileUniqueMark implements FileUniqueMark<String> {
    @Override
    public String getFileUniqueMark(String filePath) {
        UnixFileNode unixFileNode = ChannelUtil.buildUnixFileNode(filePath);
        return unixFileNode.getSt_ino() + "";
    }
}
