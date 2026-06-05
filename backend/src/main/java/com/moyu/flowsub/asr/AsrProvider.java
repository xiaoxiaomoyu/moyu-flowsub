package com.moyu.flowsub.asr;

import com.moyu.flowsub.audio.AudioChunk;
import com.moyu.flowsub.audio.AudioChunkMeta;

/**
 * ASR 统一适配接口。第三阶段开始按“会话级流式连接”管理真实识别服务。
 */
public interface AsrProvider {

    String name();

    int priority();

    AsrProviderStatusPayload status();

    AsrStreamSession start(String sessionId, AudioChunkMeta meta) throws Exception;
}
