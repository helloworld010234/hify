# Hify 全量接口 E2E 验证测试实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在本地启动 Hify 前后端，通过 Chrome DevTools MCP 浏览器手动操作前端 UI，覆盖全部 7 个模块 42 个接口，验证功能正确性，发现问题立即修复。

**Architecture:** 按业务依赖链顺序测试（健康检查 → Provider → Agent → 知识库 → MCP → 对话 → 工作流），使用 Chrome MCP 打开浏览器访问前端页面，通过 Network 面板和页面交互验证每个接口。问题记录到 `docs/TEST_ISSUES.md`，修复后重新验证。

**Tech Stack:** Spring Boot 3.3.5, Vue 3 + Vite + Element Plus, Chrome DevTools MCP, MySQL 8, Redis, pgvector

---

## File Structure

| File | Responsibility |
|------|----------------|
| `docs/TEST_ISSUES.md` | 测试过程中发现的问题记录（新建） |
| `docs/API_INTERFACE_CATALOG.md` | 接口目录参考（只读） |
| `hify-app/src/main/resources/application.yml` | 后端配置（Redis 密码已修正） |

---

### Task 0: 环境启动与基线检查

**Files:**
- Read: `hify-app/src/main/resources/application.yml`
- Create: `docs/TEST_ISSUES.md`

- [ ] **Step 1: 启动后端服务**

Run:
```bash
cd /e/hify && mvn spring-boot:run -pl hify-app -am -DskipTests -B -q
```

Wait for `Started HifyApplication` in output. Expected startup time: 30-60s.

- [ ] **Step 2: 验证后端健康检查**

Run (in new terminal):
```bash
curl -s http://localhost:8080/api/v1/health | python3 -m json.tool
```

Expected:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "status": "UP",
        "components": {
            "db": {"status": "UP"},
            "redis": {"status": "UP"},
            "pgvector": {"status": "UP"}
        }
    }
}
```

If `pgvector` is DOWN, check network to `8.136.34.168:5432`. If `db` or `redis` is DOWN, stop and fix before proceeding.

- [ ] **Step 3: 启动前端 dev server**

Run (in new terminal):
```bash
cd /e/hify/hify-web && npm run dev
```

Wait for `Local:   http://localhost:5173/` in output.

- [ ] **Step 4: 初始化问题记录文件**

Create `docs/TEST_ISSUES.md`:
```markdown
# Hify E2E 测试问题记录

> 测试日期：2026-05-27
> 测试人：Agent

## 问题列表

| 序号 | 模块 | 接口/功能 | 问题描述 | 状态 | 修复 commit |
|------|------|----------|----------|------|------------|

## 状态说明
- open: 待修复
- fixed: 已修复
- verified: 修复后已验证
```

---

### Task 1: 健康检查端点验证

**Files:**
- Test via browser: `http://localhost:8080/api/v1/health`
- Test via browser: `http://localhost:8081/actuator/health`

- [ ] **Step 1: Chrome MCP 打开健康检查页面**

Use `mcp__chrome-devtools__new_page` with url `http://localhost:8080/api/v1/health`.

Expected: JSON response with `status: "UP"`, components `db`, `redis`, `pgvector` all `UP`.

- [ ] **Step 2: 打开 Actuator 健康端点**

Use `mcp__chrome-devtools__new_page` with url `http://localhost:8081/actuator/health`.

Expected: JSON with `components.db`, `components.redis`, `components.pgvector`.

- [ ] **Step 3: 记录检查结果**

If any component is DOWN, add to `docs/TEST_ISSUES.md` and STOP testing until fixed.

---

### Task 2: 模型管理（Provider）测试

**前置条件：** DeepSeek Provider (id=3) 已在数据库中存在且 healthy。OpenAI Provider (id=1) 因无有效 API Key，其 unhealthy 状态直接忽略。

**Files:**
- Test via browser UI: `http://localhost:5173`

- [ ] **Step 1: 打开前端首页**

Use `mcp__chrome-devtools__new_page` with url `http://localhost:5173`.

Wait for page load. Take snapshot to confirm Element Plus layout renders.

- [ ] **Step 2: 进入「模型管理」页面**

