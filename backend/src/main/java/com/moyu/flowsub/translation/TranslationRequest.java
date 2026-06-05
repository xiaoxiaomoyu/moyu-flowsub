package com.moyu.flowsub.translation;

import java.util.List;

/**
 * 单条稳定 ASR 字幕进入翻译链路时携带的上下文。
 */
public record TranslationRequest(
        String sessionId,
        String segmentId,
        String sourceText,
        List<TranslationContextItem> recentContext
) {
}
