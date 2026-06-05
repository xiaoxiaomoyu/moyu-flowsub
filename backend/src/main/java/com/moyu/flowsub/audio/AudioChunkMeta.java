package com.moyu.flowsub.audio;

/**
 * 前端每个音频二进制块之前发送的元数据，用于让后端正确关联下一帧 Binary Message。
 */
public record AudioChunkMeta(
        int chunkIndex,
        long timestamp,
        String format,
        int sampleRate,
        int channels,
        double level
) {
}
