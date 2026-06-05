package com.moyu.flowsub.asr;

import com.moyu.flowsub.audio.AudioChunk;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AsrService {

    private final List<AsrProvider> providers;

    public AsrService(List<AsrProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(AsrProvider::priority))
                .toList();
    }

    public Optional<AsrResult> recognize(AudioChunk chunk) {
        for (AsrProvider provider : providers) {
            Optional<AsrResult> result = provider.recognize(chunk);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    public AsrProviderStatusPayload currentStatus() {
        for (AsrProvider provider : providers) {
            AsrProviderStatusPayload status = provider.status();
            if (status.available()) {
                return status;
            }
        }
        return providers.stream()
                .map(AsrProvider::status)
                .filter(AsrProviderStatusPayload::available)
                .findFirst()
                .orElse(new AsrProviderStatusPayload("未启用", false, true, "没有可用的 ASR Provider。"));
    }

    public AsrProviderStatusPayload selectedProviderStatus() {
        // 当前真实可产出字幕的是 Provider 链中第一个返回结果的实现；默认 Mock 作为第二阶段兜底。
        return providers.stream()
                .filter(provider -> provider instanceof MockAsrProvider)
                .findFirst()
                .map(AsrProvider::status)
                .orElseGet(this::currentStatus);
    }
}
