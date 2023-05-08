package com.xiaomi.mone.log.agent.channel.wildcard.symbol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/8 15:56
 */
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
@Builder
public class FileSymbol {
    private String filePath;
    private String fileUniqueMark;
    private Long lastSendTime;
}
