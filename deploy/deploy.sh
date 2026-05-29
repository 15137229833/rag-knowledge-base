#!/bin/bash

################################################################################
# RAG 知识问答系统 - 一键部署脚本（腾讯云轻量服务器优化版）
#
# 服务器配置：2 核 4GB+ 内存（亦适用于 8GB OpenCloudOS 等）
# 系统要求：Ubuntu 20.04+ 或 OpenCloudOS 8 / RHEL 8+ 系（dnf）
#
# 使用方法：
#   sudo ./deploy.sh
#
# IPv6 优先（IPv4 留给其它项目时）示例：
#   export DEPLOY_PUBLIC_HOST='你的公网IPv6'
#   export DEPLOY_NGINX_IPV6_ONLY=1   # Nginx 仅监听 IPv6，不占 IPv4 对应端口
#   export DEPLOY_NONINTERACTIVE=1
#   sudo -E ./deploy/deploy.sh
#
# 本机 80 已被其它进程占用（或 IPv6 的 80 也被占用）时，改用其它端口，例如 8088：
#   export DEPLOY_HTTP_PORT=8088
#   export DEPLOY_PUBLIC_HOST='你的公网IPv6'
#   export DEPLOY_NGINX_IPV6_ONLY=1
#   sudo -E ./deploy/deploy.sh
# 访问: http://[IPv6]:8088/
#
# 仅用云端 API、跳过本机 Ollama（4GB 内存更省）：
#   export DEPLOY_SKIP_OLLAMA=1
#
# 功能：
#   - 自动安装所有依赖
#   - 配置环境变量
#   - 构建前后端
#   - 启动所有服务
#   - 配置 Nginx 反向代理
#   - 针对 4GB 内存优化
################################################################################

set -e  # 遇到错误立即退出

# Docker Compose v2（docker compose）或旧版 docker-compose
dc() {
    if docker compose version &>/dev/null; then
        docker compose "$@"
    else
        docker-compose "$@"
    fi
}

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 判断是否像 IPv6（含冒号；IPv4 不含冒号）
is_ipv6_addr() {
    [[ "$1" == *:* ]]
}

# Nginx server_name / 展示用：IPv6 加方括号，IPv4 原样
nginx_server_name_for() {
    local h="$1"
    if is_ipv6_addr "$h"; then
        echo "[$h]"
    else
        echo "$h"
    fi
}

# 解析对外访问主机（CORS、Nginx server_name）；优先环境变量 DEPLOY_PUBLIC_HOST
resolve_public_host() {
    if [ -n "${DEPLOY_PUBLIC_HOST:-}" ]; then
        echo "${DEPLOY_PUBLIC_HOST}"
        return
    fi
    if [ "${DEPLOY_USE_IPV6:-0}" = "1" ]; then
        local v6
        v6=$(ip -6 addr show scope global 2>/dev/null | awk '/inet6 /{print $2}' | cut -d/ -f1 | grep -v '^::1$' | grep -v '^fe80' | head -1)
        if [ -n "$v6" ]; then
            echo "$v6"
            return
        fi
        log_warn "未检测到全局 IPv6，尝试 curl 获取..."
        v6=$(curl -s -6 --max-time 5 https://ifconfig.co 2>/dev/null | tr -d '[:space:]')
        if [ -n "$v6" ] && is_ipv6_addr "$v6"; then
            echo "$v6"
            return
        fi
    fi
    curl -s --max-time 5 ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}'
}

# Nginx 对外 HTTP 端口（80 被其它进程占用时设为 8088 等）
deploy_http_port() {
    local p="${DEPLOY_HTTP_PORT:-80}"
    if ! [[ "$p" =~ ^[0-9]+$ ]] || [ "$p" -lt 1 ] || [ "$p" -gt 65535 ]; then
        log_error "DEPLOY_HTTP_PORT 无效: ${DEPLOY_HTTP_PORT:-}（须为 1-65535 数字）"
        exit 1
    fi
    echo "$p"
}

# 检查是否为 root 用户
check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "请使用 root 用户或 sudo 运行此脚本"
        exit 1
    fi
}

# 发行版族：deb（apt）或 rpm（dnf）
DIST_FAMILY="deb"

