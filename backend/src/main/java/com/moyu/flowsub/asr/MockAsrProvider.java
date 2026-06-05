package com.moyu.flowsub.asr;

import com.moyu.flowsub.audio.AudioChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MockAsrProvider implements AsrProvider {

    private static final List<String> MOCK_SENTENCES = List.of(
            "Today we will build a real time translation assistant for technical talks.",
            "The browser captures microphone audio and sends small chunks to the backend.",
            "The backend can connect to Qiniu intelligent speech for recognition.",
            "If the cloud provider is not ready, the system falls back to a local ASR service.",
            "Every stable sentence becomes an English subtitle segment on the page.",
            "In the next stage, DeepSeek will translate and correct the bilingual subtitles."
    );

    private final AsrProperties properties;

    public MockAsrProvider(AsrProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "Mock ASR";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public AsrProviderStatusPayload status() {
        return new AsrProviderStatusPayload(name(), properties.mockEnabled(), true,
                properties.mockEnabled() ? "Mock ASR 已启用，用于无云端配置时保证演示闭环。" : "Mock ASR 已关闭。");
    }

    @Override
    public Optional<AsrResult> recognize(AudioChunk chunk) {
        if (!properties.mockEnabled() || chunk.chunkIndex() <= 0) {
            return Optional.empty();
        }

        // 每 3 个音频块形成一个稳定句子，中间块先推送 PARTIAL，贴近真实 ASR 的流式体验。
        int segmentNumber = ((chunk.chunkIndex() - 1) / 3) + 1;
        int sentenceIndex = (segmentNumber - 1) % MOCK_SENTENCES.size();
        boolean finalSegment = chunk.chunkIndex() % 3 == 0;
        String fullText = MOCK_SENTENCES.get(sentenceIndex);
        String text = finalSegment ? fullText : partialText(fullText, chunk.chunkIndex() % 3);
        long latencyMs = Math.max(120, System.currentTimeMillis() - chunk.timestamp());
        return Optional.of(new AsrResult(
                "seg_asr_" + String.format("%06d", segmentNumber),
                text,
                finalSegment ? "FINAL" : "PARTIAL",
                latencyMs,
                chunk.chunkIndex(),
                name()
        ));
    }

    private String partialText(String fullText, int step) {
        int end = step == 1 ? fullText.length() / 2 : (fullText.length() * 3) / 4;
        return fullText.substring(0, Math.max(1, end)).trim() + "...";
    }
}
