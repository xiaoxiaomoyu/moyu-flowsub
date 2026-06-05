package com.moyu.flowsub.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class DeepSeekTranslationProvider implements TranslationProvider {

    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekTranslationProvider(DeepSeekProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.timeoutMs())))
                .build();
    }

    @Override
    public String name() {
        return "DeepSeek-V4-Pro";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public TranslationProviderStatusPayload status() {
        boolean configured = properties.enabled()
                && StringUtils.hasText(properties.apiKey())
                && StringUtils.hasText(properties.baseUrl())
                && StringUtils.hasText(properties.model());
        String message = configured
                ? "DeepSeek 翻译已配置，将优先生成真实中文译文。"
                : "DeepSeek 未配置，自动降级到 Mock 翻译。";
        return new TranslationProviderStatusPayload(name(), configured, false, message, message);
    }

    @Override
    public TranslationResult translate(TranslationRequest request) throws Exception {
        if (!status().available()) {
            throw new TranslationProviderUnavailableException("DeepSeek 配置不完整。");
        }

        long start = System.currentTimeMillis();
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", properties.model(),
                "temperature", 0.2,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", userPrompt(request))
                )
        ));
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl()))
                .timeout(Duration.ofMillis(Math.max(1000, properties.timeoutMs())))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TranslationProviderUnavailableException("DeepSeek 调用失败，HTTP " + response.statusCode());
        }

        String content = objectMapper.readTree(response.body())
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText("");
        if (!StringUtils.hasText(content)) {
            throw new TranslationProviderUnavailableException("DeepSeek 返回内容为空。");
        }

        TranslationResultPayload payload = parseModelPayload(content);
        return new TranslationResult(
                payload.translatedText(),
                Math.max(1, System.currentTimeMillis() - start),
                name(),
                false,
                payload.corrections()
        );
    }

    private String chatCompletionsUrl() {
        String baseUrl = properties.baseUrl().replaceAll("/+$", "");
        return baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";
    }

    private String systemPrompt() {
        return """
                你是 MoYu FlowSub 的实时同传字幕翻译引擎。
                请把英文技术演讲字幕翻译成自然、准确、简洁的中文。
                你可以根据最近上下文修正前 1 到 2 条历史字幕，但不要过度改写。
                必须只返回 JSON，不要返回 Markdown。
                JSON 格式：
                {
                  "translatedText": "当前句中文译文",
                  "corrections": [
                    {
                      "segmentId": "需要修正的历史字幕 ID",
                      "newSourceText": "修正后的英文原文，可与原文相同",
                      "newTranslatedText": "修正后的中文译文",
                      "reason": "中文修正原因"
                    }
                  ]
                }
                """;
    }

    private String userPrompt(TranslationRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("最近稳定字幕上下文：\n");
        for (TranslationContextItem item : request.recentContext()) {
            builder.append("- ")
                    .append(item.segmentId())
                    .append(" | EN: ")
                    .append(item.sourceText())
                    .append(" | ZH: ")
                    .append(item.translatedText())
                    .append(" | v")
                    .append(item.version())
                    .append('\n');
        }
        builder.append("\n当前需要翻译的英文字幕：\n")
                .append(request.segmentId())
                .append(" | ")
                .append(request.sourceText());
        return builder.toString();
    }

    private TranslationResultPayload parseModelPayload(String content) {
        try {
            String normalized = content.replace("```json", "").replace("```", "").trim();
            JsonNode root = objectMapper.readTree(normalized);
            String translatedText = root.path("translatedText").asText("");
            if (!StringUtils.hasText(translatedText)) {
                translatedText = root.path("translation").asText("");
            }
            List<TranslationCorrection> corrections = new ArrayList<>();
            JsonNode correctionNodes = root.path("corrections");
            if (correctionNodes.isArray()) {
                for (JsonNode node : correctionNodes) {
                    corrections.add(new TranslationCorrection(
                            node.path("segmentId").asText(""),
                            node.path("newSourceText").asText(""),
                            node.path("newTranslatedText").asText(""),
                            node.path("reason").asText("根据上下文修正译文。")
                    ));
                }
            }
            return new TranslationResultPayload(
                    StringUtils.hasText(translatedText) ? translatedText : content.trim(),
                    corrections
            );
        } catch (Exception ignored) {
            // 模型偶尔会返回纯文本译文，此时直接作为当前句中文译文使用。
            return new TranslationResultPayload(content.trim(), List.of());
        }
    }

    private record TranslationResultPayload(
            String translatedText,
            List<TranslationCorrection> corrections
    ) {
    }
}
