package com.moyu.flowsub.asr;

import com.moyu.flowsub.audio.AudioChunk;

import java.util.Optional;

/**
 * ASR 统一适配接口，后续可把七牛云智能语音、FunASR 或其他识别服务挂到同一条链路上。
 */
public interface AsrProvider {

    String name();

    int priority();

    AsrProviderStatusPayload status();

    Optional<AsrResult> recognize(AudioChunk chunk);
}
