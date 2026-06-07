package com.moyu.flowsub.summary;

import com.moyu.flowsub.archive.ArchiveSnapshot;
import com.moyu.flowsub.subtitle.SubtitlePayload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Component
public class MockSummaryProvider implements SummaryProvider {

    @Override
    public String name() {
        return "Mock 总结";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String unavailableReason() {
        return "";
    }

    @Override
    public SummaryResult summarize(SummaryRequest request) {
        ArchiveSnapshot snapshot = request.snapshot();
        List<SubtitlePayload> subtitles = snapshot.subtitles();
        if (subtitles.isEmpty()) {
            return SummaryResult.empty("没有稳定字幕，使用 Mock 总结占位。");
        }
        return new SummaryResult(
                abstractText(snapshot),
                timeline(subtitles),
                terms(subtitles),
                keySentences(subtitles),
                name(),
                true,
                "Qwen 未配置或调用失败，已使用规则型 Mock 总结。"
        );
    }

    private String abstractText(ArchiveSnapshot snapshot) {
        String title = snapshot.session().getTitle();
        return "本次「%s」会话共生成 %d 条稳定字幕、%d 条修正记录，内容围绕外语内容理解、实时翻译链路和关键信息提炼展开。"
                .formatted(title, snapshot.subtitleCount(), snapshot.correctionCount());
    }

    private List<SummaryTimelineItem> timeline(List<SubtitlePayload> subtitles) {
        return subtitles.stream()
                .limit(5)
                .map(subtitle -> new SummaryTimelineItem(
                        subtitle.segmentId(),
                        firstSentence(subtitle.translatedText()),
                        subtitle.sourceText()
                ))
                .toList();
    }

    private List<SummaryTerm> terms(List<SubtitlePayload> subtitles) {
        String joined = subtitles.stream()
                .map(SubtitlePayload::sourceText)
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);
        return List.of(
                        termIfPresent(joined, "asr", "语音识别", "把音频流转换为文本的能力。"),
                        termIfPresent(joined, "translation", "翻译", "把英文原文转换为中文字幕的过程。"),
                        termIfPresent(joined, "context", "上下文", "用于修正历史字幕和提升译文一致性的参考信息。")
                ).stream()
                .filter(term -> StringUtils.hasText(term.term()))
                .limit(5)
                .toList();
    }

    private SummaryTerm termIfPresent(String source, String term, String translation, String explanation) {
        if (!source.contains(term)) {
            return new SummaryTerm("", "", "");
        }
        return new SummaryTerm(term, translation, explanation);
    }

    private List<SummaryKeySentence> keySentences(List<SubtitlePayload> subtitles) {
        return subtitles.stream()
                .filter(subtitle -> StringUtils.hasText(subtitle.translatedText()))
                .limit(3)
                .map(subtitle -> new SummaryKeySentence(
                        subtitle.sourceText(),
                        subtitle.translatedText(),
                        subtitle.isCorrected() ? "该句经过上下文修正，适合作为重点回看。" : "该句代表本次会话的稳定字幕内容。"
                ))
                .toList();
    }

    private String firstSentence(String text) {
        if (!StringUtils.hasText(text)) {
            return "稳定字幕片段";
        }
        return text.length() > 32 ? text.substring(0, 32) + "..." : text;
    }
}
