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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioStreamService {

    private static final Logger log = LoggerFactory.getLogger(AudioStreamService.class);

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

    /**
     * ASR 不可用时仍允许音频采集，前端字幕区会显示音频采集状态而非 ASR 识别结果。
     */
    public void startWithoutAsr(String sessionId, AudioChunkMeta meta) {
        sessionService.markRunning(sessionId);
        int sampleRate = meta == null || meta.sampleRate() <= 0 ? 16000 : meta.sampleRate();
        AsrStreamSession noOp = new AsrStreamSession() {
            @Override
            public AsrProviderStatusPayload status() {
                return asrService.currentStatus();
            }

            @Override
            public List<AsrResult> accept(AudioChunk chunk) {
                return List.of();
            }

            @Override
            public List<AsrResult> stop() {
                return List.of();
            }

            @Override
            public void close() {
            }
        };
        streams.computeIfAbsent(sessionId, k -> new StreamState(noOp));
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
        state.chunkCount++;
        List<AsrResult> results = state.asrSession.accept(chunk);
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
        private final AsrStreamSession asrSession;
        private int chunkCount;
        private int subtitleCount;

        private StreamState(AsrStreamSession asrSession) {
            this.asrSession = asrSession;
        }
    }
}
