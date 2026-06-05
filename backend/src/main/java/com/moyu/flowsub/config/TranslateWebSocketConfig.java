package com.moyu.flowsub.config;

import com.moyu.flowsub.websocket.TranslateWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class TranslateWebSocketConfig implements WebSocketConfigurer {

    private final TranslateWebSocketHandler translateWebSocketHandler;

    public TranslateWebSocketConfig(TranslateWebSocketHandler translateWebSocketHandler) {
        this.translateWebSocketHandler = translateWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 第一阶段使用原生 WebSocket，路径中的 sessionId 用来绑定当前同传会话。
        registry.addHandler(translateWebSocketHandler, "/ws/translate/{sessionId}")
                .setAllowedOrigins("*");
    }
}
