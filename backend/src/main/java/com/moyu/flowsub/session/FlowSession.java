package com.moyu.flowsub.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 第一阶段暂时只用内存保存会话，后续接入 MySQL 时可平滑映射为实体。
public class FlowSession {
    private String sessionId;
    private String title;
    private String sourceLang;
    private String targetLang;
    private String sceneType;
    private SessionStatus status;
    private Instant createdAt;
    private Instant finishedAt;
}
