package com.moyu.flowsub.translation;

/**
 * 向前端说明当前翻译 Provider 和降级原因。
 */
public record TranslationProviderStatusPayload(
        String provider,
        boolean available,
        boolean fallback,
        String message,
        String reason
) {
}
