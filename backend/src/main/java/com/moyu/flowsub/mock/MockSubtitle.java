package com.moyu.flowsub.mock;

/**
 * 模拟字幕数据，保留英文原文和中文译文，方便演示双语同传效果。
 */
public record MockSubtitle(
        String segmentId,
        String sourceText,
        String translatedText,
        long asrLatencyMs,
        long translateLatencyMs
) {
}
