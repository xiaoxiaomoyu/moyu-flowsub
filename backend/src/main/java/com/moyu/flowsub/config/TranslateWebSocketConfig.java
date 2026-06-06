package com.moyu.flowsub.config;

import com.moyu.flowsub.websocket.TranslateWebSocketHandler;
import jakarta.websocket.server.ServerContainer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
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

    @Bean
    public ServletContextInitializer webSocketBufferCustomizer() {
        return servletContext -> {
            Object container = servletContext.getAttribute(ServerContainer.class.getName());
            if (container instanceof ServerContainer serverContainer) {
                // 300ms 的 16k/16bit PCM 音频块约 9.6KB，默认 8KB 缓冲会导致连接被容器关闭。
                serverContainer.setDefaultMaxBinaryMessageBufferSize(64 * 1024);
                serverContainer.setDefaultMaxTextMessageBufferSize(64 * 1024);
            }
        };
    }
}
