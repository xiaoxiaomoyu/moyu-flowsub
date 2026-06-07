package com.moyu.flowsub.translation;

import java.util.List;

/**
 * 翻译 Provider 统一接口，Qwen 与 Mock 翻译都挂在同一条链路上。
 */
public interface TranslationProvider {

    String name();

    int priority();

    TranslationProviderStatusPayload status();

    TranslationResult translate(TranslationRequest request) throws Exception;

    /**
     * 独立的上下文修正链路，不与实时翻译争抢延迟。默认返回空列表。
     */
    default List<TranslationCorrection> review(List<TranslationContextItem> recentContext) throws Exception {
        return List.of();
    }
}
