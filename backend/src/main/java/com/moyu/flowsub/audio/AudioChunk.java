package com.moyu.flowsub.audio;

import java.time.Instant;

/**
 * 后端内部使用的音频块对象，包含二进制音频数据和前端上报的采样信息。
 */
public record AudioChunk(
        String sessionId,
        int chunkIndex,
        long timestamp,
        String format,
        int sampleRate,
        int channels,
        double level,
        byte[] data,
        Instant receivedAt
) {
}
