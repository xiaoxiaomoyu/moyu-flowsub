import type { ArchiveStatusResponse } from '../types'
import { request } from './http'

// 归档接口用于会话结束后的资源查询和手动重试。
export function getArchive(sessionId: string) {
  return request<ArchiveStatusResponse>(`/api/archive/sessions/${sessionId}`)
}

export function listArchives() {
  return request<ArchiveStatusResponse[]>('/api/archive/sessions')
}

export function archiveSession(sessionId: string) {
  return request<ArchiveStatusResponse>(`/api/archive/sessions/${sessionId}`, { method: 'POST' })
}
