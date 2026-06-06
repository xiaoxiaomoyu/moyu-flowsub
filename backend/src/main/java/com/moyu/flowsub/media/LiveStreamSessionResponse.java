package com.moyu.flowsub.media;

import java.time.Instant;

public record LiveStreamSessionResponse(
        String sessionId,
        String streamName,
        String publishUrl,
        String playUrl,
        String whepUrl,
        LiveIngestStatus ingestStatus,
        String provider,
        boolean fallback,
        String reason,
        Instant startedAt,
        Instant updatedAt
) {
}
