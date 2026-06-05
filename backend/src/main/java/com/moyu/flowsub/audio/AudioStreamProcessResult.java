package com.moyu.flowsub.audio;

import com.moyu.flowsub.asr.AsrProviderStatusPayload;
import com.moyu.flowsub.asr.AsrResult;

import java.util.List;

/**
 * 单个音频块处理后的结果，WebSocket 层据此决定是否推送字幕和指标。
 */
public record AudioStreamProcessResult(
        int chunkCount,
        int subtitleCount,
        List<AsrResult> asrResults,
        AsrProviderStatusPayload providerStatus
) {
}
