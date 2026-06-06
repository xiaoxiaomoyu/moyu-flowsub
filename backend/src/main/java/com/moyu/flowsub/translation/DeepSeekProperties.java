package com.moyu.flowsub.translation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek OpenAI-compatible 调用配置。真实密钥只从环境变量读取，不写入仓库。
 */
@ConfigurationProperties(prefix = "moyu.flowsub.deepseek")
public record DeepSeekProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String model,
        int timeoutMs,
        double temperature
) {
}
