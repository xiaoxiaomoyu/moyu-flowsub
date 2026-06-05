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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioStreamService {

    private static final int MAX_RECENT_CHUNKS = 24;

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
        private AsrStreamSession asrSession;
        private int chunkCount;
        private int subtitleCount;

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
            return state.asrSession.accept(chunk);
        } catch (Exception ignored) {
            // 真实 ASR 长连接可能在演示中途断开，立即重选 Provider，保证页面仍能继续出字幕。
            state.asrSession.close();
            state.asrSession = asrService.start(sessionId, meta);
            return state.asrSession.accept(chunk);
        }
    }
}
