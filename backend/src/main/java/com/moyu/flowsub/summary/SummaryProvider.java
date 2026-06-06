package com.moyu.flowsub.summary;

/**
 * 会后总结 Provider 统一接口，DeepSeek 优先，Mock 保底。
 */
public interface SummaryProvider {
    String name();

    int priority();

    boolean available();

    String unavailableReason();

    SummaryResult summarize(SummaryRequest request) throws Exception;
}