Navigate to Provider list page via sidebar menu.

Expected: Table loads with 2 rows (OpenAI + DeepSeek). DeepSeek shows `healthy` tag.

- [ ] **Step 3: 查看 DeepSeek Provider 详情**

Click "详情" on DeepSeek row.

Expected: Detail page shows `baseUrl=https://api.deepseek.com`, model configs, health status.

- [ ] **Step 4: 测试 DeepSeek 连通性**

Click "连通性测试" button on DeepSeek row.

Expected: Toast shows "连通性测试成功" or similar success message. Network panel shows `POST /api/v1/providers/3/test-connection` returning `success=true`.

- [ ] **Step 5: 编辑 DeepSeek 名称**

Click "编辑" on DeepSeek row. Change name to "DeepSeek Test". Save.

Expected: Update success, list refreshes, name changed.

- [ ] **Step 6: 恢复 DeepSeek 名称**

Edit again, change name back to "DeepSeek Updated". Save.

Expected: Name restored.

- [ ] **Step 7: 记录 Provider 模块问题**

If any step fails, record in `docs/TEST_ISSUES.md` with Network screenshot and response body.

---

### Task 3: Agent 管理测试

**前置条件：** Task 2 通过，至少存在 1 个 healthy Provider（DeepSeek id=3）。

**Files:**
- Test via browser UI: `http://localhost:5173`

- [ ] **Step 1: 进入「Agent 管理」页面**

Navigate via sidebar. Expected: Agent list loads (6 existing rows from database).

- [ ] **Step 2: 创建新 Agent**

Click "新增 Agent". Fill:
- Name: `E2E-Test-Agent`
- System Prompt: `你是一个测试助手`
- Provider: Select DeepSeek (id=3)
- Model: Select `deepseek-v4-pro` or available model
- Temperature: 0.7
- Max Context Turns: 10

Click Save.

Expected: Create success, redirect to list, new agent appears.

- [ ] **Step 3: 查看 Agent 详情**

Click "详情" on `E2E-Test-Agent`.

Expected: Shows all fields, model config, knowledge base (empty), tools (empty).

- [ ] **Step 4: 快捷修改 temperature**

Back to list. Click inline edit on temperature field of `E2E-Test-Agent`. Change to 0.5. Save.

Expected: `PATCH /api/v1/agents/{id}/temperature` returns 200. Value updated in UI.

- [ ] **Step 5: 快捷修改 maxContextTurns**

Click inline edit on maxContextTurns. Change to 5. Save.

Expected: `PATCH /api/v1/agents/{id}/max-context-turns` returns 200.

- [ ] **Step 6: 修改工具绑定**

Go to detail page. Click "修改工具绑定". Select any available MCP tools. Save.

Expected: `PUT /api/v1/agents/{id}/tools` returns 200. Tools updated.

- [ ] **Step 7: 克隆 Agent**

Back to list. Click "克隆" on `E2E-Test-Agent`.

Expected: `POST /api/v1/agents/{id}/clone` returns new agent ID. List shows cloned agent.

- [ ] **Step 8: 删除克隆的 Agent**

Click "删除" on cloned agent. Confirm.

Expected: `DELETE` returns 200. Agent removed from list (logic delete, `deleted=1` in DB).

- [ ] **Step 9: 验证元数据接口**

Enter "新增 Agent" page again. Verify:
- "模型分组" dropdown shows providers grouped by supplier (calls `GET /api/v1/agents/models`)
- "工具列表" dropdown shows available tools (calls `GET /api/v1/agents/tools`)

- [ ] **Step 10: 记录 Agent 模块问题**

Any failure → `docs/TEST_ISSUES.md`.

---

### Task 4: 知识库 RAG 测试

**前置条件：** Task 3 通过。

**Files:**
- Test via browser UI: `http://localhost:5173`
- Read: `docs/superpowers/specs/2026-05-27-e2e-api-verification-design.md` Section 4

- [ ] **Step 1: 进入「知识库」页面**

Navigate via sidebar. Expected: List loads (2 existing knowledge bases from DB).

- [ ] **Step 2: 创建新知识库**