# 检查系统
check_system() {
    log_info "检查系统环境..."
    
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
        VERSION=$VERSION_ID
        log_info "检测到系统: $OS $VERSION"
    else
        log_error "无法检测系统类型"
        exit 1
    fi

    DIST_FAMILY="deb"
    case "$OS" in
        ubuntu|debian) DIST_FAMILY="deb" ;;
        centos|rhel|rocky|almalinux|opencloudos|anolis|tencentos|fedora)
            DIST_FAMILY="rpm"
            ;;
        *)
            if echo "${ID_LIKE:-}" | grep -qiE 'rhel|fedora|centos'; then
                DIST_FAMILY="rpm"
            fi
            ;;
    esac
    export DIST_FAMILY
    log_info "包管理: $([ "$DIST_FAMILY" = "rpm" ] && echo dnf || echo apt)"
}

# 更新系统
update_system() {
    log_info "更新系统包..."
    if [ "$DIST_FAMILY" = "rpm" ]; then
        dnf update -y
    else
        apt update
        DEBIAN_FRONTEND=noninteractive apt upgrade -y
    fi
}

# 安装基础工具
install_basic_tools() {
    log_info "安装基础工具..."
    if [ "$DIST_FAMILY" = "rpm" ]; then
        dnf install -y curl wget git vim fail2ban htop net-tools iotop
    else
        apt install -y curl wget git vim ufw fail2ban htop net-tools iotop
    fi
    log_success "基础工具安装完成"
}

# 安装 Docker
install_docker() {
    if command -v docker &> /dev/null; then
        log_info "Docker 已安装，跳过"
        return
    fi
    
    log_info "安装 Docker..."
    curl -fsSL https://get.docker.com | sh
    usermod -aG docker $SUDO_USER
    
    # 旧版 docker-compose 二进制（无 Docker Compose 插件时备用）
    if ! docker compose version &>/dev/null; then
        log_info "安装 docker-compose 二进制..."
        curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
        chmod +x /usr/local/bin/docker-compose
    fi

    log_success "Docker 安装完成"
}

# 安装 Maven（打包后端）
install_maven() {
    if command -v mvn &>/dev/null; then
        log_info "Maven 已安装，跳过"
        return
    fi
    log_info "安装 Maven..."
    if [ "$DIST_FAMILY" = "rpm" ]; then
        dnf install -y maven
    else
        apt install -y maven
    fi
    log_success "Maven 安装完成"
}

# 安装 Java 17
install_java() {
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
        if [ "$JAVA_VERSION" -ge 17 ]; then
            log_info "Java $JAVA_VERSION 已安装，跳过"
            return
        fi
    fi
    
    log_info "安装 Java 17..."
    if [ "$DIST_FAMILY" = "rpm" ]; then
        dnf install -y java-17-openjdk-devel
    else
        apt install -y openjdk-17-jdk
    fi
    log_success "Java 安装完成"
}

# 安装 Node.js
install_nodejs() {
    if command -v node &> /dev/null; then
        log_info "Node.js 已安装，跳过"
        return
    fi
    
    log_info "安装 Node.js 18..."
    if [ "$DIST_FAMILY" = "rpm" ]; then
        curl -fsSL https://rpm.nodesource.com/setup_18.x | bash -
        dnf install -y nodejs
    else
        curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
        apt install -y nodejs
    fi
    log_success "Node.js 安装完成"
}

# 安装 Ollama
install_ollama() {
    if command -v ollama &> /dev/null; then
        log_info "Ollama 已安装，跳过"
        return
    fi
    
    log_info "安装 Ollama..."
    curl -fsSL https://ollama.com/install.sh | sh

    if systemctl list-unit-files | grep -q '^ollama.service'; then
        systemctl enable ollama --now 2>/dev/null || true
        sleep 3
    else
        log_warn "未检测到 ollama systemd 单元，请手动保证 ollama serve 常驻"
        nohup ollama serve >/var/log/ollama-serve.log 2>&1 &
        sleep 5
    fi

    log_info "拉取 AI 模型（2核4G 建议顺序拉取，耗时较长）..."
    ollama pull nomic-embed-text
    ollama pull llama3.2
    ollama pull bge-reranker-v2-m3 || log_warn "重排序模型拉取失败可稍后手动: ollama pull bge-reranker-v2-m3"

    log_success "Ollama 安装完成"
}

