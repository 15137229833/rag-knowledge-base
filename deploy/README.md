# 部署脚本说明

`deploy.sh` 用于在 Linux 服务器上安装依赖、构建项目并启动服务。适用于 Ubuntu 22.04、OpenCloudOS 8 等常见发行版。

## 服务器要求

| 项目 | 最低 | 推荐 |
|------|------|------|
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 磁盘 | 40 GB | 80 GB+ |
| 系统 | Ubuntu 20.04+ | Ubuntu 22.04 LTS |

2 核 4GB 环境下脚本会限制 JVM 堆内存和 Docker 容器占用，必要时可用 `DEPLOY_SKIP_OLLAMA=1` 跳过本机 Ollama。

## 使用步骤

```bash
# 1. 克隆代码
git clone <your-repo-url> /opt/rag-kb
cd /opt/rag-kb

# 2. 配置大模型（必须，脚本不会写入 API Key）
cp backend/llm-local.properties.example backend/llm-local.properties
vim backend/llm-local.properties

# 3. 执行部署
chmod +x deploy/deploy.sh
sudo ./deploy/deploy.sh
```

部署完成后：

- 前端与 API 通过 Nginx 对外提供（默认 80 端口）
- 管理员账号密码写在 `/opt/rag-kb/admin-credentials.txt`

## 环境变量

| 变量 | 说明 |
|------|------|
| `DEPLOY_SKIP_OLLAMA=1` | 不安装 Ollama，仅用云端 API |
| `DEPLOY_HTTP_PORT=8088` | Nginx 监听端口（非 80 时） |
| `DEPLOY_PUBLIC_HOST` | 公网 IP 或域名，用于 CORS 和 Nginx |
| `DEPLOY_NGINX_IPV6_ONLY=1` | Nginx 仅监听 IPv6 |
| `DEPLOY_NONINTERACTIVE=1` | 非交互模式 |

示例：

```bash
export DEPLOY_SKIP_OLLAMA=1
export DEPLOY_PUBLIC_HOST=203.0.113.10
export DEPLOY_NONINTERACTIVE=1
sudo -E ./deploy/deploy.sh
```

## 脚本做了什么

1. 更新系统包，安装 Docker、Java 17、Node.js
2. 可选安装 Ollama 及模型
3. 生成 JWT、MinIO、管理员随机密码
4. `docker compose up -d` 启动 PostgreSQL、MinIO、Redis
5. `mvn package` 构建后端，`npm run build` 构建前端
6. 配置 Nginx 反向代理
7. 注册后端 systemd 服务（如适用）

## 辅助脚本

| 文件 | 用途 |
|------|------|
| `generate-nginx-site.sh` | 单独生成 Nginx 站点配置 |
| `enable-nginx-site.sh` | 启用站点 |
| `install-backend-service.sh` | 安装 systemd 单元 |
| `start-backend-nohup.sh` | 后台启动后端 JAR |

## 部署后维护

```bash
# 查看 Docker 服务
cd /opt/rag-kb && docker compose ps

# 查看后端日志
journalctl -u rag-kb-backend -f

# 重启后端
sudo systemctl restart rag-kb-backend

# 更新代码后重新构建
cd /opt/rag-kb/backend && mvn -DskipTests package
cd /opt/rag-kb/frontend && npm ci && npm run build
sudo systemctl restart rag-kb-backend
```

## 故障排查

**502 Bad Gateway**  
后端未启动或端口不是 8081。检查 `systemctl status rag-kb-backend`。

**CORS 错误**  
公网访问时需在 `backend/application-deploy.yml` 中补充前端域名。部署脚本会按 `DEPLOY_PUBLIC_HOST` 自动生成，有域名后手动追加。

**问答失败**  
检查 `llm-local.properties` 中的 API Key 是否有效，以及 DashScope 账户余额。

**MinIO 上传失败**  
确认 `backend/.env` 里的 `MINIO_SECRET_KEY` 与项目根 `.env` 的 `MINIO_ROOT_PASSWORD` 一致。

更完整的说明见 [docs/deployment.md](../docs/deployment.md)。
