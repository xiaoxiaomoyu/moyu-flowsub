# MoYu FlowSub

MoYu FlowSub 是一款基于七牛云的 AI 实时双语同传字幕助手，面向外语演讲、技术分享、国际会议和网课场景。第一阶段 Demo 先跑通前后端工程、会话管理、WebSocket 实时字幕推送、字幕修正记录、延迟指标和七牛云配置占位。

## 技术栈

- 后端：Java 17、Spring Boot 3、Spring Web、Spring WebSocket、Validation、Lombok、Maven
- 前端：Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、原生 WebSocket
- 当前存储：内存存储
- 云能力：七牛云 Kodo、DeepSeek-V4-Pro、智能语音配置占位，后续阶段接入

## 当前阶段功能

- `GET /api/health` 健康检查
- 会话创建、查询、列表、结束
- `/ws/translate/{sessionId}` WebSocket 实时通信
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

复制 `.env.example` 为 `.env` 后按需填写。第一阶段不会真实调用云服务，也不要提交真实密钥。

| 变量 | 说明 |
| --- | --- |
| `QINIU_ACCESS_KEY` | 七牛云 AK，占位 |
| `QINIU_SECRET_KEY` | 七牛云 SK，占位 |
| `QINIU_BUCKET` | Kodo 存储桶，占位 |
| `QINIU_DOMAIN` | Kodo 访问域名，占位 |
| `DEEPSEEK_API_KEY` | DeepSeek-V4-Pro 密钥，占位 |
| `DEEPSEEK_MODEL` | 默认 `deepseek-v4-pro` |

## 第一阶段验收清单

1. 启动后端，访问 `http://localhost:8080/api/health` 返回 `UP`。
2. 启动前端，页面显示 `MoYu FlowSub` 标题。
3. 点击“创建会话”，会话状态显示为 `CREATED`，连接状态变为 `CONNECTED`。
4. 点击“开始模拟同传”，中间字幕面板逐秒显示至少 6 条双语字幕。
5. 第 4 条字幕后，右侧出现 1 条修正记录，`seg_000002` 显示“已修正”。
6. 右侧延迟指标更新 ASR、翻译、总延迟、字幕数和修正数。
7. 点击“结束会话”，会话状态变为 `FINISHED`，WebSocket 断开。
8. 访问 `http://localhost:8080/api/qiniu/status` 返回七牛云配置占位状态。

## 后续阶段计划

- 第二阶段：接入浏览器真实音频采集和音频切片上传。
- 第三阶段：接入 ASR Provider，优先对接七牛云智能语音。
- 第四阶段：接入七牛云 AI 大模型 DeepSeek-V4-Pro，完成真实翻译和上下文修正。
- 第五阶段：接入七牛云 Kodo，归档音频、字幕、修正记录和会后总结。
- 第六阶段：生成会后摘要、时间线、术语表和重点句。
