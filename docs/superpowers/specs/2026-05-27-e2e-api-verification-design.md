# Hify 全量接口 E2E 验证测试设计

> 生成日期：2026-05-27
> 类型：端到端（E2E）验证测试
> 工具：Chrome DevTools MCP + Hify 前端 + 后端

---

## Goal

在本地启动 Hify 前后端，通过 Chrome 浏览器手动操作前端 UI，覆盖全部 7 个模块、42 个接口，验证功能正确性。发现问题立即修复，修复后重新验证，直至全部通过。

## Scope

| 模块 | 接口数 | 测试内容 |
|------|--------|----------|
| 健康检查 | 1 | `/api/v1/health` 返回 MySQL/Redis/pgvector 全 UP |
| 模型管理 | 6 | Provider CRUD + 连通性测试 |
| Agent 管理 | 11 | Agent CRUD + 克隆 + 快捷修改 + 元数据查询 |
| 知识库 RAG | 10 | 知识库 CRUD + 文档上传/列表/分块/删除 |
| MCP 工具 | 7 | MCP Server CRUD + 连通性测试 + 工具调试 |
| 对话引擎 | 2 | 创建会话 + SSE 流式对话 |
| 简版工作流 | 6 | 工作流 CRUD + 执行 |

**排除项：** Actuator 端点（`8081`）仅做健康确认，不纳入 E2E 流程。

## Environment

### 依赖服务

| 服务 | 地址 | 状态 |
|------|------|------|
| MySQL | localhost:3306 / root / root | ✅ 已运行，hify 数据库就绪 |
| Redis | localhost:6379 / 123456 | ✅ 已运行，密码已修正 |
| pgvector | 8.136.34.168:5432 / postgres / 123456 | ⚠️ 远程，网络可达 |

### 应用启动

```bash
# 1. 后端（端口 8080/8081）
cd /e/hify && mvn spring-boot:run -pl hify-app -am -DskipTests

# 2. 前端（端口 5173，代理到 8080）
cd /e/hify/hify-web && npm run dev
```

### 浏览器入口

`http://localhost:5173`

## Test Sequence

按业务依赖链顺序测试，避免前序数据缺失导致后续失败。

```
健康检查 → 模型管理 → Agent 管理 → 知识库 RAG → MCP 工具 → 对话引擎 → 简版工作流
```

## Per-Module Test Cases

### 1. 健康检查

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1.1 | 浏览器访问 `http://localhost:8080/api/v1/health` | 返回 `{"code":200,"data":{"status":"UP","components":{"db":"UP","redis":"UP","pgvector":"UP"}}}` |
| 1.2 | 访问 `http://localhost:8081/actuator/health` | 返回包含 `components.pgvector` 的完整健康信息 |

### 2. 模型管理（Provider）

| 步骤 | 操作 | 期望 |
|------|------|------|
| 2.1 | 进入「模型管理」页面 | 列表加载正常，有分页 |
| 2.2 | 点击「新增供应商」，填写 OpenAI 信息（baseUrl, apiKey, model） | 创建成功，跳转列表页 |
| 2.3 | 列表页确认新 Provider 存在 | 数据正确显示 |
| 2.4 | 点击「连通性测试」 | 返回 success=true，延迟正常 |
| 2.5 | 点击「编辑」，修改名称 | 更新成功，列表刷新 |
| 2.6 | 点击「删除」 | 逻辑删除成功，列表不再显示 |

### 3. Agent 管理

**前置条件**：至少存在 1 个 Provider（步骤 2.2 创建）和 1 个知识库（步骤 4.2 创建）。

| 步骤 | 操作 | 期望 |
|------|------|------|
| 3.1 | 进入「Agent 管理」页面 | 列表加载正常 |
| 3.2 | 点击「新增 Agent」，选择 Provider、填写系统提示词 | 创建成功 |
| 3.3 | 查看 Agent 详情 | 显示 modelConfig、知识库、工具绑定信息 |
| 3.4 | 列表页「快捷修改」temperature | 更新成功，数值变化 |
| 3.5 | 列表页「快捷修改」maxContextTurns | 更新成功 |
| 3.6 | 进入详情页「修改工具绑定」 | 工具列表更新成功 |
| 3.7 | 点击「克隆」 | 生成新 Agent，配置与原 Agent 一致 |
| 3.8 | 删除克隆的 Agent | 逻辑删除成功 |
| 3.9 | 进入「新增 Agent」页面，观察「模型分组」下拉 | 按供应商分组显示（AgentMetaController） |
| 3.10 | 观察「工具列表」下拉 | 显示所有可用 MCP 工具（AgentMetaController） |

