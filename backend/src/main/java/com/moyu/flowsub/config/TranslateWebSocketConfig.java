package com.moyu.flowsub.config;

import com.moyu.flowsub.websocket.TranslateWebSocketHandler;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
        registry.addHandler(translateWebSocketHandler, "/ws/translate/{sessionId}")
                .setAllowedOrigins("*");
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ServletContextInitializer tomcatWebSocketBufferCustomizer() {
        // Tomcat WsSci 会在初始化时读取这两个 init 参数来设置 WebSocket 缓冲区大小。
        // 300ms × 16kHz × 2 字节/sample ≈ 9.6KB，默认 8KB 缓冲会导致音频二进制帧被 Tomcat 拒绝。
        return servletContext -> {
            servletContext.setInitParameter(
                    "org.apache.tomcat.websocket.binaryBufferSize", "65536");
            servletContext.setInitParameter(
                    "org.apache.tomcat.websocket.textBufferSize", "65536");
        };
    }
}
