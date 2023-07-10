package com.xiaomi.mone.log.agent.channel.process;

/**
 * @author wtt
 * @version 1.0
 * @description 配置处理器
 * @date 2023/7/10 15:42
 */
public interface ConfigProcessor<T> {
    boolean process(T config);
}
