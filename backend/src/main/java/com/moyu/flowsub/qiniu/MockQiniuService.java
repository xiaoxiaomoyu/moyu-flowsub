package com.moyu.flowsub.qiniu;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MockQiniuService implements QiniuService {

    private final QiniuProperties properties;

    public MockQiniuService(QiniuProperties properties) {
        this.properties = properties;
    }

    @Override
    public QiniuStatusResponse status() {
        // 这里只检查配置是否填写，避免第一阶段误触发真实云端调用。
        return new QiniuStatusResponse(
                properties.enabled(),
                StringUtils.hasText(properties.bucket()),
                StringUtils.hasText(properties.domain()),
                "七牛云 Kodo 集成占位已就绪。"
        );
    }
}
