<script setup lang="ts">
import { computed } from 'vue'
import { DataLine, Timer } from '@element-plus/icons-vue'
import { useMetricsStore } from '../stores/metricsStore'

const metricsStore = useMetricsStore()
const metrics = computed(() => metricsStore.metrics)
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
        <span>ASR</span>
        <strong>{{ metrics.providerName }}</strong>
      </div>
      <div>
        <span>降级状态</span>
        <strong>{{ metrics.providerFallback ? '已降级' : '主链路' }}</strong>
      </div>
    </div>
  </section>
</template>
