package com.moyu.flowsub.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.audio.AudioChunk;
import com.moyu.flowsub.audio.AudioChunkMeta;
import com.moyu.flowsub.qwen.QwenProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Component
public class QwenAsrProvider implements AsrProvider {

    private static final Logger log = LoggerFactory.getLogger(QwenAsrProvider.class);
    private static final int MAX_RECONNECT_ATTEMPTS = 2;

    private final QwenProperties qwenProperties;
    private final ObjectMapper objectMapper;

    public QwenAsrProvider(QwenProperties qwenProperties, ObjectMapper objectMapper) {
        this.qwenProperties = qwenProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "Qwen ASR";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public AsrProviderStatusPayload status() {
        boolean configured = qwenProperties.enabled() && StringUtils.hasText(qwenProperties.apiKey());
        String message = configured
                ? "Qwen 实时语音识别已配置，作为优先实时语音识别方案。"
                : "Qwen 实时语音识别未配置，请设置 QWEN_ENABLED=true 和 DASHSCOPE_API_KEY。";
        return new AsrProviderStatusPayload(name(), configured, true, message,
                false, configured ? "等待音频流启动后连接 Qwen ASR。" : message, "QWEN_ASR");
    }

    @Override
    public AsrStreamSession start(String sessionId, AudioChunkMeta meta) throws Exception {
        if (!qwenProperties.enabled() || !StringUtils.hasText(qwenProperties.apiKey())) {
            throw new AsrProviderUnavailableException("Qwen DashScope API Key 未配置。");
        }
        QwenAsrStreamSession session = new QwenAsrStreamSession(sessionId, meta);
        session.connect(meta);
        log.info("Qwen ASR WebSocket 连接已建立，sessionId={}", sessionId);
        return session;
    }

    private String asrModel() {
        return StringUtils.hasText(qwenProperties.asrModel()) ? qwenProperties.asrModel() : "qwen3-asr-flash-realtime";
    }

    private Map<String, Object> sessionUpdatePayload(AudioChunkMeta meta) {
        int sampleRate = meta == null || meta.sampleRate() <= 0 ? 16000 : meta.sampleRate();
        return Map.of(
                "event_id", "event_" + UUID.randomUUID(),
                "type", "session.update",
                "session", Map.of(
                        "input_audio_format", "pcm",
                        "sample_rate", sampleRate,
                        "input_audio_transcription", Map.of(
                                "language", "en"
                        ),
                        "turn_detection", Map.of(
                                "type", "server_vad",
                                "threshold", 0.0,
                                "silence_duration_ms", 400
                        )
                )
        );
    }

    private Map<String, Object> audioAppendPayload(byte[] audio) {
        return Map.of(
                "event_id", "event_" + UUID.randomUUID(),
                "type", "input_audio_buffer.append",
                "audio", Base64.getEncoder().encodeToString(audio)
        );
    }

    private class QwenAsrStreamSession implements AsrStreamSession {
        private final String sessionId;
        private final AudioChunkMeta meta;
        private final Queue<AsrResult> results = new ConcurrentLinkedQueue<>();
        private volatile WebSocket webSocket;
        private volatile boolean connected;
        private volatile boolean terminalClose;
        private volatile String lastDiagnostic = "";
        private int reconnectCount;

        private QwenAsrStreamSession(String sessionId, AudioChunkMeta meta) {
            this.sessionId = sessionId;
            this.meta = meta;
        }

        private void connect(AudioChunkMeta meta) throws Exception {
            QwenAsrListener listener = new QwenAsrListener();
            String model = asrModel();
            String wsUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=" + model;
            this.webSocket = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build()
                    .newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + qwenProperties.apiKey())
                    .buildAsync(URI.create(wsUrl), listener)
                    .get(8, TimeUnit.SECONDS);
            this.webSocket.sendText(objectMapper.writeValueAsString(sessionUpdatePayload(meta)), true)
                    .get(2, TimeUnit.SECONDS);
            synchronized (this) {
                if (this.terminalClose) {
                    throw new AsrProviderUnavailableException("Qwen ASR 连接异常终止，无法建立连接。");
                }
                this.connected = true;
            }
            this.reconnectCount = 0;
        }

        private boolean reconnect() {
            if (reconnectCount >= MAX_RECONNECT_ATTEMPTS || terminalClose) {
                return false;
            }
            try {
                close();
                long backoffMs = Math.min(500L * (reconnectCount + 1), 3000);
                Thread.sleep(backoffMs);
                connect(meta);
                reconnectCount++;
                log.info("Qwen ASR 重连成功，sessionId={}, 第{}次重连", sessionId, reconnectCount);
                return true;
            } catch (Exception e) {
                log.warn("Qwen ASR 重连失败，sessionId={}, attempt={}", sessionId, reconnectCount + 1, e);
                reconnectCount++;
                return false;
            }
        }

        @Override
        public AsrProviderStatusPayload status() {
            String diagnostic = StringUtils.hasText(lastDiagnostic) ? "；" + lastDiagnostic : "";
            if (reconnectCount > 0) {
                diagnostic = diagnostic + "；已重连" + reconnectCount + "次";
            }
            return new AsrProviderStatusPayload(name(), connected, true,
                    connected ? "Qwen ASR 连接已建立。" : "Qwen ASR 连接已断开。",
                    connected, connected
                    ? "Qwen ASR 正在接收浏览器音频流。" + diagnostic
                    : "连接关闭或服务异常。" + diagnostic, "QWEN_ASR");
        }

        @Override
        public List<AsrResult> accept(AudioChunk chunk) {
            if (terminalClose) {
                throw new AsrProviderUnavailableException("Qwen ASR 连接异常终止，跳过重连。");
            }
            try {
                String msg = objectMapper.writeValueAsString(audioAppendPayload(chunk.data()));
                webSocket.sendText(msg, true).get(2, TimeUnit.SECONDS);
                if (chunk.chunkIndex() % 10 == 1) {
                    log.info("Qwen ASR 已发送音频块 chunkIndex={}, 数据大小={}字节",
                            chunk.chunkIndex(), chunk.data().length);
                }
            } catch (Exception e) {
                log.warn("Qwen ASR 发送音频块失败，尝试重连，sessionId={}", sessionId, e);
                lastDiagnostic = "发送失败尝试重连：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                if (!reconnect()) {
                    connected = false;
                    throw new AsrProviderUnavailableException("Qwen ASR 连接失败，已重连" + reconnectCount + "次，准备降级。", e);
                }
                try {
                    String msg = objectMapper.writeValueAsString(audioAppendPayload(chunk.data()));
                    webSocket.sendText(msg, true).get(2, TimeUnit.SECONDS);
                    lastDiagnostic = "重连后发送成功。";
                } catch (Exception retryEx) {
                    connected = false;
                    throw new AsrProviderUnavailableException("Qwen ASR 重连后发送仍失败，准备降级。", retryEx);
                }
            }
            List<AsrResult> results = drainResults();
            // 诊断：Qwen ASR 返回结果时打印
            if (!results.isEmpty()) {
                log.info("Qwen ASR 返回 {} 条结果", results.size());
                for (AsrResult r : results) {
                    log.info("Qwen ASR 识别结果: status={}, text={}", r.status(), r.text());
                }
            }
            return results;
        }

