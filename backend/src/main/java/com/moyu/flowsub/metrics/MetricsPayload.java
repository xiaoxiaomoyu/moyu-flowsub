package com.moyu.flowsub.metrics;

/**
 * 每次字幕推送后的延迟与计数指标，便于前端实时展示链路状态。
 */
public record MetricsPayload(
        long asrLatencyMs,
        long translateLatencyMs,
        long totalLatencyMs,
        int subtitleCount,
        int correctionCount,
        int audioChunkCount,
        String providerName,
        boolean providerFallback,
        String translationProviderName,
        boolean translationProviderFallback
) {
    public MetricsPayload(long asrLatencyMs,
                          long translateLatencyMs,
                          long totalLatencyMs,
                          int subtitleCount,
                          int correctionCount) {
        this(asrLatencyMs, translateLatencyMs, totalLatencyMs, subtitleCount, correctionCount,
                0, "Mock ASR", true, "Mock 翻译", true);
    }

    public MetricsPayload(long asrLatencyMs,
                          long translateLatencyMs,
                          long totalLatencyMs,
                          int subtitleCount,
                          int correctionCount,
                          int audioChunkCount,
                          String providerName,
                          boolean providerFallback) {
        this(asrLatencyMs, translateLatencyMs, totalLatencyMs, subtitleCount, correctionCount,
                audioChunkCount, providerName, providerFallback, "等待翻译", false);
    }
}
