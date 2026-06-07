# MoYu FlowSub

基于 Qwen + 七牛云的 AI 实时双语同传字幕助手，面向外语演讲、技术分享、国际会议和网课场景。

## 功能概览

- 浏览器麦克风/系统音频采集，AudioWorklet PCM 切片
- Qwen ASR Realtime 语音识别，Mock ASR 自动降级
- Qwen/DashScope 实时翻译与上下文修正
- Qwen/DashScope 会后总结（摘要、时间线、术语表、重点句）
- 七牛云 Kodo 会话归档（10 种资源），未配置时本地保底
- 双语字幕实时展示、延迟指标、Provider 降级状态
- 会话回放（音频 + 字幕同步高亮）
- 浮窗字幕（独立窗口，跨窗口同步）
- Docker 一键部署

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5、Spring WebSocket、Lombok、Maven |
| 前端 | Vue 3、Vite 7、TypeScript、Element Plus、Pinia、Vue Router |
| ASR | Qwen ASR Realtime 优先，Mock ASR 保底 |
| 翻译/总结 | Qwen/DashScope Chat API 优先，Mock 保底 |
| 存储 | 内存（运行时）+ 七牛云 Kodo（归档）|

## 快速开始

### Docker Compose（推荐）

```bash
git clone <repo-url> && cd MoYu-FlowSub
cp .env.example .env          # 按需编辑
docker compose up -d
```

访问 `http://localhost:5173`。

### 本地开发

**后端**（需要 Java 17）：

```bash
cd backend
mvn spring-boot:run
```

**前端**（需要 Node.js 20+）：

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，前端自动代理 `/api` 到 `localhost:8080`。

## 环境变量

复制 `.env.example` 为 `.env` 后按需填写：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `QWEN_ENABLED` | 启用 Qwen ASR / 翻译 / 总结 | `true` |
| `DASHSCOPE_API_KEY` | 阿里云百炼 DashScope API Key | — |
| `QWEN_ASR_MODEL` | Qwen ASR 实时识别模型 | `qwen3-asr-flash-realtime` |
| `QWEN_TRANSLATION_MODEL` | Qwen 翻译模型 | `qwen-plus` |
| `QWEN_SUMMARY_MODEL` | Qwen 总结模型 | `qwen-plus` |
| `QWEN_BASE_URL` | DashScope 接口地址 | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `QWEN_TIMEOUT_MS` | API 调用超时（毫秒）| `12000` |
| `QWEN_TEMPERATURE` | 翻译温度参数 | `0` |
| `ASR_MOCK_ENABLED` | Mock ASR 保底 | `true` |
| `AUDIO_CHUNK_DURATION_MS` | 音频切片间隔（毫秒）| `300` |
| `QINIU_ACCESS_KEY` | 七牛云 AK | — |
| `QINIU_SECRET_KEY` | 七牛云 SK | — |
| `QINIU_BUCKET` | Kodo 存储桶 | — |
| `QINIU_DOMAIN` | Kodo 访问域名 | — |
| `QINIU_ARCHIVE_PREFIX` | 归档对象前缀 | `moyu-flowsub` |
| `QINIU_PRIVATE_BUCKET` | 私有空间模式 | `false` |
| `QINIU_DOWNLOAD_EXPIRE_SECONDS` | 私有链接有效期（秒）| `3600` |
| `VITE_WS_BASE_URL` | 前端 WebSocket 地址覆盖 | 自动检测 |
| `VITE_AUDIO_CHUNK_DURATION_MS` | 前端切片间隔（毫秒）| `200` |

## 架构

```
浏览器麦克风/系统音频
  → AudioWorklet (PCM 切片)
  → WebSocket Binary Frame
  → AudioStreamService
  → AsrService (Qwen ASR → Mock ASR 降级)
  → TranslationService (Qwen 翻译 → Mock 翻译 降级)
  → WebSocket Text Frame
  → Pinia Store → Vue 组件渲染
```

