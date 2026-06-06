# MoYu FlowSub

MoYu FlowSub 是一款基于七牛云的 AI 实时双语同传字幕助手，面向外语演讲、技术分享、国际会议和网课场景。当前 Demo 已跑通前后端工程、会话管理、WebSocket 实时通信、麦克风音频采集、七牛云智能语音/FunASR/Mock ASR 降级链路、DeepSeek 实时翻译、上下文修正、字幕展示、延迟指标、七牛云 Kodo 会话归档、AI 会后总结、Miku 快直播拉流和会话回放同步。

## 技术栈

- 后端：Java 17、Spring Boot 3、Spring Web、Spring WebSocket、Validation、Lombok、Maven
- 前端：Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、原生 WebSocket
- 当前存储：内存存储
- 音频采集：浏览器麦克风、AudioWorklet、PCM 音频切片、WebSocket Binary Frame
- 直播拉流：七牛云 Miku 快直播地址、FFmpeg 后端拉流、PCM 转写入 ASR
- ASR 策略：七牛云智能语音实时 WebSocket 优先、FunASR 本地 WebSocket 兜底、Mock ASR 保底演示
- 翻译与总结策略：DeepSeek-V4-Pro 优先、Mock 翻译和 Mock 总结保底演示
- 云能力：七牛云 Kodo 会话归档、七牛云智能语音真实识别、Miku 快直播、DeepSeek-V4-Pro 真实翻译

## 当前阶段功能

- `GET /api/health` 健康检查
- 会话创建、查询、列表、结束
- `/ws/translate/{sessionId}` WebSocket 实时通信
- 浏览器麦克风权限申请与音频采集
- Miku 快直播推流/播放地址生成和后端 FFmpeg 拉流
- AudioWorklet 将音频切成 PCM 小块并上传到后端
- 后端接收 WebSocket 二进制音频帧并做内存缓存
- ASR Provider 会话级流式适配层与降级状态展示
- 配置七牛云 AI API Key 后优先调用七牛云智能语音实时识别
- 配置 FunASR WebSocket 地址后可作为本地真实识别兜底
- 无云端配置时使用 Mock ASR 生成英文原文字幕
- ASR 稳定结果自动进入 DeepSeek 翻译队列
- DeepSeek 未配置或调用失败时自动降级到 Mock 翻译
- 基于最近 6 条稳定字幕进行上下文修正
- 至少 6 条模拟双语字幕推送
- 第 4 条字幕后主动推送 1 条上下文修正
- 延迟指标实时更新
- 会话结束后自动生成归档快照
- 未配置 Kodo 时生成本地内存归档，配置完整时上传到七牛云 Kodo
- 归档资源包含会话元数据、双语字幕、修正记录、指标快照、Markdown 总结、结构化洞察、PCM 音频、WAV 音频、WebVTT 字幕和回放清单
- 历史会话页展示归档状态并支持手动重试归档
- 会后总结页展示中文摘要、时间线、术语表、重点句、Markdown 原文和 Kodo 资源链接
- 会话回放页支持音频播放时同步高亮双语字幕
- Demo 文档、API 文档、架构文档和 Docker 配置占位

## 本地启动

后端：

