package com.moyu.flowsub.qiniu;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
public class MockQiniuService implements QiniuService {

    private final QiniuProperties properties;
    private final UploadManager uploadManager;

    public MockQiniuService(QiniuProperties properties) {
        this.properties = properties;
        this.uploadManager = new UploadManager(Configuration.create());
    }

    @Override
    public QiniuStatusResponse status() {
        boolean accessKeyConfigured = StringUtils.hasText(properties.accessKey());
        boolean secretKeyConfigured = StringUtils.hasText(properties.secretKey());
        boolean bucketConfigured = StringUtils.hasText(properties.bucket());
        boolean domainConfigured = StringUtils.hasText(properties.domain());
        boolean uploadReady = uploadReady();
        return new QiniuStatusResponse(
                properties.enabled(),
                accessKeyConfigured,
                secretKeyConfigured,
                bucketConfigured,
                domainConfigured,
                uploadReady,
                archivePrefix(),
                uploadReady ? "七牛云 Kodo 上传配置已就绪。" : "七牛云 Kodo 未完整配置，归档将保存在本地内存。"
        );
    }

    @Override
    public boolean uploadReady() {
        return properties.enabled()
                && StringUtils.hasText(properties.accessKey())
                && StringUtils.hasText(properties.secretKey())
                && StringUtils.hasText(properties.bucket());
    }

    @Override
    public KodoUploadResult upload(String key, byte[] data, String contentType) {
        if (!uploadReady()) {
            throw new IllegalStateException("七牛云 Kodo 未完整配置，无法上传。");
        }
        try {
            Auth auth = Auth.create(properties.accessKey(), properties.secretKey());
            String token = auth.uploadToken(properties.bucket(), key);
            StringMap params = new StringMap();
            Response response = uploadManager.put(data, key, token, params, contentType, true);
            response.close();
            return new KodoUploadResult(key, resourceUrl(auth, key), contentType, data.length, Instant.now());
        } catch (QiniuException e) {
            String detail = e.response == null ? e.getMessage() : e.response.toString();
            throw new IllegalStateException("七牛云 Kodo 上传失败：" + detail, e);
        }
    }

    private String resourceUrl(Auth auth, String key) {
        if (!StringUtils.hasText(properties.domain())) {
            return "";
        }
        String domain = properties.domain().endsWith("/")
                ? properties.domain().substring(0, properties.domain().length() - 1)
                : properties.domain();
        String baseUrl = (domain.startsWith("http://") || domain.startsWith("https://") ? domain : "https://" + domain)
                + "/" + key;
        if (properties.privateBucket()) {
            return auth.privateDownloadUrl(baseUrl, properties.downloadExpireSeconds());
        }
        return baseUrl;
    }

    private String archivePrefix() {
        return StringUtils.hasText(properties.archivePrefix()) ? properties.archivePrefix() : "moyu-flowsub";
    }
}
