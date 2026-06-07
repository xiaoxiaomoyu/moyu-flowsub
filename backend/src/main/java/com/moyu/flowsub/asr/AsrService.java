package com.moyu.flowsub.asr;

import com.moyu.flowsub.audio.AudioChunkMeta;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class AsrService {

    private final List<AsrProvider> providers;

    public AsrService(List<AsrProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(AsrProvider::priority))
                .toList();
    }

    public AsrStreamSession start(String sessionId, AudioChunkMeta meta) {
        for (AsrProvider provider : providers) {
            AsrProviderStatusPayload status = provider.status();
            if (!status.available()) {
                continue;
            }
            try {
                return provider.start(sessionId, meta);
            } catch (Exception e) {
                throw new AsrProviderUnavailableException(
                        status.provider() + " 启动失败：" + e.getMessage(), e);
            }
        }
        throw new AsrProviderUnavailableException("没有可用的 ASR Provider，请配置 Qwen DashScope API Key。");
    }

    public AsrProviderStatusPayload currentStatus() {
        for (AsrProvider provider : providers) {
            AsrProviderStatusPayload status = provider.status();
            if (status.available()) {
                return status;
            }
        }
        return new AsrProviderStatusPayload("未启用", false, false,
                "请配置 Qwen DashScope API Key。",
                false, "请将 DASHSCOPE_API_KEY 填入 .env 文件。", "NONE");
    }
}
