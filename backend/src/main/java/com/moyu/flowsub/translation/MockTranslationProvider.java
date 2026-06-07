package com.moyu.flowsub.translation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MockTranslationProvider implements TranslationProvider {

    private static final Map<String, String> MOCK_TRANSLATIONS = Map.of(
            "Today we will build a real time translation assistant for technical talks.",
            "今天我们将构建一个面向技术分享的实时翻译助手。",
            "The browser captures microphone audio and sends small chunks to the backend.",
            "浏览器会采集麦克风音频，并把小块音频发送到后端。",
            "The backend can connect to Qwen ASR for speech recognition.",
            "后端可以连接 Qwen ASR 完成语音识别。",
            "If the cloud provider is not ready, the system falls back to a local ASR service.",
            "如果云端服务暂不可用，系统会降级到本地 ASR 服务。",
            "Every stable sentence becomes an English subtitle segment on the page.",
            "每个稳定句子都会成为页面上的英文字幕片段。",
            "In the next stage, Qwen will translate and correct the bilingual subtitles.",
            "在当前阶段，Qwen 会翻译并修正双语字幕。"
    );

    @Override
    public String name() {
        return "Mock 翻译";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public TranslationProviderStatusPayload status() {
        return new TranslationProviderStatusPayload(name(), true, false,
                "Mock 翻译保底服务可用。",
                "Qwen 未配置或调用失败时自动使用。");
    }

    @Override
    public TranslationResult translate(TranslationRequest request) {
        long start = System.currentTimeMillis();
        String translated = MOCK_TRANSLATIONS.getOrDefault(request.sourceText(), "模拟翻译：" + request.sourceText());
        List<TranslationCorrection> corrections = shouldCorrectPrevious(request)
                ? List.of(correctionForPrevious(request))
                : List.of();
        return new TranslationResult(translated, Math.max(80, System.currentTimeMillis() - start),
                name(), true, corrections);
    }

    private boolean shouldCorrectPrevious(TranslationRequest request) {
        String lowerText = request.sourceText().toLowerCase();
        return !request.recentContext().isEmpty()
                && (lowerText.contains("context") || lowerText.contains("correct"));
    }

    private TranslationCorrection correctionForPrevious(TranslationRequest request) {
        TranslationContextItem previous = request.recentContext().get(request.recentContext().size() - 1);
        return new TranslationCorrection(
                previous.segmentId(),
                previous.sourceText(),
                previous.translatedText().replace("模拟翻译：", "根据上下文优化："),
                "根据后文上下文优化上一条中文表达。"
        );
    }
}
