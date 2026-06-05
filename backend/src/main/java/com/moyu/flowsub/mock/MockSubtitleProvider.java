package com.moyu.flowsub.mock;

import com.moyu.flowsub.subtitle.SubtitleCorrectionPayload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockSubtitleProvider {

    /**
     * 固定返回一组技术分享场景字幕，保证 Demo 验收结果稳定可复现。
     */
    public List<MockSubtitle> subtitles() {
        return List.of(
                new MockSubtitle("seg_000001", "Today we are going to talk about AI agents.", "今天我们将讨论 AI 智能体。", 410, 390),
                new MockSubtitle("seg_000002", "We use rag to improve the answer.", "我们使用破布来改进答案。", 430, 410),
                new MockSubtitle("seg_000003", "The system keeps a short context window for each session.", "系统会为每个会话保留一个短上下文窗口。", 390, 360),
                new MockSubtitle("seg_000004", "When new context arrives, previous subtitles can be corrected.", "当新的上下文到来时，之前的字幕可以被修正。", 460, 420),
                new MockSubtitle("seg_000005", "Qiniu Kodo will store audio, subtitles, and summaries later.", "后续七牛云 Kodo 将存储音频、字幕和总结。", 415, 405),
                new MockSubtitle("seg_000006", "This demo focuses on the real-time communication pipeline.", "这个 Demo 重点展示实时通信链路。", 440, 370)
        );
    }

    /**
     * 模拟把 rag 识别为 RAG 的上下文修正，用来体现题目要求的纠错能力。
     */
    public SubtitleCorrectionPayload correction() {
        return new SubtitleCorrectionPayload(
                "seg_000002",
                "We use rag to improve the answer.",
                "We use RAG to improve the answer.",
                "我们使用破布来改进答案。",
                "我们使用 RAG 检索增强生成来提升回答质量。",
                2,
                "根据后文技术语境，rag 应识别为 RAG，而不是普通英文单词。"
        );
    }
}
