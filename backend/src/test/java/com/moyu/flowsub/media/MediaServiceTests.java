package com.moyu.flowsub.media;

import com.moyu.flowsub.archive.ArchiveService;
import com.moyu.flowsub.audio.AudioStreamService;
import com.moyu.flowsub.session.SessionService;
import com.moyu.flowsub.translation.TranslationService;
import com.moyu.flowsub.websocket.WebSocketMessageBus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MediaServiceTests {

    @Test
    void shouldReportMockFallbackWhenMikuAndFfmpegMissing() {
        MediaService service = service(
                new MikuProperties(false, "https://mls.cn-east-1.qiniumiku.com", "cn-east-1",
                        "", "", "", "moyu-flowsub"),
                new FfmpegProperties("missing-ffmpeg-for-test", "missing-ffprobe-for-test", true)
        );

        MediaStatusResponse status = service.status();

        assertThat(status.mikuConfigured()).isFalse();
        assertThat(status.ffmpegConfigured()).isFalse();
        assertThat(status.mockEnabled()).isTrue();
        assertThat(status.message()).contains("降级到 Mock");
    }

    @Test
    void shouldPreparePredictableMikuUrls() {
        MediaService service = service(
                new MikuProperties(true, "https://mls.cn-east-1.qiniumiku.com", "cn-east-1",
                        "publish.example.com", "https://play.example.com", "https://whep.example.com",
                        "flowsub"),
                new FfmpegProperties("missing-ffmpeg-for-test", "missing-ffprobe-for-test", true)
        );

        LiveStreamSessionResponse response = service.prepare("session_demo");

        assertThat(response.ingestStatus()).isEqualTo(LiveIngestStatus.PREPARED);
        assertThat(response.streamName()).isEqualTo("flowsub-session-demo");
        assertThat(response.publishUrl()).isEqualTo("rtmp://publish.example.com/live/flowsub-session-demo");
        assertThat(response.playUrl()).isEqualTo("https://play.example.com/live/flowsub-session-demo.m3u8");
        assertThat(response.whepUrl()).isEqualTo("https://whep.example.com/live/flowsub-session-demo/whep");
    }

    @Test
    void shouldFailWhenNoRealStreamAndMockDisabled() {
        MediaService service = service(
                new MikuProperties(false, "", "cn-east-1", "", "", "", "moyu-flowsub"),
                new FfmpegProperties("missing-ffmpeg-for-test", "missing-ffprobe-for-test", false)
        );

        LiveStreamSessionResponse response = service.startIngest("session_demo");

        assertThat(response.ingestStatus()).isEqualTo(LiveIngestStatus.FAILED);
        assertThat(response.reason()).contains("MEDIA_MOCK_ENABLED=false");
    }

    private MediaService service(MikuProperties mikuProperties, FfmpegProperties ffmpegProperties) {
        return new MediaService(
                mikuProperties,
                ffmpegProperties,
                mock(SessionService.class),
                mock(AudioStreamService.class),
                mock(ArchiveService.class),
                mock(TranslationService.class),
                mock(WebSocketMessageBus.class)
        );
    }
}
