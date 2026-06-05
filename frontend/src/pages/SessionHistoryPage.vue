<script setup lang="ts">
import { onMounted } from 'vue'
import { useSessionStore } from '../stores/sessionStore'

const sessionStore = useSessionStore()
onMounted(() => sessionStore.refreshSessions())
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
        <el-button @click="sessionStore.refreshSessions">刷新</el-button>
      </div>
      <el-table :data="sessionStore.sessions" stripe>
        <el-table-column prop="sessionId" label="会话 ID" min-width="180" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="sceneType" label="场景" width="140" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="createdAt" label="创建时间" min-width="220" />
      </el-table>
    </section>
  </main>
</template>
