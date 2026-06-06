package com.moyu.flowsub.qiniu;

import java.time.Instant;

/**
 * Kodo 上传后的统一结果，归档模块只关心 key、访问地址和大小。
 */
public record KodoUploadResult(
        String key,
        String url,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
}
