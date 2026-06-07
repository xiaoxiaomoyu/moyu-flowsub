<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import AudioControl from '../components/AudioControl.vue'
import CorrectionPanel from '../components/CorrectionPanel.vue'
import MetricsPanel from '../components/MetricsPanel.vue'
import QiniuStatusCard from '../components/QiniuStatusCard.vue'
import SubtitlePanel from '../components/SubtitlePanel.vue'

const overlayWindow = ref<Window | null>(null)
const focusTimer = ref<ReturnType<typeof setInterval> | null>(null)

function openOverlay() {
  const width = 520
  const height = window.screen.height
  const left = window.screen.width - width
  overlayWindow.value = window.open(
    '/overlay',
    'moyu-flowsub-overlay',
    `width=${width},height=${height},left=${left},top=0,menubar=no,toolbar=no,location=no,status=no`
  )
  if (overlayWindow.value) {
    // 保持浮窗置顶：主窗口获得焦点时同步聚焦浮窗
    window.addEventListener('focus', focusOverlay)
    // 浮窗关闭后清理
    focusTimer.value = setInterval(() => {
      if (overlayWindow.value?.closed) {
        cleanupOverlay()
      }
    }, 1000)
  }
}

function focusOverlay() {
  overlayWindow.value?.focus()
}

function cleanupOverlay() {
  overlayWindow.value = null
  window.removeEventListener('focus', focusOverlay)
  if (focusTimer.value) {
    clearInterval(focusTimer.value)
    focusTimer.value = null
  }
}

onUnmounted(() => {
  cleanupOverlay()
})
</script>

<template>
  <main class="app-shell">
    <header class="topbar">
      <div>
        <h1>MoYu FlowSub</h1>
        <p>基于七牛云的 AI 实时双语同传字幕助手</p>
      </div>
      <nav class="topnav">
        <RouterLink to="/">实时同传</RouterLink>
        <RouterLink to="/sessions">历史会话</RouterLink>
        <RouterLink to="/summary">会后总结</RouterLink>
        <a href="#" @click.prevent="openOverlay" class="overlay-btn">浮窗字幕</a>
      </nav>
    </header>

    <section class="workspace">
      <aside class="left-column">
        <AudioControl />
      </aside>
      <section class="center-column">
        <SubtitlePanel />
      </section>
      <aside class="right-column">
        <CorrectionPanel />
        <MetricsPanel />
        <QiniuStatusCard />
      </aside>
    </section>
  </main>
</template>
