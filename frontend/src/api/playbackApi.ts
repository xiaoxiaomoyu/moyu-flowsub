import type { PlaybackManifest } from '../types'
import { request } from './http'

export function getPlaybackManifest(sessionId: string) {
  return request<PlaybackManifest>(`/api/playback/sessions/${sessionId}`)
}
