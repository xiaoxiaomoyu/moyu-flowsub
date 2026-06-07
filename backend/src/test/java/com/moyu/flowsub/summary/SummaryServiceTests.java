package com.moyu.flowsub.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.archive.ArchiveSnapshot;
import com.moyu.flowsub.metrics.MetricsPayload;
import com.moyu.flowsub.qwen.QwenProperties;
import com.moyu.flowsub.session.FlowSession;
import com.moyu.flowsub.session.SessionStatus;
import com.moyu.flowsub.subtitle.SubtitleCorrectionPayload;
import com.moyu.flowsub.subtitle.SubtitlePayload;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryServiceTests {

    @Test
    void shouldReturnEmptyWhenQwenUnavailable() {
        SummaryService summaryService = new SummaryService(List.of(
                new QwenSummaryProvider(new QwenProperties(false, "", "", "", "", "http://localhost", 1000, 0),
                        new ObjectMapper())
        ));

        SummaryResult result = summaryService.summarize(snapshot());

        assertThat(result.providerName()).isEqualTo("未配置");
        assertThat(result.fallback()).isFalse();
        assertThat(result.reason()).contains("没有可用的");
    }

    @Test
    void shouldParseQwenSummaryJson() throws Exception {
        HttpServer server = startServer("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"abstractText\\":\\"本次分享介绍了实时字幕系统的识别、翻译和修正闭环。\\",\\"timeline\\":[{\\"timeLabel\\":\\"seg_000001\\",\\"title\\":\\"介绍实时字幕\\",\\"detail\\":\\"说明 ASR 与翻译链路。\\"}],\\"terms\\":[{\\"term\\":\\"ASR\\",\\"translation\\":\\"语音识别\\",\\"explanation\\":\\"把音频转换为文本。\\"}],\\"keySentences\\":[{\\"sourceText\\":\\"We build real time captions.\\",\\"translatedText\\":\\"我们构建实时字幕。\\",\\"reason\\":\\"概括核心能力。\\"}]}"
                      }
                    }
                  ]
                }
                """);
        try {
            QwenSummaryProvider provider = new QwenSummaryProvider(
                    new QwenProperties(true, "test-key", "", "", "qwen-plus",
                            "http://127.0.0.1:" + server.getAddress().getPort(), 3000, 0),
                    new ObjectMapper()
            );

            SummaryResult result = provider.summarize(new SummaryRequest(snapshot()));

            assertThat(result.providerName()).isEqualTo("Qwen 总结");
            assertThat(result.fallback()).isFalse();
            assertThat(result.abstractText()).contains("实时字幕系统");
            assertThat(result.timeline()).hasSize(1);
            assertThat(result.terms().get(0).translation()).isEqualTo("语音识别");
            assertThat(result.keySentences().get(0).translatedText()).contains("实时字幕");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRenderMarkdownWhenNoProviderAvailable() {
        SummaryService summaryService = new SummaryService(List.of(
                new QwenSummaryProvider(new QwenProperties(false, "", "", "", "", "http://localhost", 1000, 0),
                        new ObjectMapper())
        ));

        SummaryResult result = summaryService.summarize(snapshot());
        String markdown = summaryService.toMarkdown(snapshot(), result, false);

        assertThat(markdown).contains("## 中文摘要");
        assertThat(markdown).contains("## 时间线");
        assertThat(markdown).contains("insights.json");
        assertThat(markdown).contains("未配置");
    }

    private ArchiveSnapshot snapshot() {
        FlowSession session = FlowSession.builder()
                .sessionId("session_test")
                .title("归档测试会话")
                .sourceLang("en")
                .targetLang("zh")
                .sceneType("TECH_TALK")
                .status(SessionStatus.FINISHED)
                .createdAt(Instant.now())
                .finishedAt(Instant.now())
                .build();
        List<SubtitlePayload> subtitles = List.of(
                subtitle("seg_000001", "We build real time captions with ASR.",
                        "我们使用语音识别构建实时字幕。"),
                subtitle("seg_000002", "The context helps translation correction.",
                        "上下文会帮助翻译修正。")
        );
        List<SubtitleCorrectionPayload> corrections = List.of(new SubtitleCorrectionPayload(
                "seg_000002",
                "The context help translation correction.",
                "The context helps translation correction.",
                "上下文帮助翻译。",
                "上下文会帮助翻译修正。",
                2,
                "修正动词和译文表达。"
        ));
        return new ArchiveSnapshot(session, subtitles.size(), corrections.size(), 128,
                new MetricsPayload(120, 260, 380, subtitles.size(), corrections.size(), 4,
                        "Qwen ASR", false, "Qwen 翻译", false),
                subtitles, corrections, Instant.now());
    }

    private SubtitlePayload subtitle(String segmentId, String sourceText, String translatedText) {
        return new SubtitlePayload(segmentId, sourceText, translatedText, "FINAL", 1, false, 380);
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
