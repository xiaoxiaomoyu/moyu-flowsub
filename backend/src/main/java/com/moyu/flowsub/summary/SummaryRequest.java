package com.moyu.flowsub.summary;

import com.moyu.flowsub.archive.ArchiveSnapshot;

/**
 * 总结 Provider 的输入保持为归档快照，避免和实时翻译状态耦合。
 */
public record SummaryRequest(
        ArchiveSnapshot snapshot
) {
}
