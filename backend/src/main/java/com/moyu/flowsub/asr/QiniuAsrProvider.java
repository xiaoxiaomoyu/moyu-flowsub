package com.moyu.flowsub.asr;

import com.moyu.flowsub.audio.AudioChunk;
import com.moyu.flowsub.qiniu.QiniuProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
public class QiniuAsrProvider implements AsrProvider {

    private final QiniuProperties qiniuProperties;

    public QiniuAsrProvider(QiniuProperties qiniuProperties) {
        this.qiniuProperties = qiniuProperties;
    }

    @Override
    public String name() {
        return "七牛云智能语音";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public AsrProviderStatusPayload status() {
        boolean configured = qiniuProperties.enabled()
                && StringUtils.hasText(qiniuProperties.accessKey())
                && StringUtils.hasText(qiniuProperties.secretKey());
        String message = configured
                ? "七牛云基础凭证已配置，智能语音实时 ASR 调用点待接入。"
                : "七牛云智能语音未配置，自动降级到后续 ASR Provider。";
        return new AsrProviderStatusPayload(name(), false, !configured, message);
    }

    @Override
    public Optional<AsrResult> recognize(AudioChunk chunk) {
        // 第二阶段先把七牛云智能语音作为最高优先级接入点；拿到正式接口权限后在这里替换真实调用。
        return Optional.empty();
    }
}
