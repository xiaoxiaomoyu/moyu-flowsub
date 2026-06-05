package com.moyu.flowsub.audio;

import com.moyu.flowsub.asr.AsrProperties;
import com.moyu.flowsub.asr.AsrService;
import com.moyu.flowsub.asr.FunAsrProvider;
import com.moyu.flowsub.asr.MockAsrProvider;
import com.moyu.flowsub.asr.QiniuAsrProvider;
import com.moyu.flowsub.qiniu.QiniuProperties;
import com.moyu.flowsub.session.CreateSessionRequest;
import com.moyu.flowsub.session.SessionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudioStreamServiceTests {

    @Test
    void shouldFallbackToMockAsrWhenCloudProviderUnavailable() {
        AsrProperties asrProperties = new AsrProperties(true, "", 750);
        QiniuProperties qiniuProperties = new QiniuProperties(false, "", "", "", "");
        AsrService asrService = new AsrService(List.of(
                new QiniuAsrProvider(qiniuProperties),
                new FunAsrProvider(asrProperties),
                new MockAsrProvider(asrProperties)
        ));
        SessionService sessionService = new SessionService();
        AudioStreamService audioStreamService = new AudioStreamService(sessionService, asrService);
        String sessionId = sessionService.create(new CreateSessionRequest("测试会话", "en", "zh", "TECH_TALK")).getSessionId();

        audioStreamService.start(sessionId, new AudioChunkMeta(0, System.currentTimeMillis(), "pcm_s16le", 16000, 1, 0));
        AudioStreamProcessResult result = null;
        for (int index = 1; index <= 3; index++) {
            result = audioStreamService.accept(sessionId,
                    new AudioChunkMeta(index, System.currentTimeMillis(), "pcm_s16le", 16000, 1, 0.4),
                    new byte[3200]);
        }

        assertThat(result).isNotNull();
        assertThat(result.asrResult()).isNotNull();
        assertThat(result.asrResult().status()).isEqualTo("FINAL");
        assertThat(result.providerStatus().provider()).isEqualTo("Mock ASR");
        assertThat(result.providerStatus().fallback()).isTrue();
        assertThat(result.chunkCount()).isEqualTo(3);
        assertThat(result.subtitleCount()).isEqualTo(1);
    }
}
