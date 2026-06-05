import { defineStore } from 'pinia'
import { createSession, finishSession, listSessions } from '../api/sessionApi'
import type { CreateSessionRequest, FlowSession, Metrics, SubtitleCorrection, SubtitleSegment, WsMessage } from '../types'
import { wsClient } from '../utils/wsClient'
import { useMetricsStore } from './metricsStore'
import { useSubtitleStore } from './subtitleStore'

export const useSessionStore = defineStore('session', {
  state: () => ({
    currentSession: null as FlowSession | null,
    sessions: [] as FlowSession[],
    connectionStatus: 'DISCONNECTED' as 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR',
    loading: false,
    lastEvent: ''
  }),
  actions: {
    async create(payload: CreateSessionRequest) {
      this.loading = true
      try {
        const subtitleStore = useSubtitleStore()
        const metricsStore = useMetricsStore()
        // 创建新会话时清空上一轮演示数据，保证验收流程干净。
        subtitleStore.reset()
        metricsStore.reset()
        this.currentSession = await createSession(payload)
        this.connectCurrentSession()
      } finally {
        this.loading = false
      }
    },
    async refreshSessions() {
      this.sessions = await listSessions()
    },
    connectCurrentSession() {
      if (!this.currentSession) {
        return
      }
      // WebSocket 消息统一进入 handleWsMessage，页面组件不直接处理协议细节。
      wsClient.connect(
        this.currentSession.sessionId,
        (message) => this.handleWsMessage(message),
        (status) => {
          this.connectionStatus = status
        }
      )
    },
    startMockTranslate() {
      if (!this.currentSession) {
        return
      }
      wsClient.sendStartMockTranslate(this.currentSession.title)
      this.lastEvent = 'START_MOCK_TRANSLATE'
    },
    async finish() {
      if (!this.currentSession) {
        return
      }
      this.currentSession = await finishSession(this.currentSession.sessionId)
      wsClient.disconnect()
      this.connectionStatus = 'DISCONNECTED'
      this.lastEvent = 'SESSION_FINISHED'
    },
    handleWsMessage(message: WsMessage) {
      this.lastEvent = message.type
      const subtitleStore = useSubtitleStore()
      const metricsStore = useMetricsStore()

      // 按消息类型分发到不同 store，后续接入真实 ASR/翻译时仍可复用这层结构。
      if (message.type === 'SESSION_CONNECTED') {
        this.connectionStatus = 'CONNECTED'
      }
      if (message.type === 'SUBTITLE_UPDATE') {
        subtitleStore.addSubtitle(message.payload as SubtitleSegment)
      }
      if (message.type === 'SUBTITLE_CORRECTION') {
        subtitleStore.applyCorrection(message.payload as SubtitleCorrection)
      }
      if (message.type === 'METRICS_UPDATE') {
        metricsStore.update(message.payload as Metrics)
        if (this.currentSession?.status === 'CREATED') {
          this.currentSession.status = 'RUNNING'
        }
      }
    }
  }
})
