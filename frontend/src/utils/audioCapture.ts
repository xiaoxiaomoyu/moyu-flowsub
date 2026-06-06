export interface AudioChunkMeta {
  chunkIndex: number
  timestamp: number
  format: string
  sampleRate: number
  channels: number
  level: number
}

export interface AudioCaptureState {
  running: boolean
  permissionStatus: 'IDLE' | 'REQUESTING' | 'GRANTED' | 'DENIED' | 'ERROR'
  sampleRate: number
  level: number
  chunkCount: number
  errorMessage: string
}

type ChunkHandler = (meta: AudioChunkMeta, data: ArrayBuffer) => void
type StateHandler = (state: AudioCaptureState) => void

const initialState: AudioCaptureState = {
  running: false,
  permissionStatus: 'IDLE',
  sampleRate: 0,
  level: 0,
  chunkCount: 0,
  errorMessage: ''
}

class AudioCapture {
  private mediaStream: MediaStream | null = null
  private audioContext: AudioContext | null = null
  private sourceNode: MediaStreamAudioSourceNode | null = null
  private workletNode: AudioWorkletNode | null = null
  private silentGain: GainNode | null = null
  private state: AudioCaptureState = { ...initialState }
  private onState: StateHandler | null = null

  async start(onChunk: ChunkHandler, onState: StateHandler, chunkDurationMs = 300) {
    if (this.state.running) {
      return
    }

    this.onState = onState
    this.patchState({ permissionStatus: 'REQUESTING', errorMessage: '' })

    try {
      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          channelCount: 1
        }
      })

      this.audioContext = new AudioContext({ sampleRate: 16000 })
      await this.audioContext.audioWorklet.addModule(`${import.meta.env.BASE_URL}audio-worklet-processor.js`)

      this.sourceNode = this.audioContext.createMediaStreamSource(this.mediaStream)
      this.workletNode = new AudioWorkletNode(this.audioContext, 'flowsub-audio-processor', {
        processorOptions: { chunkDurationMs }
      })
      this.silentGain = this.audioContext.createGain()
      this.silentGain.gain.value = 0

      this.workletNode.port.onmessage = (event: MessageEvent<AudioChunkMeta & { buffer: ArrayBuffer }>) => {
        const { buffer, ...meta } = event.data
        this.patchState({
          sampleRate: meta.sampleRate,
          level: meta.level,
          chunkCount: meta.chunkIndex
        })
        onChunk(meta, buffer)
      }

      // Worklet 需要挂在音频图中才会持续运行，静音 Gain 可以避免本地回放产生啸叫。
      this.sourceNode.connect(this.workletNode)
      this.workletNode.connect(this.silentGain)
      this.silentGain.connect(this.audioContext.destination)

      this.patchState({
        running: true,
        permissionStatus: 'GRANTED',
        sampleRate: this.audioContext.sampleRate,
        level: 0,
        chunkCount: 0
      })
    } catch (error) {
      this.stop()
      const message = error instanceof Error ? error.message : '麦克风采集启动失败'
      this.patchState({
        running: false,
        permissionStatus: message.includes('Permission') || message.includes('denied') ? 'DENIED' : 'ERROR',
        errorMessage: message
      })
      throw error
    }
  }

  stop() {
    this.workletNode?.disconnect()
    this.sourceNode?.disconnect()
    this.silentGain?.disconnect()
    this.mediaStream?.getTracks().forEach((track) => track.stop())
    void this.audioContext?.close()

    this.mediaStream = null
    this.audioContext = null
    this.sourceNode = null
    this.workletNode = null
    this.silentGain = null
    this.patchState({
      ...initialState,
      permissionStatus: this.state.permissionStatus === 'GRANTED' ? 'IDLE' : this.state.permissionStatus
    })
  }

  snapshot() {
    return { ...this.state }
  }

  private patchState(patch: Partial<AudioCaptureState>) {
    this.state = { ...this.state, ...patch }
    this.onState?.({ ...this.state })
  }
}

export const audioCapture = new AudioCapture()
