package com.moyu.flowsub.translation;

import java.util.List;
import java.util.function.Consumer;

/**
 * 翻译 Provider 统一接口。
 */
public interface TranslationProvider {

    String name();

    int priority();

    TranslationProviderStatusPayload status();

    TranslationResult translate(TranslationRequest request) throws Exception;

    /**
     * 流式翻译，每收到一个 token 时回调 onToken（传入累积的完整译文）。
     * 默认实现回退到同步 translate()，一次性返回全文。
     *
     * @return 最终完整译文
     */
    default String translateStreaming(TranslationRequest request, Consumer<String> onToken) throws Exception {
        TranslationResult result = translate(request);
        onToken.accept(result.translatedText());
        return result.translatedText();
    }

    /**
     * 独立的上下文修正链路，不与实时翻译争抢延迟。默认返回空列表。
     */
    default List<TranslationCorrection> review(List<TranslationContextItem> recentContext) throws Exception {
        return List.of();
    }
}