### 4. 知识库 RAG

| 步骤 | 操作 | 期望 |
|------|------|------|
| 4.1 | 进入「知识库」页面 | 列表加载正常 |
| 4.2 | 点击「新增知识库」 | 创建成功 |
| 4.3 | 进入知识库详情，上传 PDF 文档 | 上传成功，状态变为 PENDING/PROCESSING |
| 4.4 | 等待向量化完成，刷新列表 | 文档状态变为 DONE，chunk_count > 0 |
| 4.5 | 点击文档「查看分块」 | 显示 pgvector 中的分块列表 |
| 4.6 | 删除文档 | 逻辑删除，列表不再显示 |
| 4.7 | 删除知识库 | 级联删除文档和 chunk，列表不再显示 |

### 5. MCP 工具

| 步骤 | 操作 | 期望 |
|------|------|------|
| 5.1 | 进入「MCP 工具」页面 | 列表加载正常 |
| 5.2 | 点击「新增 MCP Server」，填写 transport 信息 | 创建成功 |
| 5.3 | 查看 MCP Server 详情 | 显示工具列表（如 search、fetch 等） |
| 5.4 | 点击「连通性测试」 | 返回 success=true |
| 5.5 | 点击「调试工具」，选择工具并填写参数执行 | 返回正确结果 |
| 5.6 | 编辑 MCP Server 信息 | 更新成功 |
| 5.7 | 删除 MCP Server | 逻辑删除成功 |

### 6. 对话引擎

**前置条件**：至少存在 1 个 Agent（步骤 3.2 创建）。

| 步骤 | 操作 | 期望 |
|------|------|------|
| 6.1 | 进入「对话」页面，选择 Agent | 创建会话成功，显示聊天窗口 |
| 6.2 | 输入消息并发送 | SSE 流式响应，逐字显示 |
| 6.3 | 观察浏览器 Network 面板 | `/api/v1/chat/sessions/{id}/messages` 返回 `text/event-stream` |
| 6.4 | 发送多条消息，验证上下文记忆 | 多轮对话正常，Agent 能引用前文 |

### 7. 简版工作流

| 步骤 | 操作 | 期望 |
|------|------|------|
| 7.1 | 进入「工作流」页面 | 列表加载正常 |
| 7.2 | 点击「新增工作流」，拖拽节点（开始→LLM→结束）并连接 | 保存成功 |
| 7.3 | 查看工作流详情 | 显示完整节点和边信息 |
| 7.4 | 点击「执行工作流」，输入用户消息 | 返回执行结果，各节点状态正确 |
| 7.5 | 查看工作流执行记录 | 显示 WorkflowRun + NodeRun 详情 |
| 7.6 | 编辑工作流节点配置 | 更新成功 |
| 7.7 | 删除工作流 | 逻辑删除成功 |

## Success Criteria

- [ ] 全部 42 个接口至少被调用一次
- [ ] 所有写操作（POST/PUT/PATCH/DELETE）返回成功
- [ ] 所有读操作（GET）返回预期数据结构
- [ ] SSE 流式对话正常，无断开
- [ ] 文件上传正常，向量化流程无报错
- [ ] 健康检查中 db/redis/pgvector 全为 UP
- [ ] 浏览器 Console 无 Error 级别日志

## Issue Handling

发现问题时按以下流程处理：

1. **记录**：截图 + Network 请求/响应 + Console 报错 → 记录到 `docs/TEST_ISSUES.md`
2. **定位**：根据错误信息定位到后端 Controller/Service 或前端代码
3. **修复**：按 CLAUDE.md 规范修改代码（命名、包结构、异常处理等）
4. **重启**：后端热重启或全量重启
5. **重测**：重新执行失败的测试步骤
6. **闭环**：确认修复后更新 `docs/TEST_ISSUES.md` 标记为已解决

## Placeholder Scan

- [x] 无 TBD/TODO
- [x] 无 "implement later"
- [x] 所有测试步骤包含具体操作和期望结果
- [x] 所有前置条件明确

## File Changes (测试过程可能产生)

| 文件 | 预期变更 |
|------|----------|
| `hify-app/src/main/resources/application.yml` | Redis 密码已修正（已完成） |
| 各模块 Controller/Service | 发现 bug 时修复 |
| `docs/TEST_ISSUES.md` | 新增测试问题记录 |
| `docs/API_INTERFACE_CATALOG.md` | 如有接口变更则同步更新 |
