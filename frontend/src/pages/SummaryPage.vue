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
const summary = computed(() => archive.value?.summary)

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
  INSIGHTS: '结构化洞察',
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
            <h2>AI 会后摘要</h2>
            <p class="panel-subtitle">{{ archive.message }}</p>
          </div>
          <div class="summary-actions">
            <el-tag v-if="summary" :type="summary.fallback ? 'warning' : 'success'" effect="plain">
              {{ summary.providerName }}{{ summary.fallback ? ' · 已降级' : '' }}
            </el-tag>
            <el-tag :type="archiveStatusType[archive.status]" effect="plain">
              {{ archiveStatusText[archive.status] }}
            </el-tag>
            <el-button size="small" :loading="archiving" @click="handleArchive">重新归档</el-button>
          </div>
        </div>

        <div v-if="summary" class="insight-overview">
          <p>{{ summary.abstractText || '本次会话暂无可总结的稳定字幕。' }}</p>
          <span>{{ summary.reason }}</span>
        </div>

        <div class="insight-grid">
          <section class="insight-section timeline-section">
            <div class="section-title">
              <h3>时间线</h3>
              <span>{{ summary?.timeline.length || 0 }} 项</span>
            </div>
            <ol v-if="summary?.timeline.length" class="timeline-list">
              <li v-for="item in summary.timeline" :key="`${item.timeLabel}-${item.title}`">
                <time>{{ item.timeLabel }}</time>
                <strong>{{ item.title }}</strong>
                <p>{{ item.detail }}</p>
              </li>
            </ol>
            <p v-else class="muted-text">暂无可提炼的时间线。</p>
          </section>

          <section class="insight-section">
            <div class="section-title">
              <h3>术语表</h3>
              <span>{{ summary?.terms.length || 0 }} 项</span>
            </div>
            <div v-if="summary?.terms.length" class="term-list">
              <article v-for="term in summary.terms" :key="`${term.term}-${term.translation}`" class="term-item">
                <strong>{{ term.term }}</strong>
                <span>{{ term.translation }}</span>
                <p>{{ term.explanation }}</p>
              </article>
            </div>
            <p v-else class="muted-text">暂无明确术语。</p>
          </section>
        </div>

        <section class="insight-section key-sentence-section">
          <div class="section-title">
            <h3>重点句</h3>
            <span>{{ summary?.keySentences.length || 0 }} 句</span>
          </div>
          <div v-if="summary?.keySentences.length" class="key-sentence-list">
            <article
              v-for="sentence in summary.keySentences"
              :key="`${sentence.sourceText}-${sentence.translatedText}`"
              class="key-sentence-item"
            >
              <p class="source-text">{{ sentence.sourceText }}</p>
              <p class="target-text">{{ sentence.translatedText }}</p>
              <span>{{ sentence.reason }}</span>
            </article>
          </div>
          <p v-else class="muted-text">暂无重点句。</p>
        </section>

        <details class="markdown-details">
          <summary>查看 Markdown 原文</summary>
          <pre class="markdown-preview">{{ archive.summaryMarkdown || '归档摘要正在等待生成。' }}</pre>
        </details>
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
