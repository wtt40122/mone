package com.xiaomi.mone.log.manager.service.extension.common;

import com.xiaomi.youpin.docean.anno.Service;
import lombok.extern.slf4j.Slf4j;

import static com.xiaomi.mone.log.common.Constant.LOG_MANAGE_PREFIX;
import static com.xiaomi.mone.log.manager.service.extension.common.CommonExtensionService.DEFAULT_COMMON_EXTENSION_SERVICE_KEY;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/4/27 16:13
 */
@Service(name = DEFAULT_COMMON_EXTENSION_SERVICE_KEY)
@Slf4j
public class DefaultCommonExtensionService implements CommonExtensionService {

    @Override
    public String getLogManagePrefix() {
        return LOG_MANAGE_PREFIX;
    }
}