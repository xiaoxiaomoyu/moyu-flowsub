package com.moyu.flowsub.summary;

/**
 * 会后术语表条目，帮助用户快速回看技术关键词。
 */
public record SummaryTerm(
        String term,
        String translation,
        String explanation
) {
}
