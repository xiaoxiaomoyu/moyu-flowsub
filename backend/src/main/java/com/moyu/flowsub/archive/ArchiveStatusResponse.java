package com.moyu.flowsub.archive;

import java.time.Instant;
import java.util.List;

/**
 * 会话归档状态聚合响应，用于历史会话页和会后总结页展示。
 */
public record ArchiveStatusResponse(
        String sessionId,
        ArchiveStatus status,
        String message,
        String summaryMarkdown,
        List<ArchiveResourceResponse> resources,
        Instant updatedAt
) {
}
