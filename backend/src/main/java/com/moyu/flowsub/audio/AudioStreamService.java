package com.moyu.flowsub.audio;

import com.moyu.flowsub.asr.AsrProviderStatusPayload;
import com.moyu.flowsub.asr.AsrResult;
import com.moyu.flowsub.asr.AsrService;
import com.moyu.flowsub.asr.AsrStreamSession;
import com.moyu.flowsub.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioStreamService {

    private static final Logger log = LoggerFactory.getLogger(AudioStreamService.class);
    private static final int MAX_RECENT_CHUNKS = 24;
    private static final int NO_RESULT_FALLBACK_CHUNKS = 40;
    private static final int SILENT_FALLBACK_AFTER_FIRST_RESULT = 40;
    private static final int MAX_TRANSIENT_ERRORS = 3;
    private static final int REPLAY_CHUNKS_ON_SWITCH = 3;

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
        streams.computeIfAbsent(sessionId, k -> new StreamState(asrSession));
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
        private int transientErrorCount;

        private StreamState(AsrStreamSession asrSession) {
            this.asrSession = asrSession;
        }

        private synchronized void remember(AudioChunk chunk) {
            chunkCount++;
            recentChunks.addLast(chunk);
            while (recentChunks.size() > MAX_RECENT_CHUNKS) {
                recentChunks.removeFirst();
            }
        }

        private synchronized List<AudioChunk> recentChunksForReplay() {
            List<AudioChunk> list = new ArrayList<>(recentChunks);
            int fromIndex = Math.max(0, list.size() - REPLAY_CHUNKS_ON_SWITCH);
            return list.subList(fromIndex, list.size());
        }
    }

    private List<AsrResult> acceptWithFallback(String sessionId, AudioChunkMeta meta, StreamState state, AudioChunk chunk) {
        try {
            List<AsrResult> results = state.asrSession.accept(chunk);
            state.transientErrorCount = 0;
            if (!results.isEmpty()) {
                state.chunksWithoutText = 0;
                return results;
            }
            return fallbackIfProviderSilent(sessionId, meta, state, chunk);
        } catch (Exception e) {
            state.transientErrorCount++;
            String provider = state.asrSession.status().provider();
            if (state.transientErrorCount < MAX_TRANSIENT_ERRORS) {
                log.warn("{} 处理音频块失败（第{}次临时错误），继续重试，sessionId={}",
                        provider, state.transientErrorCount, sessionId);
                return List.of();
            }
            log.warn("{} 连续失败{}次，触发降级，sessionId={}", provider, MAX_TRANSIENT_ERRORS, sessionId);
            switchProvider(sessionId, meta, state,
                    provider + " 连续" + MAX_TRANSIENT_ERRORS + "次处理音频块失败，已自动降级。");
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
        // WebSocket 尚未连接成功时，空块属于正常建立阶段，不计入静默降级计数。
        if (!status.connected()) {
            return List.of();
        }
        state.chunksWithoutText++;
        int threshold = state.subtitleCount > 0 ? SILENT_FALLBACK_AFTER_FIRST_RESULT : NO_RESULT_FALLBACK_CHUNKS;
        if (state.chunksWithoutText < threshold) {
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
        state.transientErrorCount = 0;
        state.asrSession = asrService.startExcluding(sessionId, meta, Set.copyOf(state.excludedProviders), reason);
        replayRecentChunks(state);
    }

    private void replayRecentChunks(StreamState state) {
        List<AudioChunk> chunks = state.recentChunksForReplay();
        if (chunks.isEmpty()) {
            return;
        }
        log.info("向新 Provider 回放最近 {} 个音频块", chunks.size());
        for (AudioChunk chunk : chunks) {
            try {
                state.asrSession.accept(chunk);
            } catch (Exception ignored) {
            }
        }
    }
}
