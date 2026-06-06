package com.moyu.flowsub.translation;

import com.moyu.flowsub.asr.AsrResult;
import com.moyu.flowsub.subtitle.SubtitleCorrectionPayload;
import com.moyu.flowsub.subtitle.SubtitlePayload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TranslationService {

    private static final int MAX_CONTEXT_SIZE = 3;
    private static final int MAX_CORRECTION_SIZE = 2;

    private final List<TranslationProvider> providers;
    private final Map<String, TranslationSessionState> sessions = new ConcurrentHashMap<>();

    public TranslationService(List<TranslationProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(TranslationProvider::priority))
                .toList();
    }

    public TranslationProviderStatusPayload currentStatus() {
        for (TranslationProvider provider : providers) {
            TranslationProviderStatusPayload status = provider.status();
            if (status.available()) {
                return status;
            }
        }
        return new TranslationProviderStatusPayload("未启用", false, true,
                "没有可用的翻译 Provider。", "请配置 DeepSeek 或保留 Mock 翻译。");
    }

    public TranslationProcessResult translateFinal(String sessionId, AsrResult asrResult) {
        TranslationSessionState state = sessions.computeIfAbsent(sessionId, ignored -> new TranslationSessionState());
        List<TranslationContextItem> context = state.snapshot();
        TranslationRequest request = new TranslationRequest(
                sessionId,
                asrResult.segmentId(),
                asrResult.text(),
                context
        );
        TranslationResult result = translateWithFallback(request);
        SubtitlePayload subtitle = new SubtitlePayload(
                asrResult.segmentId(),
                asrResult.text(),
                result.translatedText(),
                "FINAL",
                1,
                false,
                asrResult.latencyMs() + result.latencyMs()
        );
        // 翻译链路不再携带修正，修正由 reviewCorrections() 异步独立触发，避免阻塞实时字幕展示。
        state.remember(new TranslationContextItem(
                asrResult.segmentId(),
                asrResult.text(),
                result.translatedText(),
                1
        ));
        return new TranslationProcessResult(
                subtitle,
                List.of(),
                new TranslationProviderStatusPayload(result.providerName(), true, result.fallback(),
                        result.fallback() ? "翻译已降级到 " + result.providerName() : "DeepSeek 流式翻译完成。",
                        result.fallback() ? "DeepSeek 未配置或调用失败。" : "真实翻译链路正常。"),
                result.latencyMs(),
                state.correctionCount
        );
    }

    /**
     * 异步上下文修正，取当前会话上下文窗口内的字幕，调用修正 Provider 检查是否需要修正。
     * 由 WebSocket 层在翻译完成后异步触发，不阻塞实时字幕展示。
     */
    public List<SubtitleCorrectionPayload> reviewCorrections(String sessionId) {
        TranslationSessionState state = sessions.get(sessionId);
        if (state == null) {
            return List.of();
        }
        List<TranslationContextItem> context = state.snapshot();
        if (context.size() < 2) {
            return List.of();
        }
        List<TranslationCorrection> suggestions = reviewWithFallback(context);
        List<SubtitleCorrectionPayload> payloads = state.applyCorrections(suggestions);
        state.correctionCount += payloads.size();
        return payloads;
    }

    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }

    private TranslationResult translateWithFallback(TranslationRequest request) {
        StringBuilder reasons = new StringBuilder();
        for (TranslationProvider provider : providers) {
            TranslationProviderStatusPayload status = provider.status();
            if (!status.available()) {
                appendReason(reasons, status.provider(), status.reason());
                continue;
            }
            try {
                return provider.translate(request);
            } catch (Exception e) {
                appendReason(reasons, status.provider(), e.getMessage());
            }
        }
        throw new TranslationProviderUnavailableException("没有可用的翻译 Provider：" + reasons);
    }

    private List<TranslationCorrection> reviewWithFallback(List<TranslationContextItem> context) {
        for (TranslationProvider provider : providers) {
            TranslationProviderStatusPayload status = provider.status();
            if (!status.available()) {
                continue;
            }
            try {
                return provider.review(context);
            } catch (Exception ignored) {
                // 单个 Provider 修正失败时尝试下一个。
            }
        }
        return List.of();
    }

    private void appendReason(StringBuilder builder, String provider, String reason) {
        if (!builder.isEmpty()) {
            builder.append("；");
        }
        builder.append(provider).append("：").append(reason == null ? "不可用" : reason);
    }

    private static class TranslationSessionState {
        private final Deque<TranslationContextItem> context = new ArrayDeque<>();
        private int correctionCount;

        private synchronized List<TranslationContextItem> snapshot() {
            return List.copyOf(context);
        }

        private synchronized void remember(TranslationContextItem item) {
            context.addLast(item);
            while (context.size() > MAX_CONTEXT_SIZE) {
                context.removeFirst();
            }
        }

        private synchronized List<SubtitleCorrectionPayload> applyCorrections(List<TranslationCorrection> suggestions) {
            if (suggestions == null || suggestions.isEmpty()) {
                return List.of();
            }
            List<SubtitleCorrectionPayload> payloads = new ArrayList<>();
            for (TranslationCorrection suggestion : suggestions) {
                if (payloads.size() >= MAX_CORRECTION_SIZE) {
                    break;
                }
                TranslationContextItem target = findRecent(suggestion.segmentId());
                if (target == null || !StringUtils.hasText(suggestion.newTranslatedText())) {
                    continue;
                }
                int nextVersion = target.version() + 1;
                String newSourceText = StringUtils.hasText(suggestion.newSourceText())
                        ? suggestion.newSourceText()
                        : target.sourceText();
                payloads.add(new SubtitleCorrectionPayload(
                        target.segmentId(),
                        target.sourceText(),
                        newSourceText,
                        target.translatedText(),
                        suggestion.newTranslatedText(),
                        nextVersion,
                        suggestion.reason()
                ));
                replaceContextItem(new TranslationContextItem(
                        target.segmentId(),
                        newSourceText,
                        suggestion.newTranslatedText(),
                        nextVersion
                ));
            }
            return payloads;
        }

        private TranslationContextItem findRecent(String segmentId) {
            if (!StringUtils.hasText(segmentId)) {
                return null;
            }
            return context.stream()
                    .skip(Math.max(0, context.size() - MAX_CORRECTION_SIZE))
                    .filter(item -> segmentId.equals(item.segmentId()))
                    .findFirst()
                    .orElse(null);
        }

        private void replaceContextItem(TranslationContextItem updated) {
            List<TranslationContextItem> items = new ArrayList<>(context);
            context.clear();
            for (TranslationContextItem item : items) {
                context.addLast(item.segmentId().equals(updated.segmentId()) ? updated : item);
            }
        }
    }
}
