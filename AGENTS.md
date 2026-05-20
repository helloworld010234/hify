# Hify Agent 开发指南

> 本文档面向 AI Coding Agent（如 Claude、Kimi、Cursor 等），包含项目背景、编码规范、安全要求和常用工具链说明。

---

## 项目背景

Hify 是一个简化版内部 AI Agent 平台，基于 Dify 思路设计，面向 20-50 人内部团队本地部署使用。

- **团队规模**：1 人开发
- **技术栈**：Spring Boot 3.2 + MyBatis-Plus + Vue 3 + MySQL 8 + Redis + pgvector
- **架构模式**：模块化单体（Modular Monolith）

详细规范见根目录 `CLAUDE.md`。

---

## 安全要求

### 推送前安全审计（强制）

**每次 `git push` 到远程仓库前，必须经过安全审计。**

#### 安装方式

```bash
./scripts/install-security-hook.sh
```

此命令将注册 `pre-push` hook，此后每次推送都会自动扫描敏感信息。

#### 审计规则

| 级别 | 触发条件 | 处理方式 |
|------|---------|---------|
| 🔴 高危 | 真实 API Key（`sk-...` 40+位）、AWS Key、私钥、Bearer Token、`.env` 文件、证书文件 | **阻断推送** |
| 🟡 中危 | 配置文件中的硬编码密码（非占位符）、测试文件中的疑似真实 Key | 警告，需人工确认 |
| 🟢 低危 | IDE 本地配置文件 | 提示建议 |

#### 绕过机制（仅限紧急情况）

```bash
git push --no-verify
```

> ⚠️ 使用 `--no-verify` 必须在 commit message 或 PR 描述中说明绕过原因。

#### 白名单机制

以下情况不会触发告警（已内置）：
- 测试文件中的 `sk-test`、`sk-dummy`、`sk-mock` 等假数据
- 配置文件中的占位符如 `your_redis_password`、`root`（开发环境默认值）
- 空密码 `password:`（H2 测试数据库）

---

## 模块边界（跨模块调用规则）

- **只能**通过目标模块的 `api/` 接口调用
- 禁止直接 `import` 其他模块的 `domain/` 或 `infra/` 类
- 跨模块传递使用 `api/` 包下定义的 DTO，不传递 PO 或领域对象

---

## 常用命令

```bash
# 编译
mvn compile -pl hify-chat -am

# 全量测试
mvn test -q

# 启动后端
mvn spring-boot:run -pl hify-app

# 启动前端
cd hify-web && npm run dev

# 安全审计（手动触发）
./scripts/security-audit.sh
```

---

## 新增模块交付 checklist

1. [ ] 建表脚本（MySQL + H2）
2. [ ] Entity、Mapper、DTO、Controller
3. [ ] Service 接口提取到 `api/` 包
4. [ ] 单元测试 + 集成测试（H2）
5. [ ] 安全审计通过（`./scripts/security-audit.sh`）
6. [ ] Conventional Commits 格式的 commit message
