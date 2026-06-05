import type { ApiResponse } from '../types'

// 统一解析后端 ApiResponse，组件层只关心真正的 data 数据。
export async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(options?.headers ?? {})
    },
    ...options
  })

  if (!response.ok) {
    throw new Error(`请求失败，状态码：${response.status}`)
  }

  const body = (await response.json()) as ApiResponse<T>
  if (body.code !== 0) {
    throw new Error(body.message)
  }
  return body.data
}
