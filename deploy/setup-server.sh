#!/bin/bash
# 服务器端一键部署脚本
# 在阿里云服务器上执行

set -e

echo "========================================"
echo "  Hify 服务器端部署"
echo "========================================"

# 1. 创建目录
echo "[1/5] 创建工作目录..."
mkdir -p /opt/hify
cd /opt/hify

# 2. 获取代码（如果没有 git，先安装）
echo "[2/5] 获取代码..."
if ! command -v git &> /dev/null; then
    yum install -y git 2>/dev/null || apt-get update && apt-get install -y git
fi

# 如果有 git 仓库则拉取，否则需要手动上传代码
if [ -d ".git" ]; then
    git pull origin master
else
    echo "  请将项目代码上传到 /opt/hify/"
    echo "  或者执行: git clone <你的仓库地址> /opt/hify/"
    exit 1
fi

# 3. 安装 Docker（如未安装）
echo "[3/5] 检查 Docker..."
if ! command -v docker &> /dev/null; then
    echo "  安装 Docker..."
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker
    systemctl start docker
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "  安装 Docker Compose..."
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
fi

# 4. 配置环境变量
echo "[4/5] 配置环境变量..."
cd /opt/hify/deploy
if [ ! -f .env ]; then
    cp .env.template .env
    echo "  请编辑 .env 文件，填入数据库密码"
    echo "  执行: vi /opt/hify/deploy/.env"
    exit 1
fi

# 5. 构建并启动
echo "[5/5] 构建并启动服务..."
docker compose down 2>/dev/null || true
docker compose up -d --build

echo ""
echo "========================================"
echo "  部署完成!"
echo ""
echo "  查看状态: docker compose ps"
echo "  后端日志: docker compose logs -f backend"
echo "  前端日志: docker compose logs -f frontend"
echo "  健康检查: curl http://localhost:8080/actuator/health"
echo ""
echo "  访问地址:"
echo "    前端: http://8.136.34.168"
echo "    API:  http://8.136.34.168:8080"
echo "========================================"
