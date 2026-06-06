package com.moyu.flowsub.archive;

/**
 * 会话归档状态，前端会映射为中文展示。
 */
public enum ArchiveStatus {
    PENDING,
    LOCAL_ONLY,
    UPLOADING,
    UPLOADED,
    FAILED
}
