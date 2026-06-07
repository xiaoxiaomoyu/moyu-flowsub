<script setup lang="ts">
import { computed } from 'vue'
import { DataLine, Timer } from '@element-plus/icons-vue'
import { useMetricsStore } from '../stores/metricsStore'
import { useSessionStore } from '../stores/sessionStore'

const metricsStore = useMetricsStore()
const sessionStore = useSessionStore()
const metrics = computed(() => metricsStore.metrics)

// 翻译模型名称优先取 Metrics（翻译完成时才有），否则用 WebSocket 推送的 Provider 状态
const translationName = computed(() => {
  const name = metrics.value.translationProviderName
  return name && name !== '' ? name : sessionStore.translationProviderStatus.provider
})
</script>

<template>
  <section class="panel metrics-panel">
    <div class="panel-header">
      <h2>延迟指标</h2>
      <el-icon><DataLine /></el-icon>
    </div>

    <div class="metric-primary">
      <el-icon><Timer /></el-icon>
      <div>
        <span>总延迟</span>
        <strong>{{ metrics.totalLatencyMs }}ms</strong>
      </div>
    </div>

    <div class="metric-grid">
      <div>
        <span>语音识别</span>
        <strong>{{ metrics.asrLatencyMs }}ms</strong>
      </div>
      <div>
        <span>翻译</span>
        <strong>{{ metrics.translateLatencyMs }}ms</strong>
      </div>
      <div>
        <span>字幕数</span>
        <strong>{{ metrics.subtitleCount }}</strong>
      </div>
      <div>
        <span>修正数</span>
        <strong>{{ metrics.correctionCount }}</strong>
      </div>
      <div>
        <span>音频块</span>
        <strong>{{ metrics.audioChunkCount }}</strong>
      </div>
      <div>
        <span>ASR 模型</span>
        <strong>{{ metrics.providerName }}</strong>
      </div>
      <div>
        <span>翻译模型</span>
        <strong>{{ translationName }}</strong>
      </div>
    </div>
  </section>
</template>
