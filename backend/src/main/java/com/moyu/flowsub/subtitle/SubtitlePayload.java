package com.moyu.flowsub.subtitle;

/**
 * WebSocket 推送给前端的单条双语字幕。
 */
public record SubtitlePayload(
        String segmentId,
        String sourceText,
        String translatedText,
        String status,
        int version,
        boolean isCorrected,
        long latencyMs
) {
}
