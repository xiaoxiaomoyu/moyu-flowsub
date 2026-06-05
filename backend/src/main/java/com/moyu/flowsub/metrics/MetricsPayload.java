package com.moyu.flowsub.metrics;

/**
 * 每次字幕推送后的延迟与计数指标，便于前端实时展示链路状态。
 */
public record MetricsPayload(
        long asrLatencyMs,
        long translateLatencyMs,
        long totalLatencyMs,
        int subtitleCount,
        int correctionCount
) {
}
