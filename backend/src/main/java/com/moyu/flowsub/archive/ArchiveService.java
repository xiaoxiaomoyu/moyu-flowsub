package com.moyu.flowsub.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.metrics.MetricsPayload;
import com.moyu.flowsub.qiniu.KodoUploadResult;
import com.moyu.flowsub.qiniu.QiniuProperties;
import com.moyu.flowsub.qiniu.QiniuService;
import com.moyu.flowsub.session.FlowSession;
import com.moyu.flowsub.session.SessionService;
import com.moyu.flowsub.subtitle.SubtitleCorrectionPayload;
import com.moyu.flowsub.subtitle.SubtitlePayload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
    private final Map<String, ArchiveState> archives = new ConcurrentHashMap<>();

    public ArchiveService(SessionService sessionService,
                          QiniuService qiniuService,
                          QiniuProperties qiniuProperties,
                          ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.qiniuService = qiniuService;
        this.qiniuProperties = qiniuProperties;
        this.objectMapper = objectMapper;
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
                String summary = summaryMarkdown(snapshot, qiniuService.uploadReady());
                Map<ArchiveResourceType, ResourceContent> contents = resourceContents(sessionId, snapshot, summary);
                List<ArchiveResourceResponse> resources = qiniuService.uploadReady()
                        ? uploadResources(sessionId, contents)
                        : localResources(sessionId, contents);
                state.resources = resources;
                state.summaryMarkdown = summary;
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

    private ArchiveState state(String sessionId) {
        return archives.computeIfAbsent(sessionId, ignored -> new ArchiveState());
    }

    private Map<ArchiveResourceType, ResourceContent> resourceContents(String sessionId,
                                                                       ArchiveSnapshot snapshot,
                                                                       String summary) throws Exception {
        Map<ArchiveResourceType, ResourceContent> contents = new LinkedHashMap<>();
        contents.put(ArchiveResourceType.METADATA, json("metadata.json", snapshot));
        contents.put(ArchiveResourceType.SUBTITLES, json("subtitles.json", snapshot.subtitles()));
        contents.put(ArchiveResourceType.CORRECTIONS, json("corrections.json", snapshot.corrections()));
        contents.put(ArchiveResourceType.METRICS, json("metrics.json", snapshot.metrics()));
        contents.put(ArchiveResourceType.SUMMARY, new ResourceContent("summary.md", "text/markdown; charset=utf-8",
                summary.getBytes(StandardCharsets.UTF_8)));
        contents.put(ArchiveResourceType.AUDIO, new ResourceContent("audio.pcm", "application/octet-stream",
                state(sessionId).audioBytes()));
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

    private String summaryMarkdown(ArchiveSnapshot snapshot, boolean uploadReady) {
        FlowSession session = snapshot.session();
        String audioState = snapshot.audioSizeBytes() > 0 ? snapshot.audioSizeBytes() + " bytes" : "NO_AUDIO";
        String provider = snapshot.metrics() == null ? "未知" : snapshot.metrics().providerName();
        String translationProvider = snapshot.metrics() == null ? "未知" : snapshot.metrics().translationProviderName();
        return """
                # %s

                - 会话 ID：`%s`
                - 场景：%s
                - 语言：%s -> %s
                - 会话状态：%s
                - 字幕数：%d
                - 修正数：%d
                - 音频归档：%s
                - ASR Provider：%s
                - 翻译 Provider：%s
                - Kodo 上传：%s

                ## 资源清单

                - `metadata.json`：会话元数据和归档快照
                - `subtitles.json`：双语字幕
                - `corrections.json`：上下文修正记录
                - `metrics.json`：最后一次延迟指标
                - `summary.md`：当前会后总结
                - `audio.pcm`：原始 PCM 音频流
                """.formatted(
                session.getTitle(),
                session.getSessionId(),
                session.getSceneType(),
                session.getSourceLang(),
                session.getTargetLang(),
                session.getStatus(),
                snapshot.subtitleCount(),
                snapshot.correctionCount(),
                audioState,
                provider,
                translationProvider,
                uploadReady ? "已启用" : "未启用，本地归档"
        );
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
            return new ArchiveStatusResponse(sessionId, status, message, summaryMarkdown, resources, updatedAt);
        }
    }
}
