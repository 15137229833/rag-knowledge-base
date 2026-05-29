# 配置参考

## 配置文件位置

| 文件 | 用途 | 是否提交 Git |
|------|------|--------------|
| `backend/src/main/resources/application.yml` | 默认配置 | 是 |
| `backend/llm-local.properties` | 大模型密钥与覆盖项 | 否 |
| `backend/jwt-secret.properties` | JWT 密钥（可选） | 否 |
| `backend/.env` | 环境变量（部署脚本生成） | 否 |
| `.env` | Docker Compose 变量 | 否 |

`application.yml` 会通过 `spring.config.import` 自动加载同目录下的 `jwt-secret.properties` 和 `llm-local.properties`（存在才加载）。

## 大模型配置

### 通义千问（默认）

复制示例文件并编辑：

```bash
cp backend/llm-local.properties.example backend/llm-local.properties
```

最小配置：

```properties
app.llm.provider=qwen
app.llm.chat-backend=openai
spring.ai.openai.api-key=YOUR_DASHSCOPE_API_KEY
spring.ai.openai.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
spring.ai.openai.chat.options.model=qwen-plus
```

也可通过环境变量：

```bash
export LLM_PROVIDER=qwen
export SPRING_AI_OPENAI_API_KEY=YOUR_DASHSCOPE_API_KEY
export LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export LLM_CHAT_MODEL=qwen-plus
```

### Ollama（本机）

安装 [Ollama](https://ollama.com/) 并拉取模型：

```bash
ollama pull llama3.2
```

`llm-local.properties`：

```properties
app.llm.provider=ollama
app.llm.chat-backend=ollama
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
```

## 数据库

默认连接 Docker Compose 中的 PostgreSQL：

```yaml
spring.datasource.url: jdbc:postgresql://localhost:5432/ragkb
spring.datasource.username: rag
spring.datasource.password: rag
```

## MinIO

```yaml
app.minio.endpoint: http://127.0.0.1:9000
app.minio.access-key: minio
app.minio.secret-key: minio123456   # 与 docker-compose / .env 保持一致
app.minio.bucket: rag-docs
```

首次启动时后端会自动创建 bucket（若不存在）。

## Redis

```yaml
spring.data.redis.host: localhost
spring.data.redis.port: 6379
```

Redis 未启动时，后端启动阶段可能报错。请先 `docker compose up -d`。

## JWT 与管理员

```yaml
app.jwt.secret: ${JWT_SECRET:...}      # 至少 32 字符
app.bootstrap.enabled: true            # 生产环境建议 false
app.bootstrap.admin-username: admin
app.bootstrap.admin-password: admin123456
```

生产环境：

```bash
export JWT_SECRET=$(openssl rand -base64 48)
export BOOTSTRAP_ENABLED=false
```

## RAG 检索

```yaml
app.rag.hybrid.enabled: true
app.rag.hybrid.recall-fts-k: 48
app.rag.rerank.enabled: false          # 需要 Ollama rerank 接口
app.rag.rerank.model: bge-reranker-v2-m3
app.rag.ingest.semantic-chunking: true
app.rag.ingest.semantic-max-chunk: 800
```

## 多模态

```yaml
app.multimodal.image-caption-enabled: false
app.multimodal.video-caption-enabled: true
app.multimodal.ffmpeg-command: ffmpeg
app.multimodal.open-ai-vision-model: qwen-vl-plus
```

视频处理依赖系统已安装 ffmpeg：

```bash
# Ubuntu
sudo apt install ffmpeg

# Windows
# 安装后确保 ffmpeg 在 PATH 中
```

## 问答限流

```yaml
app.chat.min-question-length: 4
app.chat.max-question-length: 1000
app.chat.min-interval-ms: 300
```

## 前端代理

开发模式下 `frontend/vite.config.js` 将 `/api` 代理到 `http://localhost:8081`。若修改后端端口，同步改 `server.proxy.target`。

## 常见问题

**启动报 OpenAiChatModel 未注册**  
未配置 API Key。检查 `llm-local.properties` 或 `SPRING_AI_OPENAI_API_KEY`。

**文档一直 PROCESSING**  
查看后端日志；常见原因是 MinIO 连不上或文件格式不支持。

**问答无引用**  
确认文档状态为 READY，且问题与文档内容相关。空库或检索无命中时不会生成有效引用。

**Redis 连接失败**  
执行 `docker compose up -d redis` 或关闭 Redis 相关配置（需改代码，不推荐）。
