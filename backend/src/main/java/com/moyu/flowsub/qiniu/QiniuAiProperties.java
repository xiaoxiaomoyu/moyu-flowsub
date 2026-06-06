package com.moyu.flowsub.qiniu;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 七牛云 QNAIGC AI 网关配置，ASR 通过 Bearer token 鉴权连接 WebSocket。
 */
@ConfigurationProperties(prefix = "qiniu.ai")
public record QiniuAiProperties(
        boolean asrEnabled,
        String apiKey,
        String asrWsUrl
) {
}
