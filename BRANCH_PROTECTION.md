# 分支保护规则

> 本文档记录 Hify 项目的分支保护策略。当前项目为本地 Git 仓库，以下规则需在创建远程仓库（GitHub / GitLab / Gitea）后，在 Web 管理界面中手动配置。

---

## master 分支（生产分支）

**保护级别：严格**

| 规则 | 配置 |
|------|------|
| 禁止直接 Push | ✅ 开启 |
| 禁止 Force Push | ✅ 开启 |
| 合并方式 | 仅允许 **Pull Request** |
| 必需审查 | 至少 **1 个 Approval** |
| 必需状态检查 | CI 构建通过、集成测试通过 |
| 必需对话解决 | 所有 Review 评论必须标记为已解决 |
| 允许合并的人群 | Maintainers |

**合并策略：**
- 使用 **Squash and Merge**（保持 master 历史简洁）
- 合并标题必须遵循 Conventional Commits 格式
- 合并后自动删除源分支

---

## develop 分支（集成分支）

**保护级别：中等**

| 规则 | 配置 |
|------|------|
| 禁止直接 Push | ⚠️ 建议开启（紧急修复除外） |
| 禁止 Force Push | ✅ 开启 |
| 合并方式 | 建议通过 Pull Request |
| 必需审查 | 建议至少 1 个 Approval（单人开发可自检） |
| 必需状态检查 | CI 构建通过 |

---

## feature/* 分支

**保护级别：无**

- 开发者自由推送
- 开发完成后通过 PR 合并到 `develop`
- 合并后由维护者删除

---

## release/* 分支

**保护级别：严格（等同于 master）**

- 从 `develop` 切出后锁定，只允许 bug fix 级别的 commit
- 禁止新增功能
- 稳定后分别 PR 到 `master`（发版）和 `develop`（同步修复）

---

## hotfix/* 分支

**保护级别：中等**

- 从 `master` 切出
- 修复完成后必须同时合并到 `master` 和 `develop`
- 合并到 `master` 时打补丁版本标签（如 `v0.1.1`）

---

## 各平台配置参考

### GitHub

路径：`Settings` → `Branches` → `Add rule`

- Branch name pattern: `master`
- ☑️ Require a pull request before merging
  - ☑️ Require approvals: `1`
  - ☑️ Dismiss stale PR approvals when new commits are pushed
- ☑️ Require status checks to pass before merging
  - Status checks: `ci/build`, `ci/test`
- ☑️ Restrict pushes that create files larger than 100 MB
- ☑️ Block force pushes

### GitLab

路径：`Settings` → `Repository` → `Protected branches`

- Branch: `master`
- Allowed to merge: `Maintainers`
- Allowed to push: `No one`
- ☑️ Require approval from code owners

### Gitea

路径：`Settings` → `Branches` → `Add Branch Protection Rule`

- Branch name pattern: `master`
- ☑️ Enable branch protection
- ☑️ Disable force push
- ☑️ Require pull request
  - ☑️ Require approval
  - Approval count: `1`
