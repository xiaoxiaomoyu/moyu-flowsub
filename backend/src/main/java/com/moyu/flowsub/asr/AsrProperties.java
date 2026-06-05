package com.moyu.flowsub.asr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ASR 配置集中管理。第二阶段默认启用 Mock 保底，避免没有云端权限时演示中断。
 */
@ConfigurationProperties(prefix = "moyu.flowsub.asr")
public record AsrProperties(
        boolean mockEnabled,
        String funasrEndpoint,
        int chunkDurationMs
) {
}
