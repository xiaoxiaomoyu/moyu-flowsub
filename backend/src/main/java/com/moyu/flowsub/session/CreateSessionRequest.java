package com.moyu.flowsub.session;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建同传会话时前端提交的基础信息。
 */
public record CreateSessionRequest(
        @NotBlank String title,
        @NotBlank String sourceLang,
        @NotBlank String targetLang,
        @NotBlank String sceneType
) {
}
