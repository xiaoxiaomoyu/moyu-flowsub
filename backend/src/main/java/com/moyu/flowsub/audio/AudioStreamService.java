package com.moyu.flowsub.audio;

import com.moyu.flowsub.asr.AsrProviderStatusPayload;
import com.moyu.flowsub.asr.AsrResult;
import com.moyu.flowsub.asr.AsrService;
import com.moyu.flowsub.session.SessionService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
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
        streams.put(sessionId, new StreamState());
        int sampleRate = meta == null || meta.sampleRate() <= 0 ? 16000 : meta.sampleRate();
        return new AudioStreamStartedPayload("麦克风", "pcm_s16le", sampleRate, 750);
    }

    public AudioStreamProcessResult accept(String sessionId, AudioChunkMeta meta, byte[] data) {
        StreamState state = streams.computeIfAbsent(sessionId, ignored -> new StreamState());
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
        AsrResult result = asrService.recognize(chunk).orElse(null);
        if (result != null && "FINAL".equals(result.status())) {
            state.subtitleCount++;
        }
        AsrProviderStatusPayload providerStatus = asrService.selectedProviderStatus();
        return new AudioStreamProcessResult(state.chunkCount, state.subtitleCount, result, providerStatus);
    }

    public AudioStreamStoppedPayload stop(String sessionId) {
        StreamState removed = streams.remove(sessionId);
        if (removed == null) {
            return new AudioStreamStoppedPayload(0, 0);
        }
        return new AudioStreamStoppedPayload(removed.chunkCount, removed.subtitleCount);
    }

    public AsrProviderStatusPayload providerStatus() {
        return asrService.selectedProviderStatus();
    }

    private static class StreamState {
        private final Deque<AudioChunk> recentChunks = new ArrayDeque<>();
        private int chunkCount;
        private int subtitleCount;

        private synchronized void remember(AudioChunk chunk) {
            chunkCount++;
            recentChunks.addLast(chunk);
            // 只保留最近少量音频块，避免第二阶段内存缓存无界增长。
            while (recentChunks.size() > MAX_RECENT_CHUNKS) {
                recentChunks.removeFirst();
            }
        }
    }
}
