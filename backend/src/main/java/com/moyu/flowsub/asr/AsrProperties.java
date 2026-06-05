package com.moyu.flowsub.asr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ASR 配置集中管理。真实 Provider 不可用时默认启用 Mock 保底，避免演示中断。
 */
@ConfigurationProperties(prefix = "moyu.flowsub.asr")
public record AsrProperties(
        boolean mockEnabled,
        boolean funasrEnabled,
        String funasrEndpoint,
        String funasrWsEndpoint,
        int chunkDurationMs
) {
}
