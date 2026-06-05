import type { QiniuStatus } from '../types'
import { request } from './http'

// 获取七牛云配置占位状态，第一阶段不触发真实云端调用。
export function getQiniuStatus() {
  return request<QiniuStatus>('/api/qiniu/status')
}
