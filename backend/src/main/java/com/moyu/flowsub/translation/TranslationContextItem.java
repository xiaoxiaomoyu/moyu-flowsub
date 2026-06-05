package com.moyu.flowsub.translation;

/**
 * 翻译上下文窗口中的稳定字幕，用于让大模型根据后文修正前文。
 */
public record TranslationContextItem(
        String segmentId,
        String sourceText,
        String translatedText,
        int version
) {
}
