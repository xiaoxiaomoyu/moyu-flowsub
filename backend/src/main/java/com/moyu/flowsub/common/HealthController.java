package com.moyu.flowsub.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 提供最小健康检查，方便前端启动时确认后端服务已就绪。
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of(
                "service", "moyu-flowsub-backend",
                "status", "UP"
        ));
    }
}
