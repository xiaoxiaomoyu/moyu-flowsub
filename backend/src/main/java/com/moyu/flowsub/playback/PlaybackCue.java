package com.moyu.flowsub.playback;

/**
 * 回放页使用的双语字幕片段，时间轴先按字幕顺序生成，后续接真实时间戳时可直接替换。
 */
public record PlaybackCue(
        String segmentId,
        double startSeconds,
        double endSeconds,
        String sourceText,
        String translatedText,
        boolean corrected
) {
}
