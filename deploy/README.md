# Hify Docker 部署指南

> 服务器: `root@8.136.34.168`

## 前置要求

服务器上已安装：
- Docker Engine 20.10+
- Docker Compose 2.0+
- MySQL 8.x（外部，已运行）
- Redis（外部，已运行）
- PostgreSQL + pgvector（外部，已运行）

## 快速部署（推荐）

### 1. 登录服务器

```bash
ssh root@8.136.34.168
# 密码: Ecs123456
```

### 2. 上传项目代码

从本地开发机执行：

```bash
# 方式 1：使用 scp 上传（在项目根目录执行）
scp -r deploy/ root@8.136.34.168:/opt/hify/
scp -r hify-web/ root@8.136.34.168:/opt/hify/
scp pom.xml root@8.136.34.168:/opt/hify/
scp -r hify-app/ hify-common/ hify-provider/ hify-agent/ hify-chat/ hify-knowledge/ hify-workflow/ hify-mcp/ root@8.136.34.168:/opt/hify/

# 方式 2：使用 rsync（更快，支持增量）
rsync -avz --exclude='node_modules' --exclude='target' --exclude='.git' \
  ./ root@8.136.34.168:/opt/hify/
```

### 3. 服务器端配置

```bash
ssh root@8.136.34.168

cd /opt/hify/deploy

# 复制环境变量模板并修改
cp .env.template .env
vi .env
```

**必须修改的变量：**

```env
# MySQL（服务器上的真实配置）
MYSQL_HOST=8.136.34.168
MYSQL_PASSWORD=你的MySQL密码

# Redis
REDIS_HOST=8.136.34.168
REDIS_PASSWORD=你的Redis密码

# PostgreSQL / pgvector
PGVECTOR_HOST=8.136.34.168
PGVECTOR_PASSWORD=你的PG密码
```

### 4. 构建并启动

```bash
cd /opt/hify/deploy

# 构建并后台启动
docker compose up -d --build

# 查看日志
docker compose logs -f backend   # 后端日志
docker compose logs -f frontend  # 前端日志

# 查看状态
docker compose ps
```

### 5. 验证

```bash
# 后端健康检查
curl http://localhost:8080/actuator/health

# 前端访问（浏览器打开）
http://8.136.34.168
```

## 常用命令

```bash
# 停止服务
docker compose down

# 重启服务
docker compose restart

# 重新构建（代码更新后）
docker compose up -d --build

# 查看容器日志
docker compose logs -f [backend|frontend]

# 进入容器调试
docker exec -it hify-backend sh
docker exec -it hify-frontend sh

# 清理无用镜像
docker image prune -f
```

## 文件说明

| 文件 | 说明 |
|------|------|
| `Dockerfile.backend` | 后端多阶段构建 Dockerfile |
| `Dockerfile.frontend` | 前端 Node + Nginx Dockerfile |
| `nginx.conf` | Nginx 配置（SSE 支持、反向代理） |
| `docker-compose.yml` | 服务编排 |
| `.env.template` | 环境变量模板 |
| `deploy.sh` | 一键部署脚本（本地执行） |

## 注意事项

1. **外部服务 IP**：MySQL/Redis/PG 必须用真实 IP（`8.136.34.168`），不能用 `localhost`——容器内的 localhost 是容器自身
2. **密码安全**：`.env` 文件包含敏感信息，**不要提交到 git**，已在 `.gitignore` 中排除
3. **上传目录**：默认挂载 `./uploads` 到容器，确保宿主机目录有写权限
4. **SSE 支持**：Nginx 配置已关闭 `proxy_buffering`，确保流式响应正常工作
5. **健康检查**：后端使用 Spring Boot Actuator (`/actuator/health`)，前端等后端健康后才启动
