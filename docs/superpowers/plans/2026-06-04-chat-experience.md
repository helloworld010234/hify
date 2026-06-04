# Chat Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve the Chat page's sending control and state feedback while keeping the existing single-session SSE flow.

**Architecture:** Keep the work focused in the existing `hify-web/src/views/chat/index.vue` component. Add explicit session and assistant message statuses, reuse the existing `AbortController`, and keep the existing `chatApi.streamSendMessage` contract unchanged.

**Tech Stack:** Vue 3 Composition API, TypeScript, Element Plus, marked, DOMPurify, Vite build verification.

---

## File Structure

- Modify: `hify-web/src/views/chat/index.vue`
  - Owns Chat page state, session creation, send/stop control, message rendering, and scoped styles.
- No expected change: `hify-web/src/api/chat.ts`
  - Existing streaming API already accepts an `AbortSignal` and callback handlers.
- Verification command: `cd hify-web && npm run build`

---

### Task 1: Add Explicit Chat State

**Files:**
- Modify: `hify-web/src/views/chat/index.vue`

- [ ] **Step 1: Add typed state models near the existing `ChatMessage` interface**

Use explicit statuses so rendering no longer depends on ambiguous combinations of `loading` and `isError`.

```ts
type SessionStatus = 'creating' | 'ready' | 'failed'
type AssistantMessageStatus = 'loading' | 'streaming' | 'done' | 'stopped' | 'error'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  status?: AssistantMessageStatus
  errorMessage?: string
  htmlContent?: string
}
```

- [ ] **Step 2: Add session state refs after the existing refs**

```ts
const sessionStatus = ref<SessionStatus>('creating')
const sessionError = ref('')
const stoppingMessageIndex = ref<number | null>(null)
```

- [ ] **Step 3: Replace session initialization in `onMounted` with a helper**

Create a helper that can be reused by the retry action.

```ts
async function initializeSession(forceNew = false) {
  sessionStatus.value = 'creating'
  sessionError.value = ''

  if (!forceNew) {
    const urlParams = new URLSearchParams(window.location.search)
    const sid = urlParams.get('sessionId') || localStorage.getItem('chatSessionId')
    if (sid) {
      sessionId.value = sid
      sessionStatus.value = 'ready'
      return
    }
  }

  try {
    const data = await chatApi.createSession(6)
    sessionId.value = String(data.id || data.data?.id || '')
    if (!sessionId.value) {
      throw new Error('创建会话接口未返回 sessionId')
    }
    localStorage.setItem('chatSessionId', sessionId.value)
    sessionStatus.value = 'ready'
  } catch (e: any) {
    sessionId.value = ''
    sessionStatus.value = 'failed'
    sessionError.value = e.message || '创建会话失败'
    ElMessage.error('创建会话失败: ' + sessionError.value)
  }
}

onMounted(() => {
  initializeSession()
})
```

- [ ] **Step 4: Add a retry helper**

```ts
function retryCreateSession() {
  localStorage.removeItem('chatSessionId')
  initializeSession(true)
}
```

- [ ] **Step 5: Run the frontend build**

Run: `cd hify-web && npm run build`

Expected: PASS. If TypeScript fails because old template references `loading` or `isError`, continue to Task 2 and rebuild after the rendering update.

---

### Task 2: Implement Stop Generation and Stream Cleanup

**Files:**
- Modify: `hify-web/src/views/chat/index.vue`

- [ ] **Step 1: Add a centralized send cleanup helper**

```ts
function finishSending() {
  isSending.value = false
  currentAbortController.value = null
  stoppingMessageIndex.value = null
}
```

- [ ] **Step 2: Add a stop handler before `handleSend`**

```ts
function handleStop() {
  if (!isSending.value || !currentAbortController.value) return
  stoppingMessageIndex.value = messages.value.length - 1
  currentAbortController.value.abort()
}
```

- [ ] **Step 3: Update the beginning of `handleSend`**

The send path must be disabled until the session is ready.

```ts
async function handleSend() {
  const content = inputText.value.trim()
  if (!content || isSending.value) return

  if (sessionStatus.value !== 'ready' || !sessionId.value) {
    ElMessage.warning('会话准备完成后再发送消息')
    return
  }
```

