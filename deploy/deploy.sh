#!/bin/bash
# ============================================
# Hify 一键部署脚本
# 服务器: root@8.136.34.168
# ============================================

set -e

# 配置
SERVER_IP="8.136.34.168"
SERVER_USER="root"
SERVER_PASSWORD="Ecs123456"
REMOTE_DIR="/opt/hify"
LOCAL_PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "========================================"
echo "  Hify 部署脚本"
echo "  目标服务器: ${SERVER_USER}@${SERVER_IP}"
echo "========================================"

# 检查本地环境
if ! command -v sshpass &> /dev/null; then
    echo "错误: 未安装 sshpass，请先安装"
    echo "  Ubuntu/Debian: apt-get install -y sshpass"
    echo "  macOS: brew install hudochenkov/sshpass/sshpass"
    exit 1
fi

if ! command -v rsync &> /dev/null; then
    echo "错误: 未安装 rsync"
    exit 1
fi

# 步骤 1：编译后端
echo ""
echo "[1/6] 编译后端..."
cd "${LOCAL_PROJECT_DIR}"
mvn clean package -pl hify-app -am -DskipTests -B -q
echo "  ✓ 后端编译完成"

# 步骤 2：构建前端
echo ""
echo "[2/6] 构建前端..."
cd "${LOCAL_PROJECT_DIR}/hify-web"
npm ci --prefer-offline --no-audit
npm run build
echo "  ✓ 前端构建完成"

# 步骤 3：准备部署包
echo ""
echo "[3/6] 准备部署文件..."
DEPLOY_PACKAGE="${LOCAL_PROJECT_DIR}/deploy-package"
rm -rf "${DEPLOY_PACKAGE}"
mkdir -p "${DEPLOY_PACKAGE}"

# 复制必要文件
cp -r "${LOCAL_PROJECT_DIR}/deploy" "${DEPLOY_PACKAGE}/"
cp -r "${LOCAL_PROJECT_DIR}/hify-web" "${DEPLOY_PACKAGE}/"
cp -r "${LOCAL_PROJECT_DIR}/pom.xml" "${DEPLOY_PACKAGE}/"
cp -r "${LOCAL_PROJECT_DIR}/hify-app" "${DEPLOY_PACKAGE}/"
cp -r "${LOCAL_PROJECT_DIR}/hify-common" "${DEPLOY_PACKAGE}/"
cp -r "${LOCAL_PROJECT_DIR}/hify-provider" "${DEPLOY_PACKAGE}/"
cp -r "${LOCAL_PROJECT_DIR}/hify-agent" "${DEPLOY_PACKAGE}/"
cp -r "${LOCAL_PROJECT_DIR}/hify-chat" "${DEPLOY_PACKAGE}/"
cp -r "${LOCAL_PROJECT_DIR}/hify-knowledge" "${DEPLOY_PACKAGE}/"
cp -r "${LOCAL_PROJECT_DIR}/hify-workflow" "${DEPLOY_PACKAGE}/"
cp -r "${LOCAL_PROJECT_DIR}/hify-mcp" "${DEPLOY_PACKAGE}/"

echo "  ✓ 部署包准备完成"

# 步骤 4：上传到服务器
echo ""
echo "[4/6] 上传到服务器 ${SERVER_IP}..."
sshpass -p "${SERVER_PASSWORD}" ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_IP}" "mkdir -p ${REMOTE_DIR}"

# 使用 rsync 增量上传
sshpass -p "${SERVER_PASSWORD}" rsync -avz --delete \
    --exclude='node_modules' \
    --exclude='target' \
    --exclude='.git' \
    --exclude='.claude' \
    --exclude='*.log' \
    "${DEPLOY_PACKAGE}/" "${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/"

echo "  ✓ 上传完成"

# 步骤 5：服务器端构建和启动
echo ""
echo "[5/6] 服务器端构建 Docker 镜像..."
REMOTE_SCRIPT=$(cat << 'EOF'
cd /opt/hify/deploy

# 检查 .env 文件是否存在
if [ ! -f .env ]; then
    echo "错误: .env 文件不存在，请从 .env.template 复制并配置"
    echo "  cp .env.template .env"
    echo "  vi .env"
    exit 1
fi

# 停止旧服务
echo "  停止旧服务..."
docker compose down 2>/dev/null || true

# 构建并启动
echo "  构建并启动新服务..."
docker compose up -d --build

echo "  ✓ 服务启动完成"
EOF
)

sshpass -p "${SERVER_PASSWORD}" ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_IP}" "${REMOTE_SCRIPT}"

# 步骤 6：验证
echo ""
echo "[6/6] 验证服务状态..."
sleep 5

REMOTE_VERIFY=$(cat << 'EOF'
echo ""
echo "Docker 容器状态:"
docker compose -f /opt/hify/deploy/docker-compose.yml ps

echo ""
echo "后端健康检查:"
curl -s http://localhost:8080/actuator/health | head -20

echo ""
echo "前端访问: http://$(curl -s ifconfig.me 2>/dev/null || echo 'your-server-ip')"
EOF
)

sshpass -p "${SERVER_PASSWORD}" ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_IP}" "${REMOTE_VERIFY}"

echo ""
echo "========================================"
echo "  部署完成!"
echo "  前端: http://${SERVER_IP}"
echo "  后端 API: http://${SERVER_IP}:8080"
echo "  健康检查: http://${SERVER_IP}:8080/actuator/health"
echo "========================================"

# 清理本地临时文件
rm -rf "${DEPLOY_PACKAGE}"
