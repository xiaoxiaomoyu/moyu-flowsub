package com.moyu.flowsub.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.metrics.MetricsPayload;
import com.moyu.flowsub.qiniu.KodoUploadResult;
import com.moyu.flowsub.qiniu.QiniuProperties;
import com.moyu.flowsub.qiniu.QiniuService;
import com.moyu.flowsub.qiniu.QiniuStatusResponse;
import com.moyu.flowsub.session.CreateSessionRequest;
import com.moyu.flowsub.session.SessionService;
import com.moyu.flowsub.subtitle.SubtitleCorrectionPayload;
import com.moyu.flowsub.subtitle.SubtitlePayload;
import com.moyu.flowsub.qwen.QwenProperties;
import com.moyu.flowsub.summary.QwenSummaryProvider;
import com.moyu.flowsub.summary.SummaryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveServiceTests {

    @Test
    void shouldCreateLocalArchiveWhenKodoUnavailable() {
        SessionService sessionService = new SessionService();
        ArchiveService archiveService = new ArchiveService(sessionService, new FakeQiniuService(false, false),
                qiniuProperties(), objectMapper(), summaryService());
        String sessionId = createSession(sessionService);

        archiveService.recordSubtitle(sessionId, subtitle("seg_000001", "Hello everyone.", "大家好。"));
        archiveService.recordCorrection(sessionId, new SubtitleCorrectionPayload("seg_000001",
                "Hello every one.", "Hello everyone.", "大家每一个人好。", "大家好。", 2, "修正口语断句。"));
        archiveService.recordMetrics(sessionId, new MetricsPayload(120, 300, 420, 1, 1, 3,
                "Mock ASR", "Mock 翻译"));
        archiveService.appendAudio(sessionId, new byte[]{1, 2, 3, 4});

        ArchiveStatusResponse response = archiveService.archiveSession(sessionId);

        assertThat(response.status()).isEqualTo(ArchiveStatus.LOCAL_ONLY);
        assertThat(response.message()).contains("本地内存归档");
        assertThat(response.summaryMarkdown()).contains("字幕数：1");
        assertThat(response.summaryMarkdown()).contains("修正数：1");
        assertThat(response.summary()).isNotNull();
        assertThat(response.summary().providerName()).isEqualTo("未配置");
        assertThat(response.resources()).hasSize(10);
        assertThat(response.resources())
                .extracting(ArchiveResourceResponse::type)
                .containsExactly(ArchiveResourceType.METADATA, ArchiveResourceType.SUBTITLES,
                        ArchiveResourceType.CORRECTIONS, ArchiveResourceType.METRICS,
                        ArchiveResourceType.SUMMARY, ArchiveResourceType.INSIGHTS, ArchiveResourceType.AUDIO,
                        ArchiveResourceType.AUDIO_WAV, ArchiveResourceType.SUBTITLES_VTT,
                        ArchiveResourceType.PLAYBACK_MANIFEST);
        assertThat(response.resources())
                .filteredOn(resource -> resource.type() == ArchiveResourceType.AUDIO)
                .first()
                .extracting(ArchiveResourceResponse::sizeBytes)
                .isEqualTo(4L);
    }

    @Test
    void shouldAllowManualRetryWithoutBreakingExistingResources() {
        SessionService sessionService = new SessionService();
        ArchiveService archiveService = new ArchiveService(sessionService, new FakeQiniuService(false, false),
                qiniuProperties(), objectMapper(), summaryService());
        String sessionId = createSession(sessionService);
        archiveService.recordSubtitle(sessionId, subtitle("seg_000001", "We are testing archive retry.", "我们正在测试归档重试。"));

        ArchiveStatusResponse first = archiveService.archiveSession(sessionId);
        ArchiveStatusResponse second = archiveService.archiveSession(sessionId);

        assertThat(first.status()).isEqualTo(ArchiveStatus.LOCAL_ONLY);
        assertThat(second.status()).isEqualTo(ArchiveStatus.LOCAL_ONLY);
        assertThat(second.resources()).hasSize(10);
        assertThat(second.resources().get(0).key()).isEqualTo(first.resources().get(0).key());
        assertThat(second.summaryMarkdown()).contains("字幕数：1");
        assertThat(second.resources())
                .extracting(ArchiveResourceResponse::type)
                .contains(ArchiveResourceType.PLAYBACK_MANIFEST);
    }

    @Test
    void shouldReturnFailedStatusWhenKodoUploadFails() {
        SessionService sessionService = new SessionService();
        ArchiveService archiveService = new ArchiveService(sessionService, new FakeQiniuService(true, true),
                qiniuProperties(), objectMapper(), summaryService());
        String sessionId = createSession(sessionService);
        archiveService.recordSubtitle(sessionId, subtitle("seg_000001", "Upload may fail.", "上传可能失败。"));

        ArchiveStatusResponse response = archiveService.archiveSession(sessionId);

        assertThat(response.status()).isEqualTo(ArchiveStatus.FAILED);
        assertThat(response.message()).contains("模拟 Kodo 上传失败");
        assertThat(response.resources()).isEmpty();
    }

    @Test
    void shouldExposeUploadedResourcesWhenKodoReady() {
        SessionService sessionService = new SessionService();
        ArchiveService archiveService = new ArchiveService(sessionService, new FakeQiniuService(true, false),
                qiniuProperties(), objectMapper(), summaryService());
        String sessionId = createSession(sessionService);
        archiveService.recordSubtitle(sessionId, subtitle("seg_000001", "Kodo stores session files.", "Kodo 保存会话文件。"));

        ArchiveStatusResponse response = archiveService.archiveSession(sessionId);

        assertThat(response.status()).isEqualTo(ArchiveStatus.UPLOADED);
        assertThat(response.message()).contains("七牛云 Kodo");
        assertThat(response.resources()).hasSize(10);
        assertThat(response.resources()).allMatch(resource -> resource.url().startsWith("https://kodo.example.com/"));
        assertThat(response.resources())
                .extracting(ArchiveResourceResponse::type)
                .contains(ArchiveResourceType.INSIGHTS, ArchiveResourceType.AUDIO_WAV,
                        ArchiveResourceType.SUBTITLES_VTT, ArchiveResourceType.PLAYBACK_MANIFEST);
    }

    @Test
    void shouldExposePlaybackManifestForLocalArchive() {
        SessionService sessionService = new SessionService();
        ArchiveService archiveService = new ArchiveService(sessionService, new FakeQiniuService(false, false),
                qiniuProperties(), objectMapper(), summaryService());
        String sessionId = createSession(sessionService);
        archiveService.recordSubtitle(sessionId, subtitle("seg_000001", "Playback needs cues.", "回放需要字幕时间轴。"));
        archiveService.appendAudio(sessionId, new byte[]{1, 0, 2, 0});
        archiveService.archiveSession(sessionId);

        var manifest = archiveService.playbackManifest(sessionId);

        assertThat(manifest.cues()).hasSize(1);
        assertThat(manifest.audioUrl()).startsWith("data:audio/wav;base64,");
        assertThat(manifest.subtitleUrl()).startsWith("data:text/vtt;charset=utf-8;base64,");
        assertThat(manifest.resources())
                .extracting(ArchiveResourceResponse::type)
                .contains(ArchiveResourceType.AUDIO_WAV, ArchiveResourceType.SUBTITLES_VTT,
                        ArchiveResourceType.PLAYBACK_MANIFEST);
    }

    private String createSession(SessionService sessionService) {
        return sessionService.create(new CreateSessionRequest("归档测试", "en", "zh", "TECH_TALK")).getSessionId();
    }

    private SubtitlePayload subtitle(String segmentId, String sourceText, String translatedText) {
        return new SubtitlePayload(segmentId, sourceText, translatedText, "FINAL", 1, false, 420);
    }

    private QiniuProperties qiniuProperties() {
        return new QiniuProperties(true, "", "", "flowsub-test", "https://kodo.example.com",
                "moyu-flowsub", false, 3600);
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private SummaryService summaryService() {
        return new SummaryService(List.of(
                new QwenSummaryProvider(new QwenProperties(false, "", "", "", "", "", "", 1000, 0), objectMapper())
        ));
    }

    private static class FakeQiniuService implements QiniuService {
        private final boolean uploadReady;
        private final boolean failUpload;

        private FakeQiniuService(boolean uploadReady, boolean failUpload) {
            this.uploadReady = uploadReady;
            this.failUpload = failUpload;
        }

        @Override
        public QiniuStatusResponse status() {
            return new QiniuStatusResponse(uploadReady, uploadReady, uploadReady, uploadReady, true,
                    uploadReady, "moyu-flowsub", uploadReady ? "测试上传已就绪。" : "测试上传未配置。");
        }

        @Override
        public boolean uploadReady() {
            return uploadReady;
        }

        @Override
        public KodoUploadResult upload(String key, byte[] data, String contentType) {
            if (failUpload) {
                throw new IllegalStateException("模拟 Kodo 上传失败");
            }
            return new KodoUploadResult(key, "https://kodo.example.com/" + key, contentType, data.length, Instant.now());
        }

        @Override
        public java.util.List<String> list(String prefix) {
            return java.util.List.of();
        }

        @Override
        public byte[] download(String key) {
            throw new UnsupportedOperationException("FakeQiniuService does not support download");
        }

        @Override
        public String downloadUrl(String key) {
            return "https://kodo.example.com/" + key;
        }
    }
}
