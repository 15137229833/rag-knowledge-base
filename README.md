# RAG Knowledge Base

基于 Spring Boot 3 与 Spring AI 的企业知识库问答系统。支持文档上传、全文检索、引用溯源、多轮对话，以及知识库协作、审计日志等扩展能力。

后端：Java 17、Spring Boot 3.4、Spring AI、PostgreSQL、Redis、MinIO  
前端：Vue 3、Vite、Element Plus

## 功能概览

- 用户注册、登录（JWT）
- 知识库创建、成员权限
- 文档上传：PDF、DOCX、Markdown、TXT；支持 URL 导入
- 文档解析、分块、全文索引（PostgreSQL FTS）
- RAG 问答：检索相关片段 → 调用大模型生成回答 → 返回引用来源
- 对话历史：分页、搜索、导出
- 管理员审计日志
- 可选：多模态（图片/视频摘要）、知识图谱、API Token

## 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 18+ |
| Docker | 用于 PostgreSQL、MinIO、Redis |

大模型二选一：

1. **通义千问（DashScope）** — 默认方案，需 API Key  
2. **Ollama** — 本机离线，需自行安装并拉取模型

## 快速开始

### 1. 启动基础服务

```bash
cp .env.example .env
# 编辑 .env，修改 MINIO_ROOT_PASSWORD

docker compose up -d
```

启动后：

- PostgreSQL：`localhost:5432`，库 `ragkb`，用户/密码 `rag`/`rag`
- MinIO API：`http://127.0.0.1:9000`，控制台 `http://127.0.0.1:9001`
- Redis：`localhost:6379`

MinIO 默认账号见 `.env` 中的 `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`（示例文件为 `minio` / `change-me-minio-password`）。后端 `application.yml` 里 MinIO 客户端默认密钥为 `minio123456`，若改了 `.env` 里的密码，请同步修改 `MINIO_SECRET_KEY` 或 `backend/.env`。

### 2. 配置大模型

```bash
cd backend
cp llm-local.properties.example llm-local.properties
```

编辑 `llm-local.properties`，填入 DashScope API Key（或改为 Ollama 方案，见文件内注释）。

密钥获取：[阿里云 DashScope 控制台](https://dashscope.console.aliyun.com/)

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认端口 **8081**。Swagger：`http://localhost:8081/swagger-ui/index.html`

首次启动会自动创建管理员账号（若不存在）：

- 用户名：`admin`
- 密码：`admin123456`（可在 `application.yml` 或环境变量 `ADMIN_PASSWORD` 中修改）

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。Vite 已将 `/api` 代理到 `http://localhost:8081`。

### 5. 验证流程

1. 注册普通用户并登录  
2. 创建知识库，上传一份 TXT 或 PDF  
3. 等待文档状态变为 `READY`  
4. 在问答区提问，检查回答与引用片段  

## 目录结构

```
├── backend/          Spring Boot 后端
├── frontend/         Vue 3 前端
├── deploy/           服务器部署脚本
├── docs/             架构与配置说明
└── docker-compose.yml
```

## 配置说明

常用环境变量：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `SERVER_PORT` | 后端端口 | `8081` |
| `JWT_SECRET` | JWT 签名密钥 | 内置开发默认值 |
| `LLM_PROVIDER` | `qwen` 或 `ollama` | `qwen` |
| `BOOTSTRAP_ENABLED` | 是否自动创建管理员 | `true` |
| `ADMIN_PASSWORD` | 管理员初始密码 | `admin123456` |

完整配置见 [docs/configuration.md](docs/configuration.md)。

## 生产部署

Linux 服务器可使用 `deploy/deploy.sh` 一键部署，详见 [docs/deployment.md](docs/deployment.md) 与 [deploy/README.md](deploy/README.md)。

上线前建议：

- 修改 JWT 密钥、MinIO 密码、管理员密码
- 将 `BOOTSTRAP_ENABLED` 设为 `false`
- 配置 HTTPS 反向代理

## 文档

- [架构说明](docs/architecture.md)
- [配置参考](docs/configuration.md)
- [部署指南](docs/deployment.md)

## 许可证

[MIT](LICENSE)
