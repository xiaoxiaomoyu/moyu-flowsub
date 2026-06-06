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
import java.util.stream.Stream;

@Component
public class DeepSeekTranslationProvider implements TranslationProvider {

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
                ? "DeepSeek 翻译已配置，将优先使用流式翻译生成中文译文。"
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
                "temperature", properties.temperature(),
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", TRANSLATION_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildTranslationUserPrompt(request))
                )
        ));
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl()))
                .timeout(Duration.ofMillis(Math.max(1000, properties.timeoutMs())))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<Stream<String>> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TranslationProviderUnavailableException("DeepSeek 调用失败，HTTP " + response.statusCode());
        }

        StringBuilder content = new StringBuilder();
        try (Stream<String> lines = response.body()) {
            lines.forEach(line -> {
                if (line.startsWith("data: ") && line.length() > 6) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        return;
                    }
                    try {
                        JsonNode chunk = objectMapper.readTree(data);
                        JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
                        if (!delta.isMissingNode()) {
                            content.append(delta.asText(""));
                        }
                    } catch (Exception ignored) {
                        // 个别 SSE 行解析失败不影响整体翻译结果。
                    }
                }
            });
        }

        String translatedText = content.toString().trim();
        if (!StringUtils.hasText(translatedText)) {
            throw new TranslationProviderUnavailableException("DeepSeek 流式翻译返回内容为空。");
        }

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

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", properties.model(),
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
            // 修正链路失败不应影响主翻译链路，静默返回空列表。
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
