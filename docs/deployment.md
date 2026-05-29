# 部署指南

## 本地开发

见项目根目录 [README.md](../README.md)。核心步骤：

1. `docker compose up -d`
2. 配置 `backend/llm-local.properties`
3. `mvn spring-boot:run` + `npm run dev`

## Linux 服务器（脚本部署）

`deploy/deploy.sh` 适用于 Ubuntu 20.04+ / OpenCloudOS 8 等，目标机器建议 2 核 4GB 以上。

### 准备

```bash
git clone <your-repo-url> /opt/rag-kb
cd /opt/rag-kb
```

配置大模型（脚本不会替你填写 API Key）：

```bash
cp backend/llm-local.properties.example backend/llm-local.properties
# 编辑并填入 DashScope API Key
```

### 执行部署

```bash
chmod +x deploy/deploy.sh
sudo ./deploy/deploy.sh
```

脚本会：

- 安装 Docker、Java 17、Node.js（以及可选的 Ollama）
- 生成 JWT、MinIO、管理员随机密码
- 构建前后端
- 启动 Docker 服务与后端
- 配置 Nginx 反向代理

管理员密码保存在 `/opt/rag-kb/admin-credentials.txt`，部署结束后请查看并妥善保管。

### 跳过 Ollama

纯云端 API 部署时可省内存：

```bash
export DEPLOY_SKIP_OLLAMA=1
sudo -E ./deploy/deploy.sh
```

### 自定义 HTTP 端口

80 端口被占用时：

```bash
export DEPLOY_HTTP_PORT=8088
export DEPLOY_PUBLIC_HOST=your-server-ip
sudo -E ./deploy/deploy.sh
```

### IPv6

```bash
export DEPLOY_PUBLIC_HOST='your-ipv6-address'
export DEPLOY_NGINX_IPV6_ONLY=1
sudo -E ./deploy/deploy.sh
```

## 手动部署要点

若不用脚本，按以下顺序操作：

1. 安装 JDK 17、Maven、Node.js、Docker
2. `docker compose up -d`
3. 配置 `backend/llm-local.properties` 和环境变量（JWT、MinIO）
4. 构建后端：`cd backend && mvn -DskipTests package`
5. 构建前端：`cd frontend && npm ci && npm run build`
6. 运行后端 JAR，Nginx 托管 `frontend/dist` 并反代 `/api`

后端 JAR 启动示例：

```bash
java -jar backend/target/rag-knowledge-backend-0.0.1-SNAPSHOT.jar \
  --spring.config.additional-location=file:./backend/llm-local.properties
```

## Nginx 参考

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /opt/rag-kb/frontend/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 300s;
    }
}
```

## 上线检查清单

- [ ] 修改 JWT_SECRET
- [ ] 修改 MinIO 密码，并与后端配置一致
- [ ] 修改或禁用 bootstrap 管理员（BOOTSTRAP_ENABLED=false）
- [ ] 配置 HTTPS
- [ ] 限制 MinIO 控制台（9001）仅内网访问
- [ ] 确认 `llm-local.properties` 未进入版本库
- [ ] 防火墙仅开放 80/443（及 SSH）

## 资源占用参考

2 核 4GB 机器建议：

- PostgreSQL 容器限制约 768MB
- Redis 约 256MB
- MinIO 约 384MB
- JVM 堆 1.5GB 左右（脚本已做限制）

内存紧张时使用 `DEPLOY_SKIP_OLLAMA=1`，仅走云端 API。

## 相关脚本

| 脚本 | 说明 |
|------|------|
| `deploy/deploy.sh` | 主部署脚本 |
| `deploy/generate-nginx-site.sh` | 生成 Nginx 站点配置 |
| `deploy/install-backend-service.sh` | 注册 systemd 服务 |
| `backend/start-backend.sh` | 本地 JAR 启动（需先打包） |

更多细节见 [deploy/README.md](../deploy/README.md)。
