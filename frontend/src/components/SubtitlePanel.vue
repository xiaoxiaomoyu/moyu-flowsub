<script setup lang="ts">
import { useSubtitleStore } from '../stores/subtitleStore'

const subtitleStore = useSubtitleStore()
</script>

<template>
  <section class="panel subtitle-panel">
    <div class="panel-header">
      <h2>实时双语字幕</h2>
      <el-tag effect="plain">{{ subtitleStore.subtitles.length }} 条</el-tag>
    </div>

    <div v-if="subtitleStore.subtitles.length === 0" class="empty-state">
      <strong>等待字幕流</strong>
      <p>创建会话并点击开始模拟同传后，字幕会按秒推送到这里。</p>
    </div>

    <div v-else class="subtitle-list">
      <article v-for="subtitle in subtitleStore.subtitles" :key="subtitle.segmentId" class="subtitle-item">
        <div class="subtitle-meta">
          <span>{{ subtitle.segmentId }}</span>
          <el-tag size="small" :type="subtitle.isCorrected ? 'warning' : 'success'">
            {{ subtitle.isCorrected ? '已修正' : subtitle.status }}
          </el-tag>
          <span>v{{ subtitle.version }}</span>
          <span>{{ subtitle.latencyMs }}ms</span>
        </div>
        <p class="source-text">{{ subtitle.sourceText }}</p>
        <p class="target-text">{{ subtitle.translatedText }}</p>
      </article>
    </div>
  </section>
</template>
