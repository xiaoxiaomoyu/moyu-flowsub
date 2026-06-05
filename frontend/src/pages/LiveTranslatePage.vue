<script setup lang="ts">
import AudioControl from '../components/AudioControl.vue'
import CorrectionPanel from '../components/CorrectionPanel.vue'
import MetricsPanel from '../components/MetricsPanel.vue'
import QiniuStatusCard from '../components/QiniuStatusCard.vue'
import SubtitlePanel from '../components/SubtitlePanel.vue'
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
