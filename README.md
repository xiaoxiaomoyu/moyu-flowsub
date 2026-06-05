# MoYu FlowSub

MoYu FlowSub 是一款基于七牛云的 AI 实时双语同传字幕助手，面向外语演讲、技术分享、国际会议和网课场景。当前 Demo 已跑通前后端工程、会话管理、WebSocket 实时通信、麦克风音频采集、七牛云智能语音/FunASR/Mock ASR 降级链路、字幕展示、修正记录、延迟指标和七牛云配置占位。

## 技术栈

- 后端：Java 17、Spring Boot 3、Spring Web、Spring WebSocket、Validation、Lombok、Maven
- 前端：Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、原生 WebSocket
- 当前存储：内存存储
- 音频采集：浏览器麦克风、AudioWorklet、PCM 音频切片、WebSocket Binary Frame
- ASR 策略：七牛云智能语音实时 WebSocket 优先、FunASR 本地 WebSocket 兜底、Mock ASR 保底演示
- 云能力：七牛云 Kodo 配置占位、七牛云智能语音真实识别、DeepSeek-V4-Pro 后续接入

## 当前阶段功能

- `GET /api/health` 健康检查
- 会话创建、查询、列表、结束
- `/ws/translate/{sessionId}` WebSocket 实时通信
- 浏览器麦克风权限申请与音频采集
- AudioWorklet 将音频切成 PCM 小块并上传到后端
- 后端接收 WebSocket 二进制音频帧并做内存缓存
- ASR Provider 会话级流式适配层与降级状态展示
- 配置七牛云 AI API Key 后优先调用七牛云智能语音实时识别
- 配置 FunASR WebSocket 地址后可作为本地真实识别兜底
- 无云端配置时使用 Mock ASR 生成英文原文字幕
- 至少 6 条模拟双语字幕推送
- 第 4 条字幕后主动推送 1 条上下文修正
- 延迟指标实时更新
- 七牛云 Kodo 配置占位状态
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
| `QINIU_BUCKET` | Kodo 存储桶，占位 |
| `QINIU_DOMAIN` | Kodo 访问域名，占位 |
| `QINIU_ASR_ENABLED` | 是否启用七牛云智能语音真实识别，默认 `false` |
| `QINIU_AI_API_KEY` | 七牛云 AI Token API Key，用于智能语音 Bearer 鉴权 |
| `QINIU_ASR_WS_URL` | 七牛云智能语音 WebSocket 地址，默认 `wss://openai.qiniu.com/v1/voice/asr` |
| `DEEPSEEK_API_KEY` | DeepSeek-V4-Pro 密钥，占位 |
| `DEEPSEEK_MODEL` | 默认 `deepseek-v4-pro` |
| `ASR_MOCK_ENABLED` | 是否启用 Mock ASR 保底，默认 `true` |
| `FUNASR_ENABLED` | 是否启用 FunASR 本地兜底，默认 `true` |
| `FUNASR_ENDPOINT` | 兼容旧配置的 FunASR 地址 |
| `FUNASR_WS_ENDPOINT` | FunASR 本地 WebSocket 地址，例如 `ws://localhost:10095` |
| `AUDIO_CHUNK_DURATION_MS` | 后端音频切片建议间隔，默认 `300` |
| `VITE_WS_BASE_URL` | 前端 WebSocket 地址覆盖项，例如 `ws://localhost:8080` |
| `VITE_AUDIO_CHUNK_DURATION_MS` | 前端 AudioWorklet 切片间隔，默认 `300` |

## 真实识别配置

七牛云智能语音优先：

```env
QINIU_ASR_ENABLED=true
QINIU_AI_API_KEY=你的七牛云 AI Token API Key
QINIU_ASR_WS_URL=wss://openai.qiniu.com/v1/voice/asr
```

FunASR 本地兜底：

```env
FUNASR_ENABLED=true
FUNASR_WS_ENDPOINT=ws://localhost:10095
```

不填写七牛云和 FunASR 配置时，系统会自动使用 `Mock ASR` 保底，保证 Demo 演示闭环。

## 第三阶段验收清单

1. 启动后端，访问 `http://localhost:8080/api/health` 返回 `UP`。
2. 启动前端，页面显示 `MoYu FlowSub` 标题。
3. 点击“创建会话”，会话状态显示为 `CREATED`，连接状态变为 `CONNECTED`。
4. 点击“开始麦克风采集”，浏览器请求麦克风权限。
5. 授权后左侧输入电平、采样率和音频块数量开始更新。
6. 配置 `QINIU_ASR_ENABLED=true` 和 `QINIU_AI_API_KEY` 后，右侧指标显示“七牛云智能语音”，并推送真实英文识别字幕。
7. 不配置七牛云但配置 `FUNASR_WS_ENDPOINT` 时，右侧指标显示 `FunASR`，并使用本地服务推送真实英文识别字幕。
8. 七牛云和 FunASR 都未配置时，右侧指标显示 `Mock ASR` 和“已降级”，Demo 仍可跑通。
9. 点击“停止麦克风采集”后，浏览器麦克风占用释放。
10. “模拟同传兜底”仍可展示第一阶段的双语字幕与修正效果。
11. 点击“结束会话”，会话状态变为 `FINISHED`，WebSocket 断开。
12. 访问 `http://localhost:8080/api/qiniu/status` 返回七牛云配置占位状态。

## 后续阶段计划

- 第四阶段：接入七牛云 AI 大模型 DeepSeek-V4-Pro，完成真实翻译和上下文修正。
- 第五阶段：接入七牛云 Kodo，归档音频、字幕、修正记录和会后总结。
- 第六阶段：生成会后摘要、时间线、术语表和重点句。
