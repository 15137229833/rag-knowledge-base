# 架构说明

## 整体结构

系统分为四层：前端、API 网关（Spring MVC）、业务服务、基础设施。

```
浏览器 (Vue 3)
    │  HTTP /api/v1/*
    ▼
Spring Boot 后端
    ├── 认证 (JWT)
    ├── 知识库 / 文档 / 问答
    ├── 检索 (PostgreSQL FTS)
    └── 大模型调用 (Spring AI)
    │
    ├── PostgreSQL   业务数据 + 文本块索引
    ├── MinIO        原始文件
    ├── Redis        缓存
    └── LLM API      DashScope 或 Ollama
```

## 核心数据流

### 文档入库

```
上传文件 → MinIO 存储
         → 异步解析 (PDF/DOCX/TXT/MD)
         → 文本分块
         → 写入 vector_store 表 (FTS 索引，无向量嵌入)
         → 状态更新为 READY
```

图片和视频走多模态分支：抽帧或读图 → 调用视觉模型生成文字描述 → 同样分块入库。需要 ffmpeg 和可用的视觉模型 API。

### RAG 问答

```
用户提问
  → PostgreSQL 全文检索 (按知识库过滤)
  → (可选) Ollama rerank 精排
  → 取 Top-N 片段拼入 Prompt
  → Spring AI 调用对话模型
  → 返回答案 + citations
  → 写入 chat_record
```

当前版本的检索以 **PostgreSQL 全文检索** 为主，不依赖 embedding 模型。`vector_store` 是历史表名，实际存的是文本块和 metadata。

## 主要模块

### 后端包结构 (`com.rag.kb`)

| 包 | 职责 |
|----|------|
| `api` | REST 控制器 |
| `service` | 业务逻辑 |
| `rag` | 检索编排、FTS 仓库 |
| `domain` | JPA 实体 |
| `repository` | 数据访问 |
| `config` | 安全、缓存、LLM 路由 |
| `security` | JWT |

### 数据库迁移

Flyway 脚本位于 `backend/src/main/resources/db/migration/`，应用启动时自动执行。

主要表：`app_user`、`knowledge_base`、`kb_document`、`vector_store`、`chat_record`、`chat_session`、`audit_log` 等。

### 前端路由

| 路径 | 页面 |
|------|------|
| `/` | 知识库列表 |
| `/kb/:id` | 知识库详情（文档 + 问答） |
| `/kb/:id/insights` | 统计洞察 |
| `/kb/:id/graph` | 文档关联图谱 |
| `/documents` | 跨库文档中心 |
| `/admin/audit` | 审计日志（管理员） |

## API 概览

基础路径：`/api/v1`

| 分组 | 路径前缀 | 说明 |
|------|----------|------|
| Auth | `/auth` | 注册、登录 |
| KnowledgeBase | `/knowledge-bases` | 知识库 CRUD |
| Document | `/knowledge-bases/{id}/documents` | 文档上传与管理 |
| Chat | `/knowledge-bases/{id}/chat` | 问答与历史 |
| Admin | `/admin` | 审计日志 |
| System | `/system` | 运行状态、配置 |

完整接口见 Swagger UI。

## 缓存

Redis 缓存知识库信息、权限、部分统计结果。`spring.cache.type=redis`，Docker Compose 需启动 Redis 容器。

## 安全

- 接口默认需 JWT，白名单：`/api/v1/auth/**`、Swagger 相关路径
- 知识库按所有者 / 成员角色控制读写
- 关键操作写入 `audit_log`

## 扩展功能说明

- **知识图谱**：基于问答引用记录的文档共现关系，非独立图数据库
- **协作**：成员邀请、文档评论、版本记录
- **API Token**：对外提供程序化访问入口
- **Rerank**：依赖 Ollama `/api/rerank`，未安装 Ollama 时自动跳过
