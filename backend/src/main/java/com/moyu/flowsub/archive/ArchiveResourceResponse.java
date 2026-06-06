package com.moyu.flowsub.archive;

import java.time.Instant;

/**
 * 前端展示的单个归档资源，url 为空表示只保留了本地内存副本。
 */
public record ArchiveResourceResponse(
        ArchiveResourceType type,
        String key,
        String url,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
}