```bash
cd backend
mvn -s maven-settings.xml spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

浏览器打开：

```text
http://localhost:5173
```

## 环境变量

复制 `.env.example` 为 `.env` 后按需填写。不要提交真实密钥。

| 变量 | 说明 |
| --- | --- |
| `QINIU_ACCESS_KEY` | 七牛云 AK，占位 |
| `QINIU_SECRET_KEY` | 七牛云 SK，占位 |
| `QINIU_BUCKET` | Kodo 存储桶，用于会话归档 |
| `QINIU_DOMAIN` | Kodo 访问域名，用于生成资源链接 |
| `QINIU_ARCHIVE_PREFIX` | Kodo 归档对象前缀，默认 `moyu-flowsub` |
| `QINIU_PRIVATE_BUCKET` | 是否为私有空间，默认 `false` |
| `QINIU_DOWNLOAD_EXPIRE_SECONDS` | 私有资源下载链接过期时间，默认 `3600` |
| `QINIU_ASR_ENABLED` | 是否启用七牛云智能语音真实识别，默认 `true` |
| `QINIU_AI_API_KEY` | 七牛云 AI Token API Key，用于智能语音 Bearer 鉴权 |
| `QINIU_ASR_WS_URL` | 七牛云智能语音 WebSocket 地址，默认 `wss://api.qnaigc.com/v1/voice/asr` |
| `DEEPSEEK_ENABLED` | 是否启用 DeepSeek 真实翻译与会后总结，默认 `false` |
| `DEEPSEEK_API_KEY` | DeepSeek-V4-Pro 密钥，占位，用于实时翻译和会后总结 |
| `DEEPSEEK_BASE_URL` | OpenAI-compatible 接口地址，默认 `https://api.deepseek.com/v1` |
| `DEEPSEEK_MODEL` | 默认 `deepseek-v4-flash` |
| `DEEPSEEK_TIMEOUT_MS` | DeepSeek 调用超时，默认 `12000` |
| `ASR_MOCK_ENABLED` | 是否启用 Mock ASR 保底，默认 `true` |
| `FUNASR_ENABLED` | 是否启用 FunASR 本地兜底，默认 `false`，需要本地服务启动后再开启 |
| `FUNASR_ENDPOINT` | 兼容旧配置的 FunASR 地址 |
| `FUNASR_WS_ENDPOINT` | FunASR 本地 WebSocket 地址，例如 `ws://localhost:10095` |
| `AUDIO_CHUNK_DURATION_MS` | 后端音频切片建议间隔，默认 `300` |
| `VITE_WS_BASE_URL` | 前端 WebSocket 地址覆盖项，例如 `ws://localhost:8080` |
| `VITE_AUDIO_CHUNK_DURATION_MS` | 前端 AudioWorklet 切片间隔，默认 `300` |
| `MIKU_ENABLED` | 是否启用 Miku 快直播地址生成，默认 `false` |
| `MIKU_API_HOST` | Miku 管理 API Host，默认 `https://mls.cn-east-1.qiniumiku.com` |
| `MIKU_REGION` | Miku 区域，默认 `cn-east-1` |
| `MIKU_PUBLISH_DOMAIN` | Miku 推流域名，用于生成 RTMP 推流地址 |
| `MIKU_PLAY_DOMAIN` | Miku 播放域名，用于 FFmpeg 拉取 HLS 音频 |
| `MIKU_WHEP_DOMAIN` | Miku WHEP 域名，占位用于低延时播放 |
| `MIKU_STREAM_PREFIX` | 直播流名前缀，默认 `moyu-flowsub` |
| `FFMPEG_PATH` | FFmpeg 可执行文件路径，默认使用 PATH 中的 `ffmpeg` |
| `FFPROBE_PATH` | FFprobe 可执行文件路径，默认使用 PATH 中的 `ffprobe` |
| `MEDIA_MOCK_ENABLED` | Miku/FFmpeg 不可用时是否使用 Mock 直播源保底，默认 `true` |

## 真实识别配置

七牛云智能语音优先：

```env
QINIU_ASR_ENABLED=true
QINIU_AI_API_KEY=你的七牛云 AI Token API Key
QINIU_ASR_WS_URL=wss://api.qnaigc.com/v1/voice/asr
```

注意：`QINIU_AI_API_KEY` 使用七牛云 AI Token API Key，不是 Kodo 的 `QINIU_ACCESS_KEY` / `QINIU_SECRET_KEY`。后端启动时会尝试读取项目根目录和 `backend/` 目录下的 `.env`；如果页面仍显示 `Mock ASR`，请查看左侧 ASR 状态中的降级原因，通常是未开启 `QINIU_ASR_ENABLED`、未配置 `QINIU_AI_API_KEY` 或七牛云 WebSocket 连接失败。

FunASR 本地兜底：

```env
FUNASR_ENABLED=true
FUNASR_WS_ENDPOINT=ws://localhost:10095
```

不填写七牛云和 FunASR 配置时，系统会自动使用 `Mock ASR` 保底，保证 Demo 演示闭环。

## 真实翻译与会后总结配置

DeepSeek-V4-Pro 优先：

```env
DEEPSEEK_ENABLED=true
DEEPSEEK_API_KEY=你的 DeepSeek API Key
DEEPSEEK_BASE_URL=https://api.deepseek.com/v1
DEEPSEEK_MODEL=deepseek-v4-flash
DEEPSEEK_TIMEOUT_MS=12000
```

DeepSeek 未配置或调用失败时，系统会自动使用 `Mock 翻译` 和 `Mock 总结` 保底，并在页面指标或会后总结页中显示降级原因。

## Kodo 归档配置

只使用 Mock ASR 也可以完整验收第五阶段归档链路。未配置 Kodo 时，会话结束后自动生成 `LOCAL_ONLY` 本地内存归档；配置完整后，后端会把归档资源上传到七牛云 Kodo。

```env
QINIU_ACCESS_KEY=你的七牛云 AK
QINIU_SECRET_KEY=你的七牛云 SK
QINIU_BUCKET=你的 Kodo 存储桶
QINIU_DOMAIN=https://你的 Kodo 访问域名
QINIU_ARCHIVE_PREFIX=moyu-flowsub
QINIU_PRIVATE_BUCKET=false
QINIU_DOWNLOAD_EXPIRE_SECONDS=3600
```

固定归档对象路径：

```text
{prefix}/{sessionId}/metadata.json
{prefix}/{sessionId}/subtitles.json
{prefix}/{sessionId}/corrections.json
{prefix}/{sessionId}/metrics.json
{prefix}/{sessionId}/summary.md
{prefix}/{sessionId}/insights.json
{prefix}/{sessionId}/audio.pcm
{prefix}/{sessionId}/audio.wav
{prefix}/{sessionId}/subtitles.vtt
{prefix}/{sessionId}/playback-manifest.json
```

归档接口：

