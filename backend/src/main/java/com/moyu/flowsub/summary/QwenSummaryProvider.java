package com.moyu.flowsub.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.archive.ArchiveSnapshot;
import com.moyu.flowsub.qwen.QwenProperties;
import com.moyu.flowsub.subtitle.SubtitleCorrectionPayload;
import com.moyu.flowsub.subtitle.SubtitlePayload;
import com.moyu.flowsub.translation.TranslationProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class QwenSummaryProvider implements SummaryProvider {

    private static final Logger log = LoggerFactory.getLogger(QwenSummaryProvider.class);

    private final QwenProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QwenSummaryProvider(QwenProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.timeoutMs())))
                .build();
    }

    @Override
    public String name() {
        return "Qwen 总结";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean available() {
        return properties.enabled()
                && StringUtils.hasText(properties.apiKey())
                && StringUtils.hasText(properties.baseUrl());
    }

    @Override
    public String unavailableReason() {
        return available() ? "" : "Qwen 总结未启用或配置不完整。";
    }

    @Override
    public SummaryResult summarize(SummaryRequest request) throws Exception {
        if (!available()) {
            throw new TranslationProviderUnavailableException(unavailableReason());
        }
        String model = StringUtils.hasText(properties.summaryModel()) ? properties.summaryModel() : "qwen-plus";
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "temperature", 0.2,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", userPrompt(request.snapshot()))
                )
        ));
        log.info("调用 Qwen 会后总结 API，model={}，字幕数={}", model, request.snapshot().subtitleCount());
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl()))
                .timeout(Duration.ofMillis(Math.max(30000, properties.timeoutMs())))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = response.body() == null ? "" : response.body().substring(0, Math.min(500, response.body().length()));
            log.warn("Qwen 总结返回非 2xx 状态码：{}，body={}", response.statusCode(), errorBody);
            throw new TranslationProviderUnavailableException("Qwen 总结调用失败，HTTP " + response.statusCode());
        }
        String content = objectMapper.readTree(response.body())
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText("");
        if (!StringUtils.hasText(content)) {
            log.warn("Qwen 总结返回内容为空，body={}",
                    response.body() == null ? "" : response.body().substring(0, Math.min(500, response.body().length())));
            throw new TranslationProviderUnavailableException("Qwen 总结返回内容为空。");
        }
        log.info("Qwen 总结生成成功，响应长度={}", content.length());
        return parseModelPayload(content);
    }

    private String chatCompletionsUrl() {
        String baseUrl = properties.baseUrl().replaceAll("/+$", "");
        return baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";
    }

    private String systemPrompt() {
        return """
                你是 MoYu FlowSub 的会后总结引擎。
                请根据实时同传字幕、修正记录和指标生成中文会后复盘。
                必须只返回 JSON，不要返回 Markdown。JSON 字段：
                {
                  "abstractText": "150 字以内中文摘要",
                  "timeline": [{"timeLabel":"片段或阶段标识","title":"阶段标题","detail":"阶段说明"}],
                  "terms": [{"term":"英文术语","translation":"中文译名","explanation":"中文解释"}],
                  "keySentences": [{"sourceText":"英文原句","translatedText":"中文译文","reason":"入选原因"}]
                }
                时间线最多 6 条，术语最多 8 条，重点句最多 5 条。
                """;
    }

    private String userPrompt(ArchiveSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("会话标题：").append(snapshot.session().getTitle()).append('\n');
        builder.append("场景：").append(snapshot.session().getSceneType()).append('\n');
        builder.append("语言：").append(snapshot.session().getSourceLang()).append(" -> ")
                .append(snapshot.session().getTargetLang()).append('\n');
        builder.append("字幕数：").append(snapshot.subtitleCount()).append("，修正数：")
                .append(snapshot.correctionCount()).append('\n');
        builder.append("\n双语字幕：\n");
        for (SubtitlePayload subtitle : snapshot.subtitles()) {
            builder.append("- ").append(subtitle.segmentId())
                    .append(" | EN: ").append(subtitle.sourceText())
                    .append(" | ZH: ").append(subtitle.translatedText())
                    .append(" | corrected=").append(subtitle.isCorrected())
                    .append('\n');
        }
        builder.append("\n修正记录：\n");
        for (SubtitleCorrectionPayload correction : snapshot.corrections()) {
            builder.append("- ").append(correction.segmentId())
                    .append(" | old: ").append(correction.oldTranslatedText())
                    .append(" | new: ").append(correction.newTranslatedText())
                    .append(" | reason: ").append(correction.reason())
                    .append('\n');
        }
        return builder.toString();
    }

    private SummaryResult parseModelPayload(String content) throws Exception {
        JsonNode root = objectMapper.readTree(content.replace("```json", "").replace("```", "").trim());
        return new SummaryResult(
                root.path("abstractText").asText("本次会话已生成 AI 会后摘要。"),
                parseTimeline(root.path("timeline")),
                parseTerms(root.path("terms")),
                parseKeySentences(root.path("keySentences")),
                name(),
                false,
                "Qwen 会后总结生成成功。"
        );
    }

    private List<SummaryTimelineItem> parseTimeline(JsonNode nodes) {
        List<SummaryTimelineItem> result = new ArrayList<>();
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                result.add(new SummaryTimelineItem(
                        node.path("timeLabel").asText("片段"),
                        node.path("title").asText("阶段摘要"),
                        node.path("detail").asText("")
                ));
            }
        }
        return result;
    }

    private List<SummaryTerm> parseTerms(JsonNode nodes) {
        List<SummaryTerm> result = new ArrayList<>();
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                result.add(new SummaryTerm(
                        node.path("term").asText(""),
                        node.path("translation").asText(""),
                        node.path("explanation").asText("")
                ));
            }
        }
        return result;
    }

    private List<SummaryKeySentence> parseKeySentences(JsonNode nodes) {
        List<SummaryKeySentence> result = new ArrayList<>();
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                result.add(new SummaryKeySentence(
                        node.path("sourceText").asText(""),
                        node.path("translatedText").asText(""),
                        node.path("reason").asText("")
                ));
            }
        }
        return result;
    }
}
