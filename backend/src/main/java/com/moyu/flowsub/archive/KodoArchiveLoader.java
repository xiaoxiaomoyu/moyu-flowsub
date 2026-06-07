package com.moyu.flowsub.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyu.flowsub.qiniu.QiniuProperties;
import com.moyu.flowsub.qiniu.QiniuService;
import com.moyu.flowsub.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class KodoArchiveLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KodoArchiveLoader.class);

    private final QiniuService qiniuService;
    private final QiniuProperties qiniuProperties;
    private final SessionService sessionService;
    private final ArchiveService archiveService;
    private final ObjectMapper objectMapper;

    public KodoArchiveLoader(QiniuService qiniuService,
                             QiniuProperties qiniuProperties,
                             SessionService sessionService,
                             ArchiveService archiveService,
                             ObjectMapper objectMapper) {
        this.qiniuService = qiniuService;
        this.qiniuProperties = qiniuProperties;
        this.sessionService = sessionService;
        this.archiveService = archiveService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!qiniuService.uploadReady()) {
            log.info("七牛云 Kodo 未完整配置，跳过云端归档恢复。会话和归档仅在内存中维护。");
            return;
        }
        try {
            String prefix = archivePrefix();
            log.info("开始扫描 Kodo 归档，前缀={}", prefix);
            List<String> keys = qiniuService.list(prefix + "/");
            Set<String> sessionIds = extractSessionIds(keys, prefix);
            log.info("Kodo 扫描到 {} 个潜在会话目录", sessionIds.size());

            int loaded = 0;
            for (String sessionId : sessionIds) {
                try {
                    String metadataKey = prefix + "/" + sessionId + "/metadata.json";
                    byte[] metadataBytes = qiniuService.download(metadataKey);
                    ArchiveSnapshot snapshot = objectMapper.readValue(metadataBytes, ArchiveSnapshot.class);
                    sessionService.loadFromKodo(snapshot.session());
                    archiveService.loadFromKodo(sessionId, snapshot);
                    loaded++;
                } catch (Exception e) {
                    log.warn("从 Kodo 恢复会话归档失败，sessionId={}", sessionId, e);
                }
            }
            log.info("从 Kodo 恢复完成：{}/{} 个会话。", loaded, sessionIds.size());
        } catch (Exception e) {
            log.warn("扫描 Kodo 归档失败，回退到纯内存模式。", e);
        }
    }

    /**
     * 从完整的对象 key 列表中提取唯一的会话 ID。
     * key 格式为 {prefix}/{sessionId}/filename
     */
    private Set<String> extractSessionIds(List<String> keys, String prefix) {
        Set<String> ids = new LinkedHashSet<>();
        String pattern = prefix + "/";
        for (String key : keys) {
            if (key.startsWith(pattern)) {
                String rest = key.substring(pattern.length());
                int slash = rest.indexOf('/');
                if (slash > 0) {
                    ids.add(rest.substring(0, slash));
                }
            }
        }
        return ids;
    }

    private String archivePrefix() {
        String prefix = StringUtils.hasText(qiniuProperties.archivePrefix())
                ? qiniuProperties.archivePrefix()
                : "moyu-flowsub";
        return prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
