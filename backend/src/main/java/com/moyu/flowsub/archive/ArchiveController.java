package com.moyu.flowsub.archive;

import com.moyu.flowsub.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    private final ArchiveService archiveService;

    public ArchiveController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @PostMapping("/sessions/{sessionId}")
    public ApiResponse<ArchiveStatusResponse> archive(@PathVariable String sessionId) {
        return ApiResponse.success(archiveService.archiveSession(sessionId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<ArchiveStatusResponse> get(@PathVariable String sessionId) {
        return ApiResponse.success(archiveService.getArchive(sessionId));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ArchiveStatusResponse>> list() {
        return ApiResponse.success(archiveService.listArchives());
    }
}
