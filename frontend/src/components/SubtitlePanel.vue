<script setup lang="ts">
import { useSubtitleStore } from '../stores/subtitleStore'
import { useSessionStore } from '../stores/sessionStore'

const subtitleStore = useSubtitleStore()
const sessionStore = useSessionStore()
</script>

<template>
  <section class="panel subtitle-panel">
    <div class="panel-header">
      <h2>实时双语字幕</h2>
      <el-tag effect="plain">{{ subtitleStore.subtitles.length }} 条</el-tag>
    </div>

    <div v-if="subtitleStore.subtitles.length === 0" class="empty-state">
      <strong v-if="sessionStore.audioCapture.running && sessionStore.audioCapture.chunkCount > 0">
        正在接收麦克风音频...
      </strong>
      <strong v-else>等待字幕流</strong>
      <p v-if="sessionStore.audioCapture.running">
        音频块 {{ sessionStore.audioCapture.chunkCount }} | 采样率 {{ sessionStore.audioCapture.sampleRate }}Hz
      </p>
      <p v-else>创建会话并开始麦克风采集后，英文识别字幕会实时推送到这里。</p>
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
