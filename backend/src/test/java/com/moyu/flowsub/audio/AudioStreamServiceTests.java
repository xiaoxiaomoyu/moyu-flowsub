package com.moyu.flowsub.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.asr.AsrProperties;
import com.moyu.flowsub.asr.AsrService;
import com.moyu.flowsub.asr.QwenAsrProvider;
import com.moyu.flowsub.qwen.QwenProperties;
import com.moyu.flowsub.asr.AsrProviderUnavailableException;
import com.moyu.flowsub.session.CreateSessionRequest;
import com.moyu.flowsub.session.SessionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioStreamServiceTests {

    @Test
    void shouldReturnUnavailableStatusWhenQwenNotConfigured() {
        ObjectMapper objectMapper = new ObjectMapper();
        AsrProperties asrProperties = new AsrProperties(300);
        QwenProperties qwenProperties = new QwenProperties(false, "", "", "", "", "", 1000, 0);
        AsrService asrService = new AsrService(List.of(
                new QwenAsrProvider(qwenProperties, objectMapper)
        ));
        SessionService sessionService = new SessionService();
        AudioStreamService audioStreamService = new AudioStreamService(sessionService, asrService);

        var status = audioStreamService.providerStatus();

        assertThat(status.available()).isFalse();
        assertThat(status.provider()).isEqualTo("未启用");
        assertThat(status.endpointType()).isEqualTo("NONE");
    }

    @Test
    void shouldThrowWhenStartCalledWithoutAvailableProvider() {
        ObjectMapper objectMapper = new ObjectMapper();
        AsrProperties asrProperties = new AsrProperties(300);
        QwenProperties qwenProperties = new QwenProperties(false, "", "", "", "", "", 1000, 0);
        AsrService asrService = new AsrService(List.of(
                new QwenAsrProvider(qwenProperties, objectMapper)
        ));
        SessionService sessionService = new SessionService();
        AudioStreamService audioStreamService = new AudioStreamService(sessionService, asrService);
        String sessionId = sessionService.create(new CreateSessionRequest("测试会话", "en", "zh", "TECH_TALK"))
                .getSessionId();

        assertThatThrownBy(() -> audioStreamService.start(sessionId,
                new AudioChunkMeta(0, System.currentTimeMillis(), "pcm_s16le", 16000, 1, 0)))
                .isInstanceOf(AsrProviderUnavailableException.class);
    }
}
