# Core Path UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Provider -> Agent -> Chat internal MVP path clear, readable, and stable for colleagues.

**Architecture:** Keep all changes in the existing Vue frontend. Reuse Element Plus, the current page-level Composition API style, `HifyTable`, `HifyFormDialog`, `useConfirm`, and the existing API modules; do not add backend endpoints or new UI frameworks.

**Tech Stack:** Vue 3 Composition API, TypeScript, Element Plus, Vue Router, existing Hify frontend components, Vite build verification.

---

## File Structure

- Modify: `hify-web/src/App.vue`
  - Fix sidebar labels and logo glyph while preserving the current layout.
- Modify: `hify-web/src/views/provider/index.vue`
  - Fix readable Chinese copy and add row-level connection testing feedback plus Agent next-step guidance.
- Modify: `hify-web/src/views/agent/index.vue`
  - Fix readable Chinese copy and add visible model metadata failure/no-model guidance plus Chat next-step guidance.
- Modify: `hify-web/src/views/chat/index.vue`
  - Preserve existing Chat behavior and polish SSE display stability.
- Optional Modify: `hify-web/src/api/provider.ts`
  - Only if a type field is missing for connection test feedback.

Style constraints:

- Match the current code style: `<script setup lang="ts">`, `ref`, plain helper functions, Element Plus components, scoped CSS, and design tokens already used in the app.
- Do not introduce a new global store, route, component library, or backend contract.
- Do not rewrite pages wholesale when a focused template/script/style edit is enough.

---

### Task 1: Fix Global Navigation Copy

**Files:**
- Modify: `hify-web/src/App.vue`

- [ ] **Step 1: Replace mojibake labels in the sidebar**

Use readable labels while keeping the current icons and menu indexes:

```vue
<div class="logo">
  <span class="logo-icon">◆</span>
  <span class="logo-text">Hify</span>
</div>

<el-menu-item index="/providers">
  <el-icon><Setting /></el-icon>
  <span>模型管理</span>
</el-menu-item>
<el-menu-item index="/agents">
  <el-icon><User /></el-icon>
  <span>Agent 管理</span>
</el-menu-item>
<el-menu-item index="/chat">
  <el-icon><ChatLineRound /></el-icon>
  <span>对话</span>
</el-menu-item>
<el-menu-item index="/mcp-servers">
  <el-icon><Tools /></el-icon>
  <span>MCP Server</span>
</el-menu-item>
<el-menu-item index="/workflows">
  <el-icon><Connection /></el-icon>
  <span>工作流</span>
</el-menu-item>
```

- [ ] **Step 2: Run the frontend build**

Run: `cd hify-web && npm run build`

Expected: PASS. The route list and navigation behavior should be unchanged.

---

### Task 2: Improve Provider Page Readability And Connection Feedback

**Files:**
- Modify: `hify-web/src/views/provider/index.vue`

- [ ] **Step 1: Fix visible Provider copy**

Replace page title, button text, toolbar placeholder, table labels, form labels, validation messages, and action text with readable Chinese. Keep current field names and API calls.

Important replacements:

```ts
const columns = [
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'providerType', label: '协议类型', width: 140 },
  { prop: 'baseUrl', label: 'Base URL', minWidth: 240 },
  { prop: 'status', label: '状态', width: 90 },
  { prop: 'healthStatus', label: '健康状态', width: 140, slot: 'healthStatus' },
  { prop: 'modelCount', label: '模型数', width: 100, slot: 'modelCount' },
  { prop: 'action', label: '操作', width: 220, slot: 'action' }
]

const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择协议类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  authType: [{ required: true, message: '请选择鉴权类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}
```

Template copy should use:

```vue
<h2>模型供应商管理</h2>
<el-button type="primary" @click="handleAdd">新增供应商</el-button>
<el-input v-model="searchKey" placeholder="搜索供应商名称" clearable style="width: 240px" @clear="tableRef?.refresh()" />
```

- [ ] **Step 2: Add row-level connection testing state**

Add state near `loadingModels`:

```ts
const testingProviders = ref<Record<number, boolean>>({})
```

Update the action button to show loading without changing the existing action layout:

```vue
<el-button
  link
  type="success"
  :loading="testingProviders[row.id]"
  @click="handleTestConnection(row)"
>
  测试连接
</el-button>
```

- [ ] **Step 3: Update `handleTestConnection` feedback**

Keep the current `testConnection(row.id)` call, but wrap it with per-row loading and actionable messages:

```ts
const handleTestConnection = async (row: ProviderListItem) => {
  testingProviders.value[row.id] = true
  try {
    const result = await testConnection(row.id)
    const latencyText = result.latencyMs ? `，耗时 ${result.latencyMs}ms` : ''
    const modelText = result.modelCount !== undefined ? `，发现 ${result.modelCount} 个模型` : ''
    ElMessage.success(`连接成功${latencyText}${modelText}。下一步可以创建 Agent。`)
    tableRef.value?.refresh()
    modelMap.value[row.id] = []
  } catch (e: any) {
    ElMessage.error(e.message || '连接失败，请检查 API Key、Base URL 或网络连通性')
  } finally {
    testingProviders.value[row.id] = false
  }
}
```

