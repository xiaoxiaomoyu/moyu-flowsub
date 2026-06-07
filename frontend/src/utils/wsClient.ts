import type { WsMessage } from '../types'
import type { AudioChunkMeta } from './audioCapture'

type MessageHandler = (message: WsMessage) => void
type StatusHandler = (status: 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR') => void

class WsClient {
  private socket: WebSocket | null = null
  private onMessage: MessageHandler | null = null
  private onStatus: StatusHandler | null = null

  connect(sessionId: string, onMessage: MessageHandler, onStatus: StatusHandler) {
    // 每次连接新会话前先断开旧连接，避免多个 WebSocket 同时推送字幕。
    this.disconnect()
    this.onMessage = onMessage
    this.onStatus = onStatus
    this.onStatus('CONNECTING')

    this.socket = new WebSocket(`${this.baseUrl()}/ws/translate/${sessionId}`)
    this.socket.onopen = () => this.onStatus?.('CONNECTED')
    this.socket.onclose = () => this.onStatus?.('DISCONNECTED')
    this.socket.onerror = () => this.onStatus?.('ERROR')
    this.socket.onmessage = (event) => {
      // 后端统一按 WsMessage 结构推送，前端只需要按 type 分发。
      const parsed = JSON.parse(event.data) as WsMessage
      this.onMessage?.(parsed)
    }
  }

  disconnect() {
    if (this.socket && this.socket.readyState !== WebSocket.CLOSED) {
      this.socket.close()
    }
    this.socket = null
  }

  sendStartAudioStream(sampleRate = 16000) {
    this.send({
      type: 'START_AUDIO_STREAM',
      payload: {
        chunkIndex: 0,
        timestamp: Date.now(),
        format: 'pcm_s16le',
        sampleRate,
        channels: 1,
        level: 0
      }
    })
  }

  sendAudioChunk(meta: AudioChunkMeta, data: ArrayBuffer) {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: 'AUDIO_CHUNK_META', payload: meta }))
      this.socket.send(data)
      return true
    }
    return false
  }

  sendStopAudioStream() {
    this.send({
      type: 'STOP_AUDIO_STREAM',
      payload: {}
    })
  }

  private send(payload: unknown) {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(payload))
    }
  }

  private baseUrl() {
    const explicit = import.meta.env.VITE_WS_BASE_URL as string | undefined
    if (explicit) {
      return explicit
    }
    // 开发环境下 Vite 运行在 5173，WebSocket 直连后端 8080。
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.hostname}:8080`
  }
}

export const wsClient = new WsClient()
