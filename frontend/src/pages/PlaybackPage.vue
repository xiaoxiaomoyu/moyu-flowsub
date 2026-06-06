<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getPlaybackManifest } from '../api/playbackApi'
import type { ArchiveResourceType, PlaybackManifest } from '../types'

const route = useRoute()
const manifest = ref<PlaybackManifest | null>(null)
const loading = ref(false)
const currentTime = ref(0)

const sessionId = computed(() => String(route.query.sessionId || ''))
const activeCueId = computed(() => {
  const cue = manifest.value?.cues.find((item) => currentTime.value >= item.startSeconds && currentTime.value < item.endSeconds)
  return cue?.segmentId || ''
})

const resourceTypeText: Record<ArchiveResourceType, string> = {
  METADATA: '会话元数据',
  SUBTITLES: '双语字幕',
  CORRECTIONS: '修正记录',
  METRICS: '指标快照',
  SUMMARY: 'Markdown 总结',
  INSIGHTS: '结构化洞察',
  AUDIO: 'PCM 音频',
  AUDIO_WAV: 'WAV 音频',
  SUBTITLES_VTT: 'WebVTT 字幕',
  PLAYBACK_MANIFEST: '回放清单'
}

async function loadPlayback() {
  if (!sessionId.value) {
    return
  }
  loading.value = true
  try {
    manifest.value = await getPlaybackManifest(sessionId.value)
  } finally {
    loading.value = false
  }
}

function updateTime(event: Event) {
  currentTime.value = (event.target as HTMLAudioElement).currentTime
}

function formatTime(seconds: number) {
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

onMounted(loadPlayback)
</script>

<template>
  <main class="app-shell secondary-page">
    <header class="topbar">
      <div>
        <h1>会话回放</h1>
        <p>同步播放音频、双语字幕、修正记录和会后摘要。</p>
      </div>
      <nav class="topnav">
        <RouterLink to="/sessions">历史会话</RouterLink>
        <RouterLink to="/">实时同传</RouterLink>
      </nav>
    </header>

    <section v-if="loading" class="panel summary-placeholder">
      <el-skeleton :rows="8" animated />
    </section>

    <section v-else-if="!manifest" class="panel summary-placeholder">
      <h2>暂无回放清单</h2>
      <p>请先完成会话并触发归档，再从历史会话进入回放。</p>
    </section>

    <section v-else class="playback-layout">
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>{{ manifest.session.title }}</h2>
            <p class="panel-subtitle">{{ manifest.message }}</p>
          </div>
          <el-tag :type="manifest.fallback ? 'warning' : 'success'" effect="plain">
            {{ manifest.provider }}{{ manifest.fallback ? ' · 已降级' : '' }}
          </el-tag>
        </div>

        <audio
          v-if="manifest.audioUrl"
          class="playback-audio"
          controls
          :src="manifest.audioUrl"
          @timeupdate="updateTime"
        >
          <track v-if="manifest.subtitleUrl" kind="subtitles" srclang="zh" label="双语字幕" :src="manifest.subtitleUrl" default>
        </audio>
        <el-alert v-else type="warning" :closable="false" title="当前会话没有可播放音频，仍可查看字幕时间线。" />

        <div class="playback-cue-list">
          <article
            v-for="cue in manifest.cues"
            :key="cue.segmentId"
            class="playback-cue"
            :class="{ active: activeCueId === cue.segmentId }"
          >
            <time>{{ formatTime(cue.startSeconds) }} - {{ formatTime(cue.endSeconds) }}</time>
            <p class="source-text">{{ cue.sourceText }}</p>
            <p class="target-text">{{ cue.translatedText }}</p>
            <el-tag v-if="cue.corrected" size="small" type="success" effect="plain">已修正</el-tag>
          </article>
        </div>
      </div>

      <aside class="playback-side">
        <section class="panel">
          <div class="panel-header">
            <h2>会后摘要</h2>
            <el-tag :type="manifest.summary.fallback ? 'warning' : 'success'" effect="plain">
              {{ manifest.summary.providerName }}
            </el-tag>
          </div>
          <p class="playback-summary">{{ manifest.summary.abstractText }}</p>
        </section>

        <section class="panel">
          <div class="panel-header">
            <h2>修正记录</h2>
            <span class="panel-subtitle">{{ manifest.corrections.length }} 条</span>
          </div>
          <div class="correction-list">
            <article v-for="correction in manifest.corrections" :key="correction.segmentId" class="correction-item">
              <strong>{{ correction.segmentId }}</strong>
              <p class="new-text">{{ correction.newTranslatedText }}</p>
              <p class="reason">{{ correction.reason }}</p>
            </article>
          </div>
        </section>

        <section class="panel">
          <div class="panel-header">
            <h2>回放资源</h2>
            <span class="panel-subtitle">{{ manifest.resources.length }} 项</span>
          </div>
          <div class="resource-list">
            <article v-for="resource in manifest.resources" :key="resource.type" class="resource-item compact-resource">
              <div>
                <strong>{{ resourceTypeText[resource.type] }}</strong>
                <p>{{ resource.key }}</p>
              </div>
              <a v-if="resource.url" :href="resource.url" target="_blank" rel="noreferrer">打开</a>
              <el-tag v-else type="warning" effect="plain">本地</el-tag>
            </article>
          </div>
        </section>
      </aside>
    </section>
  </main>
</template>
