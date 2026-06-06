package com.moyu.flowsub.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.asr.AsrProvider;
import com.moyu.flowsub.asr.AsrProperties;
import com.moyu.flowsub.asr.AsrProviderStatusPayload;
import com.moyu.flowsub.asr.AsrResult;
import com.moyu.flowsub.asr.AsrService;
import com.moyu.flowsub.asr.AsrStreamSession;
import com.moyu.flowsub.asr.FunAsrProvider;
import com.moyu.flowsub.asr.MockAsrProvider;
import com.moyu.flowsub.asr.QiniuAsrProvider;
import com.moyu.flowsub.qiniu.QiniuAiProperties;
import com.moyu.flowsub.session.CreateSessionRequest;
import com.moyu.flowsub.session.SessionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudioStreamServiceTests {

    @Test
    void shouldFallbackToMockAsrWhenCloudProviderUnavailable() {
        ObjectMapper objectMapper = new ObjectMapper();
        AsrProperties asrProperties = new AsrProperties(true, false, "", "", 300);
        QiniuAiProperties qiniuAiProperties = new QiniuAiProperties(false, "", "wss://api.qnaigc.com/v1/voice/asr");
        AsrService asrService = new AsrService(List.of(
                new QiniuAsrProvider(qiniuAiProperties, objectMapper),
                new FunAsrProvider(asrProperties, objectMapper),
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
        assertThat(result.asrResults()).hasSize(1);
        assertThat(result.asrResults().get(0).status()).isEqualTo("FINAL");
        assertThat(result.providerStatus().provider()).isEqualTo("Mock ASR");
        assertThat(result.providerStatus().fallback()).isTrue();
        assertThat(result.providerStatus().endpointType()).isEqualTo("MOCK");
        assertThat(result.chunkCount()).isEqualTo(3);
        assertThat(result.subtitleCount()).isEqualTo(1);
    }

    @Test
    void shouldExposeUnavailableStatusWhenAllProvidersDisabled() {
        ObjectMapper objectMapper = new ObjectMapper();
        AsrProperties asrProperties = new AsrProperties(false, false, "", "", 300);
        QiniuAiProperties qiniuAiProperties = new QiniuAiProperties(false, "", "wss://api.qnaigc.com/v1/voice/asr");
        AsrService asrService = new AsrService(List.of(
                new QiniuAsrProvider(qiniuAiProperties, objectMapper),
                new FunAsrProvider(asrProperties, objectMapper),
                new MockAsrProvider(asrProperties)
        ));
        SessionService sessionService = new SessionService();
        AudioStreamService audioStreamService = new AudioStreamService(sessionService, asrService);
        String sessionId = sessionService.create(new CreateSessionRequest("禁用测试", "en", "zh", "TECH_TALK")).getSessionId();

        audioStreamService.start(sessionId, new AudioChunkMeta(0, System.currentTimeMillis(), "pcm_s16le", 16000, 1, 0));
        AudioStreamProcessResult result = audioStreamService.accept(sessionId,
                new AudioChunkMeta(1, System.currentTimeMillis(), "pcm_s16le", 16000, 1, 0.4),
                new byte[3200]);

        assertThat(result.asrResults()).isEmpty();
        assertThat(result.providerStatus().available()).isFalse();
        assertThat(result.providerStatus().endpointType()).isEqualTo("NONE");
        assertThat(result.providerStatus().reason()).contains("七牛云智能语音");
    }

    @Test
    void shouldFallbackWhenRealtimeProviderReturnsNoTextForManyChunks() {
        AsrProperties asrProperties = new AsrProperties(true, false, "", "", 300);
        AsrService asrService = new AsrService(List.of(
                new SilentRealtimeProvider(),
                new MockAsrProvider(asrProperties)
        ));
        SessionService sessionService = new SessionService();
        AudioStreamService audioStreamService = new AudioStreamService(sessionService, asrService);
        String sessionId = sessionService.create(new CreateSessionRequest("沉默降级", "en", "zh", "TECH_TALK")).getSessionId();

        audioStreamService.start(sessionId, new AudioChunkMeta(0, System.currentTimeMillis(), "pcm_s16le", 16000, 1, 0));
        AudioStreamProcessResult result = null;
        for (int index = 1; index <= 20; index++) {
            result = audioStreamService.accept(sessionId,
                    new AudioChunkMeta(index, System.currentTimeMillis(), "pcm_s16le", 16000, 1, 0.4),
                    new byte[3200]);
        }

        assertThat(result).isNotNull();
        assertThat(result.asrResults()).hasSize(1);
        assertThat(result.providerStatus().provider()).isEqualTo("Mock ASR");
        assertThat(result.providerStatus().fallback()).isTrue();
        assertThat(result.providerStatus().reason()).contains("未返回任何字幕文本");
        assertThat(result.chunkCount()).isEqualTo(20);
    }

    private static class SilentRealtimeProvider implements AsrProvider {

        @Override
        public String name() {
            return "沉默云端 ASR";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public AsrProviderStatusPayload status() {
            return new AsrProviderStatusPayload(name(), true, false, "测试用沉默 Provider。",
                    true, "测试用连接已建立。", "QINIU_WS");
        }

        @Override
        public AsrStreamSession start(String sessionId, AudioChunkMeta meta) {
            return new AsrStreamSession() {
                @Override
                public AsrProviderStatusPayload status() {
                    return SilentRealtimeProvider.this.status();
                }

                @Override
                public List<AsrResult> accept(AudioChunk chunk) {
                    return List.of();
                }

                @Override
                public void close() {
                    // 测试 Provider 没有真实连接。
                }
            };
        }
    }
}
