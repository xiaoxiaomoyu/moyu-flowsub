package com.moyu.flowsub.asr;

/**
 * 向前端说明当前 ASR Provider 选择和降级原因。
 */
public record AsrProviderStatusPayload(
        String provider,
        boolean available,
        boolean fallback,
        String message,
        boolean connected,
        String reason,
        String endpointType
) {
    public AsrProviderStatusPayload(String provider, boolean available, boolean fallback, String message) {
        this(provider, available, fallback, message, available, message, "UNKNOWN");
    }
}
