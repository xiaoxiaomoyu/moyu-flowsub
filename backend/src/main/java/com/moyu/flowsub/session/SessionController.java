package com.moyu.flowsub.session;

import com.moyu.flowsub.archive.ArchiveService;
import com.moyu.flowsub.common.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionService sessionService;
    private final ArchiveService archiveService;

    public SessionController(SessionService sessionService, ArchiveService archiveService) {
        this.sessionService = sessionService;
        this.archiveService = archiveService;
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
        FlowSession session = sessionService.finish(sessionId);
        try {
            // 会话结束时自动尝试归档；归档失败不能影响“结束会话”这个主流程。
            archiveService.archiveSession(sessionId);
        } catch (Exception e) {
            log.warn("会话结束后的自动归档失败，sessionId={}", sessionId, e);
        }
        return ApiResponse.success(session);
    }
}
