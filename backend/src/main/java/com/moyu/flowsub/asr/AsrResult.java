package com.moyu.flowsub.asr;

/**
 * ASR Provider 的统一识别结果。FINAL 结果会进入翻译链路，PARTIAL 结果只做临时展示。
 */
public record AsrResult(
        String segmentId,
        String text,
        String status,
        long latencyMs,
        int chunkIndex,
        String providerName
) {
}
