package com.moyu.flowsub.archive;

import com.moyu.flowsub.metrics.MetricsPayload;
import com.moyu.flowsub.session.FlowSession;
import com.moyu.flowsub.subtitle.SubtitleCorrectionPayload;
import com.moyu.flowsub.subtitle.SubtitlePayload;

import java.time.Instant;
import java.util.List;

/**
 * 归档时写入 metadata.json 的快照结构，方便后续接数据库或对象存储索引。
 */
public record ArchiveSnapshot(
        FlowSession session,
        int subtitleCount,
        int correctionCount,
        long audioSizeBytes,
        MetricsPayload metrics,
        List<SubtitlePayload> subtitles,
        List<SubtitleCorrectionPayload> corrections,
        Instant archivedAt
) {
}
