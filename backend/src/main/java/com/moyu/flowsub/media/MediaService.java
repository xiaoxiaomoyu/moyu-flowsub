package com.moyu.flowsub.media;

import com.moyu.flowsub.archive.ArchiveService;
import com.moyu.flowsub.asr.AsrResult;
import com.moyu.flowsub.audio.AudioChunkMeta;
import com.moyu.flowsub.audio.AudioStreamProcessResult;
import com.moyu.flowsub.audio.AudioStreamService;
import com.moyu.flowsub.audio.AudioStreamStoppedPayload;
import com.moyu.flowsub.metrics.MetricsPayload;
import com.moyu.flowsub.session.SessionService;
import com.moyu.flowsub.subtitle.SubtitlePayload;
import com.moyu.flowsub.translation.TranslationProcessResult;
import com.moyu.flowsub.translation.TranslationService;
import com.moyu.flowsub.websocket.WebSocketMessageBus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class MediaService {

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int CHUNK_DURATION_MS = 300;
    private static final int CHUNK_BYTES = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE * CHUNK_DURATION_MS / 1000;

    private final MikuProperties mikuProperties;
    private final FfmpegProperties ffmpegProperties;
    private final AudioStreamService audioStreamService;
    private final ArchiveService archiveService;
    private final TranslationService translationService;
    private final WebSocketMessageBus messageBus;
    private final Map<String, LiveState> liveStates = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public MediaService(MikuProperties mikuProperties,
                        FfmpegProperties ffmpegProperties,
                        SessionService sessionService,
                        AudioStreamService audioStreamService,
                        ArchiveService archiveService,
                        TranslationService translationService,
                        WebSocketMessageBus messageBus) {
        this.mikuProperties = mikuProperties;
        this.ffmpegProperties = ffmpegProperties;
        this.audioStreamService = audioStreamService;
        this.archiveService = archiveService;
        this.translationService = translationService;
        this.messageBus = messageBus;
    }

    public MediaStatusResponse status() {
        boolean mikuConfigured = mikuConfigured();
        boolean ffmpegConfigured = commandAvailable(ffmpegCommand());
        boolean ffprobeConfigured = commandAvailable(ffprobeCommand());
        String message = mikuConfigured && ffmpegConfigured
                ? "Miku 直播配置和 FFmpeg 拉流能力已就绪。"
                : "Miku 或 FFmpeg 未完整配置，直播拉流会降级到 Mock 演示。";
        return new MediaStatusResponse(mikuProperties.enabled(), mikuConfigured, ffmpegConfigured,
                ffprobeConfigured, ffmpegProperties.mockEnabled(), mikuProperties.apiHost(), mikuProperties.region(), message);
    }

    public LiveStreamSessionResponse prepare(String sessionId) {
        LiveState state = liveStates.computeIfAbsent(sessionId, this::newState);
        synchronized (state) {
            state.ingestStatus = LiveIngestStatus.PREPARED;
            state.updatedAt = Instant.now();
            return state.response();
        }
    }

    public LiveStreamSessionResponse getLive(String sessionId) {
        return liveStates.computeIfAbsent(sessionId, this::newState).response();
    }

    public LiveStreamSessionResponse startIngest(String sessionId) {
        LiveState state = liveStates.computeIfAbsent(sessionId, this::newState);
        synchronized (state) {
            if (state.ingestStatus == LiveIngestStatus.INGESTING || state.ingestStatus == LiveIngestStatus.MOCK) {
                return state.response();
            }
            state.stopRequested = false;
            state.startedAt = Instant.now();
            state.updatedAt = state.startedAt;
            boolean canUseRealStream = mikuConfigured() && commandAvailable(ffmpegCommand()) && StringUtils.hasText(state.playUrl);
            if (canUseRealStream) {
                state.ingestStatus = LiveIngestStatus.INGESTING;
                state.provider = "Miku 快直播 + FFmpeg";
                state.fallback = false;
                state.reason = "正在从 Miku 播放地址拉取音频并送入 ASR。";
                executorService.submit(() -> runFfmpeg(sessionId, state));
                return state.response();
            }
            if (ffmpegProperties.mockEnabled()) {
                state.ingestStatus = LiveIngestStatus.MOCK;
                state.provider = "Mock 直播源";
                state.fallback = true;
                state.reason = "Miku 或 FFmpeg 未完整配置，已使用 Mock PCM 流验证直播链路。";
                executorService.submit(() -> runMockStream(sessionId, state));
                return state.response();
            }
            state.ingestStatus = LiveIngestStatus.FAILED;
            state.provider = "未启用";
            state.fallback = true;
            state.reason = "Miku/FFmpeg 未就绪且 MEDIA_MOCK_ENABLED=false，无法启动直播拉流。";
            return state.response();
        }
    }

    public LiveStreamSessionResponse stopIngest(String sessionId) {
        LiveState state = liveStates.computeIfAbsent(sessionId, this::newState);
        synchronized (state) {
            state.stopRequested = true;
            if (state.process != null) {
                state.process.destroyForcibly();
            }
            AudioStreamStoppedPayload stopped = audioStreamService.stop(sessionId);
            translationService.clear(sessionId);
            state.ingestStatus = LiveIngestStatus.STOPPED;
            state.updatedAt = Instant.now();
            state.reason = "直播拉流已停止，累计音频块：" + stopped.chunkCount() + "。";
            messageBus.send(sessionId, "AUDIO_STREAM_STOPPED", stopped);
            return state.response();
        }
    }

    private void runFfmpeg(String sessionId, LiveState state) {
        try {
            startAudioStream(sessionId);
            ProcessBuilder builder = new ProcessBuilder(
                    ffmpegCommand(),
                    "-hide_banner",
                    "-loglevel", "error",
                    "-i", state.playUrl,
                    "-vn",
                    "-ac", String.valueOf(CHANNELS),
                    "-ar", String.valueOf(SAMPLE_RATE),
                    "-f", "s16le",
                    "pipe:1"
            );
            builder.redirectErrorStream(true);
            state.process = builder.start();
            readPcmLoop(sessionId, state, state.process.getInputStream());
        } catch (Exception e) {
            fallbackToMockAfterFailure(sessionId, state, "FFmpeg 拉流失败：" + readable(e));
        }
    }

    private void runMockStream(String sessionId, LiveState state) {
        startAudioStream(sessionId);
        int index = 0;
        while (!state.stopRequested && (state.ingestStatus == LiveIngestStatus.MOCK || state.ingestStatus == LiveIngestStatus.INGESTING)) {
            index++;
            byte[] data = new byte[CHUNK_BYTES];
            acceptChunk(sessionId, index, data);
            sleepChunk();
        }
    }

    private void readPcmLoop(String sessionId, LiveState state, InputStream inputStream) throws IOException {
        int index = 0;
        byte[] buffer = new byte[CHUNK_BYTES];
        while (!state.stopRequested) {
            int read = readFully(inputStream, buffer);
            if (read <= 0) {
                break;
            }
            index++;
            byte[] data = read == buffer.length ? buffer.clone() : java.util.Arrays.copyOf(buffer, read);
            acceptChunk(sessionId, index, data);
        }
        if (!state.stopRequested) {
            fallbackToMockAfterFailure(sessionId, state, "直播播放地址未持续输出音频，已切换 Mock PCM 流。");
        }
    }

    private void startAudioStream(String sessionId) {
        AudioChunkMeta meta = new AudioChunkMeta(0, System.currentTimeMillis(), "pcm_s16le", SAMPLE_RATE, CHANNELS, 0);
        messageBus.send(sessionId, "AUDIO_STREAM_STARTED", audioStreamService.start(sessionId, meta));
        messageBus.send(sessionId, "ASR_PROVIDER_STATUS", audioStreamService.providerStatus(sessionId));
        messageBus.send(sessionId, "TRANSLATION_PROVIDER_STATUS", translationService.currentStatus());
    }

    private void acceptChunk(String sessionId, int index, byte[] data) {
        AudioChunkMeta meta = new AudioChunkMeta(index, System.currentTimeMillis(), "pcm_s16le", SAMPLE_RATE, CHANNELS, 0.1);
        archiveService.appendAudio(sessionId, data);
        AudioStreamProcessResult result = audioStreamService.accept(sessionId, meta, data);
        messageBus.send(sessionId, "ASR_PROVIDER_STATUS", result.providerStatus());
        if (result.asrResults().isEmpty()) {
            messageBus.send(sessionId, "METRICS_UPDATE", recordMetrics(sessionId, new MetricsPayload(0, 0, 0,
                    result.subtitleCount(), 0, result.chunkCount(), result.providerStatus().provider(),
                    result.providerStatus().fallback())));
            return;
        }
        for (AsrResult asrResult : result.asrResults()) {
            if ("FINAL".equals(asrResult.status())) {
                translateAndPush(sessionId, asrResult, result);
            } else {
                SubtitlePayload payload = new SubtitlePayload(asrResult.segmentId(), asrResult.text(),
                        "等待稳定识别后生成中文翻译", asrResult.status(), 1, false, asrResult.latencyMs());
                archiveService.recordSubtitle(sessionId, payload);
                messageBus.send(sessionId, "ASR_PARTIAL", payload);
                messageBus.send(sessionId, "METRICS_UPDATE", recordMetrics(sessionId, new MetricsPayload(
                        asrResult.latencyMs(), 0, asrResult.latencyMs(), result.subtitleCount(), 0,
                        result.chunkCount(), result.providerStatus().provider(), result.providerStatus().fallback())));
            }
        }
    }

    private void translateAndPush(String sessionId, AsrResult asrResult, AudioStreamProcessResult audioResult) {
        messageBus.send(sessionId, "TRANSLATION_STARTED", Map.of(
                "segmentId", asrResult.segmentId(),
                "sourceText", asrResult.text()
        ));
        TranslationProcessResult translation = translationService.translateFinal(sessionId, asrResult);
        messageBus.send(sessionId, "TRANSLATION_PROVIDER_STATUS", translation.providerStatus());
        archiveService.recordSubtitle(sessionId, translation.subtitle());
        messageBus.send(sessionId, "SUBTITLE_UPDATE", translation.subtitle());
        translation.corrections().forEach(correction -> {
            archiveService.recordCorrection(sessionId, correction);
            messageBus.send(sessionId, "SUBTITLE_CORRECTION", correction);
        });
        long totalLatency = asrResult.latencyMs() + translation.translateLatencyMs();
        messageBus.send(sessionId, "METRICS_UPDATE", recordMetrics(sessionId, new MetricsPayload(
                asrResult.latencyMs(), translation.translateLatencyMs(), totalLatency, audioResult.subtitleCount(),
                translation.totalCorrectionCount(), audioResult.chunkCount(), audioResult.providerStatus().provider(),
                audioResult.providerStatus().fallback(), translation.providerStatus().provider(),
                translation.providerStatus().fallback())));
    }

    private MetricsPayload recordMetrics(String sessionId, MetricsPayload metrics) {
        archiveService.recordMetrics(sessionId, metrics);
        return metrics;
    }

    private void fallbackToMockAfterFailure(String sessionId, LiveState state, String reason) {
        synchronized (state) {
            if (state.stopRequested) {
                return;
            }
            if (!ffmpegProperties.mockEnabled()) {
                state.ingestStatus = LiveIngestStatus.FAILED;
                state.fallback = true;
                state.reason = reason;
                state.updatedAt = Instant.now();
                return;
            }
            state.ingestStatus = LiveIngestStatus.MOCK;
            state.provider = "Mock 直播源";
            state.fallback = true;
            state.reason = reason;
            state.updatedAt = Instant.now();
        }
        runMockStream(sessionId, state);
    }

    private LiveState newState(String sessionId) {
        String streamName = streamName(sessionId);
        return new LiveState(
                sessionId,
                streamName,
                publishUrl(streamName),
                playUrl(streamName),
                whepUrl(streamName)
        );
    }

    private boolean mikuConfigured() {
        return mikuProperties.enabled()
                && StringUtils.hasText(mikuProperties.publishDomain())
                && StringUtils.hasText(mikuProperties.playDomain());
    }

    private String streamName(String sessionId) {
        String prefix = StringUtils.hasText(mikuProperties.streamPrefix()) ? mikuProperties.streamPrefix() : "moyu-flowsub";
        return prefix + "-" + sessionId.replace("_", "-");
    }

    private String publishUrl(String streamName) {
        if (!StringUtils.hasText(mikuProperties.publishDomain())) {
            return "";
        }
        return normalizeRtmp(mikuProperties.publishDomain()) + "/live/" + streamName;
    }

    private String playUrl(String streamName) {
        if (!StringUtils.hasText(mikuProperties.playDomain())) {
            return "";
        }
        return normalizeHttp(mikuProperties.playDomain()) + "/live/" + streamName + ".m3u8";
    }

    private String whepUrl(String streamName) {
        if (!StringUtils.hasText(mikuProperties.whepDomain())) {
            return "";
        }
        return normalizeHttp(mikuProperties.whepDomain()) + "/live/" + streamName + "/whep";
    }

    private String normalizeRtmp(String domain) {
        String trimmed = trimSlash(domain);
        return trimmed.startsWith("rtmp://") ? trimmed : "rtmp://" + trimmed;
    }

    private String normalizeHttp(String domain) {
        String trimmed = trimSlash(domain);
        return trimmed.startsWith("http://") || trimmed.startsWith("https://") ? trimmed : "https://" + trimmed;
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String ffmpegCommand() {
        return StringUtils.hasText(ffmpegProperties.ffmpegPath()) ? ffmpegProperties.ffmpegPath() : "ffmpeg";
    }

    private String ffprobeCommand() {
        return StringUtils.hasText(ffmpegProperties.ffprobePath()) ? ffmpegProperties.ffprobePath() : "ffprobe";
    }

    private boolean commandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "-version").start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int readFully(InputStream inputStream, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = inputStream.read(buffer, total, buffer.length - total);
            if (read < 0) {
                return total;
            }
            total += read;
        }
        return total;
    }

    private String readable(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void sleepChunk() {
        try {
            Thread.sleep(CHUNK_DURATION_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class LiveState {
        private final String sessionId;
        private final String streamName;
        private final String publishUrl;
        private final String playUrl;
        private final String whepUrl;
        private LiveIngestStatus ingestStatus = LiveIngestStatus.IDLE;
        private String provider = "等待直播源";
        private boolean fallback = true;
        private String reason = "等待准备直播源。";
        private Instant startedAt;
        private Instant updatedAt = Instant.now();
        private boolean stopRequested;
        private Process process;

        private LiveState(String sessionId, String streamName, String publishUrl, String playUrl, String whepUrl) {
            this.sessionId = sessionId;
            this.streamName = streamName;
            this.publishUrl = publishUrl;
            this.playUrl = playUrl;
            this.whepUrl = whepUrl;
        }

        private synchronized LiveStreamSessionResponse response() {
            return new LiveStreamSessionResponse(sessionId, streamName, publishUrl, playUrl, whepUrl, ingestStatus,
                    provider, fallback, reason, startedAt, updatedAt);
        }
    }
}