- [ ] **Step 4: Push the assistant message with explicit status**

```ts
const aiIndex = messages.value.length
messages.value.push({ role: 'assistant', content: '', status: 'loading' })
scrollToBottom()
```

- [ ] **Step 5: Update streaming callbacks**

```ts
onDelta: (delta: string) => {
  const aiMsg = messages.value[aiIndex]
  if (!aiMsg) return
  aiMsg.content += delta
  aiMsg.status = 'streaming'
  scrollToBottom()
},
onDone: (data: ChatStreamEvent) => {
  const aiMsg = messages.value[aiIndex]
  if (!aiMsg) return
  aiMsg.status = 'done'
  aiMsg.htmlContent = renderMarkdown(aiMsg.content)
  finishSending()
  if (data.latencyMs !== undefined) {
    console.log('Latency:', data.latencyMs, 'ms')
  }
},
onError: (data: ChatStreamEvent) => {
  const aiMsg = messages.value[aiIndex]
  if (!aiMsg) return
  aiMsg.status = 'error'
  aiMsg.errorMessage = data.message || data.content || '流式响应出错'
  aiMsg.content = aiMsg.errorMessage
  finishSending()
  ElMessage.error(aiMsg.errorMessage)
},
```

- [ ] **Step 6: Update the catch block to distinguish user stop from real errors**

```ts
} catch (err: any) {
  const aiMsg = messages.value[aiIndex]
  const isAbort = err?.name === 'AbortError'
  if (aiMsg) {
    if (isAbort && stoppingMessageIndex.value === aiIndex) {
      aiMsg.status = 'stopped'
      if (!aiMsg.content) {
        aiMsg.content = '已停止生成'
      }
      aiMsg.htmlContent = renderMarkdown(aiMsg.content)
    } else {
      aiMsg.status = 'error'
      aiMsg.errorMessage = err.message || '发送失败，请稍后重试'
      aiMsg.content = aiMsg.errorMessage
      ElMessage.error(aiMsg.errorMessage)
    }
  }
  finishSending()
}
```

- [ ] **Step 7: Run the frontend build**

Run: `cd hify-web && npm run build`

Expected: PASS after Task 3 template changes are complete.

---

### Task 3: Update Template for State Feedback

**Files:**
- Modify: `hify-web/src/views/chat/index.vue`

- [ ] **Step 1: Replace the header state block**

```vue
<div class="chat-header">
  <div class="session-state">
    <el-tag v-if="sessionStatus === 'creating'" type="info" size="small">会话创建中</el-tag>
    <el-tag v-else-if="sessionStatus === 'failed'" type="danger" size="small">会话创建失败</el-tag>
    <el-tag v-else type="success" size="small">Session: {{ sessionId }}</el-tag>
    <span v-if="sessionStatus === 'failed'" class="session-error">{{ sessionError }}</span>
  </div>
  <el-button
    v-if="sessionStatus === 'failed'"
    size="small"
    type="primary"
    @click="retryCreateSession"
  >
    重试
  </el-button>
</div>
```

- [ ] **Step 2: Add empty state before the message loop**

```vue
<div v-if="sessionStatus === 'ready' && messages.length === 0" class="empty-state">
  <div class="empty-title">当前会话已准备好</div>
  <div class="empty-subtitle">输入消息后即可开始对话。</div>
</div>
```

- [ ] **Step 3: Replace assistant loading/error branches**

```vue
<template v-if="msg.status === 'loading'">
  <div class="loading-dots" aria-label="助手正在思考">
    <span></span>
    <span></span>
    <span></span>
  </div>
</template>
<template v-else-if="msg.status === 'error'">
  <div class="message-error">{{ msg.errorMessage || msg.content }}</div>
</template>
<template v-else>
  <pre v-if="msg.role === 'user'" class="message-content">{{ msg.content }}</pre>
  <div v-else>
    <div class="markdown-body" v-html="msg.htmlContent ?? renderMarkdown(msg.content)"></div>
    <div v-if="msg.status === 'stopped'" class="message-meta">已停止生成</div>
  </div>
</template>
```

