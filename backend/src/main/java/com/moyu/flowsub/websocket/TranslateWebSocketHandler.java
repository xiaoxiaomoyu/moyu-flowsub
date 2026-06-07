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
import com.moyu.flowsub.mock.MockSubtitle;
import com.moyu.flowsub.mock.MockSubtitleProvider;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TranslateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TranslateWebSocketHandler.class);
    private static final UriTemplate URI_TEMPLATE = new UriTemplate("/ws/translate/{sessionId}");

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final MockSubtitleProvider mockSubtitleProvider;
    private final AudioStreamService audioStreamService;
    private final TranslationService translationService;
    private final ArchiveService archiveService;
    private final WebSocketMessageBus messageBus;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    // 用 WebSocket 会话编号标记推送状态，避免用户重复点击"开始模拟同传"导致多条任务并发推送。
    private final Map<String, AtomicBoolean> runningSessions = new java.util.concurrent.ConcurrentHashMap<>();
    // 每个音频二进制帧到达前，前端会先发一条元数据文本消息，这里按 WebSocket 会话暂存。
    private final Map<String, AudioChunkMeta> pendingAudioMetas = new java.util.concurrent.ConcurrentHashMap<>();
    // 每个会话的字幕计数，用于控制上下文修正触发频率（每 3 条字幕触发一次异步修正）。
    private final Map<String, Integer> sessionSubtitleCounts = new java.util.concurrent.ConcurrentHashMap<>();

    public TranslateWebSocketHandler(ObjectMapper objectMapper,
                                     SessionService sessionService,
                                     MockSubtitleProvider mockSubtitleProvider,
                                     AudioStreamService audioStreamService,
                                     TranslationService translationService,
                                     ArchiveService archiveService,
                                     WebSocketMessageBus messageBus) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.mockSubtitleProvider = mockSubtitleProvider;
        this.audioStreamService = audioStreamService;
        this.translationService = translationService;
        this.archiveService = archiveService;
        this.messageBus = messageBus;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        runningSessions.put(session.getId(), new AtomicBoolean(false));
        messageBus.register(sessionId, session);
        // 连接建立后立即回传确认消息，前端据此把连接状态更新为已连接。
        send(session, WsMessage.of("SESSION_CONNECTED", sessionId, Map.of()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();
        if ("START_MOCK_TRANSLATE".equals(type)) {
            // 模拟同传作为兜底演示入口，真实主链路走麦克风采集、ASR 和翻译。
            startMockTranslate(session);
        }
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
            // 音频帧处理异常不能直接断开前端 WebSocket，否则浏览器会继续本地采集但后端完全收不到数据。
            log.warn("音频帧处理失败，sessionId={}", extractSessionId(session), e);
            String sessionId = extractSessionId(session);
            AsrProviderStatusPayload status = audioStreamService.providerStatus(sessionId);
            sendQuietly(session, WsMessage.of("ASR_PROVIDER_STATUS", sessionId,
                    new AsrProviderStatusPayload(status.provider(), status.available(), true,
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
        // 诊断：打印音频块到达情况，确认麦克风采集链路是否正常
        if (meta.chunkIndex() % 10 == 1) {
            log.info("收到音频块 chunkIndex={}, size={}字节, sampleRate={}, level={}",
                    meta.chunkIndex(), data.length, meta.sampleRate(), String.format("%.4f", meta.level()));
        }
        archiveService.appendAudio(sessionId, data);
        AudioStreamProcessResult result = audioStreamService.accept(sessionId, meta, data);

        sendQuietly(session, WsMessage.of("ASR_PROVIDER_STATUS", sessionId, result.providerStatus()));
        if (result.asrResults().isEmpty()) {
            // 真实 ASR 可能需要几秒才返回文本，但音频块计数应实时更新，方便前端判断链路是否在工作。
            sendQuietly(session, WsMessage.of("METRICS_UPDATE", sessionId,
                    recordMetrics(sessionId, new MetricsPayload(0, 0, 0, result.subtitleCount(), 0,
                            result.chunkCount(), result.providerStatus().provider(), result.providerStatus().fallback()))));
            return;
        }
        for (AsrResult asrResult : result.asrResults()) {
            // 先发 ASR 识别结果到前端，不阻塞等待翻译，让用户即时看到识别到的原文。
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
                            result.providerStatus().provider(), result.providerStatus().fallback()))));

            if ("FINAL".equals(asrResult.status())) {
                startTranslation(session, sessionId, asrResult, result);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("浏览器 WebSocket 已关闭，sessionId={}，closeStatus={}", extractSessionId(session), status);
        AtomicBoolean running = runningSessions.remove(session.getId());
        if (running != null) {
            running.set(false);
        }
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

    private void startMockTranslate(WebSocketSession session) {
        AtomicBoolean running = runningSessions.computeIfAbsent(session.getId(), ignored -> new AtomicBoolean(false));
        if (!running.compareAndSet(false, true)) {
            // 已经在推送时忽略重复开始请求，保证字幕顺序稳定。
            return;
        }

        executorService.submit(() -> {
            String sessionId = extractSessionId(session);
            sessionService.markRunning(sessionId);
            int correctionCount = 0;
            var subtitles = mockSubtitleProvider.subtitles();

            // 按秒推送模拟字幕，贴近真实同传的流式体验。
            for (int i = 0; i < subtitles.size() && running.get(); i++) {
                if (!session.isOpen()) {
                    break;
                }
                MockSubtitle mock = subtitles.get(i);
                long totalLatency = mock.asrLatencyMs() + mock.translateLatencyMs();
                SubtitlePayload subtitle = new SubtitlePayload(mock.segmentId(), mock.sourceText(), mock.translatedText(),
                        "FINAL", 1, false, totalLatency);
                archiveService.recordSubtitle(sessionId, subtitle);
                sendQuietly(session, WsMessage.of("SUBTITLE_UPDATE", sessionId, subtitle));

                if (i == 3) {
                    // 第 4 条字幕后修正第 2 条字幕，模拟"后文到来后纠正历史结果"。
                    correctionCount = 1;
                    var correction = mockSubtitleProvider.correction();
                    archiveService.recordCorrection(sessionId, correction);
                    sendQuietly(session, WsMessage.of("SUBTITLE_CORRECTION", sessionId, correction));
                }

                // 指标与字幕同步推送，前端可以实时观察链路延迟。
                sendQuietly(session, WsMessage.of("METRICS_UPDATE", sessionId,
                        recordMetrics(sessionId, new MetricsPayload(mock.asrLatencyMs(), mock.translateLatencyMs(), totalLatency,
                                i + 1, correctionCount))));
                sleepOneSecond();
            }
            running.set(false);
        });
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

            // 翻译完成后异步触发上下文修正，不阻塞当前字幕展示。
            triggerCorrectionReview(session, sessionId);
        });
    }

    private void triggerCorrectionReview(WebSocketSession session, String sessionId) {
        int count = sessionSubtitleCounts.merge(sessionId, 1, Integer::sum);
        // 每 3 条稳定字幕触发一次上下文修正，控制请求频率避免无谓消耗。
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
            // 客户端断开或网络异常时停止当前会话推送，避免后台线程继续空跑。
            AtomicBoolean running = runningSessions.get(session.getId());
            if (running != null) {
                running.set(false);
            }
        }
    }

    private void send(WebSocketSession session, WsMessage<?> message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }

    private void sleepOneSecond() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
