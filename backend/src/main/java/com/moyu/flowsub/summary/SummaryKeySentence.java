package com.moyu.flowsub.summary;

/**
 * 重点句保留中英双语，方便用户回看原意和译文。
 */
public record SummaryKeySentence(
        String sourceText,
        String translatedText,
        String reason
) {
}
