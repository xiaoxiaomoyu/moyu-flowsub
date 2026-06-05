package com.moyu.flowsub.translation;

/**
 * 翻译 Provider 统一接口，DeepSeek 与 Mock 翻译都挂在同一条链路上。
 */
public interface TranslationProvider {

    String name();

    int priority();

    TranslationProviderStatusPayload status();

    TranslationResult translate(TranslationRequest request) throws Exception;
}
