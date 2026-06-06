package com.moyu.flowsub.summary;

/**
 * 会后时间线条目，用于把长会话拆成易扫读的阶段。
 */
public record SummaryTimelineItem(
        String timeLabel,
        String title,
        String detail
) {
}