# 配置防火墙
configure_firewall() {
    log_info "配置防火墙..."

    if [ "$DIST_FAMILY" = "rpm" ]; then
        if systemctl list-unit-files | grep -q '^firewalld.service'; then
            systemctl enable firewalld --now 2>/dev/null || true
            firewall-cmd --permanent --add-service=ssh 2>/dev/null || firewall-cmd --permanent --add-port=22/tcp
            local hp
            hp="$(deploy_http_port)"
            firewall-cmd --permanent --add-port="${hp}/tcp" 2>/dev/null || true
            firewall-cmd --permanent --add-service=http
            firewall-cmd --permanent --add-service=https
            firewall-cmd --reload || true
            log_success "firewalld 已放行 22/80/443 及本系统端口 ${hp}/tcp"
        else
            log_warn "未检测到 firewalld，请在本机与腾讯云控制台安全组中放行 80/443/22"
        fi
        return
    fi

    ufw allow 22/tcp
    local hp
    hp="$(deploy_http_port)"
    ufw allow "${hp}/tcp"
    ufw allow 80/tcp
    ufw allow 443/tcp
    if [ "$hp" != "80" ]; then
        log_info "已放行本系统 Nginx 端口 ${hp}/tcp（DEPLOY_HTTP_PORT）；80/443 仍保留便于其它服务"
    fi
    echo "y" | ufw enable

    log_success "防火墙配置完成"
}

# 部署应用
deploy_app() {
    log_info "部署应用..."
    
    # 创建应用目录
    APP_DIR="/opt/rag-kb"
    mkdir -p $APP_DIR
    cd $APP_DIR
    
    # 检查是否已存在代码
    if [ ! -d "$APP_DIR/backend" ]; then
        log_error "代码目录不存在，请先克隆代码到 $APP_DIR"
        log_info "运行: git clone <your-repo> $APP_DIR"
        exit 1
    fi
    
    # 生成环境变量（MinIO 根密码须与后端 MINIO_SECRET_KEY 一致，并写入项目根 .env 供 docker compose 使用）
    log_info "生成环境变量..."
    MINIO_PASS="$(openssl rand -base64 24 | tr -d '\n' | tr '/+' '_-')"
    JWT_SEC="$(openssl rand -base64 48 | tr -d '\n' | tr '/+' '_-')"
    ADMIN_PASS_PLAIN="$(openssl rand -base64 16 | tr -d '\n' | tr '/+' '_-')"

    cat > "$APP_DIR/.env" << EOF
MINIO_ROOT_USER=minio
MINIO_ROOT_PASSWORD=$MINIO_PASS
EOF
    chmod 600 "$APP_DIR/.env" || true

    cd "$APP_DIR/backend"
    cat > .env << EOF
JWT_SECRET=$JWT_SEC
MINIO_ENDPOINT=http://127.0.0.1:9000
MINIO_ACCESS_KEY=minio
MINIO_SECRET_KEY=$MINIO_PASS
MINIO_BUCKET=rag-docs
BOOTSTRAP_ENABLED=true
ADMIN_USERNAME=admin
ADMIN_PASSWORD=$ADMIN_PASS_PLAIN
EOF
    chmod 600 .env || true

    # 公网访问时的 CORS（Spring 额外配置）；IPv6 须写成 http://[addr] 形式；非 80 端口须带 :port
    HTTP_P="$(deploy_http_port)"
    PUB_HOST="$(resolve_public_host)"
    if is_ipv6_addr "$PUB_HOST"; then
        if [ "$HTTP_P" = "80" ]; then
            CORS_V6="http://[${PUB_HOST}]"
            CORS_V6_80="http://[${PUB_HOST}]:80"
            CORS_V6S="https://[${PUB_HOST}]"
            cat > "$APP_DIR/backend/application-deploy.yml" << EOF
app:
  cors-allowed-origins:
    - "${CORS_V6}"
    - "${CORS_V6_80}"
    - "${CORS_V6S}"
    - "http://localhost"
    - "http://127.0.0.1"
EOF
            log_info "已写入 application-deploy.yml（CORS 含 IPv6 源 ${CORS_V6}），有域名后可再编辑该文件"
        else
            CORS_V6_P="http://[${PUB_HOST}]:${HTTP_P}"
            cat > "$APP_DIR/backend/application-deploy.yml" << EOF
app:
  cors-allowed-origins:
    - "${CORS_V6_P}"
    - "http://localhost"
    - "http://127.0.0.1"
EOF
            log_info "已写入 application-deploy.yml（CORS 含 ${CORS_V6_P}），有域名后可再编辑该文件"
        fi
    else
        if [ "$HTTP_P" = "80" ]; then
            cat > "$APP_DIR/backend/application-deploy.yml" << EOF
app:
  cors-allowed-origins:
    - "http://${PUB_HOST}"
    - "http://${PUB_HOST}:80"
    - "https://${PUB_HOST}"
    - "http://localhost"
    - "http://127.0.0.1"
EOF
        else
            cat > "$APP_DIR/backend/application-deploy.yml" << EOF
app:
  cors-allowed-origins:
    - "http://${PUB_HOST}:${HTTP_P}"
    - "http://localhost"
    - "http://127.0.0.1"
EOF
        fi
        log_info "已写入 application-deploy.yml（CORS 含 http://${PUB_HOST} 等），有域名后可再编辑该文件"
    fi

    if [ "${DEPLOY_NGINX_IPV6_ONLY:-0}" = "1" ] && ! is_ipv6_addr "$PUB_HOST"; then
        log_error "已设置 DEPLOY_NGINX_IPV6_ONLY=1，但当前解析到的地址不是 IPv6（${PUB_HOST}）。"
        log_error "请显式指定: export DEPLOY_PUBLIC_HOST='你的公网IPv6' 后重试。"
        exit 1
    fi
    
    ADMIN_PASS="$ADMIN_PASS_PLAIN"
    cat > $APP_DIR/admin-credentials.txt << EOF
==========================================
RAG 知识问答系统 - 管理员凭据
==========================================
用户名: admin
密码: $ADMIN_PASS

请妥善保存此信息！
==========================================
生成时间: $(date)
EOF
    
    log_success "环境变量配置完成"
    log_warn "管理员密码已保存到 $APP_DIR/admin-credentials.txt"
}

