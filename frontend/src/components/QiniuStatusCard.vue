<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Cloudy, Refresh } from '@element-plus/icons-vue'
import { getQiniuStatus } from '../api/qiniuApi'
import type { QiniuStatus } from '../types'

const status = ref<QiniuStatus | null>(null)
const loading = ref(false)
const uploadReadyText = computed(() => (status.value?.uploadReady ? '真实上传已启用' : '本地归档兜底'))

async function loadStatus() {
  // 页面打开后自动检查 Kodo 配置，方便演示时确认归档会走云端还是本地兜底。
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
      <h2>Kodo 配置</h2>
      <el-button :icon="Refresh" circle size="small" :loading="loading" @click="loadStatus" />
    </div>

    <div class="qiniu-main">
      <el-icon class="qiniu-icon"><Cloudy /></el-icon>
      <div>
        <strong>{{ uploadReadyText }}</strong>
        <p>{{ status?.message || '正在检查七牛云 Kodo 归档配置。' }}</p>
      </div>
    </div>

    <div class="status-grid">
      <div>
        <span>Kodo 开关</span>
        <el-tag :type="status?.enabled ? 'success' : 'info'" effect="plain">{{ status?.enabled ? '是' : '否' }}</el-tag>
      </div>
      <div>
        <span>AccessKey</span>
        <el-tag :type="status?.accessKeyConfigured ? 'success' : 'info'" effect="plain">
          {{ status?.accessKeyConfigured ? '已配置' : '未配置' }}
        </el-tag>
      </div>
      <div>
        <span>SecretKey</span>
        <el-tag :type="status?.secretKeyConfigured ? 'success' : 'info'" effect="plain">
          {{ status?.secretKeyConfigured ? '已配置' : '未配置' }}
        </el-tag>
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
      <div>
        <span>上传能力</span>
        <el-tag :type="status?.uploadReady ? 'success' : 'warning'" effect="plain">
          {{ status?.uploadReady ? '已就绪' : '本地兜底' }}
        </el-tag>
      </div>
      <div>
        <span>归档前缀</span>
        <strong>{{ status?.archivePrefix || 'moyu-flowsub' }}</strong>
      </div>
    </div>
  </section>
</template>
