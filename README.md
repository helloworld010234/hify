# Hify

<p align="center">
  <strong>面向内部团队的轻量级 AI Agent 平台</strong><br/>
  基于 <a href="https://dify.ai" target="_blank">Dify</a> 思路设计的简化版本地部署方案，让 20-50 人团队快速拥有可定制的 AI 助手、知识库问答与自动化工作流能力。
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue?logo=openjdk" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen?logo=spring" alt="Spring Boot 3.3"/>
  <img src="https://img.shields.io/badge/Vue-3.4-42b883?logo=vue.js" alt="Vue 3.4"/>
  <img src="https://img.shields.io/badge/Element--Plus-2.5-409eff" alt="Element Plus"/>
  <img src="https://img.shields.io/badge/MySQL-8.0-orange?logo=mysql" alt="MySQL 8"/>
  <img src="https://img.shields.io/badge/pgvector-PostgreSQL-336791?logo=postgresql" alt="pgvector"/>
  <img src="https://img.shields.io/badge/Maven-3.x-C71A36?logo=apachemaven" alt="Maven"/>
</p>

---

## 目录

- [项目简介](#项目简介)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [文档与资源](#文档与资源)
- [参与贡献](#参与贡献)

---

## 项目简介

Hify 是一款为中小型内部团队打造的 **AI Agent 开发与运营平台**。它以“够用、好管、可本地部署”为目标，提供模型接入、Agent 配置、知识库 RAG、对话引擎、MCP 工具调用和简版工作流等核心能力，帮助团队在一套系统内完成从提示词调试到业务落地的完整闭环。

**适用场景**

- 内部知识库问答助手（文档上传 → 自动向量化 → 智能检索回答）
- 面向业务角色的专属 Agent（客服、HR、开发、运营等）
- 轻量级 LLM 自动化工作流（审批、分类、摘要、工具联动）
- 需要数据不出域的私有化 AI 平台

---

## 核心功能

### 🤖 Agent 管理

- 创建并管理多个业务 Agent，每个 Agent 拥有独立的系统提示词、模型、上下文轮数和温度参数。
- 快速克隆 Agent，便于基于已有配置迭代新场景。
- 绑定一个或多个知识库，让 Agent 基于私有内容回答问题。
- 绑定 MCP 工具，扩展 Agent 的实时查询与操作能力。

### 💬 对话引擎

- 支持多轮会话管理，历史消息自动保存与上下文注入。
- 采用 **SSE 流式响应**，让大模型回答像打字一样实时呈现。
- 支持上下文轮数与温度参数的快捷调整，灵活控制回答风格与记忆长度。

### 📚 知识库 RAG

- 文档上传后自动切分、向量化并写入 **pgvector**。
- 基于余弦相似度的向量召回，将最相关的文档片段注入 LLM 上下文。
- 支持查看文档分块结果，便于调试召回质量。

### 🔧 MCP 工具接入

- 注册并管理外部 MCP Server（支持 Stdio / SSE 等协议）。
- 自动发现 MCP Server 提供的工具列表，并供 Agent 或工作流调用。
- 提供连通性测试与在线调试能力，降低工具接入门槛。

### 🧠 模型管理

- 统一配置 OpenAI、Claude、Gemini、Ollama 等 LLM 供应商。
- 单个供应商可维护多个模型与独立的 API 参数。
- 一键连通性测试，快速验证模型可用性。

### 🔄 简版工作流

- 可视化编排顺序节点：开始 → LLM → 条件分支 → 工具调用 → 结束。
- 支持在工作流中调用 Agent、知识库与 MCP 工具。
- 工作流可保存、复用、运行并查看执行结果。

### 🛡️ 稳定性与可观测性

- LLM 调用具备超时保护、熔断器与 Fallback 自动降级。
- 提供 `/api/v1/health` 业务健康检查，聚合 MySQL / Redis / pgvector 状态。
- 暴露 Prometheus 指标端点，方便接入 Grafana 监控。

---

## 技术栈

### 后端

| 技术 | 说明 |
|------|------|
| **Spring Boot 3.3** | 主框架，模块化单体架构 |
| **MyBatis-Plus** | ORM 与分页增强 |
| **MySQL 8** | 主数据存储 |
| **Redis** | 缓存 / Session / 限流 |
| **PostgreSQL + pgvector** | 向量存储与相似度检索 |
| **OkHttp** | LLM HTTP 客户端 |
| **Resilience4j** | 熔断、限流与重试 |
| **Lombok / SLF4J / Logback** | 编码与日志 |

### 前端

| 技术 | 说明 |
|------|------|
| **Vue 3 + TypeScript** | 响应式 UI 框架 |
| **Vite** | 构建工具 |
| **Element Plus** | 组件库 |
| **Pinia** | 状态管理 |
| **Axios** | HTTP 请求 |
| **Marked + DOMPurify** | Markdown 渲染与 XSS 过滤 |

---

## 系统架构

```text
用户浏览器
    │
    ▼
Ingress Nginx（L7 负载均衡 + SSL 终止 + SSE 长连接支持）
    │
    ├──▶ hify-web（Vue 3 SPA，Nginx 静态资源服务）
    │
    └──▶ hify-backend（Spring Boot 模块化单体）
              │
              ├──▶ MySQL 8.x（主数据存储）
              ├──▶ Redis（缓存 / Session / 限流）
              └──▶ PostgreSQL + pgvector（向量存储）
```

更详细的架构说明与部署配置请查看 [docs/architecture.svg](docs/architecture.svg) 和 [CLAUDE.md](CLAUDE.md)。

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8.x
- Redis 7.x
- PostgreSQL 14+ with pgvector 扩展

### 1. 拉取代码

```bash
git clone https://github.com/helloworld010234/hify.git
cd hify
```

### 2. 初始化数据库

按 `deploy/` 目录下的脚本创建 MySQL 与 PostgreSQL 数据库，并启用 pgvector 扩展。

### 3. 启动后端

```bash
mvn spring-boot:run -pl hify-app
```

后端默认运行在 `http://localhost:8080`，Actuator 与 Prometheus 端点位于 `http://localhost:8081`。

### 4. 启动前端

```bash
cd hify-web
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`。

### 5. 验证服务

```bash
curl http://localhost:8080/api/v1/health
```

返回 `status: UP` 即表示服务正常。

---

## 项目结构

```text
hify
├── hify-app/          # 应用入口，聚合所有模块
├── hify-common/       # 公共配置、异常、工具类、统一响应封装
├── hify-provider/     # LLM 模型与供应商管理
├── hify-agent/        # Agent 配置与元数据
├── hify-chat/         # 多轮对话引擎（SSE 流式响应）
├── hify-knowledge/    # 知识库、文档上传、向量检索 RAG
├── hify-workflow/     # 简版工作流编排与执行
├── hify-mcp/          # MCP Server 注册与工具调用
├── hify-web/          # Vue 3 前端工程
├── deploy/            # Docker、Nginx、Grafana 等部署资源
└── docs/              # 架构图、接口目录、测试规范等文档
```

跨模块调用规则：只通过目标模块 `api/` 包下的接口调用，禁止直接引用其他模块的 `domain/` 或 `infra/` 类。

---

## 文档与资源

| 文档 | 说明 |
|------|------|
| [CLAUDE.md](CLAUDE.md) | 项目开发规范、架构约定与性能要求 |
| [AGENTS.md](AGENTS.md) | AI Coding Agent 开发指南与安全要求 |
| [docs/API_INTERFACE_CATALOG.md](docs/API_INTERFACE_CATALOG.md) | 后端 REST API 接口目录 |
| [docs/testing.md](docs/testing.md) | 测试规范与重心 |
| [docs/architecture.svg](docs/architecture.svg) | 系统架构图 |

---

## 参与贡献

1. Fork 本仓库并创建功能分支。
2. 遵循 [Conventional Commits](https://www.conventionalcommits.org/) 编写提交信息。
3. 推送前请运行安全审计脚本：

```bash
./scripts/security-audit.sh
```

4. 提交 Pull Request，并说明改动范围与测试情况。

---

<p align="center">
  Hify — 让内部团队快速拥有属于自己的 AI Agent 平台。
</p>
