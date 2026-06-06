import { defineStore } from 'pinia'
import type { SubtitleCorrection, SubtitleSegment } from '../types'

export const useSubtitleStore = defineStore('subtitle', {
  state: () => ({
    subtitles: [] as SubtitleSegment[],
    corrections: [] as SubtitleCorrection[]
  }),
  actions: {
    reset() {
      this.subtitles = []
      this.corrections = []
    },
    addSubtitle(subtitle: SubtitleSegment) {
      // 同一个 segmentId 可能被重新推送，按 ID 覆盖可以保持字幕列表稳定。
      const existing = this.subtitles.findIndex((item) => item.segmentId === subtitle.segmentId)
      if (existing >= 0) {
        this.subtitles[existing] = subtitle
      } else {
        // 实时观看时最新字幕放在最上方，避免用户一直向下滚动追字幕。
        this.subtitles.unshift(subtitle)
      }
    },
    applyCorrection(correction: SubtitleCorrection) {
      // 修正记录保留原文和新译文，同时回写历史字幕的展示状态。
      this.corrections.unshift(correction)
      const target = this.subtitles.find((item) => item.segmentId === correction.segmentId)
      if (target) {
        target.sourceText = correction.newSourceText
        target.translatedText = correction.newTranslatedText
        target.version = correction.version
        target.status = 'CORRECTED'
        target.isCorrected = true
      }
    }
  }
})
