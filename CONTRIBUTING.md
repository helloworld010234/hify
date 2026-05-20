# Hify 项目贡献规范

## 分支模型

本项目采用**简化版 Git Flow**：

- `master`：生产分支，永远可部署
- `develop`：日常集成分支
- `feature/*`：功能开发分支（从 develop 切出）
- `release/*`：发版分支（从 develop 切出）
- `hotfix/*`：线上热修分支（从 master 切出）

## 提交信息规范（Conventional Commits）

### 格式

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### Type 枚举

| Type | 含义 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(chat): add SSE streaming endpoint` |
| `fix` | Bug 修复 | `fix(provider): handle null apiKey gracefully` |
| `docs` | 文档更新 | `docs: update API documentation` |
| `style` | 代码格式（不影响功能） | `style(web): fix indentation` |
| `refactor` | 重构 | `refactor(agent): extract validation logic` |
| `test` | 测试相关 | `test(provider): add cache consistency test` |
| `chore` | 构建/工具/依赖 | `chore: upgrade Spring Boot to 3.2.6` |

### Scope 枚举（当前项目）

| Scope | 对应模块 |
|-------|----------|
| `provider` | hify-provider（供应商管理） |
| `agent` | hify-agent（Agent 配置） |
| `chat` | hify-chat（对话引擎） |
| `knowledge` | hify-knowledge（知识库 RAG） |
| `workflow` | hify-workflow（简版工作流） |
| `mcp` | hify-mcp（MCP 工具接入） |
| `common` | hify-common（公共模块） |
| `web` | hify-web（前端） |
| `infra` | 构建、部署、CI/CD 配置 |

### 提交示例

```bash
# 功能开发
feat(chat): implement conversation persistence with optimistic locking

- Add ConversationMapper and MessageMapper
- Use cursor pagination for message list
- Add composite index on (conversation_id, deleted, created_at)

Fixes #15

# Bug 修复
fix(provider): correct Ollama health check endpoint path

The /api/tags endpoint returns 404 on newer Ollama versions.
Switch to /api/version for connectivity test.

# 重构
refactor(agent): replace mock tool options with real MCP query

BREAKING CHANGE: AgentCreateRequest.toolIds now requires valid MCP tool IDs
```

## 开发流程

1. 从 `develop` 切出功能分支：`git checkout -b feature/xxx develop`
2. 开发完成后，本地执行 `mvn test` 确保全绿
3. 整理 commit 历史（必要时 `git rebase -i develop`）
4. 推送到远程并创建 Pull Request 到 `develop`
5. PR 通过审查（Review）后，使用 **Squash and Merge** 合并
6. 删除已合并的功能分支

## 代码审查清单（Self-Review）

即使单人开发，提 PR 前也请自检：

- [ ] 代码符合项目编码规范（CLAUDE.md）
- [ ] 新增/修改的方法有对应的单元测试或集成测试
- [ ] 跨模块调用仅通过 `api/` 接口，不直接依赖 `domain/` 或 `infra/`
- [ ] 数据库变更包含 schema 文件（`hify-xxx/src/main/resources/db/*.sql`）
- [ ] API 变更同步更新前端调用代码
- [ ] 无敏感信息泄露（密码、密钥未明文提交）
