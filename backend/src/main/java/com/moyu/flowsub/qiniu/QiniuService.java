package com.moyu.flowsub.qiniu;

/**
 * 七牛云能力适配入口，后续真实 Kodo 上传实现会替换当前 Mock 实现。
 */
public interface QiniuService {
    QiniuStatusResponse status();

    boolean uploadReady();

    KodoUploadResult upload(String key, byte[] data, String contentType);
}
