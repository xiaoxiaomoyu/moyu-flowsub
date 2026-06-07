package com.moyu.flowsub.summary;

import com.moyu.flowsub.archive.ArchiveSnapshot;
import com.moyu.flowsub.session.FlowSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final List<SummaryProvider> providers;

    public SummaryService(List<SummaryProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(SummaryProvider::priority))
                .toList();
    }

    public SummaryResult summarize(ArchiveSnapshot snapshot) {
        for (SummaryProvider provider : providers) {
            if (!provider.available()) {
                log.info("总结 Provider {} 不可用，跳过。", provider.name());
                continue;
            }
            try {
                return provider.summarize(new SummaryRequest(snapshot));
            } catch (Exception e) {
                log.warn("{} 总结调用失败：{}", provider.name(), e.getMessage());
            }
        }
        log.warn("所有总结 Provider 均不可用，返回空结果。sessionId={}", snapshot.session().getSessionId());
        return SummaryResult.empty("没有可用的会后总结 Provider，请配置 Qwen DashScope API Key。");
    }

    public String toMarkdown(ArchiveSnapshot snapshot, SummaryResult summary, boolean uploadReady) {
        FlowSession session = snapshot.session();
        String audioState = snapshot.audioSizeBytes() > 0 ? snapshot.audioSizeBytes() + " bytes" : "NO_AUDIO";
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(session.getTitle()).append("\n\n");
        builder.append("- 会话 ID：`").append(session.getSessionId()).append("`\n");
        builder.append("- 场景：").append(session.getSceneType()).append("\n");
        builder.append("- 语言：").append(session.getSourceLang()).append(" -> ").append(session.getTargetLang()).append("\n");
        builder.append("- 会话状态：").append(session.getStatus()).append("\n");
        builder.append("- 字幕数：").append(snapshot.subtitleCount()).append("\n");
        builder.append("- 修正数：").append(snapshot.correctionCount()).append("\n");
        builder.append("- 音频归档：").append(audioState).append("\n");
        builder.append("- 总结 Provider：").append(summary.providerName())
                .append(summary.fallback() ? "（已降级）" : "").append("\n");
        builder.append("- Kodo 上传：").append(uploadReady ? "已启用" : "未启用，本地归档").append("\n\n");

        builder.append("## 中文摘要\n\n").append(summary.abstractText()).append("\n\n");
        appendTimeline(builder, summary.timeline());
        appendTerms(builder, summary.terms());
        appendKeySentences(builder, summary.keySentences());
        builder.append("## 资源清单\n\n");
        builder.append("- `metadata.json`：会话元数据和归档快照\n");
        builder.append("- `subtitles.json`：双语字幕\n");
        builder.append("- `corrections.json`：上下文修正记录\n");
        builder.append("- `metrics.json`：最后一次延迟指标\n");
        builder.append("- `summary.md`：当前会后总结\n");
        builder.append("- `insights.json`：结构化摘要、时间线、术语表和重点句\n");
        builder.append("- `audio.pcm`：原始 PCM 音频流\n");
        return builder.toString();
    }

    private void appendTimeline(StringBuilder builder, List<SummaryTimelineItem> timeline) {
        builder.append("## 时间线\n\n");
        if (timeline.isEmpty()) {
            builder.append("- 暂无可提炼的时间线。\n\n");
            return;
        }
        for (SummaryTimelineItem item : timeline) {
            builder.append("- **").append(item.timeLabel()).append("｜").append(item.title()).append("**：")
                    .append(item.detail()).append("\n");
        }
        builder.append('\n');
    }

    private void appendTerms(StringBuilder builder, List<SummaryTerm> terms) {
        builder.append("## 术语表\n\n");
        if (terms.isEmpty()) {
            builder.append("- 暂无明确术语。\n\n");
            return;
        }
        for (SummaryTerm term : terms) {
            builder.append("- **").append(term.term()).append(" / ").append(term.translation()).append("**：")
                    .append(term.explanation()).append("\n");
        }
        builder.append('\n');
    }

    private void appendKeySentences(StringBuilder builder, List<SummaryKeySentence> sentences) {
        builder.append("## 重点句\n\n");
        if (sentences.isEmpty()) {
            builder.append("- 暂无重点句。\n\n");
            return;
        }
        for (SummaryKeySentence sentence : sentences) {
            builder.append("- EN：").append(sentence.sourceText()).append("\n");
            builder.append("  ZH：").append(sentence.translatedText()).append("\n");
            builder.append("  原因：").append(sentence.reason()).append("\n");
        }
        builder.append('\n');
    }
}
