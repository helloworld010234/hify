# RAG Core Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the single-knowledge-base RAG path usable from knowledge upload through Agent binding to Chat.

**Architecture:** Keep the current modular monolith backend behavior unchanged. The frontend sends the existing effective `knowledgeBaseId` field on Agent create and update, and improves knowledge document state feedback without introducing multi-knowledge retrieval or new RAG settings.

**Tech Stack:** Vue 3, TypeScript, Element Plus, Vite, Spring Boot existing REST APIs.

---

## File Structure

- Modify: `hify-web/src/api/agent.ts`
  - Add `knowledgeBaseId` and `knowledgeCount` to Agent frontend types.
- Modify: `hify-web/src/views/agent/index.vue`
  - Load knowledge bases with existing API.
  - Add optional single knowledge base selector.
  - Show binding status in the Agent list.
  - Submit and restore `knowledgeBaseId`.
- Modify: `hify-web/src/views/knowledge/documents.vue`
  - Improve upload, parsing, success, and failure guidance.
  - Add a direct "bind to Agent" next-step action after usable documents exist.
- Verify only: `hify-web/src/router/index.ts`
  - Existing `/agents`, `/knowledge-bases`, `/knowledge-bases/:id/documents`, and `/chat` routes should continue rendering.
- Cleanup after implementation: remove this plan and `docs/superpowers/specs/2026-06-05-rag-core-path-design.md`.

## Task 1: Align Agent API Types

**Files:**
- Modify: `hify-web/src/api/agent.ts`

- [ ] **Step 1: Add the effective knowledge fields to frontend types**

In `Agent`, add:

```ts
knowledgeBaseId?: number | null
```

In `AgentListItem`, add:

```ts
knowledgeBaseId?: number | null
knowledgeCount?: number
```

In `AgentDetail`, add:

```ts
knowledgeBaseId?: number | null
```

- [ ] **Step 2: Run TypeScript build to catch type drift**

Run:

```bash
cd hify-web
npm run build
```

Expected: build still succeeds after the type-only change. If a type error appears, record the exact field name and fix it before editing the views.

## Task 2: Add Single Knowledge Base Binding to Agent Form

**Files:**
- Modify: `hify-web/src/views/agent/index.vue`

- [ ] **Step 1: Import the knowledge API and type**

Add imports near the existing API imports:

```ts
import {
  getKnowledgeBaseList,
  type KnowledgeBaseItem
} from '@/api/knowledge'
```

- [ ] **Step 2: Add knowledge metadata state**

Add state near `toolOptions`:

```ts
const knowledgeOptions = ref<KnowledgeBaseItem[]>([])
```

- [ ] **Step 3: Load knowledge bases with the existing metadata fetch**

Replace the metadata request in `loadMetaData` with:

```ts
const [modelRes, toolRes, knowledgeRes] = await Promise.all([
  getModelGroups(),
  getTools(),
  getKnowledgeBaseList({ page: 1, size: 100 })
])
modelGroups.value = modelRes || []
toolOptions.value = toolRes || []
knowledgeOptions.value = knowledgeRes?.list || []
```

Keep the existing `metaError` and warning behavior so model-only Agent creation is still possible when knowledge metadata fails.

- [ ] **Step 4: Add the optional selector to the Agent form**

Place this form item after the model selector:

```vue
<el-form-item label="知识库" prop="knowledgeBaseId">
  <el-select
    v-model="form.knowledgeBaseId"
    placeholder="不绑定知识库"
    clearable
    filterable
    style="width: 100%"
  >
    <el-option
      v-for="knowledge in knowledgeOptions"
      :key="knowledge.id"
      :label="knowledge.name"
      :value="knowledge.id"
    >
      <div class="knowledge-option">
        <span class="knowledge-option__name">{{ knowledge.name }}</span>
        <span class="knowledge-option__meta">{{ knowledge.documentCount }} 个文档</span>
      </div>
    </el-option>
  </el-select>
  <div v-if="!knowledgeOptions.length" class="field-hint">
    暂无知识库，可先保存 Agent，后续上传文档后再绑定。
  </div>
</el-form-item>
```

- [ ] **Step 5: Submit and restore `knowledgeBaseId`**

In `handleAdd`, include:

```ts
knowledgeBaseId: null,
```

In `handleEdit`, include:

```ts
knowledgeBaseId: detail.knowledgeBaseId ?? null,
```

- [ ] **Step 6: Add compact selector styles**

Add scoped styles:

```css
.knowledge-option {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
}
.knowledge-option__name {
  color: var(--color-text-primary);
}
.knowledge-option__meta {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.field-hint {
  margin-top: var(--space-2);
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-secondary);
}
```

## Task 3: Show Agent Knowledge Binding State

**Files:**
- Modify: `hify-web/src/views/agent/index.vue`

