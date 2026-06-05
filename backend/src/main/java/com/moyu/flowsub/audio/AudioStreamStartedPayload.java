package com.moyu.flowsub.audio;

/**
 * 音频流启动后回传给前端的状态。
 */
public record AudioStreamStartedPayload(
        String input,
        String format,
        int sampleRate,
        int chunkDurationMs
) {
}