If the existing request interceptor already emits an error toast and duplicate messages become noisy, remove the local `ElMessage.error` line and keep the row loading cleanup.

- [ ] **Step 4: Ensure dialog submit cleanup remains compatible**

The existing `HifyFormDialog` exposes `finishSubmit()`. Ensure `handleSubmit` still calls it in `finally`:

```ts
} finally {
  dialogRef.value?.finishSubmit()
}
```

- [ ] **Step 5: Run the frontend build**

Run: `cd hify-web && npm run build`

Expected: PASS.

---

### Task 3: Improve Agent Page Readability And Model Guidance

**Files:**
- Modify: `hify-web/src/views/agent/index.vue`

- [ ] **Step 1: Fix visible Agent copy**

Replace page title, button text, table labels, form labels, tab labels, validation messages, and action labels with readable Chinese.

Important replacements:

```ts
const columns = [
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'modelName', label: '模型', width: 160 },
  { prop: 'toolCount', label: '工具数', width: 180, slot: 'toolCount' },
  { prop: 'temperature', label: '温度', width: 100, slot: 'temperature' },
  { prop: 'maxContextTurns', label: '上下文轮数', width: 120, slot: 'maxContextTurns' },
  { prop: 'enabled', label: '状态', width: 80, slot: 'enabled' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'action', label: '操作', width: 220, slot: 'action' }
]

const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请选择模型配置', trigger: 'change', type: 'number' }],
  temperature: [{ required: true, message: '请设置温度', trigger: 'change', type: 'number' }],
  maxTokens: [{ required: true, message: '请设置最大 Token', trigger: 'change', type: 'number' }],
  maxContextTurns: [{ required: true, message: '请设置上下文轮数', trigger: 'change', type: 'number' }]
}
```

Template copy should use:

```vue
<h2>Agent 管理</h2>
<el-button type="primary" @click="handleAdd">新增 Agent</el-button>
<el-tab-pane label="基础配置" name="basic">
<el-tab-pane label="工具绑定" name="tools">
```

- [ ] **Step 2: Add metadata loading and error state**

Add refs near `modelGroups`:

```ts
const metaLoading = ref(false)
const metaError = ref('')
```

Update `loadMetaData` to show a visible warning instead of swallowing errors:

```ts
const loadMetaData = async () => {
  metaLoading.value = true
  metaError.value = ''
  try {
    const [modelRes, toolRes] = await Promise.all([getModelGroups(), getTools()])
    modelGroups.value = modelRes || []
    toolOptions.value = toolRes || []
  } catch (e: any) {
    metaError.value = e.message || '模型和工具元数据加载失败'
    ElMessage.warning('模型列表加载失败，请先检查供应商配置')
  } finally {
    metaLoading.value = false
  }
}
```

- [ ] **Step 3: Add no-model guidance above the table**

Place a compact alert under the page header, matching Element Plus style:

```vue
<el-alert
  v-if="metaError"
  type="warning"
  :title="metaError"
  description="请先确认模型供应商已配置并测试通过。"
  show-icon
  :closable="false"
  class="path-alert"
/>
<el-alert
  v-else-if="!metaLoading && modelGroups.length === 0"
  type="info"
  title="暂无可用模型"
  description="请先到模型供应商管理中新增供应商并测试连接，再创建 Agent。"
  show-icon
  :closable="false"
  class="path-alert"
/>
```

- [ ] **Step 4: Guard `handleAdd` when no model is available**

Avoid opening an unusable Agent form:

```ts
const hasAvailableModels = () => modelGroups.value.some(group => group.models?.length)

const handleAdd = () => {
  if (!hasAvailableModels()) {
    ElMessage.warning('请先配置并测试可用的模型供应商')
    return
  }
  activeTab.value = 'basic'
  dialogRef.value?.open({
    description: '',
    systemPrompt: '',
    temperature: 0.7,
    maxTokens: 2048,
    maxContextTurns: 10,
    enabled: 1,
    toolIds: []
  })
}
```

- [ ] **Step 5: Update Agent creation success guidance**

When a new Agent is created, use a success message that points to Chat. Keep update messaging simple:

```ts
if (_isEdit && data.id) {
  await updateAgent(data.id, payload)
  ElMessage.success('更新成功')
} else {
  await createAgent(payload)
  ElMessage.success('创建成功，可以前往对话页开始使用')
}
```

- [ ] **Step 6: Add small alert spacing style**

```css
.path-alert {
  margin-bottom: var(--space-4);
}
```

- [ ] **Step 7: Run the frontend build**

Run: `cd hify-web && npm run build`

Expected: PASS.

---

### Task 4: Polish Chat SSE Display Stability

