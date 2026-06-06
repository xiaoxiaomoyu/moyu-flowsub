package com.moyu.flowsub.playback;

import com.moyu.flowsub.archive.ArchiveResourceResponse;
import com.moyu.flowsub.session.FlowSession;
import com.moyu.flowsub.subtitle.SubtitleCorrectionPayload;
import com.moyu.flowsub.summary.SummaryResult;

import java.util.List;

/**
 * 会后回放清单，前端通过它同步音频、字幕、修正记录和摘要资源。
 */
public record PlaybackManifestResponse(
        String sessionId,
        FlowSession session,
        String audioUrl,
        String subtitleUrl,
        List<PlaybackCue> cues,
        List<SubtitleCorrectionPayload> corrections,
        SummaryResult summary,
        List<ArchiveResourceResponse> resources,
        String provider,
        boolean fallback,
        String message
) {
}
