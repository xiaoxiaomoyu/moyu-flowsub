import type { CreateSessionRequest, FlowSession } from '../types'
import { request } from './http'

// 会话 API 统一放在这里，避免组件直接拼接后端路径。
export function createSession(payload: CreateSessionRequest) {
  return request<FlowSession>('/api/sessions', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function getSession(sessionId: string) {
  return request<FlowSession>(`/api/sessions/${sessionId}`)
}

export function listSessions() {
  return request<FlowSession[]>('/api/sessions')
}

export function finishSession(sessionId: string) {
  return request<FlowSession>(`/api/sessions/${sessionId}/finish`, {
    method: 'POST'
  })
}
