<script setup lang="ts">
import AudioControl from '../components/AudioControl.vue'
import CorrectionPanel from '../components/CorrectionPanel.vue'
import MetricsPanel from '../components/MetricsPanel.vue'
import QiniuStatusCard from '../components/QiniuStatusCard.vue'
import SubtitlePanel from '../components/SubtitlePanel.vue'

function openOverlay() {
  const width = 520
  const height = window.screen.height
  const left = window.screen.width - width
  window.open(
    '/overlay',
    'moyu-flowsub-overlay',
    `width=${width},height=${height},left=${left},top=0,menubar=no,toolbar=no,location=no,status=no`
  )
}
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
        <QiniuStatusCard />
      </aside>
      <section class="center-column">
        <SubtitlePanel />
      </section>
      <aside class="right-column">
        <CorrectionPanel />
        <MetricsPanel />
      </aside>
    </section>
  </main>
</template>
