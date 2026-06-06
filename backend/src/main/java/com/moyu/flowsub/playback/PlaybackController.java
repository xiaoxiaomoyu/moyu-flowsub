package com.moyu.flowsub.playback;

import com.moyu.flowsub.archive.ArchiveService;
import com.moyu.flowsub.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playback")
public class PlaybackController {

    private final ArchiveService archiveService;

    public PlaybackController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<PlaybackManifestResponse> playback(@PathVariable String sessionId) {
        return ApiResponse.success(archiveService.playbackManifest(sessionId));
    }
}