# 构建后端
build_backend() {
    log_info "构建后端..."
    cd /opt/rag-kb/backend
    
    # 检查 pom.xml
    if [ ! -f "pom.xml" ]; then
        log_error "pom.xml 不存在"
        exit 1
    fi
    
    # 构建
    mvn clean package -DskipTests
    
    if [ ! -f "target/rag-knowledge-backend-0.0.1-SNAPSHOT.jar" ]; then
        log_error "构建失败，JAR 文件不存在"
        exit 1
    fi
    
    log_success "后端构建完成"
}

# 构建前端
build_frontend() {
    log_info "构建前端..."
    cd /opt/rag-kb/frontend
    
    # 检查 package.json
    if [ ! -f "package.json" ]; then
        log_error "package.json 不存在"
        exit 1
    fi
    
    # 安装依赖
    npm install
    
    # 构建
    npm run build
    
    if [ ! -d "dist" ]; then
        log_error "构建失败，dist 目录不存在"
        exit 1
    fi
    
    log_success "前端构建完成"
}

# 创建 Swap 分区（低内存机器优化；≥6GB 物理内存则跳过）
create_swap() {
    if [ -f /swapfile ]; then
        log_info "Swap 文件已存在，跳过"
        return
    fi

    MEM_KB=$(awk '/^MemTotal:/ {print $2}' /proc/meminfo)
    if [ -n "$MEM_KB" ] && [ "$MEM_KB" -ge 6000000 ]; then
        log_info "物理内存 ≥6GB，跳过创建 Swap"
        return
    fi

    log_info "创建 2GB Swap 分区（防止内存不足）..."
    
    # 创建 2GB swap 文件
    dd if=/dev/zero of=/swapfile bs=1M count=2048 status=progress
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    
    # 永久生效
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    
    # 优化 swap 使用策略
    sysctl vm.swappiness=10
    echo 'vm.swappiness=10' >> /etc/sysctl.conf
    
    log_success "Swap 分区创建完成"
}

