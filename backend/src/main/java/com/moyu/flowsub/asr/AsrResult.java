package com.moyu.flowsub.asr;

/**
 * ASR Provider 的统一识别结果。第二阶段先输出英文原文字幕，中文翻译后续阶段接入。
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
