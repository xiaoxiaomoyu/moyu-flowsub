package com.moyu.flowsub.qiniu;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 七牛云 AI Token 配置，和 Kodo AK/SK 分开管理，避免误用存储密钥。
 */
@ConfigurationProperties(prefix = "qiniu.ai")
public record QiniuAiProperties(
        boolean asrEnabled,
        String apiKey,
        String asrWsUrl
) {
}
