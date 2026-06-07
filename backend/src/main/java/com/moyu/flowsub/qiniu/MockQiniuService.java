package com.moyu.flowsub.qiniu;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class MockQiniuService implements QiniuService {

    private final QiniuProperties properties;
    private final UploadManager uploadManager;
    private final HttpClient httpClient;
    private volatile Auth cachedAuth;

    public MockQiniuService(QiniuProperties properties) {
        this.properties = properties;
        this.uploadManager = new UploadManager(Configuration.create());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private Auth auth() {
        Auth a = cachedAuth;
        if (a == null) {
            a = Auth.create(properties.accessKey(), properties.secretKey());
            cachedAuth = a;
        }
        return a;
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
            String token = auth().uploadToken(properties.bucket(), key);
            StringMap params = new StringMap();
            Response response = uploadManager.put(data, key, token, params, contentType, true);
            response.close();
            return new KodoUploadResult(key, downloadUrl(key), contentType, data.length, Instant.now());
        } catch (QiniuException e) {
            String detail = e.response == null ? e.getMessage() : e.response.toString();
            throw new IllegalStateException("七牛云 Kodo 上传失败：" + detail, e);
        }
    }

    @Override
    public List<String> list(String prefix) {
        if (!uploadReady()) {
            return List.of();
        }
        try {
            BucketManager bucketManager = new BucketManager(auth(), Configuration.create());
            BucketManager.FileListIterator iterator =
                    bucketManager.createFileListIterator(properties.bucket(), prefix, 1000, null);
            List<String> keys = new ArrayList<>();
            while (iterator.hasNext()) {
                FileInfo[] items = iterator.next();
                if (items != null) {
                    for (FileInfo item : items) {
                        keys.add(item.key);
                    }
                }
            }
            return keys;
        } catch (Exception e) {
            throw new IllegalStateException("七牛云 Kodo 列表查询失败：" + e.getMessage(), e);
        }
    }

    @Override
    public byte[] download(String key) {
        if (!uploadReady()) {
            throw new IllegalStateException("七牛云 Kodo 未完整配置，无法下载。");
        }
        String url = downloadUrl(key);
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("无法构造 Kodo 下载 URL，domain 未配置。");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Kodo 下载失败，HTTP " + response.statusCode() + "：" + key);
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Kodo 下载失败：" + key, e);
        }
    }

    @Override
    public String downloadUrl(String key) {
        if (!StringUtils.hasText(properties.domain())) {
            return "";
        }
        String domain = properties.domain().endsWith("/")
                ? properties.domain().substring(0, properties.domain().length() - 1)
                : properties.domain();
        String baseUrl = (domain.startsWith("http://") || domain.startsWith("https://") ? domain : "https://" + domain)
                + "/" + key;
        if (properties.privateBucket()) {
            return auth().privateDownloadUrl(baseUrl, properties.downloadExpireSeconds());
        }
        return baseUrl;
    }

    private String archivePrefix() {
        return StringUtils.hasText(properties.archivePrefix()) ? properties.archivePrefix() : "moyu-flowsub";
    }
}
