package com.moyu.flowsub.media;

import com.moyu.flowsub.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/status")
    public ApiResponse<MediaStatusResponse> status() {
        return ApiResponse.success(mediaService.status());
    }

    @PostMapping("/live/sessions/{sessionId}/prepare")
    public ApiResponse<LiveStreamSessionResponse> prepare(@PathVariable String sessionId) {
        return ApiResponse.success(mediaService.prepare(sessionId));
    }

    @GetMapping("/live/sessions/{sessionId}")
    public ApiResponse<LiveStreamSessionResponse> live(@PathVariable String sessionId) {
        return ApiResponse.success(mediaService.getLive(sessionId));
    }

    @PostMapping("/live/sessions/{sessionId}/start-ingest")
    public ApiResponse<LiveStreamSessionResponse> startIngest(@PathVariable String sessionId) {
        return ApiResponse.success(mediaService.startIngest(sessionId));
    }

    @PostMapping("/live/sessions/{sessionId}/stop-ingest")
    public ApiResponse<LiveStreamSessionResponse> stopIngest(@PathVariable String sessionId) {
        return ApiResponse.success(mediaService.stopIngest(sessionId));
    }
}
