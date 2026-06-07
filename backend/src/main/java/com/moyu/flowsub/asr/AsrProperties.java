package com.moyu.flowsub.asr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyu.flowsub.asr")
public record AsrProperties(
        boolean mockEnabled,
        int chunkDurationMs
) {
}