**Files:**
- Modify: `hify-web/src/views/chat/index.vue`

- [ ] **Step 1: Preserve existing state machine**

Do not remove these existing behaviors:

```ts
type SessionStatus = 'creating' | 'ready' | 'failed'
type AssistantMessageStatus = 'loading' | 'streaming' | 'done' | 'stopped' | 'error'
const currentAbortController = ref<AbortController | null>(null)
const stoppingMessageIndex = ref<number | null>(null)
```

- [ ] **Step 2: Stabilize assistant bubble dimensions**

Update bubble CSS to avoid visible jumps while streaming:

```css
.message-row.assistant .message-bubble {
  min-width: 96px;
  min-height: 44px;
}

.markdown-body {
  line-height: 1.6;
  min-width: 0;
}
```

- [ ] **Step 3: Keep input area stable while switching Send/Stop**

Ensure `.send-btn` has a stable width and does not resize when text changes:

```css
.send-btn {
  align-self: flex-end;
  width: 88px;
}
```

- [ ] **Step 4: Ensure stopped messages keep partial content**

Confirm the abort branch keeps existing content and only inserts fallback text when content is empty:

```ts
if (isAbort && stoppingMessageIndex.value === aiIndex) {
  aiMsg.status = 'stopped'
  if (!aiMsg.content) {
    aiMsg.content = '已停止生成'
  }
  aiMsg.htmlContent = renderMarkdown(aiMsg.content)
}
```

- [ ] **Step 5: Run the frontend build**

Run: `cd hify-web && npm run build`

Expected: PASS.

---

### Task 5: Final Verification And Implementation Commit

**Files:**
- Modify: `hify-web/src/App.vue`
- Modify: `hify-web/src/views/provider/index.vue`
- Modify: `hify-web/src/views/agent/index.vue`
- Modify: `hify-web/src/views/chat/index.vue`
- Optional Modify: `hify-web/src/api/provider.ts`

- [ ] **Step 1: Run final frontend build**

Run: `cd hify-web && npm run build`

Expected: PASS. Existing Vite chunk-size warnings may remain, but there should be no TypeScript or Vue template errors.

- [ ] **Step 2: Start or verify local frontend server**

Run:

```powershell
$port = 5173
$existing = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if (-not $existing) {
  Start-Process -FilePath "npm.cmd" -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1", "--port", "$port") -WorkingDirectory "E:\hify\hify-web" -WindowStyle Hidden
  Start-Sleep -Seconds 3
}
Invoke-WebRequest -Uri "http://127.0.0.1:$port/providers" -UseBasicParsing -TimeoutSec 10
Invoke-WebRequest -Uri "http://127.0.0.1:$port/agents" -UseBasicParsing -TimeoutSec 10
Invoke-WebRequest -Uri "http://127.0.0.1:$port/chat" -UseBasicParsing -TimeoutSec 10
```

Expected: each route returns HTTP 200.

- [ ] **Step 3: Inspect changed files**

Run: `git diff --stat`

Expected: implementation changes should be limited to the files listed in this task. The spec and plan docs may still exist until cleanup.

- [ ] **Step 4: Commit implementation**

```bash
git add hify-web/src/App.vue hify-web/src/views/provider/index.vue hify-web/src/views/agent/index.vue hify-web/src/views/chat/index.vue hify-web/src/api/provider.ts
git commit -m "fix: improve core path ux"
```

If `hify-web/src/api/provider.ts` is unchanged, `git add` simply leaves it out of the commit.

---

### Task 6: Clean Temporary Planning Docs

**Files:**
- Delete: `docs/superpowers/specs/2026-06-05-core-path-ux-design.md`
- Delete: `docs/superpowers/plans/2026-06-05-core-path-ux.md`

- [ ] **Step 1: Remove temporary planning files after implementation commit**

```powershell
Remove-Item -LiteralPath docs\superpowers\specs\2026-06-05-core-path-ux-design.md
Remove-Item -LiteralPath docs\superpowers\plans\2026-06-05-core-path-ux.md
```

- [ ] **Step 2: Verify cleanup**

Run: `git status --short`

Expected: only the two deleted planning files are listed.

- [ ] **Step 3: Commit cleanup**

```bash
git add docs/superpowers/specs/2026-06-05-core-path-ux-design.md docs/superpowers/plans/2026-06-05-core-path-ux.md
git commit -m "docs: remove temporary core path ux planning docs"
```

---

## Self-Review

- Spec coverage: navigation, Provider feedback, Agent model guidance, Chat SSE stability, readable copy, and build verification are covered.
- Scope check: no backend changes, no Knowledge/RAG, no Workflow, no MCP deep flow, no authentication, and no new onboarding page are included.
- Style consistency: all tasks preserve Vue Composition API, Element Plus, existing shared components, design tokens, and current page-level helper style.
- Type consistency: `testingProviders`, `metaLoading`, `metaError`, `hasAvailableModels`, and existing Chat status names are defined before use.
