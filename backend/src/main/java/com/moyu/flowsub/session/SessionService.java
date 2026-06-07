package com.moyu.flowsub.session;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    // 第一阶段不引入数据库，用线程安全 Map 保证 WebSocket 与 REST 同时访问时状态一致。
    private final Map<String, FlowSession> sessions = new ConcurrentHashMap<>();

    public FlowSession create(CreateSessionRequest request) {
        // sessionId 保持固定前缀，方便演示、日志排查和后续云端归档路径拼接。
        String sessionId = "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        FlowSession session = FlowSession.builder()
                .sessionId(sessionId)
                .title(request.title())
                .sourceLang(request.sourceLang())
                .targetLang(request.targetLang())
                .sceneType(request.sceneType())
                .status(SessionStatus.CREATED)
                .createdAt(Instant.now())
                .build();
        sessions.put(sessionId, session);
        return session;
    }

    public FlowSession get(String sessionId) {
        FlowSession session = sessions.get(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到会话：" + sessionId);
        }
        return session;
    }

    public List<FlowSession> list() {
        return sessions.values().stream()
                .sorted(Comparator.comparing(FlowSession::getCreatedAt).reversed())
                .toList();
    }

    public FlowSession markRunning(String sessionId) {
        FlowSession session = get(sessionId);
        // WebSocket 开始推送字幕后，才把 CREATED 推进到 RUNNING。
        if (session.getStatus() == SessionStatus.CREATED) {
            session.setStatus(SessionStatus.RUNNING);
        }
        return session;
    }

    /**
     * 从 Kodo 归档恢复会话。仅由 KodoArchiveLoader 调用。
     */
    public void loadFromKodo(FlowSession session) {
        sessions.putIfAbsent(session.getSessionId(), session);
    }

    public FlowSession finish(String sessionId) {
        FlowSession session = get(sessionId);
        session.setStatus(SessionStatus.FINISHED);
        session.setFinishedAt(Instant.now());
        return session;
    }
}
