package com.moyu.flowsub.qwen;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qwen")
public record QwenProperties(
        boolean enabled,
        String apiKey,
        String asrModel,
        String translationModel,
        String summaryModel,
        String correctionModel,
        String baseUrl,
        int timeoutMs,
        double temperature
) {
}
