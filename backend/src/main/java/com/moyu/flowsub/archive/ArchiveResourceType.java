package com.moyu.flowsub.archive;

/**
 * 会话归档固定产物类型，资源顺序会影响前端展示和测试断言。
 */
public enum ArchiveResourceType {
    METADATA,
    SUBTITLES,
    CORRECTIONS,
    METRICS,
    SUMMARY,
    INSIGHTS,
    AUDIO
}
