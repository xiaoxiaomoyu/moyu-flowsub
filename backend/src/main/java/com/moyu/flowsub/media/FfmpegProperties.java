package com.moyu.flowsub.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyu.flowsub.media.ffmpeg")
public record FfmpegProperties(
        String ffmpegPath,
        String ffprobePath,
        boolean mockEnabled
) {
}