        @Override
        public List<AsrResult> stop() {
            connected = false;
            try {
                Map<String, Object> finishMsg = Map.of(
                        "event_id", "event_" + UUID.randomUUID(),
                        "type", "session.finish"
                );
                webSocket.sendText(objectMapper.writeValueAsString(finishMsg), true).join();
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stop").join();
            } catch (Exception ignored) {
            }
            return drainResults();
        }

        @Override
        public void close() {
            connected = false;
            if (webSocket != null) {
                webSocket.abort();
            }
        }

        private List<AsrResult> drainResults() {
            List<AsrResult> drained = new ArrayList<>();
            AsrResult result;
            while ((result = results.poll()) != null) {
                drained.add(result);
            }
            return drained;
        }

        private class QwenAsrListener implements WebSocket.Listener {
            private final StringBuilder textBuffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                textBuffer.append(data);
                if (last) {
                    String payload = textBuffer.toString();
                    textBuffer.setLength(0);
                    parseResult(payload);
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                log.warn("Qwen ASR WebSocket 错误，sessionId={}", sessionId, error);
                lastDiagnostic = "WebSocket 错误：" + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                log.info("Qwen ASR WebSocket 关闭，sessionId={}, statusCode={}, reason={}", sessionId, statusCode, reason);
                connected = false;
                if (statusCode == 401 || statusCode == 403) {
                    terminalClose = true;
                    lastDiagnostic = "认证失败（code=" + statusCode + "），请检查 DashScope API Key 是否有效。";
                } else if (statusCode != 1000) {
                    lastDiagnostic = "WebSocket 非正常关闭（code=" + statusCode
                            + (reason != null && !reason.isBlank() ? "，" + reason : "") + "）";
                }
                return null;
            }

            private void parseResult(String payload) {
                try {
                    JsonNode root = objectMapper.readTree(payload);
                    String type = root.path("type").asText("");
                    if (!"conversation.item.input_audio_transcription.completed".equals(type)
                            && !"conversation.item.input_audio_transcription.text".equals(type)) {
                        return;
                    }
                    String text = root.path("transcript").asText("");
                    if (!StringUtils.hasText(text)) {
                        return;
                    }
                    boolean finalResult = "conversation.item.input_audio_transcription.completed".equals(type);
                    results.add(new AsrResult(
                            "seg_qwen_" + Math.abs((sessionId + text).hashCode()),
                            text,
                            finalResult ? "FINAL" : "PARTIAL",
                            0,
                            0,
                            name()
                    ));
                    lastDiagnostic = "";
                } catch (Exception e) {
                    log.warn("Qwen ASR 消息解析失败，sessionId={}", sessionId, e);
                }
            }
        }
    }
}
