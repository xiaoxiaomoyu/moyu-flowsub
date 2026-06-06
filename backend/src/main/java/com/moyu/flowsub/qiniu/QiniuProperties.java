package com.moyu.flowsub.qiniu;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 七牛云配置占位。第一阶段只读取配置状态，不执行真实上传。
 */
@ConfigurationProperties(prefix = "qiniu")
public record QiniuProperties(
        boolean enabled,
        String accessKey,
        String secretKey,
        String bucket,
        String domain,
        String archivePrefix,
        boolean privateBucket,
        long downloadExpireSeconds
) {
}
