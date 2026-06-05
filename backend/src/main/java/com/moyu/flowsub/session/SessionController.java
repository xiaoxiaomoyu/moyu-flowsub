package com.moyu.flowsub.session;

import com.moyu.flowsub.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 创建会话后，前端会立即使用返回的 sessionId 建立 WebSocket 连接。
     */
    @PostMapping
    public ApiResponse<FlowSession> create(@Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.success(sessionService.create(request));
    }

    /**
     * Demo 阶段提供内存会话列表，便于前端历史页和调试验收查看。
     */
    @GetMapping
    public ApiResponse<List<FlowSession>> list() {
        return ApiResponse.success(sessionService.list());
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<FlowSession> get(@PathVariable String sessionId) {
        return ApiResponse.success(sessionService.get(sessionId));
    }

    @PostMapping("/{sessionId}/finish")
    public ApiResponse<FlowSession> finish(@PathVariable String sessionId) {
        return ApiResponse.success(sessionService.finish(sessionId));
    }
}