会话结束后，`ArchiveService` 自动生成 10 种归档资源并上传七牛云 Kodo（未配置时本地保底）：

| 资源 | 说明 |
| --- | --- |
| `metadata.json` | 会话元数据 |
| `subtitles.json` | 双语字幕 |
| `corrections.json` | 上下文修正记录 |
| `metrics.json` | 延迟指标快照 |
| `summary.md` | Markdown 总结 |
| `insights.json` | 结构化洞察 |
| `audio.pcm` | 原始 PCM 音频 |
| `audio.wav` | WAV 音频（可播放）|
| `subtitles.vtt` | WebVTT 字幕 |
| `playback-manifest.json` | 回放清单 |

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/health` | 健康检查 |
| `POST` | `/api/sessions` | 创建会话 |
| `GET` | `/api/sessions` | 会话列表 |
| `GET` | `/api/sessions/{id}` | 会话详情 |
| `POST` | `/api/sessions/{id}/finish` | 结束会话 |
| `GET` | `/api/qiniu/status` | Kodo 配置状态 |
| `POST` | `/api/archive/sessions/{id}` | 触发归档 |
| `GET` | `/api/archive/sessions/{id}` | 归档状态与资源 |
| `GET` | `/api/archive/sessions` | 全部归档记录 |
| `GET` | `/api/playback/sessions/{id}` | 回放清单 |
| `WS` | `/ws/translate/{sessionId}` | 实时同传 WebSocket |

## 页面路由

| 路径 | 页面 | 说明 |
| --- | --- | --- |
| `/` | `LiveTranslatePage` | 实时同传主页 |
| `/overlay` | `OverlayPage` | 浮窗字幕（独立窗口）|
| `/sessions` | `SessionHistoryPage` | 历史会话列表 |
| `/summary` | `SummaryPage` | 会后总结详情 |
| `/playback` | `PlaybackPage` | 会话回放 |

## 项目结构

```
├── backend/
│   ├── src/main/java/com/moyu/flowsub/
│   │   ├── asr/            # ASR Provider 链（Qwen + Mock）
│   │   ├── translation/    # 翻译 Provider 链（Qwen + Mock）
│   │   ├── summary/        # 总结 Provider 链（Qwen + Mock）
│   │   ├── qwen/           # Qwen 通用配置
│   │   ├── qiniu/          # 七牛云 Kodo 服务
│   │   ├── archive/        # 会话归档打包与上传
│   │   ├── audio/          # 音频流接收与缓存
│   │   ├── session/        # 会话生命周期管理
│   │   ├── websocket/      # WebSocket 消息推送
│   │   ├── playback/       # 会话回放清单
│   │   └── config/         # Spring 配置
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── pages/          # 5 个路由页面
│   │   ├── components/     # 面板与控件组件
│   │   ├── stores/         # Pinia 状态管理
│   │   ├── api/            # REST API 封装
│   │   ├── utils/          # 音频采集与 WebSocket 客户端
│   │   └── router/         # Vue Router 配置
│   └── package.json
├── docker-compose.yml
└── .env.example
```

## 验收清单

1. 启动后端，`GET /api/health` 返回 `UP`
2. 启动前端，页面显示 `MoYu FlowSub` 标题
3. 创建会话，状态显示 `CREATED`
4. 开始麦克风采集，电平、采样率、音频块计数更新
5. 配置 Qwen 后，ASR 推送真实英文识别字幕
6. Qwen 未配置时，Mock ASR 自动保底
7. 配置 Qwen 后，ASR FINAL 稳定字幕生成中文译文
8. 延迟指标实时更新，显示 Provider 名称和降级状态
9. 上下文修正出现时，字幕标记"已修正"
10. 结束会话，状态变为 `FINISHED`，自动触发归档
11. 未配置 Kodo 时显示本地归档，配置后上传至七牛云
12. 会后总结页展示摘要、时间线、术语表、重点句
13. 历史会话页支持归档重试和资源查看
14. 会话回放页音频播放时字幕同步高亮
15. 浮窗字幕独立窗口与主页同步更新
