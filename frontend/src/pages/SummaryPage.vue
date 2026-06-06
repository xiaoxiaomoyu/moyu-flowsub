<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { archiveSession, getArchive, listArchives } from '../api/archiveApi'
import type { ArchiveResourceType, ArchiveStatus, ArchiveStatusResponse } from '../types'

const route = useRoute()
const archive = ref<ArchiveStatusResponse | null>(null)
const loading = ref(false)
const archiving = ref(false)

const sessionId = computed(() => String(route.query.sessionId || ''))

const archiveStatusText: Record<ArchiveStatus, string> = {
  PENDING: '待归档',
  LOCAL_ONLY: '本地归档',
  UPLOADING: '上传中',
  UPLOADED: '上传成功',
  FAILED: '上传失败'
}

const archiveStatusType: Record<ArchiveStatus, 'info' | 'success' | 'warning' | 'danger' | 'primary'> = {
  PENDING: 'info',
  LOCAL_ONLY: 'warning',
  UPLOADING: 'primary',
  UPLOADED: 'success',
  FAILED: 'danger'
}

const resourceTypeText: Record<ArchiveResourceType, string> = {
  METADATA: '会话元数据',
  SUBTITLES: '双语字幕',
  CORRECTIONS: '修正记录',
  METRICS: '指标快照',
  SUMMARY: 'Markdown 总结',
  AUDIO: 'PCM 音频'
}

async function loadArchive() {
  loading.value = true
  try {
    if (sessionId.value) {
      archive.value = await getArchive(sessionId.value)
      return
    }
    const archives = await listArchives()
    archive.value = archives[0] || null
  } finally {
    loading.value = false
  }
}

async function handleArchive() {
  if (!archive.value?.sessionId) {
    return
  }
  archiving.value = true
  try {
    archive.value = await archiveSession(archive.value.sessionId)
    ElMessage.success(archive.value.status === 'UPLOADED' ? '归档已上传到 Kodo。' : '已生成本地归档。')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '归档失败，请稍后重试。')
  } finally {
    archiving.value = false
  }
}

function formatBytes(sizeBytes: number) {
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`
  }
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MB`
}

onMounted(loadArchive)
watch(sessionId, loadArchive)
</script>

<template>
  <main class="app-shell secondary-page">
    <header class="topbar">
      <div>
        <h1>会后总结</h1>
        <p>展示会话归档摘要、字幕文件、修正记录、指标快照和 Kodo 资源链接。</p>
      </div>
      <nav class="topnav">
        <RouterLink to="/sessions">历史会话</RouterLink>
        <RouterLink to="/">返回实时同传</RouterLink>
      </nav>
    </header>

    <section v-if="loading" class="panel summary-placeholder">
      <el-skeleton :rows="8" animated />
    </section>

    <section v-else-if="!archive" class="panel summary-placeholder">
      <h2>暂无归档记录</h2>
      <p>使用 Mock ASR 跑完一轮会话并点击结束后，这里会展示本地或 Kodo 归档资源。</p>
      <RouterLink to="/">
        <el-button type="primary">开始实时同传</el-button>
      </RouterLink>
    </section>

    <section v-else class="summary-layout">
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>归档摘要</h2>
            <p class="panel-subtitle">{{ archive.message }}</p>
          </div>
          <div class="summary-actions">
            <el-tag :type="archiveStatusType[archive.status]" effect="plain">
              {{ archiveStatusText[archive.status] }}
            </el-tag>
            <el-button size="small" :loading="archiving" @click="handleArchive">重新归档</el-button>
          </div>
        </div>
        <pre class="markdown-preview">{{ archive.summaryMarkdown || '归档摘要正在等待生成。' }}</pre>
      </div>

      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>资源列表</h2>
            <p class="panel-subtitle">会话 ID：{{ archive.sessionId }}</p>
          </div>
          <span class="panel-subtitle">更新于 {{ archive.updatedAt }}</span>
        </div>

        <div class="resource-list">
          <article v-for="resource in archive.resources" :key="resource.type" class="resource-item">
            <div>
              <strong>{{ resourceTypeText[resource.type] }}</strong>
              <p>{{ resource.key }}</p>
              <span>{{ resource.contentType }} · {{ formatBytes(resource.sizeBytes) }}</span>
            </div>
            <a v-if="resource.url" :href="resource.url" target="_blank" rel="noreferrer">
              <el-button size="small" type="primary" plain>打开链接</el-button>
            </a>
            <el-tag v-else type="warning" effect="plain">本地内存归档</el-tag>
          </article>
        </div>
      </div>
    </section>
  </main>
</template>
