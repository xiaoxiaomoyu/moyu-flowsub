<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Cloudy, Refresh } from '@element-plus/icons-vue'
import { getQiniuStatus } from '../api/qiniuApi'
import type { QiniuStatus } from '../types'

const status = ref<QiniuStatus | null>(null)
const loading = ref(false)

async function loadStatus() {
  // 页面打开后自动检查配置占位，帮助演示时说明七牛云接入位置。
  loading.value = true
  try {
    status.value = await getQiniuStatus()
  } finally {
    loading.value = false
  }
}

onMounted(loadStatus)
</script>

<template>
  <section class="panel qiniu-panel">
    <div class="panel-header">
      <h2>七牛云占位</h2>
      <el-button :icon="Refresh" circle size="small" :loading="loading" @click="loadStatus" />
    </div>

    <div class="qiniu-main">
      <el-icon class="qiniu-icon"><Cloudy /></el-icon>
      <div>
        <strong>配置占位已就绪</strong>
        <p>{{ status?.message || '正在检查七牛云 Kodo 配置占位状态。' }}</p>
      </div>
    </div>

    <div class="status-grid">
      <div>
        <span>启用</span>
        <el-tag :type="status?.enabled ? 'success' : 'info'" effect="plain">{{ status?.enabled ? '是' : '否' }}</el-tag>
      </div>
      <div>
        <span>存储桶</span>
        <el-tag :type="status?.bucketConfigured ? 'success' : 'info'" effect="plain">
          {{ status?.bucketConfigured ? '已配置' : '未配置' }}
        </el-tag>
      </div>
      <div>
        <span>访问域名</span>
        <el-tag :type="status?.domainConfigured ? 'success' : 'info'" effect="plain">
          {{ status?.domainConfigured ? '已配置' : '未配置' }}
        </el-tag>
      </div>
    </div>
  </section>
</template>
