package com.moyu.flowsub.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.metrics.MetricsPayload;
import com.moyu.flowsub.mock.MockSubtitle;
import com.moyu.flowsub.mock.MockSubtitleProvider;
import com.moyu.flowsub.session.SessionService;
import com.moyu.flowsub.subtitle.SubtitlePayload;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
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

    private static final UriTemplate URI_TEMPLATE = new UriTemplate("/ws/translate/{sessionId}");

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final MockSubtitleProvider mockSubtitleProvider;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    // 用 WebSocket 会话编号标记推送状态，避免用户重复点击“开始模拟同传”导致多条任务并发推送。
    private final Map<String, AtomicBoolean> runningSessions = new java.util.concurrent.ConcurrentHashMap<>();

    public TranslateWebSocketHandler(ObjectMapper objectMapper,
                                     SessionService sessionService,
                                     MockSubtitleProvider mockSubtitleProvider) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.mockSubtitleProvider = mockSubtitleProvider;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        runningSessions.put(session.getId(), new AtomicBoolean(false));
        // 连接建立后立即回传确认消息，前端据此把连接状态更新为已连接。
        send(session, WsMessage.of("SESSION_CONNECTED", sessionId, Map.of()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText();
        if ("START_MOCK_TRANSLATE".equals(type)) {
            // 第一阶段只识别开始模拟同传指令，真实音频块会在后续阶段扩展。
            startMockTranslate(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        AtomicBoolean running = runningSessions.remove(session.getId());
        if (running != null) {
            running.set(false);
        }
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
                sendQuietly(session, WsMessage.of("SUBTITLE_UPDATE", sessionId,
                        new SubtitlePayload(mock.segmentId(), mock.sourceText(), mock.translatedText(),
                                "FINAL", 1, false, totalLatency)));

                if (i == 3) {
                    // 第 4 条字幕后修正第 2 条字幕，模拟“后文到来后纠正历史结果”。
                    correctionCount = 1;
                    sendQuietly(session, WsMessage.of("SUBTITLE_CORRECTION", sessionId, mockSubtitleProvider.correction()));
                }

                // 指标与字幕同步推送，前端可以实时观察链路延迟。
                sendQuietly(session, WsMessage.of("METRICS_UPDATE", sessionId,
                        new MetricsPayload(mock.asrLatencyMs(), mock.translateLatencyMs(), totalLatency,
                                i + 1, correctionCount)));
                sleepOneSecond();
            }
            running.set(false);
        });
    }

    private String extractSessionId(WebSocketSession session) {
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        return URI_TEMPLATE.match(path).get("sessionId");
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
