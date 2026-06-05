<script setup lang="ts">
import { useSubtitleStore } from '../stores/subtitleStore'

const subtitleStore = useSubtitleStore()
</script>

<template>
  <section class="panel correction-panel">
    <div class="panel-header">
      <h2>修正记录</h2>
      <el-tag type="warning" effect="plain">{{ subtitleStore.corrections.length }} 次</el-tag>
    </div>

    <div v-if="subtitleStore.corrections.length === 0" class="empty-state compact">
      <strong>暂无修正</strong>
      <p>第 4 条字幕后会自动推送一条上下文修正。</p>
    </div>

    <div v-else class="correction-list">
      <article v-for="correction in subtitleStore.corrections" :key="correction.segmentId" class="correction-item">
        <div class="subtitle-meta">
          <span>{{ correction.segmentId }}</span>
          <span>v{{ correction.version }}</span>
        </div>
        <p class="old-text">{{ correction.oldTranslatedText }}</p>
        <p class="new-text">{{ correction.newTranslatedText }}</p>
        <p class="reason">{{ correction.reason }}</p>
      </article>
    </div>
  </section>
</template>
