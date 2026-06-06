package com.moyu.flowsub.media;

public record MediaStatusResponse(
        boolean mikuEnabled,
        boolean mikuConfigured,
        boolean ffmpegConfigured,
        boolean ffprobeConfigured,
        boolean mockEnabled,
        String apiHost,
        String region,
        String message
) {
}
