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
  accessKeyConfigured: boolean
  secretKeyConfigured: boolean
  bucketConfigured: boolean
  domainConfigured: boolean
  uploadReady: boolean
  archivePrefix: string
  message: string
}

export type ArchiveStatus = 'PENDING' | 'LOCAL_ONLY' | 'UPLOADING' | 'UPLOADED' | 'FAILED'

export type ArchiveResourceType =
  | 'METADATA'
  | 'SUBTITLES'
  | 'CORRECTIONS'
  | 'METRICS'
  | 'SUMMARY'
  | 'INSIGHTS'
  | 'AUDIO'
  | 'AUDIO_WAV'
  | 'SUBTITLES_VTT'
  | 'PLAYBACK_MANIFEST'

export interface ArchiveResource {
  type: ArchiveResourceType
  key: string
  url: string
  contentType: string
  sizeBytes: number
  uploadedAt: string
}

export interface SummaryTimelineItem {
  timeLabel: string
  title: string
  detail: string
}

export interface SummaryTerm {
  term: string
  translation: string
  explanation: string
}

export interface SummaryKeySentence {
  sourceText: string
  translatedText: string
  reason: string
}

export interface ArchiveSummary {
  abstractText: string
  timeline: SummaryTimelineItem[]
  terms: SummaryTerm[]
  keySentences: SummaryKeySentence[]
  providerName: string
  fallback: boolean
  reason: string
}

export interface ArchiveStatusResponse {
  sessionId: string
  status: ArchiveStatus
  message: string
  summaryMarkdown: string
  summary: ArchiveSummary
  resources: ArchiveResource[]
  updatedAt: string
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
  translationProviderName: string
  translationProviderFallback: boolean
}

export interface AsrProviderStatus {
  provider: string
  available: boolean
  fallback: boolean
  message: string
  connected: boolean
  reason: string
  endpointType: string
}

export interface TranslationProviderStatus {
  provider: string
  available: boolean
  fallback: boolean
  message: string
  reason: string
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

export interface PlaybackCue {
  segmentId: string
  startSeconds: number
  endSeconds: number
  sourceText: string
  translatedText: string
  corrected: boolean
}

export interface PlaybackManifest {
  sessionId: string
  session: FlowSession
  audioUrl: string
  subtitleUrl: string
  cues: PlaybackCue[]
  corrections: SubtitleCorrection[]
  summary: ArchiveSummary
  resources: ArchiveResource[]
  provider: string
  fallback: boolean
  message: string
}

export interface WsMessage<T = unknown> {
  type: string
  sessionId: string
  timestamp: number
  payload: T
}
