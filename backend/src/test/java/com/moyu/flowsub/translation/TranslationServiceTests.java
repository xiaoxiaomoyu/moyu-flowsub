package com.moyu.flowsub.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.asr.AsrResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationServiceTests {

    @Test
    void shouldFallbackToMockTranslationWhenDeepSeekUnavailable() {
        TranslationService translationService = new TranslationService(List.of(
                new DeepSeekTranslationProvider(new DeepSeekProperties(false, "", "http://localhost", "deepseek-v4-pro", 1000), new ObjectMapper()),
                new MockTranslationProvider()
        ));

        TranslationProcessResult result = translationService.translateFinal("session_test",
                new AsrResult("seg_asr_000001",
                        "Today we will build a real time translation assistant for technical talks.",
                        "FINAL", 120, 1, "Mock ASR"));

        assertThat(result.subtitle().translatedText()).contains("实时翻译助手");
        assertThat(result.providerStatus().provider()).isEqualTo("Mock 翻译");
        assertThat(result.providerStatus().fallback()).isTrue();
    }

    @Test
    void shouldParseDeepSeekTranslationAndCorrections() throws Exception {
        HttpServer server = startServer("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"translatedText\\":\\"上下文窗口会帮助系统修正术语。\\",\\"corrections\\":[{\\"segmentId\\":\\"seg_000001\\",\\"newSourceText\\":\\"We use RAG to improve answers.\\",\\"newTranslatedText\\":\\"我们使用 RAG 来改进答案。\\",\\"reason\\":\\"修正技术术语 RAG。\\"}]}"
                      }
                    }
                  ]
                }
                """);
        try {
            DeepSeekTranslationProvider provider = new DeepSeekTranslationProvider(
                    new DeepSeekProperties(true, "test-key", "http://127.0.0.1:" + server.getAddress().getPort(), "deepseek-v4-pro", 3000),
                    new ObjectMapper()
            );

            TranslationResult result = provider.translate(new TranslationRequest(
                    "session_test",
                    "seg_000002",
                    "The context window helps correct terms.",
                    List.of(new TranslationContextItem("seg_000001", "We use rag to improve answers.", "我们使用破布来改进答案。", 1))
            ));

            assertThat(result.translatedText()).isEqualTo("上下文窗口会帮助系统修正术语。");
            assertThat(result.corrections()).hasSize(1);
            assertThat(result.corrections().get(0).newTranslatedText()).contains("RAG");
            assertThat(result.providerName()).isEqualTo("DeepSeek-V4-Pro");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldGenerateCorrectionPayloadFromProviderSuggestion() throws Exception {
        HttpServer server = startServer("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"translatedText\\":\\"后文会修正前面的字幕。\\",\\"corrections\\":[{\\"segmentId\\":\\"seg_000001\\",\\"newSourceText\\":\\"We use RAG.\\",\\"newTranslatedText\\":\\"我们使用 RAG。\\",\\"reason\\":\\"根据后文修正术语。\\"}]}"
                      }
                    }
                  ]
                }
                """);
        try {
            TranslationService translationService = new TranslationService(List.of(
                    new DeepSeekTranslationProvider(
                            new DeepSeekProperties(true, "test-key", "http://127.0.0.1:" + server.getAddress().getPort(), "deepseek-v4-pro", 3000),
                            new ObjectMapper()
                    ),
                    new MockTranslationProvider()
            ));
            translationService.translateFinal("session_test",
                    new AsrResult("seg_000001", "We use rag.", "FINAL", 100, 1, "Mock ASR"));

            TranslationProcessResult result = translationService.translateFinal("session_test",
                    new AsrResult("seg_000002", "Later context fixes the previous subtitle.", "FINAL", 100, 2, "Mock ASR"));

            assertThat(result.corrections()).hasSize(1);
            assertThat(result.corrections().get(0).segmentId()).isEqualTo("seg_000001");
            assertThat(result.corrections().get(0).newTranslatedText()).isEqualTo("我们使用 RAG。");
            assertThat(result.totalCorrectionCount()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(String responseBody) throws IOException {
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
}
