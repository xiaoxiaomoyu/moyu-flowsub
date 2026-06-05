package com.moyu.flowsub.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.audio.AudioChunk;
import com.moyu.flowsub.audio.AudioChunkMeta;
import com.moyu.flowsub.qiniu.QiniuAiProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
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
import java.util.zip.GZIPOutputStream;

@Component
public class QiniuAsrProvider implements AsrProvider {

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
        boolean configured = qiniuAiProperties.asrEnabled()
                && StringUtils.hasText(qiniuAiProperties.apiKey())
                && StringUtils.hasText(qiniuAiProperties.asrWsUrl());
        String message = configured
                ? "七牛云智能语音已配置，将优先使用真实实时识别。"
                : "七牛云智能语音未配置，自动降级到 FunASR 或 Mock ASR。";
        return new AsrProviderStatusPayload(name(), configured, false, message,
                false, configured ? "等待音频流启动后连接七牛云。" : message, "QINIU_WS");
    }

    @Override
    public AsrStreamSession start(String sessionId, AudioChunkMeta meta) throws Exception {
        if (!qiniuAiProperties.asrEnabled() || !StringUtils.hasText(qiniuAiProperties.apiKey())) {
            throw new AsrProviderUnavailableException("七牛云 AI API Key 未配置。");
        }
        QiniuAsrListener listener = new QiniuAsrListener(sessionId);
        WebSocket webSocket = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + qiniuAiProperties.apiKey())
                .buildAsync(URI.create(qiniuAiProperties.asrWsUrl()), listener)
                .get(8, TimeUnit.SECONDS);
        webSocket.sendText(objectMapper.writeValueAsString(startPayload(meta)), true).join();
        return new QiniuAsrStreamSession(webSocket, listener.results);
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

    private byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOutput = new GZIPOutputStream(output)) {
                gzipOutput.write(data);
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new AsrProviderUnavailableException("七牛云音频块 gzip 封包失败。", e);
        }
    }

    private class QiniuAsrStreamSession implements AsrStreamSession {
        private final WebSocket webSocket;
        private final Queue<AsrResult> results;
        private volatile boolean connected = true;

        private QiniuAsrStreamSession(WebSocket webSocket, Queue<AsrResult> results) {
            this.webSocket = webSocket;
            this.results = results;
        }

        @Override
        public AsrProviderStatusPayload status() {
            return new AsrProviderStatusPayload(name(), connected, false,
                    connected ? "七牛云智能语音连接已建立。" : "七牛云智能语音连接已断开。",
                    connected, connected ? "七牛云正在接收浏览器音频流。" : "连接关闭或服务异常。", "QINIU_WS");
        }

        @Override
        public List<AsrResult> accept(AudioChunk chunk) {
            webSocket.sendBinary(ByteBuffer.wrap(gzip(chunk.data())), true).join();
            return drainResults();
        }

        @Override
        public List<AsrResult> stop() {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stop").join();
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
                parseResult(textBuffer.toString());
                textBuffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        private void parseResult(String payload) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                String text = firstText(root);
                if (!StringUtils.hasText(text)) {
                    return;
                }
                boolean finalResult = root.path("is_final").asBoolean(false)
                        || root.path("final").asBoolean(false)
                        || "final".equalsIgnoreCase(root.path("type").asText());
                int chunkIndex = root.path("chunk_index").asInt(0);
                results.add(new AsrResult(
                        "seg_qiniu_" + Math.abs((sessionId + text).hashCode()),
                        text,
                        finalResult ? "FINAL" : "PARTIAL",
                        root.path("latency_ms").asLong(0),
                        chunkIndex,
                        name()
                ));
            } catch (Exception ignored) {
                // 七牛云返回字段会随模型版本扩展，无法解析的非字幕事件直接忽略。
            }
        }

        private String firstText(JsonNode root) {
            if (StringUtils.hasText(root.path("text").asText())) {
                return root.path("text").asText();
            }
            if (StringUtils.hasText(root.path("result").path("text").asText())) {
                return root.path("result").path("text").asText();
            }
            if (StringUtils.hasText(root.path("data").path("text").asText())) {
                return root.path("data").path("text").asText();
            }
            return "";
        }
    }
}
