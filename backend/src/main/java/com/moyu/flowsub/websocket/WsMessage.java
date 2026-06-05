package com.moyu.flowsub.websocket;

/**
 * WebSocket 消息统一外壳，所有实时事件都包含类型、会话编号、时间戳和载荷字段。
 */
public record WsMessage<T>(
        String type,
        String sessionId,
        long timestamp,
        T payload
) {
    public static <T> WsMessage<T> of(String type, String sessionId, T payload) {
        return new WsMessage<>(type, sessionId, System.currentTimeMillis(), payload);
    }
}