Click "新增知识库". Fill:
- Name: `E2E-Test-KB`
- Description: `测试知识库`

Save.

Expected: Create success. `POST /api/v1/knowledge-bases` returns `KnowledgeBaseResponse`.

- [ ] **Step 3: 上传文档**

Enter `E2E-Test-KB` detail. Click "上传文档". Select a small text file (e.g., `test.txt` with "Hello World").

Expected: Upload success. `POST /api/v1/knowledge-bases/{kbId}/documents` returns document ID. Status shows `PENDING` then `PROCESSING`.

- [ ] **Step 4: 等待向量化完成**

Wait 10-30 seconds. Refresh document list.

Expected: Document status changes to `DONE`. `chunk_count > 0`.

- [ ] **Step 5: 查看文档分块**

Click "查看分块" on the uploaded document.

Expected: `GET /api/v1/documents/{id}/chunks` returns chunk list. Each chunk has `content` and `embedding` info.

- [ ] **Step 6: 删除文档**

Click "删除" on the document. Confirm.

Expected: `DELETE /api/v1/documents/{id}` returns 200. Document removed from list.

- [ ] **Step 7: 删除知识库**

Back to knowledge base list. Delete `E2E-Test-KB`.

Expected: `DELETE /api/v1/knowledge-bases/{id}` returns 200. KB removed. Cascading documents also deleted.

- [ ] **Step 8: 记录知识库模块问题**

Any failure → `docs/TEST_ISSUES.md`.

---

### Task 5: MCP 工具测试

**前置条件：** Task 4 通过.

**Files:**
- Test via browser UI: `http://localhost:5173`

- [ ] **Step 1: 进入「MCP 工具」页面**

Navigate via sidebar. Expected: List loads (2 existing MCP servers from DB).

- [ ] **Step 2: 创建新 MCP Server**

Click "新增 MCP Server". Fill:
- Name: `E2E-Test-MCP`
- Transport Type: `stdio` or `sse` (depending on available test server)
- Command / URL: Appropriate value

Save.

Expected: Create success. `POST /api/v1/mcp-servers` returns ID.

- [ ] **Step 3: 查看 MCP Server 详情**

Click "详情" on `E2E-Test-MCP`.

Expected: Shows tool list (e.g., search, fetch, etc.).

- [ ] **Step 4: 连通性测试**

Click "连通性测试".

Expected: `POST /api/v1/mcp-servers/{id}/test` returns `success=true`.

- [ ] **Step 5: 调试工具调用**

Click "调试工具". Select a tool (e.g., "search"). Fill parameters. Execute.

Expected: `POST /api/v1/mcp-servers/{id}/debug` returns tool execution result.

- [ ] **Step 6: 编辑 MCP Server**

Edit name to "E2E-Test-MCP-Updated". Save.

Expected: `PUT /api/v1/mcp-servers/{id}` returns 200.

- [ ] **Step 7: 删除 MCP Server**

Delete `E2E-Test-MCP-Updated`.

Expected: `DELETE /api/v1/mcp-servers/{id}` returns 200.

- [ ] **Step 8: 记录 MCP 模块问题**

Any failure → `docs/TEST_ISSUES.md`.

---

### Task 6: 对话引擎测试

**前置条件：** Task 5 通过. Agent `E2E-Test-Agent` (from Task 3) exists and binds to DeepSeek.

**Files:**
- Test via browser UI: `http://localhost:5173`

- [ ] **Step 1: 进入「对话」页面**

Navigate via sidebar or "新建对话" button.

- [ ] **Step 2: 选择 Agent 创建会话**

Select `E2E-Test-Agent` from dropdown. Click "开始对话".

Expected: `POST /api/v1/chat/sessions` returns session ID. Chat window opens.

- [ ] **Step 3: 发送消息（SSE 流式测试）**

Type "你好，请简单介绍一下自己" in input box. Press Enter.

Expected:
- Network panel shows `POST /api/v1/chat/sessions/{id}/messages`
- Response type: `text/event-stream`
- Messages appear character-by-character in chat window
- Final message is complete and coherent

- [ ] **Step 4: 验证多轮对话**

Send second message: "刚才我说了什么？"

