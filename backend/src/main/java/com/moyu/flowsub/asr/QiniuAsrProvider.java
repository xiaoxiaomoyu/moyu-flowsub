package com.moyu.flowsub.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.audio.AudioChunk;
import com.moyu.flowsub.audio.AudioChunkMeta;
import com.moyu.flowsub.qiniu.QiniuAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Component
public class QiniuAsrProvider implements AsrProvider {

    private static final Logger log = LoggerFactory.getLogger(QiniuAsrProvider.class);

    private final QiniuAiProperties qiniuAiProperties;
    private final ObjectMapper objectMapper;

    public QiniuAsrProvider(QiniuAiProperties qiniuAiProperties, ObjectMapper objectMapper) {
        this.qiniuAiProperties = qiniuAiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "七牛云智能语音";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public AsrProviderStatusPayload status() {
        boolean enabled = qiniuAiProperties.asrEnabled();
        boolean hasApiKey = StringUtils.hasText(qiniuAiProperties.apiKey());
        boolean hasEndpoint = StringUtils.hasText(qiniuAiProperties.asrWsUrl());
        boolean configured = enabled && hasApiKey && hasEndpoint;
        String message = configured
                ? "七牛云智能语音已配置，将优先使用真实实时识别。"
                : "七牛云智能语音未就绪，自动降级到 FunASR 或 Mock ASR。";
        String reason = configured ? "等待音频流启动后连接七牛云。" : unavailableReason(enabled, hasApiKey, hasEndpoint);
        return new AsrProviderStatusPayload(name(), configured, false, message,
                false, reason, "QINIU_WS");
    }

    @Override
    public AsrStreamSession start(String sessionId, AudioChunkMeta meta) throws Exception {
        if (!qiniuAiProperties.asrEnabled() || !StringUtils.hasText(qiniuAiProperties.apiKey())) {
            throw new AsrProviderUnavailableException("七牛云 AI API Key 未配置。");
        }

        QiniuAsrListener listener = new QiniuAsrListener(sessionId);
        WebSocket.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5));
        if (StringUtils.hasText(qiniuAiProperties.apiKey())) {
            builder.header("Authorization", "Bearer " + qiniuAiProperties.apiKey());
        }
        WebSocket webSocket = builder.buildAsync(URI.create(qiniuAiProperties.asrWsUrl()), listener)
                .get(8, TimeUnit.SECONDS);

