package com.moyu.flowsub.asr;

import com.moyu.flowsub.audio.AudioChunk;
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
        StringBuilder fallbackReasons = new StringBuilder();
        for (AsrProvider provider : providers) {
            AsrProviderStatusPayload status = provider.status();
            if (!status.available()) {
                appendReason(fallbackReasons, status.provider(), status.reason());
                continue;
            }
            try {
                return provider.start(sessionId, meta);
            } catch (Exception e) {
                appendReason(fallbackReasons, status.provider(), e.getMessage());
            }
        }
        return new EmptyAsrStreamSession(fallbackReasons.toString());
    }

    public AsrProviderStatusPayload currentStatus() {
        for (AsrProvider provider : providers) {
            AsrProviderStatusPayload status = provider.status();
            if (status.available()) {
                return status;
            }
        }
        return new AsrProviderStatusPayload("未启用", false, true, "没有可用的 ASR Provider。",
                false, "请配置七牛云 AI API Key、FunASR 地址或启用 Mock ASR。", "NONE");
    }

    private void appendReason(StringBuilder builder, String provider, String reason) {
        if (!builder.isEmpty()) {
            builder.append("；");
        }
        builder.append(provider).append("：").append(reason == null ? "不可用" : reason);
    }

    private static class EmptyAsrStreamSession implements AsrStreamSession {
        private final String reason;

        private EmptyAsrStreamSession(String reason) {
            this.reason = reason;
        }

        @Override
        public AsrProviderStatusPayload status() {
            return new AsrProviderStatusPayload("未启用", false, true, "没有可用的 ASR Provider。",
                    false, reason, "NONE");
        }

        @Override
        public List<AsrResult> accept(AudioChunk chunk) {
            return List.of();
        }

        @Override
        public void close() {
            // 空会话没有外部连接。
        }
    }
}