- `POST /api/archive/sessions/{sessionId}`：手动触发或重试归档
- `GET /api/archive/sessions/{sessionId}`：查询单个会话归档状态和资源列表
- `GET /api/archive/sessions`：查询全部归档记录

## Miku 直播与回放配置

第七阶段支持后端用 FFmpeg 从 Miku 播放地址拉取直播音频，并转为 16k 单声道 PCM 送入现有 ASR/翻译链路。未配置 Miku 或 FFmpeg 不可用时，会自动使用 Mock 直播源演示完整链路。

```env
MIKU_ENABLED=true
MIKU_PUBLISH_DOMAIN=你的 Miku 推流域名
MIKU_PLAY_DOMAIN=你的 Miku 播放域名
MIKU_WHEP_DOMAIN=你的 Miku WHEP 域名
FFMPEG_PATH=ffmpeg
FFPROBE_PATH=ffprobe
MEDIA_MOCK_ENABLED=true
```

媒体接口：

- `GET /api/media/status`：查询 Miku 和 FFmpeg 状态
- `POST /api/media/live/sessions/{sessionId}/prepare`：准备直播源并生成推流/播放地址
- `GET /api/media/live/sessions/{sessionId}`：查询直播源状态
- `POST /api/media/live/sessions/{sessionId}/start-ingest`：启动后端 FFmpeg 拉流或 Mock 直播源
- `POST /api/media/live/sessions/{sessionId}/stop-ingest`：停止后端拉流
- `GET /api/playback/sessions/{sessionId}`：查询会话回放清单

## 第七阶段验收清单

1. 启动后端，访问 `http://localhost:8080/api/health` 返回 `UP`。
2. 启动前端，页面显示 `MoYu FlowSub` 标题。
3. 点击“创建会话”，会话状态显示为 `CREATED`，连接状态变为 `CONNECTED`。
4. 点击“开始麦克风采集”，浏览器请求麦克风权限。
5. 授权后左侧输入电平、采样率和音频块数量开始更新。
6. 配置 `QINIU_ASR_ENABLED=true` 和 `QINIU_AI_API_KEY` 后，右侧指标显示“七牛云智能语音”，并推送真实英文识别字幕。
7. 不配置七牛云但配置 `FUNASR_WS_ENDPOINT` 时，右侧指标显示 `FunASR`，并使用本地服务推送真实英文识别字幕。
8. 七牛云和 FunASR 都未配置时，右侧指标显示 `Mock ASR` 和“已降级”，Demo 仍可跑通。
9. 配置 `DEEPSEEK_ENABLED=true` 和 `DEEPSEEK_API_KEY` 后，`ASR_FINAL` 稳定字幕会生成真实中文译文。
10. 至少 3 条稳定字幕后，右侧指标展示翻译延迟、翻译模型和翻译链路状态。
11. 出现上下文修正时，右侧修正记录新增条目，中间字幕对应片段显示“已修正”。
12. 不配置 DeepSeek 时，页面显示 `Mock 翻译` 降级，Demo 仍可跑通。
13. 点击“停止麦克风采集”后，浏览器麦克风占用释放。
14. “模拟同传兜底”仍可展示第一阶段的双语字幕与修正效果。
15. 点击“结束会话”，会话状态变为 `FINISHED`，WebSocket 断开，并自动触发归档。
16. 未配置 Kodo 时，历史会话页显示“本地归档”，会后总结页显示 AI/Mock 摘要、时间线、术语表、重点句和本地资源列表。
17. 配置 `QINIU_ACCESS_KEY`、`QINIU_SECRET_KEY`、`QINIU_BUCKET`、`QINIU_DOMAIN` 后，历史会话页显示“上传成功”，会后总结页显示 Kodo key/url。
18. 会后总结页资源列表包含 `summary.md` 和 `insights.json`。
19. 配置 `DEEPSEEK_ENABLED=true` 和 `DEEPSEEK_API_KEY` 后，会后总结 Provider 显示 `DeepSeek-V4-Pro`。
20. 不配置 DeepSeek 时，会后总结 Provider 显示 `Mock 总结 · 已降级`，Demo 仍可跑通。
21. 访问 `http://localhost:8080/api/qiniu/status` 返回 Kodo 配置与上传能力状态。
22. 实时同传页“直播源”卡片显示 Miku/FFmpeg 配置状态。
23. 点击“准备直播”后显示推流地址、播放地址和 WHEP 地址。
24. 点击“开始后端拉流”后，配置完整时显示 `Miku 快直播 + FFmpeg`，未配置时显示 `Mock 直播源`。
25. 后端拉流或 Mock 直播源运行时，字幕流和指标持续更新。
26. 结束会话并归档后，资源列表包含 `audio.wav`、`subtitles.vtt`、`playback-manifest.json`。
27. 从历史会话点击“回放”，音频播放时双语字幕按时间高亮。

## 后续阶段计划

- 后续阶段：接入数据库持久化、完善七牛云智能语音/FunASR 真实识别稳定性，并进一步扩展视频点播 CDN 和直播回放同步能力。
