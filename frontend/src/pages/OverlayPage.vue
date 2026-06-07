<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import type { SubtitleSegment } from '../types'

const subtitles = ref<SubtitleSegment[]>([])
const listRef = ref<HTMLElement | null>(null)
const channel = ref<BroadcastChannel | null>(null)

onMounted(() => {
  channel.value = new BroadcastChannel('flowsub-subtitles')
  channel.value.onmessage = (event: MessageEvent<SubtitleSegment>) => {
    if (event.data && event.data.segmentId) {
      const existing = subtitles.value.findIndex(s => s.segmentId === event.data.segmentId)
      if (existing >= 0) {
        subtitles.value[existing] = event.data
      } else {
        subtitles.value.push(event.data)
      }
      if (subtitles.value.length > 60) {
        subtitles.value = subtitles.value.slice(-40)
      }
      nextTick(() => {
        if (listRef.value) {
          listRef.value.scrollTop = listRef.value.scrollHeight
        }
      })
    }
  }
})

onUnmounted(() => {
  channel.value?.close()
})
</script>

<template>
  <div class="overlay-shell">
    <div class="overlay-header">
      <span class="overlay-logo">MoYu FlowSub</span>
      <span class="overlay-tag">实时双语字幕</span>
    </div>
    <div ref="listRef" class="subtitle-stream">
      <div
        v-for="sub in subtitles"
        :key="sub.segmentId"
        class="overlay-cue"
        :class="{ corrected: sub.isCorrected }"
      >
        <p class="overlay-source">{{ sub.sourceText }}</p>
        <p class="overlay-target">{{ sub.translatedText }}</p>
      </div>
      <div v-if="subtitles.length === 0" class="overlay-empty">
        <p>等待字幕…</p>
        <span>请在主窗口创建会话并开始音频采集</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overlay-shell {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  user-select: none;
  -webkit-user-select: none;
}

.overlay-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.92), rgba(30, 41, 59, 0.88));
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.overlay-logo {
  font-size: 15px;
  font-weight: 750;
  letter-spacing: 0.02em;
  background: linear-gradient(135deg, #5eeac4, #3ba8ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.overlay-tag {
  padding: 3px 10px;
  border-radius: 20px;
  background: rgba(94, 234, 196, 0.15);
  color: #8bcfc1;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.subtitle-stream {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  background:
    radial-gradient(ellipse at 50% 0%, rgba(15, 23, 42, 0.72), transparent 60%),
    rgba(2, 8, 20, 0.94);
  scroll-behavior: smooth;
}

.subtitle-stream::-webkit-scrollbar {
  width: 4px;
}

.subtitle-stream::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 4px;
}

.overlay-cue {
  padding: 12px 16px;
  margin-bottom: 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  animation: cue-in 0.4s ease-out;
  transition: border-color 0.3s ease, background 0.3s ease;
}

.overlay-cue.corrected {
  border-color: rgba(94, 234, 196, 0.25);
  background: rgba(94, 234, 196, 0.06);
}

@keyframes cue-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.overlay-source {
  margin: 0 0 6px;
  color: rgba(255, 255, 255, 0.75);
  font-size: 16px;
  line-height: 1.5;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.overlay-target {
  margin: 0;
  color: rgba(94, 234, 196, 0.92);
  font-size: 22px;
  line-height: 1.4;
  font-weight: 720;
  letter-spacing: 0.02em;
}

.overlay-empty {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 280px;
  text-align: center;
}

.overlay-empty p {
  margin: 0;
  color: rgba(255, 255, 255, 0.35);
  font-size: 18px;
  font-weight: 600;
}

.overlay-empty span {
  color: rgba(255, 255, 255, 0.18);
  font-size: 13px;
}
</style>
