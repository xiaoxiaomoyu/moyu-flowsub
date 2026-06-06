package com.moyu.flowsub.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.metrics.MetricsPayload;
import com.moyu.flowsub.playback.PlaybackCue;
import com.moyu.flowsub.playback.PlaybackManifestResponse;
import com.moyu.flowsub.qiniu.KodoUploadResult;
import com.moyu.flowsub.qiniu.QiniuProperties;
import com.moyu.flowsub.qiniu.QiniuService;
import com.moyu.flowsub.session.FlowSession;
import com.moyu.flowsub.session.SessionService;
import com.moyu.flowsub.subtitle.SubtitleCorrectionPayload;
import com.moyu.flowsub.subtitle.SubtitlePayload;
import com.moyu.flowsub.summary.SummaryResult;
import com.moyu.flowsub.summary.SummaryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ArchiveService {

    private final SessionService sessionService;
    private final QiniuService qiniuService;
    private final QiniuProperties qiniuProperties;
    private final ObjectMapper objectMapper;
    private final SummaryService summaryService;
    private final Map<String, ArchiveState> archives = new ConcurrentHashMap<>();

    public ArchiveService(SessionService sessionService,
                          QiniuService qiniuService,
                          QiniuProperties qiniuProperties,
                          ObjectMapper objectMapper,
                          SummaryService summaryService) {
        this.sessionService = sessionService;
        this.qiniuService = qiniuService;
        this.qiniuProperties = qiniuProperties;
        this.objectMapper = objectMapper;
        this.summaryService = summaryService;
    }

    public void recordSubtitle(String sessionId, SubtitlePayload subtitle) {
        state(sessionId).recordSubtitle(subtitle);
    }

    public void recordCorrection(String sessionId, SubtitleCorrectionPayload correction) {
        state(sessionId).recordCorrection(correction);
    }

    public void recordMetrics(String sessionId, MetricsPayload metrics) {
        state(sessionId).recordMetrics(metrics);
    }

    public void appendAudio(String sessionId, byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        state(sessionId).appendAudio(data);
    }

    public ArchiveStatusResponse archiveSession(String sessionId) {
        FlowSession session = sessionService.get(sessionId);
        ArchiveState state = state(sessionId);
        synchronized (state) {
            state.status = qiniuService.uploadReady() ? ArchiveStatus.UPLOADING : ArchiveStatus.LOCAL_ONLY;
            state.updatedAt = Instant.now();
            try {
                ArchiveSnapshot snapshot = state.snapshot(session);
                SummaryResult summary = summaryService.summarize(snapshot);
                String summaryMarkdown = summaryService.toMarkdown(snapshot, summary, qiniuService.uploadReady());
                Map<ArchiveResourceType, ResourceContent> contents = resourceContents(sessionId, snapshot, summaryMarkdown, summary);
                List<ArchiveResourceResponse> resources = qiniuService.uploadReady()
                        ? uploadResources(sessionId, contents)
                        : localResources(sessionId, contents);
                state.resources = resources;
                state.summaryMarkdown = summaryMarkdown;
                state.summary = summary;
                state.status = qiniuService.uploadReady() ? ArchiveStatus.UPLOADED : ArchiveStatus.LOCAL_ONLY;
                state.message = qiniuService.uploadReady()
                        ? "会话资源已上传到七牛云 Kodo。"
                        : "七牛云 Kodo 未完整配置，已生成本地内存归档。";
            } catch (Exception e) {
                state.status = ArchiveStatus.FAILED;
                state.message = e.getMessage() == null ? "会话归档失败。" : e.getMessage();
            }
            state.updatedAt = Instant.now();
            return state.response(sessionId);
        }
    }

    public ArchiveStatusResponse getArchive(String sessionId) {
        ArchiveState state = state(sessionId);
        synchronized (state) {
            return state.response(sessionId);
        }
    }

    public List<ArchiveStatusResponse> listArchives() {
        return archives.entrySet().stream()
                .map(entry -> {
                    synchronized (entry.getValue()) {
                        return entry.getValue().response(entry.getKey());
                    }
                })
                .sorted(Comparator.comparing(ArchiveStatusResponse::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public PlaybackManifestResponse playbackManifest(String sessionId) {
        FlowSession session = sessionService.get(sessionId);
        ArchiveState state = state(sessionId);
        synchronized (state) {
            ArchiveSnapshot snapshot = state.snapshot(session);
            List<PlaybackCue> cues = playbackCues(snapshot);
            String audioUrl = resourceUrl(state.resources, ArchiveResourceType.AUDIO_WAV);
            String subtitleUrl = resourceUrl(state.resources, ArchiveResourceType.SUBTITLES_VTT);
            if (!StringUtils.hasText(audioUrl) && state.audio.size() > 0) {
                audioUrl = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wavBytes(state.audioBytes()));
            }
            if (!StringUtils.hasText(subtitleUrl) && !snapshot.subtitles().isEmpty()) {
                subtitleUrl = "data:text/vtt;charset=utf-8;base64,"
                        + Base64.getEncoder().encodeToString(webVtt(cues).getBytes(StandardCharsets.UTF_8));
            }
            boolean fallback = !qiniuService.uploadReady() || !StringUtils.hasText(audioUrl);
            return new PlaybackManifestResponse(
                    sessionId,
                    session,
                    audioUrl,
                    subtitleUrl,
                    cues,
                    snapshot.corrections(),
                    state.summary,
                    state.resources,
                    fallback ? "本地回放" : "Kodo 回放",
                    fallback,
                    fallback ? "未检测到 Kodo 回放资源，已使用本地内存回放清单。" : "Kodo 回放资源已就绪。"
            );
        }
    }

    private ArchiveState state(String sessionId) {
        return archives.computeIfAbsent(sessionId, ignored -> new ArchiveState());
    }

    private Map<ArchiveResourceType, ResourceContent> resourceContents(String sessionId,
                                                                       ArchiveSnapshot snapshot,
                                                                       String summary,
                                                                       SummaryResult insights) throws Exception {
        Map<ArchiveResourceType, ResourceContent> contents = new LinkedHashMap<>();
        contents.put(ArchiveResourceType.METADATA, json("metadata.json", snapshot));
        contents.put(ArchiveResourceType.SUBTITLES, json("subtitles.json", snapshot.subtitles()));
        contents.put(ArchiveResourceType.CORRECTIONS, json("corrections.json", snapshot.corrections()));
        contents.put(ArchiveResourceType.METRICS, json("metrics.json", snapshot.metrics()));
        contents.put(ArchiveResourceType.SUMMARY, new ResourceContent("summary.md", "text/markdown; charset=utf-8",
                summary.getBytes(StandardCharsets.UTF_8)));
        contents.put(ArchiveResourceType.INSIGHTS, json("insights.json", insights));
        contents.put(ArchiveResourceType.AUDIO, new ResourceContent("audio.pcm", "application/octet-stream",
                state(sessionId).audioBytes()));
        byte[] wav = wavBytes(state(sessionId).audioBytes());
        List<PlaybackCue> cues = playbackCues(snapshot);
        contents.put(ArchiveResourceType.AUDIO_WAV, new ResourceContent("audio.wav", "audio/wav", wav));
        contents.put(ArchiveResourceType.SUBTITLES_VTT, new ResourceContent("subtitles.vtt", "text/vtt; charset=utf-8",
                webVtt(cues).getBytes(StandardCharsets.UTF_8)));
        contents.put(ArchiveResourceType.PLAYBACK_MANIFEST, json("playback-manifest.json",
                new PlaybackManifestResponse(sessionId, snapshot.session(), "", "", cues, snapshot.corrections(),
                        insights, List.of(), "归档生成", !qiniuService.uploadReady(),
                        "完整资源 URL 会通过 /api/playback/sessions/" + sessionId + " 查询。")));
        return contents;
    }

    private ResourceContent json(String filename, Object payload) throws Exception {
        return new ResourceContent(filename, "application/json; charset=utf-8",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload));
    }

    private List<ArchiveResourceResponse> uploadResources(String sessionId,
                                                          Map<ArchiveResourceType, ResourceContent> contents) {
        List<ArchiveResourceResponse> resources = new ArrayList<>();
        contents.forEach((type, content) -> {
            String key = resourceKey(sessionId, content.filename());
            KodoUploadResult result = qiniuService.upload(key, content.data(), content.contentType());
            resources.add(new ArchiveResourceResponse(type, result.key(), result.url(), result.contentType(),
                    result.sizeBytes(), result.uploadedAt()));
        });
        return resources;
    }

    private List<ArchiveResourceResponse> localResources(String sessionId,
                                                         Map<ArchiveResourceType, ResourceContent> contents) {
        Instant now = Instant.now();
        return contents.entrySet().stream()
                .map(entry -> new ArchiveResourceResponse(entry.getKey(), resourceKey(sessionId, entry.getValue().filename()),
                        "", entry.getValue().contentType(), entry.getValue().data().length, now))
                .toList();
    }

    private String resourceKey(String sessionId, String filename) {
        return archivePrefix() + "/" + sessionId + "/" + filename;
    }

    private String archivePrefix() {
        String prefix = StringUtils.hasText(qiniuProperties.archivePrefix())
                ? qiniuProperties.archivePrefix()
                : "moyu-flowsub";
        return prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String resourceUrl(List<ArchiveResourceResponse> resources, ArchiveResourceType type) {
        return resources.stream()
                .filter(resource -> resource.type() == type)
                .map(ArchiveResourceResponse::url)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private List<PlaybackCue> playbackCues(ArchiveSnapshot snapshot) {
        List<PlaybackCue> cues = new ArrayList<>();
        for (int i = 0; i < snapshot.subtitles().size(); i++) {
            SubtitlePayload subtitle = snapshot.subtitles().get(i);
            double start = i * 3.0;
            cues.add(new PlaybackCue(subtitle.segmentId(), start, start + 3.0,
                    subtitle.sourceText(), subtitle.translatedText(), subtitle.isCorrected()));
        }
        return cues;
    }

    private String webVtt(List<PlaybackCue> cues) {
        StringBuilder builder = new StringBuilder("WEBVTT\n\n");
        for (PlaybackCue cue : cues) {
            builder.append(cue.segmentId()).append('\n');
            builder.append(vttTime(cue.startSeconds())).append(" --> ").append(vttTime(cue.endSeconds())).append('\n');
            builder.append(cue.sourceText()).append('\n');
            builder.append(cue.translatedText()).append("\n\n");
        }
        return builder.toString();
    }

    private String vttTime(double seconds) {
        long millis = Math.round(seconds * 1000);
        long hours = millis / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long secs = (millis % 60_000) / 1000;
        long ms = millis % 1000;
        return "%02d:%02d:%02d.%03d".formatted(hours, minutes, secs, ms);
    }

    private byte[] wavBytes(byte[] pcm) {
        int dataSize = pcm == null ? 0 : pcm.length;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(16000);
        buffer.putInt(16000 * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataSize);
        if (dataSize > 0) {
            buffer.put(pcm);
        }
        return buffer.array();
    }

    private record ResourceContent(String filename, String contentType, byte[] data) {
    }

    private static class ArchiveState {
        private final Map<String, SubtitlePayload> subtitles = new LinkedHashMap<>();
        private final List<SubtitleCorrectionPayload> corrections = new ArrayList<>();
        private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        private MetricsPayload metrics;
        private ArchiveStatus status = ArchiveStatus.PENDING;
        private String message = "等待会话结束后归档。";
        private String summaryMarkdown = "";
        private SummaryResult summary = SummaryResult.empty("等待会话结束后生成会后总结。");
        private List<ArchiveResourceResponse> resources = List.of();
        private Instant updatedAt = Instant.now();

        private synchronized void recordSubtitle(SubtitlePayload subtitle) {
            subtitles.put(subtitle.segmentId(), subtitle);
            updatedAt = Instant.now();
        }

        private synchronized void recordCorrection(SubtitleCorrectionPayload correction) {
            corrections.add(correction);
            SubtitlePayload target = subtitles.get(correction.segmentId());
            if (target != null) {
                subtitles.put(correction.segmentId(), new SubtitlePayload(
                        correction.segmentId(),
                        correction.newSourceText(),
                        correction.newTranslatedText(),
                        "CORRECTED",
                        correction.version(),
                        true,
                        target.latencyMs()
                ));
            }
            updatedAt = Instant.now();
        }

        private synchronized void recordMetrics(MetricsPayload metrics) {
            this.metrics = metrics;
            updatedAt = Instant.now();
        }

        private synchronized void appendAudio(byte[] data) {
            audio.writeBytes(data);
            updatedAt = Instant.now();
        }

        private synchronized byte[] audioBytes() {
            return audio.toByteArray();
        }

        private synchronized ArchiveSnapshot snapshot(FlowSession session) {
            return new ArchiveSnapshot(
                    session,
                    subtitles.size(),
                    corrections.size(),
                    audio.size(),
                    metrics,
                    List.copyOf(subtitles.values()),
                    List.copyOf(corrections),
                    Instant.now()
            );
        }

        private synchronized ArchiveStatusResponse response(String sessionId) {
            return new ArchiveStatusResponse(sessionId, status, message, summaryMarkdown, summary, resources, updatedAt);
        }
    }
}
