# Frontend Common UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve common frontend interactions in Hify management pages without changing business APIs or adding new features.

**Architecture:** Apply the improvements at the shared component layer first, then wire Provider and Agent search inputs into the improved refresh behavior. Keep existing Element Plus components, design tokens, props, emits, slots, and exposed methods compatible.

**Tech Stack:** Vue 3, TypeScript, Element Plus, Vite.

---

## File Structure

- Modify `hify-web/src/components/HifyTable.vue`
  - Responsibility: shared paginated list component.
  - Add request error state, single empty state, retry action, and toolbar refresh event handling.

- Modify `hify-web/src/components/HifyFormDialog.vue`
  - Responsibility: shared create/edit dialog shell.
  - Add submit loading, duplicate-submit prevention, and disabled close/cancel while submitting.

- Modify `hify-web/src/views/provider/index.vue`
  - Responsibility: provider list page.
  - Wire search input Enter and clear actions to table refresh.

- Modify `hify-web/src/views/agent/index.vue`
  - Responsibility: agent list page.
  - Wire search input Enter and clear actions to table refresh.

---

### Task 1: HifyTable State Feedback

**Files:**
- Modify: `hify-web/src/components/HifyTable.vue`
- Test: `npm run build`

- [ ] **Step 1: Add error state refs**

In `hify-web/src/components/HifyTable.vue`, after:

```ts
const loading = ref(false)
const tableData = ref<any[]>([])
```

add:

```ts
const errorMessage = ref('')
```

- [ ] **Step 2: Capture fetch errors**

Replace the current `fetchData` implementation:

```ts
const fetchData = async () => {
  loading.value = true
  try {
    const res = await props.api({
      page: currentPage.value,
      size: pageSize.value
    })
    tableData.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}
```

with:

```ts
const fetchData = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await props.api({
      page: currentPage.value,
      size: pageSize.value
    })
    tableData.value = res.list || []
    total.value = res.total || 0
  } catch (err: any) {
    tableData.value = []
    total.value = 0
    errorMessage.value = err?.message || '列表加载失败'
  } finally {
    loading.value = false
  }
}
```

- [ ] **Step 3: Add a refresh handler for toolbar children**

After `handleSizeChange`, add:

```ts
const handleToolbarRefresh = () => {
  refresh()
}
```

- [ ] **Step 4: Update the template for toolbar, table, error, empty, and pagination states**

Replace:

```vue
    <div class="toolbar">
      <slot name="toolbar" />
    </div>
    <el-table :data="tableData" v-loading="loading" stripe style="width: 100%" row-key="id" @expand-change="(row: any, expandedRows: any[]) => $emit('expand-change', row, expandedRows)">
```

with:

```vue
    <div class="toolbar" @keyup.enter="handleToolbarRefresh">
      <slot name="toolbar" :refresh="refresh" />
    </div>
    <el-table
      v-if="!errorMessage && (loading || tableData.length > 0)"
      :data="tableData"
      v-loading="loading"
      stripe
      style="width: 100%"
      row-key="id"
      @expand-change="(row: any, expandedRows: any[]) => $emit('expand-change', row, expandedRows)"
    >
```

Replace:

```vue
    <el-empty v-if="!loading && tableData.length === 0" description="暂无数据" />

    <div class="pagination-wrapper">
```

with:

```vue
    <div v-if="!loading && errorMessage" class="state-panel">
      <el-empty :description="errorMessage">
        <el-button type="primary" @click="fetchData">重试</el-button>
      </el-empty>
    </div>
    <div v-else-if="!loading && tableData.length === 0" class="state-panel">
      <el-empty description="暂无数据" />
    </div>

    <div v-if="total > 0" class="pagination-wrapper">
```

- [ ] **Step 5: Add state panel styles**

In the scoped style block, after `.toolbar`, add:

```css
.state-panel {
  border: 1px dashed var(--color-border-default);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
}
```

- [ ] **Step 6: Run frontend build**

Run:

```powershell
npm run build
```

from `hify-web`.

Expected: build passes.

---

### Task 2: HifyFormDialog Submit State

**Files:**
- Modify: `hify-web/src/components/HifyFormDialog.vue`
- Test: `npm run build`

- [ ] **Step 1: Add submitting state**

After:

```ts
const formRef = ref<any>(null)
```

add:

```ts
const submitting = ref(false)
```

- [ ] **Step 2: Reset submitting state on open and close**

