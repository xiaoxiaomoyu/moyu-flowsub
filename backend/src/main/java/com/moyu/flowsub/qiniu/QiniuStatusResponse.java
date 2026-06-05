package com.moyu.flowsub.qiniu;

/**
 * 返回给前端的七牛云配置检查结果。
 */
public record QiniuStatusResponse(
        boolean enabled,
        boolean bucketConfigured,
        boolean domainConfigured,
        String message
) {
}
