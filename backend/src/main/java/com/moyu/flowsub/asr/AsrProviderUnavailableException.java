package com.moyu.flowsub.asr;

/**
 * Provider 当前不可用时抛出该异常，AsrService 会自动尝试下一个 Provider。
 */
public class AsrProviderUnavailableException extends RuntimeException {

    public AsrProviderUnavailableException(String message) {
        super(message);
    }

    public AsrProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
