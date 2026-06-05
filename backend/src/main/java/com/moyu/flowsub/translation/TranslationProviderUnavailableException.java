package com.moyu.flowsub.translation;

/**
 * Provider 不可用或调用失败时抛出，TranslationService 会自动尝试下一个 Provider。
 */
public class TranslationProviderUnavailableException extends RuntimeException {

    public TranslationProviderUnavailableException(String message) {
        super(message);
    }

    public TranslationProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
