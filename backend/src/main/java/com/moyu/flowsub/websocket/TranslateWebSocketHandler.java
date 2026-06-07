package com.moyu.flowsub.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.archive.ArchiveService;
import com.moyu.flowsub.asr.AsrProviderStatusPayload;
import com.moyu.flowsub.asr.AsrResult;
import com.moyu.flowsub.audio.AudioChunkMeta;
import com.moyu.flowsub.audio.AudioStreamProcessResult;
import com.moyu.flowsub.audio.AudioStreamService;
import com.moyu.flowsub.metrics.MetricsPayload;
import com.moyu.flowsub.session.SessionService;
import com.moyu.flowsub.subtitle.SubtitlePayload;
import com.moyu.flowsub.translation.TranslationProcessResult;
import com.moyu.flowsub.translation.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TranslateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TranslateWebSocketHandler.class);
    private static final UriTemplate URI_TEMPLATE = new UriTemplate("/ws/translate/{sessionId}");

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final AudioStreamService audioStreamService;
    private final TranslationService translationService;
    private final ArchiveService archiveService;
    private final WebSocketMessageBus messageBus;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, AudioChunkMeta> pendingAudioMetas = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> sessionSubtitleCounts = new java.util.concurrent.ConcurrentHashMap<>();

    public TranslateWebSocketHandler(ObjectMapper objectMapper,
                                     SessionService sessionService,
                                     AudioStreamService audioStreamService,
                                     TranslationService translationService,
                                     ArchiveService archiveService,
                                     WebSocketMessageBus messageBus) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.audioStreamService = audioStreamService;
        this.translationService = translationService;
        this.archiveService = archiveService;
        this.messageBus = messageBus;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = extractSessionId(session);
        messageBus.register(sessionId, session);
        sendQuietly(session, WsMessage.of("SESSION_CONNECTED", sessionId, Map.of()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();
        if ("START_AUDIO_STREAM".equals(type)) {
            startAudioStream(session, root.path("payload"));
        }
        if ("AUDIO_CHUNK_META".equals(type)) {
            pendingAudioMetas.put(session.getId(), objectMapper.treeToValue(root.path("payload"), AudioChunkMeta.class));
        }
        if ("STOP_AUDIO_STREAM".equals(type)) {
            stopAudioStream(session);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            handleAudioBinaryMessage(session, message);
        } catch (Exception e) {
            log.warn("音频帧处理失败，sessionId={}", extractSessionId(session), e);
            String sessionId = extractSessionId(session);
            AsrProviderStatusPayload status = audioStreamService.providerStatus(sessionId);
            sendQuietly(session, WsMessage.of("ASR_PROVIDER_STATUS", sessionId,
                    new AsrProviderStatusPayload(status.provider(), status.available(), false,
                            "音频帧处理异常，已保留 WebSocket 连接。", status.connected(),
                            e.getMessage() == null ? "后端处理音频帧时发生未知异常。" : e.getMessage(),
                            status.endpointType())));
        }
    }

    private void handleAudioBinaryMessage(WebSocketSession session, BinaryMessage message) {
        AudioChunkMeta meta = pendingAudioMetas.remove(session.getId());
        if (meta == null) {
            log.warn("收到音频二进制帧但缺少元数据，webSocketSessionId={}", session.getId());
            return;
        }

        String sessionId = extractSessionId(session);
        byte[] data = new byte[message.getPayloadLength()];
        message.getPayload().get(data);
        archiveService.appendAudio(sessionId, data);
        AudioStreamProcessResult result = audioStreamService.accept(sessionId, meta, data);

        sendQuietly(session, WsMessage.of("ASR_PROVIDER_STATUS", sessionId, result.providerStatus()));
        if (result.asrResults().isEmpty()) {
            sendQuietly(session, WsMessage.of("METRICS_UPDATE", sessionId,
                    recordMetrics(sessionId, new MetricsPayload(0, 0, 0, result.subtitleCount(), 0,
                            result.chunkCount(), result.providerStatus().provider(), result.providerStatus().fallback(),
                            "", false))));
            return;
        }
        for (AsrResult asrResult : result.asrResults()) {
            SubtitlePayload asrPayload = new SubtitlePayload(
                    asrResult.segmentId(),
                    asrResult.text(),
                    "FINAL".equals(asrResult.status()) ? "翻译中..." : "等待稳定识别...",
                    asrResult.status(),
                    1,
                    false,
                    asrResult.latencyMs()
            );
            String asrEventType = "FINAL".equals(asrResult.status()) ? "ASR_FINAL" : "ASR_PARTIAL";
            archiveService.recordSubtitle(sessionId, asrPayload);
            sendQuietly(session, WsMessage.of(asrEventType, sessionId, asrPayload));
            sendQuietly(session, WsMessage.of("METRICS_UPDATE", sessionId,
                    recordMetrics(sessionId, new MetricsPayload(asrResult.latencyMs(), 0, asrResult.latencyMs(),
                            result.subtitleCount(), 0, result.chunkCount(),
                            result.providerStatus().provider(), result.providerStatus().fallback(),
                            "", false))));

            if ("FINAL".equals(asrResult.status())) {
                startTranslation(session, sessionId, asrResult, result);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("浏览器 WebSocket 已关闭，sessionId={}，closeStatus={}", extractSessionId(session), status);
        pendingAudioMetas.remove(session.getId());
        String sessionId = extractSessionId(session);
        sessionSubtitleCounts.remove(sessionId);
        messageBus.unregister(sessionId, session.getId());
        audioStreamService.stop(sessionId);
        translationService.clear(sessionId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("浏览器 WebSocket 传输异常，sessionId={}", extractSessionId(session), exception);
        super.handleTransportError(session, exception);
    }

    private void startAudioStream(WebSocketSession session, JsonNode payload) throws IOException {
        String sessionId = extractSessionId(session);
        AudioChunkMeta meta = payload == null || payload.isMissingNode()
                ? new AudioChunkMeta(0, System.currentTimeMillis(), "pcm_s16le", 16000, 1, 0)
                : objectMapper.treeToValue(payload, AudioChunkMeta.class);
        send(session, WsMessage.of("AUDIO_STREAM_STARTED", sessionId, audioStreamService.start(sessionId, meta)));
        send(session, WsMessage.of("ASR_PROVIDER_STATUS", sessionId, audioStreamService.providerStatus(sessionId)));
        send(session, WsMessage.of("TRANSLATION_PROVIDER_STATUS", sessionId, translationService.currentStatus()));
    }

    private void stopAudioStream(WebSocketSession session) throws IOException {
        String sessionId = extractSessionId(session);
        send(session, WsMessage.of("AUDIO_STREAM_STOPPED", sessionId, audioStreamService.stop(sessionId)));
        translationService.clear(sessionId);
    }

    private String extractSessionId(WebSocketSession session) {
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        return URI_TEMPLATE.match(path).get("sessionId");
    }

    private void startTranslation(WebSocketSession session,
                                  String sessionId,
                                  AsrResult asrResult,
                                  AudioStreamProcessResult audioResult) {
        sendQuietly(session, WsMessage.of("TRANSLATION_STARTED", sessionId, Map.of(
                "segmentId", asrResult.segmentId(),
                "sourceText", asrResult.text()
        )));
        executorService.submit(() -> {
            TranslationProcessResult translation = translationService.translateFinal(sessionId, asrResult);
            sendQuietly(session, WsMessage.of("TRANSLATION_PROVIDER_STATUS", sessionId, translation.providerStatus()));
            archiveService.recordSubtitle(sessionId, translation.subtitle());
            sendQuietly(session, WsMessage.of("SUBTITLE_UPDATE", sessionId, translation.subtitle()));
            long totalLatency = asrResult.latencyMs() + translation.translateLatencyMs();
            sendQuietly(session, WsMessage.of("METRICS_UPDATE", sessionId,
                    recordMetrics(sessionId, new MetricsPayload(asrResult.latencyMs(), translation.translateLatencyMs(), totalLatency,
                            audioResult.subtitleCount(), translation.totalCorrectionCount(), audioResult.chunkCount(),
                            audioResult.providerStatus().provider(), audioResult.providerStatus().fallback(),
                            translation.providerStatus().provider(), translation.providerStatus().fallback()))));

            triggerCorrectionReview(session, sessionId);
        });
    }

    private void triggerCorrectionReview(WebSocketSession session, String sessionId) {
        int count = sessionSubtitleCounts.merge(sessionId, 1, Integer::sum);
        if (count < 3 || count % 3 != 0) {
            return;
        }
        executorService.submit(() -> {
            var corrections = translationService.reviewCorrections(sessionId);
            for (var correction : corrections) {
                archiveService.recordCorrection(sessionId, correction);
                sendQuietly(session, WsMessage.of("SUBTITLE_CORRECTION", sessionId, correction));
            }
        });
    }

    private MetricsPayload recordMetrics(String sessionId, MetricsPayload metrics) {
        archiveService.recordMetrics(sessionId, metrics);
        return metrics;
    }

    private void sendQuietly(WebSocketSession session, WsMessage<?> message) {
        try {
            send(session, message);
        } catch (IOException ignored) {
        }
    }

    private void send(WebSocketSession session, WsMessage<?> message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }
}