        // 发送启动配置作为文本 JSON 消息
        webSocket.sendText(objectMapper.writeValueAsString(startPayload(meta)), true).get(2, TimeUnit.SECONDS);
        log.info("七牛云 ASR WebSocket 连接已建立，sessionId={}", sessionId);
        return new QiniuAsrStreamSession(webSocket, listener);
    }

    private Map<String, Object> startPayload(AudioChunkMeta meta) {
        int sampleRate = meta == null || meta.sampleRate() <= 0 ? 16000 : meta.sampleRate();
        return Map.of(
                "audio", Map.of(
                        "format", "pcm",
                        "sample_rate", sampleRate,
                        "channels", 1,
                        "sample_bits", 16
                ),
                "request", Map.of(
                        "model_name", "asr",
                        "enable_itn", true
                )
        );
    }

    private String unavailableReason(boolean enabled, boolean hasApiKey, boolean hasEndpoint) {
        if (!enabled) {
            return "QINIU_ASR_ENABLED 未开启，请设置为 true 后再启动后端。";
        }
        if (!hasApiKey) {
            return "缺少 QINIU_AI_API_KEY。这里需要七牛云 AI Token API Key，不是 Kodo 的 QINIU_ACCESS_KEY/QINIU_SECRET_KEY。";
        }
        if (!hasEndpoint) {
            return "缺少 QINIU_ASR_WS_URL，默认可使用 wss://api.qnaigc.com/v1/voice/asr。";
        }
        return "七牛云智能语音配置不可用。";
    }

    private class QiniuAsrStreamSession implements AsrStreamSession {
        private final WebSocket webSocket;
        private final Queue<AsrResult> results;
        private final QiniuAsrListener listener;
        private volatile boolean connected = true;

        private QiniuAsrStreamSession(WebSocket webSocket, QiniuAsrListener listener) {
            this.webSocket = webSocket;
            this.listener = listener;
            this.results = listener.results;
        }

        @Override
        public AsrProviderStatusPayload status() {
            String diagnostic = listener.diagnosticSuffix();
            return new AsrProviderStatusPayload(name(), connected, false,
                    connected ? "七牛云智能语音连接已建立。" : "七牛云智能语音连接已断开。",
                    connected, connected
                    ? "七牛云正在接收浏览器音频流。" + diagnostic
                    : "连接关闭或服务异常。" + diagnostic, "QINIU_WS");
        }

        @Override
        public List<AsrResult> accept(AudioChunk chunk) {
            try {
                webSocket.sendBinary(ByteBuffer.wrap(chunk.data()), true).get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AsrProviderUnavailableException("七牛云智能语音发送音频块超时或失败，准备降级。", e);
            }
            return drainResults();
        }

        @Override
        public List<AsrResult> stop() {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stop").join();
            } catch (Exception ignored) {
                // 停止包发送失败不阻断本地会话清理。
            } finally {
                connected = false;
            }
            return drainResults();
        }

        @Override
        public void close() {
            connected = false;
            webSocket.abort();
        }

        private List<AsrResult> drainResults() {
            List<AsrResult> drained = new ArrayList<>();
            AsrResult result;
            while ((result = results.poll()) != null) {
                drained.add(result);
            }
            return drained;
        }
    }

    private class QiniuAsrListener implements WebSocket.Listener {
        private final String sessionId;
        private final Queue<AsrResult> results = new ConcurrentLinkedQueue<>();
        private final StringBuilder textBuffer = new StringBuilder();
        private volatile String lastDiagnostic = "";

        private QiniuAsrListener(String sessionId) {
            this.sessionId = sessionId;
        }

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
            log.warn("七牛云 ASR WebSocket 错误，sessionId={}", sessionId, error);
            lastDiagnostic = "WebSocket 错误：" + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("七牛云 ASR WebSocket 关闭，sessionId={}, statusCode={}, reason={}", sessionId, statusCode, reason);
            if (statusCode != 1000) {
                lastDiagnostic = "WebSocket 非正常关闭（code=" + statusCode
                        + (reason != null && !reason.isBlank() ? "，" + reason : "") + "）";
            }
            return null;
        }

        private void parseResult(String payload) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                String text = firstText(root);
                if (!StringUtils.hasText(text)) {
                    log.debug("七牛云 ASR 返回消息未包含字幕文本，sessionId={}, payload={}", sessionId, abbreviate(payload));
                    lastDiagnostic = "最近一次七牛云返回未包含字幕文本：" + abbreviate(payload);
                    return;
                }
                boolean finalResult = root.path("is_final").asBoolean(false)
                        || root.path("final").asBoolean(false)
                        || "final".equalsIgnoreCase(root.path("type").asText())
                        || "sentence_end".equalsIgnoreCase(root.path("type").asText());
                int chunkIndex = root.path("chunk_index").asInt(0);
                results.add(new AsrResult(
                        "seg_qiniu_" + Math.abs((sessionId + text).hashCode()),
                        text,
                        finalResult ? "FINAL" : "PARTIAL",
                        root.path("latency_ms").asLong(0),
                        chunkIndex,
                        name()
                ));
                lastDiagnostic = "";
            } catch (Exception e) {
                log.warn("七牛云 ASR 返回消息解析失败，sessionId={}, payload={}", sessionId, abbreviate(payload), e);
                lastDiagnostic = "消息解析失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                        + "，payload=" + abbreviate(payload);
            }
        }

        private String firstText(JsonNode root) {
            String direct = firstPreferredText(root);
            if (StringUtils.hasText(direct)) {
                return direct;
            }
            if (root.isObject()) {
                for (JsonNode child : root) {
                    String text = firstText(child);
                    if (StringUtils.hasText(text)) {
                        return text;
                    }
                }
            }
            if (root.isArray()) {
                for (JsonNode child : root) {
                    String text = firstText(child);
                    if (StringUtils.hasText(text)) {
                        return text;
                    }
                }
            }
            return "";
        }

        private String firstPreferredText(JsonNode root) {
            for (String field : List.of("text", "transcript", "sentence", "asr_text", "recognized_text")) {
                String text = root.path(field).asText("");
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
            return "";
        }

        private String diagnosticSuffix() {
            return StringUtils.hasText(lastDiagnostic) ? "；" + lastDiagnostic : "";
        }

        private String abbreviate(String text) {
            if (text == null) {
                return "";
            }
            return text.length() <= 180 ? text : text.substring(0, 180) + "...";
        }
    }
}
