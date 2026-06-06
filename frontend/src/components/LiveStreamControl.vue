<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Refresh, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { getMediaStatus, prepareLive, startLiveIngest, stopLiveIngest } from '../api/mediaApi'
import { useSessionStore } from '../stores/sessionStore'
import type { LiveIngestStatus, LiveStreamStatus, MediaStatus } from '../types'

const sessionStore = useSessionStore()
const mediaStatus = ref<MediaStatus | null>(null)
const liveStatus = ref<LiveStreamStatus | null>(null)
const loading = ref(false)

const ingestText: Record<LiveIngestStatus, string> = {
  IDLE: '未准备',
  PREPARED: '已准备',
  INGESTING: '拉流中',
  STOPPED: '已停止',
  FAILED: '拉流失败',
  MOCK: 'Mock 拉流'
}

const ingestType: Record<LiveIngestStatus, 'info' | 'success' | 'warning' | 'danger' | 'primary'> = {
  IDLE: 'info',
  PREPARED: 'primary',
  INGESTING: 'success',
  STOPPED: 'info',
  FAILED: 'danger',
  MOCK: 'warning'
}

const currentSessionId = computed(() => sessionStore.currentSession?.sessionId || '')
const canOperate = computed(() => Boolean(currentSessionId.value))
const ingesting = computed(() => liveStatus.value?.ingestStatus === 'INGESTING' || liveStatus.value?.ingestStatus === 'MOCK')

async function refreshStatus() {
  mediaStatus.value = await getMediaStatus()
}

async function handlePrepare() {
  if (!currentSessionId.value) {
    return
  }
  loading.value = true
  try {
    liveStatus.value = await prepareLive(currentSessionId.value)
    ElMessage.success('直播源已准备。')
  } finally {
    loading.value = false
  }
}

async function handleStart() {
  if (!currentSessionId.value) {
    return
  }
  loading.value = true
  try {
    liveStatus.value = await startLiveIngest(currentSessionId.value)
    ElMessage.success(liveStatus.value.fallback ? '已启动 Mock 直播拉流。' : '已启动 Miku 直播拉流。')
  } finally {
    loading.value = false
  }
}

async function handleStop() {
  if (!currentSessionId.value) {
    return
  }
  loading.value = true
  try {
    liveStatus.value = await stopLiveIngest(currentSessionId.value)
    ElMessage.success('直播拉流已停止。')
  } finally {
    loading.value = false
  }
}

async function copyText(text: string) {
  if (!text) {
    return
  }
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制到剪贴板。')
}

onMounted(refreshStatus)
</script>

<template>
  <section class="panel">
    <div class="panel-header">
      <div>
        <h2>直播源</h2>
        <p class="panel-subtitle">Miku 快直播优先，未配置时使用 Mock 直播源。</p>
      </div>
      <el-button :icon="Refresh" circle text @click="refreshStatus" />
    </div>

    <div class="status-grid">
      <div>
        <span>Miku</span>
        <strong>{{ mediaStatus?.mikuConfigured ? '已配置' : '未配置' }}</strong>
      </div>
      <div>
        <span>FFmpeg</span>
        <strong>{{ mediaStatus?.ffmpegConfigured ? '可用' : '不可用' }}</strong>
      </div>
      <div>
        <span>区域</span>
        <strong>{{ mediaStatus?.region || '-' }}</strong>
      </div>
      <div>
        <span>状态</span>
        <el-tag :type="ingestType[liveStatus?.ingestStatus || 'IDLE']" effect="plain">
          {{ ingestText[liveStatus?.ingestStatus || 'IDLE'] }}
        </el-tag>
      </div>
    </div>

    <p class="panel-subtitle media-message">{{ liveStatus?.reason || mediaStatus?.message || '等待检查媒体配置。' }}</p>

    <div class="button-stack">
      <el-button :disabled="!canOperate" :loading="loading" @click="handlePrepare">准备直播</el-button>
      <el-button v-if="!ingesting" type="success" :icon="VideoPlay" :disabled="!canOperate" :loading="loading" @click="handleStart">
        开始后端拉流
      </el-button>
      <el-button v-else type="warning" :icon="VideoPause" :loading="loading" @click="handleStop">
        停止后端拉流
      </el-button>
    </div>

    <div v-if="liveStatus" class="live-url-list">
      <article>
        <span>推流地址</span>
        <p>{{ liveStatus.publishUrl || '未配置推流域名' }}</p>
        <el-button size="small" :icon="CopyDocument" text @click="copyText(liveStatus.publishUrl)">复制</el-button>
      </article>
      <article>
        <span>播放地址</span>
        <p>{{ liveStatus.playUrl || '未配置播放域名' }}</p>
        <el-button size="small" :icon="CopyDocument" text @click="copyText(liveStatus.playUrl)">复制</el-button>
      </article>
      <article>
        <span>WHEP 地址</span>
        <p>{{ liveStatus.whepUrl || '未配置 WHEP 域名' }}</p>
        <el-button size="small" :icon="CopyDocument" text @click="copyText(liveStatus.whepUrl)">复制</el-button>
      </article>
    </div>
  </section>
</template>
