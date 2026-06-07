package com.moyu.flowsub.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.qwen.QwenProperties;
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
public class QwenTranslationProvider implements TranslationProvider {

    private static final String TRANSLATION_SYSTEM_PROMPT = """
            你是 MoYu FlowSub 的实时同传字幕翻译引擎。
            把英文技术演讲字幕翻译成自然、准确、简洁的中文。
            只返回中文译文，不要解释、不要英文、不要 JSON、不要 Markdown。""";

    private static final String CORRECTION_SYSTEM_PROMPT = """
            你是 MoYu FlowSub 的上下文修正引擎。
            根据最近的字幕上下文，检查前 1-2 条中文翻译是否需要修正。
            只修正明显不准确或与后文矛盾的译文，不要过度修改。
            必须只返回 JSON：
            {"corrections":[{"segmentId":"字幕ID","newSourceText":"修正后英文原文","newTranslatedText":"修正后中文","reason":"修正原因"}]}
            如果无需修正，返回 {"corrections":[]}。""";

    private static final Logger log = LoggerFactory.getLogger(QwenTranslationProvider.class);

    private final QwenProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QwenTranslationProvider(QwenProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.timeoutMs())))
                .build();
    }

    @Override
    public String name() {
        return "Qwen 翻译";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public TranslationProviderStatusPayload status() {
        boolean configured = properties.enabled()
                && StringUtils.hasText(properties.apiKey())
                && StringUtils.hasText(properties.baseUrl());
        String message = configured
                ? "Qwen 翻译已配置，将优先使用流式翻译生成中文译文。"
                : "Qwen 未配置，自动降级到 Mock 翻译。";
        return new TranslationProviderStatusPayload(name(), configured, false, message, message);
    }

    @Override
    public TranslationResult translate(TranslationRequest request) throws Exception {
        if (!status().available()) {
            throw new TranslationProviderUnavailableException("Qwen 配置不完整。");
        }

        long start = System.currentTimeMillis();
        String model = StringUtils.hasText(properties.translationModel()) ? properties.translationModel() : "qwen-plus";
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "temperature", properties.temperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", TRANSLATION_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildTranslationUserPrompt(request))
                )
        ));
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl()))
                .timeout(Duration.ofMillis(Math.max(3000, properties.timeoutMs())))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("Qwen 翻译 HTTP 请求失败：{}", e.getMessage());
            throw new TranslationProviderUnavailableException("Qwen 调用失败：" + e.getMessage(), e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Qwen 翻译返回非 2xx 状态码：{}，body={}", response.statusCode(),
                    response.body() == null ? "" : response.body().substring(0, Math.min(500, response.body().length())));
            throw new TranslationProviderUnavailableException("Qwen 调用失败，HTTP " + response.statusCode());
        }

        String translatedText;
        try {
            JsonNode root = objectMapper.readTree(response.body());
            translatedText = root.path("choices").path(0).path("message").path("content").asText("").trim();
        } catch (Exception e) {
            log.warn("Qwen 翻译响应解析失败：{}", e.getMessage());
            throw new TranslationProviderUnavailableException("Qwen 响应解析失败：" + e.getMessage(), e);
        }

        if (!StringUtils.hasText(translatedText)) {
            log.warn("Qwen 翻译返回内容为空，body={}",
                    response.body() == null ? "" : response.body().substring(0, Math.min(500, response.body().length())));
            throw new TranslationProviderUnavailableException("Qwen 翻译返回内容为空。");
        }

        log.info("Qwen 翻译完成，耗时 {}ms，原文={}，译文={}",
                System.currentTimeMillis() - start,
                request.sourceText().length() > 50 ? request.sourceText().substring(0, 50) + "..." : request.sourceText(),
                translatedText.length() > 50 ? translatedText.substring(0, 50) + "..." : translatedText);

        return new TranslationResult(
                translatedText,
                Math.max(1, System.currentTimeMillis() - start),
                name(),
                false,
                List.of()
        );
    }

    @Override
    public List<TranslationCorrection> review(List<TranslationContextItem> recentContext) throws Exception {
        if (!status().available() || recentContext.size() < 2) {
            return List.of();
        }

        String model = StringUtils.hasText(properties.translationModel()) ? properties.translationModel() : "qwen-plus";
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "temperature", 0,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", CORRECTION_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildCorrectionUserPrompt(recentContext))
                )
        ));
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl()))
                .timeout(Duration.ofMillis(8000))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }
            String content = objectMapper.readTree(response.body())
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("");
            if (!StringUtils.hasText(content)) {
                return List.of();
            }
            return parseCorrections(content);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String chatCompletionsUrl() {
        String baseUrl = properties.baseUrl().replaceAll("/+$", "");
        return baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";
    }

    private String buildTranslationUserPrompt(TranslationRequest request) {
        StringBuilder builder = new StringBuilder();
        if (!request.recentContext().isEmpty()) {
            builder.append("最近字幕上下文：\n");
            for (TranslationContextItem item : request.recentContext()) {
                builder.append("- ")
                        .append(item.segmentId())
                        .append(" | EN: ")
                        .append(item.sourceText())
                        .append(" | ZH: ")
                        .append(item.translatedText())
                        .append('\n');
            }
            builder.append('\n');
        }
        builder.append("当前需要翻译的英文字幕：\n")
                .append(request.segmentId())
                .append(" | ")
                .append(request.sourceText());
        return builder.toString();
    }

    private String buildCorrectionUserPrompt(List<TranslationContextItem> context) {
        StringBuilder builder = new StringBuilder();
        builder.append("最近的翻译字幕上下文：\n");
        for (TranslationContextItem item : context) {
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
        return builder.toString();
    }

    private List<TranslationCorrection> parseCorrections(String content) {
        try {
            String normalized = content.replace("```json", "").replace("```", "").trim();
            JsonNode root = objectMapper.readTree(normalized);
            JsonNode correctionNodes = root.path("corrections");
            if (!correctionNodes.isArray()) {
                return List.of();
            }
            List<TranslationCorrection> corrections = new ArrayList<>();
            for (JsonNode node : correctionNodes) {
                String segmentId = node.path("segmentId").asText("");
                String newTranslatedText = node.path("newTranslatedText").asText("");
                if (!StringUtils.hasText(segmentId) || !StringUtils.hasText(newTranslatedText)) {
                    continue;
                }
                corrections.add(new TranslationCorrection(
                        segmentId,
                        node.path("newSourceText").asText(""),
                        newTranslatedText,
                        node.path("reason").asText("根据上下文修正译文。")
                ));
            }
            return corrections;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
