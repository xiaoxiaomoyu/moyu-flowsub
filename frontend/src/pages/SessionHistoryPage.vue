<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { archiveSession, listArchives } from '../api/archiveApi'
import { useSessionStore } from '../stores/sessionStore'
import type { ArchiveStatus, ArchiveStatusResponse, FlowSession } from '../types'

const sessionStore = useSessionStore()
const archives = ref<ArchiveStatusResponse[]>([])
const loading = ref(false)
const archivingSessionId = ref('')

const archiveMap = computed(() => new Map(archives.value.map((archive) => [archive.sessionId, archive])))

const archiveStatusText: Record<ArchiveStatus, string> = {
  PENDING: '待归档',
  LOCAL_ONLY: '本地归档',
  UPLOADING: '上传中',
  UPLOADED: '上传成功',
  FAILED: '上传失败'
}

const archiveStatusType: Record<ArchiveStatus, 'info' | 'success' | 'warning' | 'danger' | 'primary'> = {
  PENDING: 'info',
  LOCAL_ONLY: 'warning',
  UPLOADING: 'primary',
  UPLOADED: 'success',
  FAILED: 'danger'
}

function archiveOf(sessionId: string) {
  return archiveMap.value.get(sessionId)
}

async function refreshAll() {
  loading.value = true
  try {
    await sessionStore.refreshSessions()
    archives.value = await listArchives()
  } finally {
    loading.value = false
  }
}

async function handleArchive(session: FlowSession) {
  archivingSessionId.value = session.sessionId
  try {
    const archive = await archiveSession(session.sessionId)
    archives.value = [archive, ...archives.value.filter((item) => item.sessionId !== archive.sessionId)]
    ElMessage.success(archive.status === 'UPLOADED' ? '归档已上传到 Kodo。' : '已生成本地归档。')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '归档失败，请稍后重试。')
  } finally {
    archivingSessionId.value = ''
  }
}

onMounted(refreshAll)
</script>

<template>
  <main class="app-shell secondary-page">
    <header class="topbar">
      <div>
        <h1>历史会话</h1>
        <p>当前阶段使用内存存储，重启后历史数据会清空。</p>
      </div>
      <nav class="topnav">
        <RouterLink to="/">返回实时同传</RouterLink>
      </nav>
    </header>

    <section class="panel">
      <div class="panel-header">
        <h2>会话列表</h2>
        <el-button :loading="loading" @click="refreshAll">刷新</el-button>
      </div>
      <el-table :data="sessionStore.sessions" stripe>
        <el-table-column prop="sessionId" label="会话 ID" min-width="180" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="sceneType" label="场景" width="140" />
        <el-table-column prop="status" label="会话状态" width="120" />
        <el-table-column label="归档状态" width="130">
          <template #default="{ row }">
            <el-tag :type="archiveStatusType[archiveOf(row.sessionId)?.status || 'PENDING']" effect="plain">
              {{ archiveStatusText[archiveOf(row.sessionId)?.status || 'PENDING'] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="220" />
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                size="small"
                :loading="archivingSessionId === row.sessionId"
                @click="handleArchive(row)"
              >
                手动归档
              </el-button>
              <RouterLink :to="{ path: '/summary', query: { sessionId: row.sessionId } }">
                <el-button size="small" type="primary" plain>查看资源</el-button>
              </RouterLink>
              <RouterLink :to="{ path: '/playback', query: { sessionId: row.sessionId } }">
                <el-button size="small" type="success" plain>回放</el-button>
              </RouterLink>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </main>
</template>
