import type { LiveStreamStatus, MediaStatus } from '../types'
import { request } from './http'

export function getMediaStatus() {
  return request<MediaStatus>('/api/media/status')
}

export function prepareLive(sessionId: string) {
  return request<LiveStreamStatus>(`/api/media/live/sessions/${sessionId}/prepare`, { method: 'POST' })
}

export function getLive(sessionId: string) {
  return request<LiveStreamStatus>(`/api/media/live/sessions/${sessionId}`)
}

export function startLiveIngest(sessionId: string) {
  return request<LiveStreamStatus>(`/api/media/live/sessions/${sessionId}/start-ingest`, { method: 'POST' })
}

export function stopLiveIngest(sessionId: string) {
  return request<LiveStreamStatus>(`/api/media/live/sessions/${sessionId}/stop-ingest`, { method: 'POST' })
}
