package com.moyu.flowsub.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级 WebSocket 推送总线，让浏览器音频和后端直播拉流都能复用同一套实时推送通道。
 */
@Component
public class WebSocketMessageBus {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public WebSocketMessageBus(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
    }

    public void unregister(String sessionId, String webSocketSessionId) {
        WebSocketSession current = sessions.get(sessionId);
        if (current != null && current.getId().equals(webSocketSessionId)) {
            sessions.remove(sessionId);
        }
    }

    public boolean send(String sessionId, String type, Object payload) {
        return send(WsMessage.of(type, sessionId, payload));
    }

    public boolean send(WsMessage<?> message) {
        WebSocketSession session = sessions.get(message.sessionId());
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
