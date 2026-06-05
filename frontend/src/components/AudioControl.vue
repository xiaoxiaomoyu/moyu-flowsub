<script setup lang="ts">
import { computed, reactive } from 'vue'
import { CircleCheck, Link, SwitchButton, VideoPlay } from '@element-plus/icons-vue'
import { useSessionStore } from '../stores/sessionStore'

const sessionStore = useSessionStore()

const form = reactive({
  title: 'AI 智能体技术分享',
  sourceLang: 'en',
  targetLang: 'zh',
  sceneType: 'TECH_TALK'
})

const connectionLabels: Record<string, string> = {
  CONNECTING: '连接中',
  CONNECTED: '已连接',
  DISCONNECTED: '未连接',
  ERROR: '连接异常'
}

const eventLabels: Record<string, string> = {
  SESSION_CONNECTED: '会话已连接',
  START_MOCK_TRANSLATE: '已开始模拟同传',
  START_AUDIO_STREAM: '已开始音频采集',
  STOP_AUDIO_STREAM: '已停止音频采集',
  AUDIO_STREAM_STARTED: '音频流已启动',
  AUDIO_STREAM_STOPPED: '音频流已停止',
  ASR_PARTIAL: '收到临时识别',
  ASR_FINAL: '收到稳定识别',
  ASR_PROVIDER_STATUS: 'ASR 状态更新',
  SUBTITLE_UPDATE: '收到字幕更新',
  SUBTITLE_CORRECTION: '收到字幕修正',
  METRICS_UPDATE: '收到指标更新',
  SESSION_FINISHED: '会话已结束'
}

const permissionLabels: Record<string, string> = {
  IDLE: '未请求',
  REQUESTING: '请求中',
  GRANTED: '已授权',
  DENIED: '已拒绝',
  ERROR: '异常'
}

const levelPercent = computed(() => Math.min(100, Math.round(sessionStore.audioCapture.level * 180)))

function connectionText(status: string) {
  return connectionLabels[status] ?? status
}

function eventText(type: string) {
  return eventLabels[type] ?? type
}
</script>

<template>
  <section class="panel">
    <div class="panel-header">
      <h2>会话控制</h2>
      <el-tag :type="sessionStore.connectionStatus === 'CONNECTED' ? 'success' : 'info'" effect="plain">
        {{ connectionText(sessionStore.connectionStatus) }}
      </el-tag>
    </div>

    <el-form label-position="top" class="control-form">
      <el-form-item label="会话标题">
        <el-input v-model="form.title" placeholder="输入演讲或课程主题" />
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="源语言">
          <el-select v-model="form.sourceLang">
            <el-option label="英语" value="en" />
            <el-option label="日语" value="ja" />
            <el-option label="韩语" value="ko" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标语言">
          <el-select v-model="form.targetLang">
            <el-option label="中文" value="zh" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="场景">
        <el-select v-model="form.sceneType">
          <el-option label="技术分享" value="TECH_TALK" />
          <el-option label="国际会议" value="CONFERENCE" />
          <el-option label="外语网课" value="ONLINE_COURSE" />
        </el-select>
      </el-form-item>
    </el-form>

    <div class="button-stack">
      <el-button
        type="primary"
        :icon="Link"
        :loading="sessionStore.loading"
        @click="sessionStore.create(form)"
      >
        创建会话
      </el-button>
      <el-button
        type="success"
        :icon="VideoPlay"
        :disabled="sessionStore.connectionStatus !== 'CONNECTED'"
        v-if="!sessionStore.audioCapture.running"
        @click="sessionStore.startAudioCapture"
      >
        开始麦克风采集
      </el-button>
      <el-button
        type="warning"
        :icon="SwitchButton"
        v-else
        @click="sessionStore.stopAudioCapture"
      >
        停止麦克风采集
      </el-button>
      <el-button
        :icon="VideoPlay"
        :disabled="sessionStore.connectionStatus !== 'CONNECTED' || sessionStore.audioCapture.running"
        @click="sessionStore.startMockTranslate"
      >
        模拟同传兜底
      </el-button>
      <el-button
        :icon="SwitchButton"
        :disabled="!sessionStore.currentSession"
        @click="sessionStore.finish"
      >
        结束会话
      </el-button>
    </div>

    <div class="session-status" v-if="sessionStore.currentSession">
      <div>
        <span>会话 ID</span>
        <strong>{{ sessionStore.currentSession.sessionId }}</strong>
      </div>
      <div>
        <span>状态</span>
        <el-tag :type="sessionStore.currentSession.status === 'FINISHED' ? 'warning' : 'success'" effect="dark">
          {{ sessionStore.currentSession.status }}
        </el-tag>
      </div>
      <div>
        <span>最近事件</span>
        <strong>{{ sessionStore.lastEvent ? eventText(sessionStore.lastEvent) : '等待操作' }}</strong>
      </div>
      <div>
        <span>麦克风权限</span>
        <strong>{{ permissionLabels[sessionStore.audioCapture.permissionStatus] }}</strong>
      </div>
      <div>
        <span>采样率</span>
        <strong>{{ sessionStore.audioCapture.sampleRate || '-' }}Hz</strong>
      </div>
      <div>
        <span>音频块</span>
        <strong>{{ sessionStore.audioCapture.chunkCount }}</strong>
      </div>
      <div>
        <span>ASR Provider</span>
        <strong>{{ sessionStore.asrProviderStatus.provider }}</strong>
      </div>
      <div>
        <span>识别连接</span>
        <strong>{{ sessionStore.asrProviderStatus.connected ? '已连接' : '未连接' }}</strong>
      </div>
    </div>

    <div class="audio-meter" v-if="sessionStore.currentSession">
      <div class="meter-row">
        <span>输入电平</span>
        <strong>{{ levelPercent }}%</strong>
      </div>
      <el-progress :percentage="levelPercent" :show-text="false" />
      <p>{{ sessionStore.asrProviderStatus.message }}</p>
      <p v-if="sessionStore.asrProviderStatus.reason">
        {{ sessionStore.asrProviderStatus.reason }}
      </p>
      <p v-if="sessionStore.audioCapture.errorMessage" class="error-text">
        {{ sessionStore.audioCapture.errorMessage }}
      </p>
    </div>

    <el-alert
      v-else
      type="info"
      :closable="false"
      show-icon
      title="创建会话后会自动建立 WebSocket 连接"
    >
      <template #default>
        <div class="alert-line"><el-icon><CircleCheck /></el-icon> 第三阶段优先使用七牛云真实识别，FunASR 和 Mock 作为兜底。</div>
      </template>
    </el-alert>
  </section>
</template>
