package com.moyu.flowsub.audio;

import com.moyu.flowsub.asr.AsrProviderStatusPayload;
import com.moyu.flowsub.asr.AsrResult;
import com.moyu.flowsub.asr.AsrService;
import com.moyu.flowsub.asr.AsrStreamSession;
import com.moyu.flowsub.session.SessionService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioStreamService {

    private static final int MAX_RECENT_CHUNKS = 24;
    private static final int NO_RESULT_FALLBACK_CHUNKS = 20;

    private final SessionService sessionService;
    private final AsrService asrService;
    private final Map<String, StreamState> streams = new ConcurrentHashMap<>();

    public AudioStreamService(SessionService sessionService, AsrService asrService) {
        this.sessionService = sessionService;
        this.asrService = asrService;
    }

    public AudioStreamStartedPayload start(String sessionId, AudioChunkMeta meta) {
        sessionService.markRunning(sessionId);
        AsrStreamSession asrSession = asrService.start(sessionId, meta);
        streams.put(sessionId, new StreamState(asrSession));
        int sampleRate = meta == null || meta.sampleRate() <= 0 ? 16000 : meta.sampleRate();
        return new AudioStreamStartedPayload("麦克风", "pcm_s16le", sampleRate, 300);
    }

    public AudioStreamProcessResult accept(String sessionId, AudioChunkMeta meta, byte[] data) {
        StreamState state = streams.computeIfAbsent(sessionId,
                ignored -> new StreamState(asrService.start(sessionId, meta)));
        AudioChunk chunk = new AudioChunk(
                sessionId,
                meta.chunkIndex(),
                meta.timestamp(),
                meta.format(),
                meta.sampleRate(),
                meta.channels(),
                meta.level(),
                data,
                Instant.now()
        );
        state.remember(chunk);
        List<AsrResult> results = acceptWithFallback(sessionId, meta, state, chunk);
        for (AsrResult result : results) {
            if ("FINAL".equals(result.status())) {
                state.subtitleCount++;
            }
        }
        return new AudioStreamProcessResult(state.chunkCount, state.subtitleCount, results, state.asrSession.status());
    }

    public AudioStreamStoppedPayload stop(String sessionId) {
        StreamState removed = streams.remove(sessionId);
        if (removed == null) {
            return new AudioStreamStoppedPayload(0, 0);
        }
        removed.asrSession.stop();
        return new AudioStreamStoppedPayload(removed.chunkCount, removed.subtitleCount);
    }

    public AsrProviderStatusPayload providerStatus() {
        return asrService.currentStatus();
    }

    public AsrProviderStatusPayload providerStatus(String sessionId) {
        StreamState state = streams.get(sessionId);
        return state == null ? providerStatus() : state.asrSession.status();
    }

    private static class StreamState {
        private final Deque<AudioChunk> recentChunks = new ArrayDeque<>();
        private final Set<String> excludedProviders = new HashSet<>();
        private AsrStreamSession asrSession;
        private int chunkCount;
        private int subtitleCount;
        private int chunksWithoutText;

        private StreamState(AsrStreamSession asrSession) {
            this.asrSession = asrSession;
        }

        private synchronized void remember(AudioChunk chunk) {
            chunkCount++;
            recentChunks.addLast(chunk);
            // 只保留最近少量音频块，避免真实采集时内存缓存无界增长。
            while (recentChunks.size() > MAX_RECENT_CHUNKS) {
                recentChunks.removeFirst();
            }
        }
    }

    private List<AsrResult> acceptWithFallback(String sessionId, AudioChunkMeta meta, StreamState state, AudioChunk chunk) {
        try {
            List<AsrResult> results = state.asrSession.accept(chunk);
            if (!results.isEmpty()) {
                state.chunksWithoutText = 0;
                return results;
            }
            return fallbackIfProviderSilent(sessionId, meta, state, chunk);
        } catch (Exception ignored) {
            // 真实 ASR 长连接可能在演示中途断开，立即重选 Provider，保证页面仍能继续出字幕。
            switchProvider(sessionId, meta, state, "当前 ASR 长连接处理音频失败，已自动降级。");
            return state.asrSession.accept(chunk);
        }
    }

    private List<AsrResult> fallbackIfProviderSilent(String sessionId,
                                                     AudioChunkMeta meta,
                                                     StreamState state,
                                                     AudioChunk chunk) {
        AsrProviderStatusPayload status = state.asrSession.status();
        if ("MOCK".equals(status.endpointType()) || "NONE".equals(status.endpointType())) {
            return List.of();
        }
        state.chunksWithoutText++;
        if (state.chunksWithoutText < NO_RESULT_FALLBACK_CHUNKS) {
            return List.of();
        }

        String reason = "已连续接收 " + state.chunksWithoutText + " 个音频块，但 "
                + status.provider() + " 未返回任何字幕文本，已自动降级。";
        switchProvider(sessionId, meta, state, reason);
        return state.asrSession.accept(chunk);
    }

    private void switchProvider(String sessionId, AudioChunkMeta meta, StreamState state, String reason) {
        AsrProviderStatusPayload currentStatus = state.asrSession.status();
        state.excludedProviders.add(currentStatus.provider());
        state.asrSession.close();
        state.chunksWithoutText = 0;
        // 降级后保留原始失败原因，前端可以看到为什么没有继续使用七牛云或 FunASR。
        state.asrSession = asrService.startExcluding(sessionId, meta, Set.copyOf(state.excludedProviders), reason);
    }
}
