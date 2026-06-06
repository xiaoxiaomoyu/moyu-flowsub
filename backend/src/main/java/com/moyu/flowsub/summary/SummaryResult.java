package com.moyu.flowsub.summary;

import java.util.List;

/**
 * 会后总结结构化结果，同时用于 API 展示和 insights.json 归档。
 */
public record SummaryResult(
        String abstractText,
        List<SummaryTimelineItem> timeline,
        List<SummaryTerm> terms,
        List<SummaryKeySentence> keySentences,
        String providerName,
        boolean fallback,
        String reason
) {
    public static SummaryResult empty(String reason) {
        return new SummaryResult("本次会话暂无可总结的稳定字幕。", List.of(), List.of(), List.of(),
                "Mock 总结", true, reason);
    }
}
