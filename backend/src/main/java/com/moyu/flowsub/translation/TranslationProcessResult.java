package com.moyu.flowsub.translation;

import com.moyu.flowsub.subtitle.SubtitleCorrectionPayload;
import com.moyu.flowsub.subtitle.SubtitlePayload;

import java.util.List;

/**
 * WebSocket 层需要推送的翻译结果、修正记录和指标数据。
 */
public record TranslationProcessResult(
        SubtitlePayload subtitle,
        List<SubtitleCorrectionPayload> corrections,
        TranslationProviderStatusPayload providerStatus,
        long translateLatencyMs,
        int totalCorrectionCount
) {
}
