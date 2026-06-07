import { defineStore } from 'pinia'
import type { Metrics } from '../types'

const emptyMetrics: Metrics = {
  asrLatencyMs: 0,
  translateLatencyMs: 0,
  totalLatencyMs: 0,
  subtitleCount: 0,
  correctionCount: 0,
  audioChunkCount: 0,
  providerName: '等待接入',
  translationProviderName: '等待翻译'
}

// 指标独立成 store，避免字幕列表刷新时影响右侧指标面板状态。
export const useMetricsStore = defineStore('metrics', {
  state: () => ({
    metrics: { ...emptyMetrics }
  }),
  actions: {
    reset() {
      this.metrics = { ...emptyMetrics }
    },
    update(metrics: Metrics) {
      this.metrics = metrics
    }
  }
})
