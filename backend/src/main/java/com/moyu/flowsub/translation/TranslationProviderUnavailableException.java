package com.moyu.flowsub.translation;

/**
 * 翻译 Provider 不可用或调用失败时抛出。
 */
public class TranslationProviderUnavailableException extends RuntimeException {

    public TranslationProviderUnavailableException(String message) {
        super(message);
    }

    public TranslationProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
