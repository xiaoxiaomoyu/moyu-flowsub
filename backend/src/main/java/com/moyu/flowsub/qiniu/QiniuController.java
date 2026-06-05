package com.moyu.flowsub.qiniu;

import com.moyu.flowsub.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qiniu")
public class QiniuController {

    private final QiniuService qiniuService;

    public QiniuController(QiniuService qiniuService) {
        this.qiniuService = qiniuService;
    }

    /**
     * 前端启动时调用，用于展示七牛云配置占位状态。
     */
    @GetMapping("/status")
    public ApiResponse<QiniuStatusResponse> status() {
        return ApiResponse.success(qiniuService.status());
    }
}
