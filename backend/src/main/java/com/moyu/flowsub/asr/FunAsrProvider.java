package com.moyu.flowsub.asr;

import com.moyu.flowsub.audio.AudioChunk;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
public class FunAsrProvider implements AsrProvider {

    private final AsrProperties properties;

    public FunAsrProvider(AsrProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "FunASR";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public AsrProviderStatusPayload status() {
        boolean configured = StringUtils.hasText(properties.funasrEndpoint());
        String message = configured
                ? "FunASR 兜底服务地址已配置，HTTP 调用协议将在真实服务确定后补齐。"
                : "FunASR 兜底服务未配置，继续降级到 Mock ASR。";
        return new AsrProviderStatusPayload(name(), false, !configured, message);
    }

    @Override
    public Optional<AsrResult> recognize(AudioChunk chunk) {
        // 这里先保留本地 FunASR 服务调用扩展点，避免当前 Demo 强依赖额外部署。
        return Optional.empty();
    }
}
