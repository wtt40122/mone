package com.xiaomi.mone.log.agent.channel.wildcard.symbol;

import lombok.*;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/8 15:56
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class FileSymbol {
    private String filePath;
    private String fileUniqueMark;
    private Long lineNumber;
    private Long lastSendTime;
}
