package com.moyu.flowsub.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.audio.AudioChunk;
import com.moyu.flowsub.audio.AudioChunkMeta;
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
public class FunAsrProvider implements AsrProvider {

    private final AsrProperties properties;
    private final ObjectMapper objectMapper;

    public FunAsrProvider(AsrProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "FunASR";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public AsrProviderStatusPayload status() {
        boolean configured = properties.funasrEnabled() && StringUtils.hasText(funasrEndpoint());
        String message = configured
                ? "FunASR 本地流式识别已配置，可作为七牛云不可用时的兜底。"
                : "FunASR 兜底服务未配置，继续降级到 Mock ASR。";
        return new AsrProviderStatusPayload(name(), configured, true, message,
                false, configured ? "等待音频流启动后连接 FunASR。" : message, "FUNASR_WS");
    }

    @Override
    public AsrStreamSession start(String sessionId, AudioChunkMeta meta) throws Exception {
        if (!properties.funasrEnabled() || !StringUtils.hasText(funasrEndpoint())) {
            throw new AsrProviderUnavailableException("FunASR WebSocket 地址未配置。");
        }
        FunAsrListener listener = new FunAsrListener(sessionId);
        WebSocket webSocket = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(funasrEndpoint()), listener)
                .get(8, TimeUnit.SECONDS);
        webSocket.sendText(objectMapper.writeValueAsString(startPayload(meta)), true).join();
        return new FunAsrStreamSession(webSocket, listener.results);
    }

    private String funasrEndpoint() {
        return StringUtils.hasText(properties.funasrWsEndpoint())
                ? properties.funasrWsEndpoint()
                : properties.funasrEndpoint();
    }

    private Map<String, Object> startPayload(AudioChunkMeta meta) {
        int sampleRate = meta == null || meta.sampleRate() <= 0 ? 16000 : meta.sampleRate();
        return Map.of(
                "mode", "2pass",
                "chunk_size", List.of(5, 10, 5),
                "chunk_interval", 10,
                "wav_name", "moyu-flowsub",
                "is_speaking", true,
                "audio_fs", sampleRate,
                "hotwords", ""
        );
    }

    private class FunAsrStreamSession implements AsrStreamSession {
        private final WebSocket webSocket;
        private final Queue<AsrResult> results;
        private volatile boolean connected = true;

        private FunAsrStreamSession(WebSocket webSocket, Queue<AsrResult> results) {
            this.webSocket = webSocket;
            this.results = results;
        }

        @Override
        public AsrProviderStatusPayload status() {
            return new AsrProviderStatusPayload(name(), connected, true,
                    connected ? "FunASR 本地识别连接已建立。" : "FunASR 本地识别连接已断开。",
                    connected, connected ? "FunASR 正在接收浏览器音频流。" : "连接关闭或服务异常。", "FUNASR_WS");
        }

        @Override
        public List<AsrResult> accept(AudioChunk chunk) {
            webSocket.sendBinary(ByteBuffer.wrap(chunk.data()), true).join();
            return drainResults();
        }

        @Override
        public List<AsrResult> stop() {
            try {
                webSocket.sendText("{\"is_speaking\":false}", true).join();
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

    private class FunAsrListener implements WebSocket.Listener {
        private final String sessionId;
        private final Queue<AsrResult> results = new ConcurrentLinkedQueue<>();
        private final StringBuilder textBuffer = new StringBuilder();

        private FunAsrListener(String sessionId) {
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
                String text = root.path("text").asText("");
                if (!StringUtils.hasText(text)) {
                    return;
                }
                String mode = root.path("mode").asText("2pass-online");
                boolean finalResult = "2pass-offline".equals(mode) || root.path("is_final").asBoolean(false);
                int chunkIndex = root.path("stamp_sents").isArray() ? root.path("stamp_sents").size() : 0;
                results.add(new AsrResult(
                        "seg_funasr_" + Math.abs((sessionId + text).hashCode()),
                        text,
                        finalResult ? "FINAL" : "PARTIAL",
                        0,
                        chunkIndex,
                        name()
                ));
            } catch (Exception ignored) {
                // 本地 FunASR 服务可能返回额外日志字段，解析失败时忽略本帧并继续接收。
            }
        }
    }
}
