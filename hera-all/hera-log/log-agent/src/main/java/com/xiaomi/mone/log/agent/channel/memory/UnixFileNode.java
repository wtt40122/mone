package com.xiaomi.mone.log.agent.channel.memory;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/16 14:03
 */
@Data
@EqualsAndHashCode
public class UnixFileNode {
    private Long st_dev;
    private Long st_ino;
}
