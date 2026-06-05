class FlowSubAudioProcessor extends AudioWorkletProcessor {
  constructor(options) {
    super()
    this.chunkDurationMs = options.processorOptions?.chunkDurationMs ?? 750
    this.chunkSamples = Math.max(1, Math.floor(sampleRate * this.chunkDurationMs / 1000))
    this.buffer = []
    this.chunkIndex = 0
  }

  process(inputs) {
    const input = inputs[0]?.[0]
    if (!input) {
      return true
    }

    for (let i = 0; i < input.length; i += 1) {
      this.buffer.push(input[i])
    }

    while (this.buffer.length >= this.chunkSamples) {
      const samples = this.buffer.splice(0, this.chunkSamples)
      const pcm = new Int16Array(samples.length)
      let sum = 0

      for (let i = 0; i < samples.length; i += 1) {
        const clamped = Math.max(-1, Math.min(1, samples[i]))
        pcm[i] = clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff
        sum += clamped * clamped
      }

      this.chunkIndex += 1
      this.port.postMessage(
        {
          chunkIndex: this.chunkIndex,
          timestamp: Date.now(),
          format: 'pcm_s16le',
          sampleRate,
          channels: 1,
          level: Math.sqrt(sum / samples.length),
          buffer: pcm.buffer
        },
        [pcm.buffer]
      )
    }

    return true
  }
}

registerProcessor('flowsub-audio-processor', FlowSubAudioProcessor)
