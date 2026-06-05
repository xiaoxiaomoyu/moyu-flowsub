package com.moyu.flowsub.translation;

import java.util.List;

/**
 * 翻译 Provider 的统一结果，包含当前句译文和可选历史修正。
 */
public record TranslationResult(
        String translatedText,
        long latencyMs,
        String providerName,
        boolean fallback,
        List<TranslationCorrection> corrections
) {
}
