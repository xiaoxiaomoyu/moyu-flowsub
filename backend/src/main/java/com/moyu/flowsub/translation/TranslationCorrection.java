package com.moyu.flowsub.translation;

/**
 * 翻译 Provider 给出的历史字幕修正建议。
 */
public record TranslationCorrection(
        String segmentId,
        String newSourceText,
        String newTranslatedText,
        String reason
) {
}
