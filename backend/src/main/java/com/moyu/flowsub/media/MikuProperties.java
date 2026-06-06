package com.moyu.flowsub.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyu.flowsub.media.miku")
public record MikuProperties(
        boolean enabled,
        String apiHost,
        String region,
        String publishDomain,
        String playDomain,
        String whepDomain,
        String streamPrefix
) {
}
