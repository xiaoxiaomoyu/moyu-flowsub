package com.moyu.flowsub.asr;

import com.moyu.flowsub.audio.AudioChunk;
import com.moyu.flowsub.audio.AudioChunkMeta;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class AsrService {

    private final List<AsrProvider> providers;

    public AsrService(List<AsrProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(AsrProvider::priority))
                .toList();
    }

    public AsrStreamSession start(String sessionId, AudioChunkMeta meta) {
        return start(sessionId, meta, Set.of(), "");
    }

    public AsrStreamSession startExcluding(String sessionId,
                                           AudioChunkMeta meta,
                                           Set<String> excludedProviders,
                                           String initialReason) {
        return start(sessionId, meta, excludedProviders, initialReason);
    }

    private AsrStreamSession start(String sessionId,
                                   AudioChunkMeta meta,
                                   Set<String> excludedProviders,
                                   String initialReason) {
        StringBuilder fallbackReasons = new StringBuilder();
        if (initialReason != null && !initialReason.isBlank()) {
            fallbackReasons.append(initialReason);
        }
        for (AsrProvider provider : providers) {
            if (excludedProviders.contains(provider.name())) {
                appendReason(fallbackReasons, provider.name(), "本次会话已触发降级，跳过重连。");
                continue;
            }
            AsrProviderStatusPayload status = provider.status();
            if (!status.available()) {
                appendReason(fallbackReasons, status.provider(), status.reason());
                continue;
            }
            try {
                AsrStreamSession session = provider.start(sessionId, meta);
                if (!fallbackReasons.isEmpty()) {
                    return new FallbackReasonAsrStreamSession(session, fallbackReasons.toString());
                }
                return session;
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
                false, "请配置 Qwen API Key 或启用 Mock ASR。", "NONE");
    }

    private void appendReason(StringBuilder builder, String provider, String reason) {
        if (!builder.isEmpty()) {
            builder.append("；");
        }
        builder.append(provider).append("：").append(reason == null ? "不可用" : reason);
    }

    private static class FallbackReasonAsrStreamSession implements AsrStreamSession {
        private final AsrStreamSession delegate;
        private final String fallbackReason;

        private FallbackReasonAsrStreamSession(AsrStreamSession delegate, String fallbackReason) {
            this.delegate = delegate;
            this.fallbackReason = fallbackReason;
        }

        @Override
        public AsrProviderStatusPayload status() {
            AsrProviderStatusPayload status = delegate.status();
            return new AsrProviderStatusPayload(status.provider(), status.available(), true, status.message(),
                    status.connected(), fallbackReason, status.endpointType());
        }

        @Override
        public List<AsrResult> accept(AudioChunk chunk) {
            return delegate.accept(chunk);
        }

        @Override
        public List<AsrResult> stop() {
            return delegate.stop();
        }

        @Override
        public void close() {
            delegate.close();
        }
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