- [ ] **Step 1: Add the knowledge column**

Add this column after `modelName`:

```ts
{ prop: 'knowledgeBaseId', label: '知识库', width: 110, slot: 'knowledgeBaseId' },
```

- [ ] **Step 2: Add a table slot for bound/unbound state**

Add this slot near the other table slots:

```vue
<template #knowledgeBaseId="{ row }">
  <el-tag :type="row.knowledgeBaseId ? 'success' : 'info'" size="small">
    {{ row.knowledgeBaseId ? '已绑定' : '未绑定' }}
  </el-tag>
</template>
```

- [ ] **Step 3: Verify Agent payload shape manually**

Run:

```bash
cd hify-web
npm run build
```

Expected: build succeeds, and no TypeScript error complains about `knowledgeBaseId`.

## Task 4: Improve Knowledge Document Next-Step Feedback

**Files:**
- Modify: `hify-web/src/views/knowledge/documents.vue`

- [ ] **Step 1: Import router navigation**

Change the router import:

```ts
import { useRoute, useRouter } from 'vue-router'
```

Add:

```ts
const router = useRouter()
```

- [ ] **Step 2: Add a "bind to Agent" action in the page header**

Place this button next to upload:

```vue
<el-button :disabled="!hasReadyDocuments" @click="goToAgents">
  绑定到 Agent
</el-button>
```

Wrap the header actions in a stable container:

```vue
<div class="header-actions">
  <el-button :disabled="!hasReadyDocuments" @click="goToAgents">绑定到 Agent</el-button>
  <el-button type="primary" @click="uploadVisible = true">上传文档</el-button>
</div>
```

- [ ] **Step 3: Add ready-state helpers**

Add:

```ts
const hasReadyDocuments = computed(() => documentList.value.some(doc => doc.status === 'DONE'))

const goToAgents = () => {
  router.push('/agents')
}
```

Also update the Vue import:

```ts
import { ref, computed, onMounted, onUnmounted } from 'vue'
```

- [ ] **Step 4: Improve upload and parsing messages**

Change the upload success message to:

```ts
ElMessage.success('上传成功，正在解析文档')
```

Change the done polling message to:

```ts
ElMessage.success(`文档「${doc.name}」处理完成，可绑定到 Agent 使用`)
```

Keep the existing failure message with `doc.errorMessage`.

- [ ] **Step 5: Add a concise status hint above the document table**

Place this alert above the table:

```vue
<el-alert
  v-if="documentList.some(doc => doc.status === 'PENDING' || doc.status === 'PROCESSING')"
  type="info"
  title="文档正在解析"
  description="解析完成后即可绑定到 Agent，用于 Chat 检索回答。"
  show-icon
  :closable="false"
  class="path-alert"
/>
```

- [ ] **Step 6: Add header and alert styles**

Add:

```css
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.path-alert {
  margin-bottom: var(--space-4);
}
```

## Task 5: Verify Core Routes and Build

**Files:**
- Verify: `hify-web/src/router/index.ts`

- [ ] **Step 1: Build frontend**

Run:

```bash
cd hify-web
npm run build
```

Expected: `vue-tsc -b && vite build` completes successfully.

- [ ] **Step 2: Run the frontend preview server**

Run:

```bash
cd hify-web
npm run preview -- --host 127.0.0.1 --port 4173
```

Expected: preview server starts on `http://127.0.0.1:4173/`.

- [ ] **Step 3: Verify route rendering**

Open these routes and confirm they return HTML without a blank page:

```text
http://127.0.0.1:4173/providers
http://127.0.0.1:4173/agents
http://127.0.0.1:4173/knowledge-bases
http://127.0.0.1:4173/knowledge-bases/1/documents
http://127.0.0.1:4173/chat
```

Expected: each route returns HTTP 200 and the Vite app shell renders.

## Task 6: Commit Implementation and Clean Planning Artifacts

**Files:**
- Modify: code files from Tasks 1-4.
- Delete: `docs/superpowers/specs/2026-06-05-rag-core-path-design.md`
- Delete: `docs/superpowers/plans/2026-06-05-rag-core-path.md`

- [ ] **Step 1: Commit implementation**

Run:

```bash
git add hify-web/src/api/agent.ts hify-web/src/views/agent/index.vue hify-web/src/views/knowledge/documents.vue
git commit -m "fix: improve rag core path ux"
```

- [ ] **Step 2: Remove temporary planning artifacts**

Run:

```bash
git rm docs/superpowers/specs/2026-06-05-rag-core-path-design.md docs/superpowers/plans/2026-06-05-rag-core-path.md
git commit -m "docs: remove temporary rag planning docs"
```

- [ ] **Step 3: Confirm workspace cleanliness**

Run:

```bash
git status --short --branch
```

Expected: no untracked temporary planning files remain.
