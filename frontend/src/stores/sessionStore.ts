import { defineStore } from 'pinia'
import { createSession, finishSession, listSessions } from '../api/sessionApi'
import type { AsrProviderStatus, CreateSessionRequest, FlowSession, Metrics, SubtitleCorrection, SubtitleSegment, TranslationProviderStatus, WsMessage } from '../types'
import { audioCapture, type AudioCaptureState } from '../utils/audioCapture'
import { wsClient } from '../utils/wsClient'
import { useMetricsStore } from './metricsStore'
import { useSubtitleStore } from './subtitleStore'

const emptyAudioState: AudioCaptureState = {
  running: false,
  source: 'mic',
  permissionStatus: 'IDLE',
  sampleRate: 0,
  level: 0,
  chunkCount: 0,
  errorMessage: ''
}

const emptyProviderStatus: AsrProviderStatus = {
  provider: '等待接入',
  available: false,
  fallback: false,
  message: '创建会话并开始采集后显示 ASR Provider 状态。',
  connected: false,
  reason: '等待创建会话。',
  endpointType: 'NONE'
}

const subtitleChannel = new BroadcastChannel('flowsub-subtitles')

const emptyTranslationProviderStatus: TranslationProviderStatus = {
  provider: '等待翻译',
  available: false,
  fallback: false,
  message: '收到稳定 ASR 字幕后开始翻译。',
  reason: '等待稳定字幕。'
}

export const useSessionStore = defineStore('session', {
  state: () => ({
    currentSession: null as FlowSession | null,
    sessions: [] as FlowSession[],
    connectionStatus: 'DISCONNECTED' as 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR',
    loading: false,
    lastEvent: '',
    audioCapture: { ...emptyAudioState },
    asrProviderStatus: { ...emptyProviderStatus },
    translationProviderStatus: { ...emptyTranslationProviderStatus }
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
        this.audioCapture = { ...emptyAudioState }
        this.asrProviderStatus = { ...emptyProviderStatus }
        this.translationProviderStatus = { ...emptyTranslationProviderStatus }
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
          if ((status === 'DISCONNECTED' || status === 'ERROR') && this.audioCapture.running) {
            audioCapture.stop()
            this.audioCapture = {
              ...audioCapture.snapshot(),
              permissionStatus: 'ERROR',
              errorMessage: 'WebSocket 已断开，已自动停止麦克风采集。请重新创建会话后再开始采集。'
            }
          }
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
    async startAudioCapture() {
      if (!this.currentSession || this.connectionStatus !== 'CONNECTED') {
        return
      }
      this.lastEvent = 'START_AUDIO_STREAM'
      try {
        await audioCapture.start(
          (meta, data) => {
            // 诊断：每 10 个块打印一次，确认前端采集正常
            if (meta.chunkIndex % 10 === 1) {
              console.log('[audioCapture] chunk', meta.chunkIndex,
                'size=', data.byteLength, 'sampleRate=', meta.sampleRate, 'level=', meta.level.toFixed(4))
            }
            if (!wsClient.sendAudioChunk(meta, data)) {
              audioCapture.stop()
              this.audioCapture = {
                ...audioCapture.snapshot(),
                permissionStatus: 'ERROR',
                errorMessage: 'WebSocket 未连接，音频块没有发送到后端。请重新创建会话后再开始采集。'
              }
            }
          },
          (state) => {
            this.audioCapture = state
          },
          Number(import.meta.env.VITE_AUDIO_CHUNK_DURATION_MS || 200)
        )
        // AudioContext 已创建，发送实际的采样率给后端，避免采样率不匹配导致 ASR 无法识别。
        wsClient.sendStartAudioStream(audioCapture.snapshot().sampleRate || 16000)
      } catch {
        wsClient.sendStopAudioStream()
        this.lastEvent = 'STOP_AUDIO_STREAM'
      }
    },
    async startSystemAudioCapture() {
      if (!this.currentSession || this.connectionStatus !== 'CONNECTED') {
        return
      }
      this.lastEvent = 'START_AUDIO_STREAM'
      try {
        await audioCapture.start(
          (meta, data) => {
            if (meta.chunkIndex % 10 === 1) {
              console.log('[systemAudio] chunk', meta.chunkIndex,
                'size=', data.byteLength, 'sampleRate=', meta.sampleRate, 'level=', meta.level.toFixed(4))
            }
            if (!wsClient.sendAudioChunk(meta, data)) {
              audioCapture.stop()
              this.audioCapture = {
                ...audioCapture.snapshot(),
                permissionStatus: 'ERROR',
                errorMessage: 'WebSocket 未连接，音频块没有发送到后端。请重新创建会话后再开始采集。'
              }
            }
          },
          (state) => {
            this.audioCapture = state
          },
          Number(import.meta.env.VITE_AUDIO_CHUNK_DURATION_MS || 200),
          'system'
        )
        wsClient.sendStartAudioStream(audioCapture.snapshot().sampleRate || 16000)
      } catch {
        wsClient.sendStopAudioStream()
        this.lastEvent = 'STOP_AUDIO_STREAM'
      }
    },
    stopAudioCapture() {
      if (this.audioCapture.running) {
        wsClient.sendStopAudioStream()
      }
      audioCapture.stop()
      this.audioCapture = audioCapture.snapshot()
      this.lastEvent = 'STOP_AUDIO_STREAM'
    },
    async finish() {
      if (!this.currentSession) {
        return
      }
      this.stopAudioCapture()
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
        subtitleChannel.postMessage(message.payload)
      }
      if (message.type === 'ASR_PARTIAL' || message.type === 'ASR_FINAL') {
        subtitleStore.addSubtitle(message.payload as SubtitleSegment)
        subtitleChannel.postMessage(message.payload)
      }
      if (message.type === 'SUBTITLE_CORRECTION') {
        subtitleStore.applyCorrection(message.payload as SubtitleCorrection)
      }
      if (message.type === 'ASR_PROVIDER_STATUS') {
        this.asrProviderStatus = message.payload as AsrProviderStatus
      }
      if (message.type === 'TRANSLATION_PROVIDER_STATUS') {
        this.translationProviderStatus = message.payload as TranslationProviderStatus
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
