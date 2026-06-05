export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// 前后端共享的基础类型定义，字段名保持与接口 JSON 一致。
export interface FlowSession {
  sessionId: string
  title: string
  sourceLang: string
  targetLang: string
  sceneType: string
  status: 'CREATED' | 'RUNNING' | 'FINISHED'
  createdAt: string
  finishedAt?: string
}

export interface CreateSessionRequest {
  title: string
  sourceLang: string
  targetLang: string
  sceneType: string
}

export interface QiniuStatus {
  enabled: boolean
  bucketConfigured: boolean
  domainConfigured: boolean
  message: string
}

export interface SubtitleSegment {
  segmentId: string
  sourceText: string
  translatedText: string
  status: string
  version: number
  isCorrected: boolean
  latencyMs: number
}

export interface SubtitleCorrection {
  segmentId: string
  oldSourceText: string
  newSourceText: string
  oldTranslatedText: string
  newTranslatedText: string
  version: number
  reason: string
}

export interface Metrics {
  asrLatencyMs: number
  translateLatencyMs: number
  totalLatencyMs: number
  subtitleCount: number
  correctionCount: number
  audioChunkCount: number
  providerName: string
  providerFallback: boolean
}

export interface AsrProviderStatus {
  provider: string
  available: boolean
  fallback: boolean
  message: string
}

export interface AudioStreamStarted {
  input: string
  format: string
  sampleRate: number
  chunkDurationMs: number
}

export interface AudioStreamStopped {
  chunkCount: number
  subtitleCount: number
}

export interface WsMessage<T = unknown> {
  type: string
  sessionId: string
  timestamp: number
  payload: T
}
