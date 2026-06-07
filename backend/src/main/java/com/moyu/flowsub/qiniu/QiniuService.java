package com.moyu.flowsub.qiniu;

/**
 * 七牛云 Kodo 对象存储服务入口。
 */
public interface QiniuService {
    QiniuStatusResponse status();

    boolean uploadReady();

    KodoUploadResult upload(String key, byte[] data, String contentType);

    /**
     * 列出指定前缀下的所有对象 key。用于扫描已归档的会话目录。
     */
    java.util.List<String> list(String prefix);

    /**
     * 下载 Kodo 对象内容。用于读取 metadata.json 等归档文件。
     */
    byte[] download(String key);

    /**
     * 返回对象的公开或私有下载 URL。用于前端回放和资源展示。
     */
    String downloadUrl(String key);
}