Expected: Agent references previous message (context memory works).

- [ ] **Step 5: 记录对话模块问题**

Any failure → `docs/TEST_ISSUES.md`.

**Note:** If DeepSeek API returns rate limit or timeout, retry once. If consistently failing, record as issue but continue to next module.

---

### Task 7: 简版工作流测试

**前置条件:** Task 6  passed.

**Files:**
- Test via browser UI: `http://localhost:5173`

- [ ] **Step 1: 进入「工作流」页面**

Navigate via sidebar. Expected: List loads (9 existing workflows from DB).

- [ ] **Step 2: 创建工作流**

Click "新增工作流". Fill:
- Name: `E2E-Test-Workflow`
- Description: `测试工作流`

In workflow editor, add nodes:
- Start node
- LLM node (bind to DeepSeek)
- End node

Connect edges: Start → LLM → End.

Save.

Expected: `POST /api/v1/workflows` returns workflow detail.

- [ ] **Step 3: 查看工作流详情**

Click "详情" on `E2E-Test-Workflow`.

Expected: Shows complete node list and edge connections.

- [ ] **Step 4: 执行工作流**

Click "执行". Input: "今天天气怎么样？"

Expected: `POST /api/v1/workflows/{id}/run` returns `WorkflowRunResponse`. Execution completes without error.

- [ ] **Step 5: 查看执行记录**

Navigate to execution history.

Expected: Shows `WorkflowRun` and `WorkflowNodeRun` records. LLM node shows success status.

- [ ] **Step 6: 编辑工作流**

Edit `E2E-Test-Workflow`. Change LLM node prompt. Save.

Expected: `PUT /api/v1/workflows/{id}` returns 200.

- [ ] **Step 7: 删除工作流**

Delete `E2E-Test-Workflow`.

Expected: `DELETE /api/v1/workflows/{id}` returns 200.

- [ ] **Step 8: 记录工作流模块问题**

Any failure → `docs/TEST_ISSUES.md`.

---

### Task 8: 最终汇总与清理

**Files:**
- Modify: `docs/TEST_ISSUES.md`

- [ ] **Step 1: 统计接口覆盖情况**

Review `docs/API_INTERFACE_CATALOG.md`. Check each of the 42 interfaces:
- Mark `✅` if called successfully during E2E
- Mark `❌` if failed (with issue number from TEST_ISSUES.md)
- Mark `⏭️` if skipped (e.g., OpenAI-specific due to no API key)

- [ ] **Step 2: 汇总报告**

Append to `docs/TEST_ISSUES.md`:
```markdown
## 测试汇总

| 指标 | 数值 |
|------|------|
| 总接口数 | 42 |
| 成功 | X |
| 失败 | Y |
| 跳过 | Z |
| 修复数 | W |

## 结论
[通过/有条件通过/不通过]
```

- [ ] **Step 3: 提交问题记录（如发现问题）**

If any issues were found and fixed:
```bash
git add docs/TEST_ISSUES.md
git commit -m "docs: add E2E test issue record"
```

---

## Spec Coverage Check

| Spec 要求 | 对应 Task |
|-----------|----------|
| 健康检查验证 | Task 1 |
| Provider CRUD + 连通性测试 | Task 2 |
| Agent CRUD + 克隆 + 快捷修改 + 元数据 | Task 3 |
| 知识库 CRUD + 文档上传/分块/删除 | Task 4 |
| MCP Server CRUD + 连通性测试 + 调试 | Task 5 |
| 对话创建 + SSE 流式 | Task 6 |
| 工作流 CRUD + 执行 | Task 7 |
| 问题记录与汇总 | Task 8 |

## Placeholder Scan

- [x] 无 TBD/TODO/implement later
- [x] 无 "Add appropriate error handling"
- [x] 无 "Similar to Task N"
- [x] 所有步骤包含具体操作和期望结果
- [x] 所有前置条件明确

## Type Consistency

- [x] 所有接口路径与 `API_INTERFACE_CATALOG.md` 一致
- [x] DeepSeek Provider id=3 贯穿全部需要 LLM 的测试
- [x] 问题记录文件路径统一为 `docs/TEST_ISSUES.md`
