package com.moyu.flowsub.audio;

/**
 * 音频流停止时返回本轮接收统计，方便前端确认麦克风已释放且后端状态已清理。
 */
public record AudioStreamStoppedPayload(
        int chunkCount,
        int subtitleCount
) {
}