# 启动 Docker 服务
start_docker_services() {
    log_info "启动 Docker 服务..."
    cd /opt/rag-kb

    dc up -d
    
    # 等待服务启动
    log_info "等待数据库启动..."
    sleep 10
    
    log_success "Docker 服务启动完成"
}

# 配置 Systemd 服务
configure_systemd() {
    log_info "配置 Systemd 服务..."
    
    cat > /etc/systemd/system/rag-kb-backend.service << EOF
[Unit]
Description=RAG Knowledge Base Backend
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/rag-kb/backend
EnvironmentFile=-/opt/rag-kb/backend/.env
Environment=SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/opt/rag-kb/backend/application-deploy.yml
# JVM 参数须在 -jar 之前
ExecStart=/usr/bin/java -Xmx1536m -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar /opt/rag-kb/backend/target/rag-knowledge-backend-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=rag-kb-backend

# 内存限制（4GB 服务器优化）
MemoryLimit=1800M

[Install]
WantedBy=multi-user.target
EOF
    
    systemctl daemon-reload
    systemctl enable rag-kb-backend
    systemctl start rag-kb-backend
    
    log_success "Systemd 服务配置完成"
}

# 安装 Nginx
install_nginx() {
    if command -v nginx &> /dev/null; then
        log_info "Nginx 已安装，跳过"
        return
    fi
    
    log_info "安装 Nginx..."
    if [ "$DIST_FAMILY" = "rpm" ]; then
        dnf install -y nginx
    else
        apt install -y nginx
    fi
    log_success "Nginx 安装完成"
}

# 配置 Nginx
configure_nginx() {
    log_info "配置 Nginx..."
    
    DOMAIN="$(resolve_public_host)"
    SERVER_NAME="$(nginx_server_name_for "$DOMAIN")"
    HTTP_P="$(deploy_http_port)"
    log_info "Nginx server_name: $SERVER_NAME，监听端口: $HTTP_P"

    if [ "${DEPLOY_NGINX_IPV6_ONLY:-0}" = "1" ]; then
        LISTEN_BLOCK="listen [::]:${HTTP_P} ipv6only=on;"
        log_info "Nginx 仅监听 IPv6 ${HTTP_P}（DEPLOY_NGINX_IPV6_ONLY=1）"
    else
        LISTEN_BLOCK="listen ${HTTP_P};
    listen [::]:${HTTP_P};"
    fi
    
    NGINX_SERVER_BLOCK=$(cat << EOF
server {
    $LISTEN_BLOCK
    server_name $SERVER_NAME;

    # 前端静态文件
    location / {
        root /opt/rag-kb/frontend/dist;
        try_files \$uri \$uri/ /index.html;

        # 缓存配置
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;

        proxy_connect_timeout 60s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }

    # SSE 流式响应代理
    location /api/v1/chat/stream {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding on;
    }

    # 文件上传大小限制
    client_max_body_size 25M;
}
EOF
)

    # 与其它站点/进程共存时默认保留 default；仅「经典单机 80 + 双栈」时自动删除 default
    local RM_DEF="${DEPLOY_REMOVE_NGINX_DEFAULT:-}"
    if [ -z "$RM_DEF" ]; then
        RM_DEF=1
        if [ "$HTTP_P" != "80" ]; then
            RM_DEF=0
        fi
        if [ "${DEPLOY_NGINX_IPV6_ONLY:-0}" = "1" ]; then
            RM_DEF=0
        fi
    fi

    if [ "$DIST_FAMILY" = "rpm" ]; then
        printf '%s\n' "$NGINX_SERVER_BLOCK" > /etc/nginx/conf.d/rag-kb.conf
        if [ "$RM_DEF" = "1" ]; then
            rm -f /etc/nginx/conf.d/default.conf 2>/dev/null || true
        else
            log_info "保留 conf.d/default.conf（DEPLOY_REMOVE_NGINX_DEFAULT=0 或 非 80 端口）"
        fi
    else
        mkdir -p /etc/nginx/sites-available /etc/nginx/sites-enabled
        printf '%s\n' "$NGINX_SERVER_BLOCK" > /etc/nginx/sites-available/rag-kb
        ln -sf /etc/nginx/sites-available/rag-kb /etc/nginx/sites-enabled/
        if [ "$RM_DEF" = "1" ]; then
            rm -f /etc/nginx/sites-enabled/default
        else
            log_info "保留 sites-enabled/default（与其它站点共存时请确保 nginx -t 无 server_name 冲突）"
        fi
    fi

    # 测试配置
    nginx -t
    
    # 重启 Nginx
    systemctl restart nginx
    systemctl enable nginx
    
    log_success "Nginx 配置完成"
}

