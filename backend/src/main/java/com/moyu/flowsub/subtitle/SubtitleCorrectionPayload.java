package com.moyu.flowsub.subtitle;

/**
 * 字幕修正事件，用于模拟“根据后文回滚修正历史字幕”的核心亮点。
 */
public record SubtitleCorrectionPayload(
        String segmentId,
        String oldSourceText,
        String newSourceText,
        String oldTranslatedText,
        String newTranslatedText,
        int version,
        String reason
) {
}
