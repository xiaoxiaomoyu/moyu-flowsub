package com.moyu.flowsub.asr;

/**
 * ASR Provider 启动或处理失败时抛出。
 */
public class AsrProviderUnavailableException extends RuntimeException {

    public AsrProviderUnavailableException(String message) {
        super(message);
    }

    public AsrProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
