package com.moyu.flowsub.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.asr.AsrResult;
import com.moyu.flowsub.qwen.QwenProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationServiceTests {

    @Test
    void shouldThrowWhenQwenNotConfigured() {
        TranslationService translationService = new TranslationService(List.of(
                new QwenTranslationProvider(new QwenProperties(false, "", "", "", "", "", "http://localhost", 1000, 0),
                        new ObjectMapper())
        ));

        assertThatThrownBy(() -> translationService.translateFinal("session_test",
                new AsrResult("seg_001", "Hello world.", "FINAL", 100, 1, "Qwen ASR")))
                .isInstanceOf(TranslationProviderUnavailableException.class);
    }

    @Test
    void shouldTranslateWithQwen() throws Exception {
        HttpServer server = startJsonServer("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "上下文窗口会帮助系统修正术语。"
                      }
                    }
                  ]
                }
                """);
        try {
            QwenTranslationProvider provider = new QwenTranslationProvider(
                    new QwenProperties(true, "test-key", "", "qwen-plus", "", "",
                            "http://127.0.0.1:" + server.getAddress().getPort(), 3000, 0),
                    new ObjectMapper()
            );

            TranslationResult result = provider.translate(new TranslationRequest(
                    "session_test",
                    "seg_000002",
                    "The context window helps correct terms.",
                    List.of(new TranslationContextItem("seg_000001", "We use rag to improve answers.",
                            "我们使用破布来改进答案。", 1))
            ));

            assertThat(result.translatedText()).isEqualTo("上下文窗口会帮助系统修正术语。");
            assertThat(result.corrections()).isEmpty();
            assertThat(result.providerName()).isEqualTo("Qwen 翻译");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldGenerateCorrectionViaReview() throws Exception {
        HttpServer server = startJsonServer("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"corrections\\":[{\\"segmentId\\":\\"seg_000001\\",\\"newSourceText\\":\\"We use RAG.\\",\\"newTranslatedText\\":\\"我们使用 RAG。\\",\\"reason\\":\\"根据后文修正术语。\\"}]}"
                      }
                    }
                  ]
                }
                """);
        try {
            TranslationService translationService = new TranslationService(List.of(
                    new QwenTranslationProvider(
                            new QwenProperties(true, "test-key", "", "qwen-plus", "", "",
                                    "http://127.0.0.1:" + server.getAddress().getPort(), 3000, 0),
                            new ObjectMapper()
                    )
            ));
            translationService.translateFinal("session_test",
                    new AsrResult("seg_000001", "We use rag.", "FINAL", 100, 1, "Qwen ASR"));
            translationService.translateFinal("session_test",
                    new AsrResult("seg_000002", "Later context fixes the previous subtitle.", "FINAL", 100, 2, "Qwen ASR"));

            var corrections = translationService.reviewCorrections("session_test");

            assertThat(corrections).hasSize(1);
            assertThat(corrections.get(0).segmentId()).isEqualTo("seg_000001");
            assertThat(corrections.get(0).newTranslatedText()).isEqualTo("我们使用 RAG。");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldStreamTranslateViaSse() throws Exception {
        HttpServer server = startSseServer(List.of("我们", "使用", " RAG", " 改进", " 答案。"));
        try {
            QwenTranslationProvider provider = new QwenTranslationProvider(
                    new QwenProperties(true, "test-key", "", "qwen-turbo", "", "",
                            "http://127.0.0.1:" + server.getAddress().getPort(), 15000, 0),
                    new ObjectMapper()
            );

            List<String> partials = new ArrayList<>();
            AtomicReference<String> finalText = new AtomicReference<>();
            TranslationRequest request = new TranslationRequest(
                    "session_test", "seg_001", "We use RAG to improve answers.", List.of());

            String result = provider.translateStreaming(request, partial -> {
                partials.add(partial);
                finalText.set(partial);
            });

            assertThat(partials).hasSize(5);
            assertThat(partials.get(0)).isEqualTo("我们");
            assertThat(partials.get(1)).isEqualTo("我们使用");
            assertThat(partials.get(4)).isEqualTo("我们使用 RAG 改进 答案。");
            assertThat(result).isEqualTo("我们使用 RAG 改进 答案。");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldIntegrateStreamingViaTranslationService() throws Exception {
        HttpServer server = startSseServer(List.of("上下文", "窗口", "修正", "术语。"));
        try {
            TranslationService translationService = new TranslationService(List.of(
                    new QwenTranslationProvider(
                            new QwenProperties(true, "test-key", "", "qwen-turbo", "", "",
                                    "http://127.0.0.1:" + server.getAddress().getPort(), 15000, 0),
                            new ObjectMapper()
                    )
            ));

            List<String> partialTexts = new ArrayList<>();
            AtomicReference<String> finalText = new AtomicReference<>();
            translationService.translateFinalStreaming("session_test",
                    new AsrResult("seg_001", "Context window helps.", "FINAL", 100, 1, "Qwen ASR"),
                    partial -> partialTexts.add(partial.translatedText()),
                    result -> finalText.set(result.subtitle().translatedText()));

            assertThat(partialTexts).isNotEmpty();
            assertThat(partialTexts.get(partialTexts.size() - 1)).isEqualTo("上下文窗口修正术语。");
            assertThat(finalText.get()).isEqualTo("上下文窗口修正术语。");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startJsonServer(String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startSseServer(List<String> tokens) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                for (String token : tokens) {
                    String sse = "data: " + new ObjectMapper().writeValueAsString(Map.of(
                            "choices", List.of(Map.of("delta", Map.of("content", token)))
                    )) + "\n\n";
                    os.write(sse.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
                os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            exchange.close();
        });
        server.start();
        return server;
    }
}