- [ ] **Step 4: Update the input and button**

```vue
<el-input
  v-model="inputText"
  type="textarea"
  :rows="3"
  resize="none"
  placeholder="输入消息，Enter 发送，Shift+Enter 换行"
  :disabled="isSending || sessionStatus !== 'ready'"
  @keydown="handleKeydown"
/>
<el-button
  :type="isSending ? 'danger' : 'primary'"
  class="send-btn"
  :disabled="(!isSending && !inputText.trim()) || sessionStatus !== 'ready'"
  @click="isSending ? handleStop() : handleSend()"
>
  {{ isSending ? '停止' : '发送' }}
</el-button>
```

- [ ] **Step 5: Run the frontend build**

Run: `cd hify-web && npm run build`

Expected: PASS with no TypeScript template errors.

---

### Task 4: Polish Styles and Verify Scope

**Files:**
- Modify: `hify-web/src/views/chat/index.vue`

- [ ] **Step 1: Replace hard-coded layout colors with design-system tokens where practical**

Use these scoped style updates:

```css
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 48px);
  min-height: 560px;
  background-color: var(--color-bg-subtle);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background-color: var(--color-bg-elevated);
  border-bottom: 1px solid var(--color-border-default);
  flex-shrink: 0;
}

.session-state {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.session-error {
  color: var(--color-error);
  font-size: var(--text-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

- [ ] **Step 2: Add empty state and stopped metadata styles**

```css
.empty-state {
  margin: auto;
  text-align: center;
  color: var(--color-text-secondary);
}

.empty-title {
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
  color: var(--color-text-primary);
}

.empty-subtitle {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.message-meta {
  margin-top: var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}
```

- [ ] **Step 3: Keep existing bubble and markdown styles, but swap obvious token colors**

Examples:

```css
.message-row.user .message-bubble {
  background-color: var(--color-primary-500);
  color: var(--color-text-inverse);
  border-bottom-right-radius: 4px;
}

.message-row.assistant .message-bubble {
  background-color: var(--color-bg-elevated);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border-default);
  border-bottom-left-radius: 4px;
}

.message-error {
  color: var(--color-error);
}
```

- [ ] **Step 4: Run final frontend build**

Run: `cd hify-web && npm run build`

Expected: PASS.

- [ ] **Step 5: Check changed files**

Run: `git diff --stat`

Expected: only `hify-web/src/views/chat/index.vue` should have implementation changes. The spec and plan docs may still exist until cleanup after implementation.

- [ ] **Step 6: Commit implementation**

```bash
git add hify-web/src/views/chat/index.vue
git commit -m "fix: improve chat sending feedback"
```

---

### Task 5: Clean Temporary Planning Docs

**Files:**
- Delete: `docs/superpowers/specs/2026-06-04-chat-experience-design.md`
- Delete: `docs/superpowers/plans/2026-06-04-chat-experience.md`

- [ ] **Step 1: Remove the temporary Chat planning files after the implementation commit**

```powershell
Remove-Item -LiteralPath docs\superpowers\specs\2026-06-04-chat-experience-design.md
Remove-Item -LiteralPath docs\superpowers\plans\2026-06-04-chat-experience.md
```

- [ ] **Step 2: Verify cleanup**

Run: `git status --short`

Expected: only the two deleted planning files are listed.

- [ ] **Step 3: Commit cleanup**

```bash
git add docs/superpowers/specs/2026-06-04-chat-experience-design.md docs/superpowers/plans/2026-06-04-chat-experience.md
git commit -m "docs: remove temporary chat experience planning docs"
```

---

## Self-Review

- Spec coverage: session state, stop generation, message feedback, empty state, conservative concurrency, and build verification are covered.
- Scope check: the plan keeps the API and backend unchanged and does not add history, session switching, prompt suggestions, or agent selection.
- Type consistency: `SessionStatus`, `AssistantMessageStatus`, `sessionStatus`, `sessionError`, `stoppingMessageIndex`, and `finishSending` are used consistently across tasks.