# 配置 SSL（可选，非交互：先 export DEPLOY_SSL_DOMAIN=xxx DEPLOY_SSL_EMAIL=xxx）
configure_ssl() {
    if [ -z "${DEPLOY_SSL_DOMAIN:-}" ]; then
        log_info "跳过 SSL（绑定域名后可执行: certbot --nginx -d 你的域名）"
        return
    fi
    log_info "安装 Certbot..."
    if [ "$DIST_FAMILY" = "rpm" ]; then
        dnf install -y certbot python3-certbot-nginx
    else
        apt install -y certbot python3-certbot-nginx
    fi
    certbot --nginx -d "$DEPLOY_SSL_DOMAIN" --email "${DEPLOY_SSL_EMAIL:-admin@localhost}" --agree-tos --no-eff-email --non-interactive
    log_success "SSL 证书配置完成"
}

# 显示部署信息
show_deployment_info() {
    echo ""
    echo "=========================================="
    log_success "部署完成！"
    echo "=========================================="
    echo ""
    PUB="$(resolve_public_host)"
    HP="$(deploy_http_port)"
    if is_ipv6_addr "$PUB"; then
        if [ "$HP" = "80" ]; then
            echo "访问地址（IPv6）: http://[${PUB}]/"
        else
            echo "访问地址（IPv6）: http://[${PUB}]:${HP}/"
        fi
    else
        if [ "$HP" = "80" ]; then
            echo "访问地址: http://${PUB}/"
        else
            echo "访问地址: http://${PUB}:${HP}/"
        fi
    fi
    if [ "${DEPLOY_NGINX_IPV6_ONLY:-0}" = "1" ]; then
        echo "（当前 Nginx 仅绑定 IPv6；请在云控制台放行 IPv6 入站 TCP ${HP}；若用 HTTPS 另放行 443）"
    fi
    echo ""
    echo "管理员凭据: /opt/rag-kb/admin-credentials.txt"
    echo ""
    echo "服务管理命令:"
    echo "  查看后端状态: systemctl status rag-kb-backend"
    echo "  查看后端日志: journalctl -u rag-kb-backend -f"
    echo "  重启后端: systemctl restart rag-kb-backend"
    echo "  查看 Docker: cd /opt/rag-kb && docker compose ps  （或 docker-compose ps）"
    echo "  查看 Nginx: systemctl status nginx"
    echo ""
    echo "=========================================="
}

# 主函数
main() {
    echo ""
    echo "=========================================="
    echo "  RAG 知识问答系统 - 一键部署脚本"
    echo "=========================================="
    echo ""
    
    check_root
    check_system
    
    if [ "${DEPLOY_NONINTERACTIVE:-}" != "1" ]; then
        read -p "是否继续部署？(y/n): " CONTINUE
        if [ "$CONTINUE" != "y" ]; then
            log_info "部署已取消"
            exit 0
        fi
    fi

    # 开始部署
    update_system
    install_basic_tools
    create_swap  # 4GB 内存优化
    install_docker
    install_java
    install_maven
    install_nodejs
    if [ "${DEPLOY_SKIP_OLLAMA:-0}" = "1" ]; then
        log_info "已跳过 Ollama（DEPLOY_SKIP_OLLAMA=1）；请配置 DashScope 等云端模型见 backend/llm-local.properties"
    else
        install_ollama
    fi
    configure_firewall
    deploy_app
    build_backend
    build_frontend
    start_docker_services
    configure_systemd
    install_nginx
    configure_nginx
    configure_ssl
    
    show_deployment_info
}

# 运行主函数
main
