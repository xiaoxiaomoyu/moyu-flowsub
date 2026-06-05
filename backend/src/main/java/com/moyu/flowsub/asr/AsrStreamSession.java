package com.moyu.flowsub.asr;

import com.moyu.flowsub.audio.AudioChunk;

import java.util.List;

/**
 * 单个同传会话内的 ASR 流式连接，负责接收音频块并产出识别结果。
 */
public interface AsrStreamSession extends AutoCloseable {

    AsrProviderStatusPayload status();

    List<AsrResult> accept(AudioChunk chunk);

    default List<AsrResult> stop() {
        close();
        return List.of();
    }

    @Override
    void close();
}