In `open`, immediately after `visible.value = true`, add:

```ts
  submitting.value = false
```

Replace the current `close` implementation:

```ts
const close = () => {
  visible.value = false
}
```

with:

```ts
const close = () => {
  submitting.value = false
  visible.value = false
}
```

- [ ] **Step 3: Make submit await parent handlers and prevent duplicate clicks**

Replace:

```ts
const handleSubmit = async () => {
  const valid = await formRef.value?.validate?.().catch(() => false)
  if (!valid) return
  emit('submit', { ...formData.value, _isEdit: isEdit.value })
}
```

with:

```ts
const handleSubmit = async () => {
  if (submitting.value) return
  const valid = await formRef.value?.validate?.().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await emit('submit', { ...formData.value, _isEdit: isEdit.value })
  } finally {
    if (visible.value) {
      submitting.value = false
    }
  }
}
```

- [ ] **Step 4: Update emit typing so async parent handlers are allowed**

Replace:

```ts
const emit = defineEmits<{
  submit: [{ [key: string]: any; _isEdit: boolean }]
}>()
```

with:

```ts
const emit = defineEmits<{
  submit: [data: { [key: string]: any; _isEdit: boolean }]
}>()
```

- [ ] **Step 5: Disable dialog closing and buttons during submit**

In the `<el-dialog>` props, add:

```vue
    :show-close="!submitting"
    :close-on-press-escape="!submitting"
```

Replace:

```vue
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">
```

with:

```vue
        <el-button :disabled="submitting" @click="close">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
```

- [ ] **Step 6: Run frontend build**

Run:

```powershell
npm run build
```

from `hify-web`.

Expected: build passes.

---

### Task 3: Provider and Agent Search Refresh Wiring

**Files:**
- Modify: `hify-web/src/views/provider/index.vue`
- Modify: `hify-web/src/views/agent/index.vue`
- Test: `npm run build`

- [ ] **Step 1: Add Provider search clear refresh**

In `hify-web/src/views/provider/index.vue`, update the toolbar input from:

```vue
        <el-input
          v-model="searchKey"
          placeholder="搜索供应商名称"
          clearable
          style="width: 240px"
        />
```

to:

```vue
        <el-input
          v-model="searchKey"
          placeholder="搜索供应商名称"
          clearable
          style="width: 240px"
          @clear="tableRef?.refresh()"
        />
```

Enter refresh is handled by `HifyTable` toolbar keyup handling.

- [ ] **Step 2: Add Agent search clear refresh**

In `hify-web/src/views/agent/index.vue`, update the toolbar input from:

```vue
        <el-input
          v-model="searchKey"
          placeholder="搜索 Agent 名称"
          clearable
          style="width: 240px"
        />
```

to:

```vue
        <el-input
          v-model="searchKey"
          placeholder="搜索 Agent 名称"
          clearable
          style="width: 240px"
          @clear="tableRef?.refresh()"
        />
```

- [ ] **Step 3: Run frontend build**

Run:

```powershell
npm run build
```

from `hify-web`.

Expected: build passes.

---

### Task 4: Full Verification and Commit

**Files:**
- Verify all modified frontend files.

- [ ] **Step 1: Run final frontend build**

Run:

```powershell
npm run build
```

from `hify-web`.

Expected: build passes.

- [ ] **Step 2: Check planned diff only**

Run:

```powershell
git status --short
git diff -- hify-web/src/components/HifyTable.vue hify-web/src/components/HifyFormDialog.vue hify-web/src/views/provider/index.vue hify-web/src/views/agent/index.vue
```

Expected: only the planned frontend files are modified, plus this plan file if it has not already been committed.

- [ ] **Step 3: Commit implementation**

Run:

```powershell
git add hify-web/src/components/HifyTable.vue hify-web/src/components/HifyFormDialog.vue hify-web/src/views/provider/index.vue hify-web/src/views/agent/index.vue
git commit -m "fix: improve common frontend interactions"
```

Expected: commit succeeds after hooks pass.

---

## Self-Review

- Spec coverage: Task 1 covers table feedback, empty state, error state, retry, and toolbar refresh. Task 2 covers dialog submit loading and duplicate-submit prevention. Task 3 covers Provider and Agent search clear wiring. Task 4 covers verification and commit.
- Scope check: no backend API changes, route changes, visual redesign, authentication, new dependencies, or business features are included.
- Type consistency: existing Vue refs, slots, emits, and exposed methods are preserved.
