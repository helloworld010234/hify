<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Close, Position, RefreshRight } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { chatApi } from '../../api/chat'
import type { ChatStreamEvent } from '../../api/chat'
import { getAgentList, type AgentListItem } from '@/api/agent'

marked.setOptions({
  gfm: true,
  breaks: true,
})

function renderMarkdown(content: string): string {
  if (!content) return ''
  const rawHtml = marked.parse(content, { async: false }) as string
  return DOMPurify.sanitize(rawHtml)
}

type SessionStatus = 'idle' | 'creating' | 'ready' | 'failed'
type AssistantMessageStatus = 'loading' | 'streaming' | 'done' | 'stopped' | 'error'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  status?: AssistantMessageStatus
  errorMessage?: string
  htmlContent?: string
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const isSending = ref(false)
const messageListRef = ref<HTMLDivElement>()
const sessionId = ref('')
const currentAbortController = ref<AbortController | null>(null)
const sessionStatus = ref<SessionStatus>('idle')
const sessionError = ref('')
const stoppingMessageIndex = ref<number | null>(null)
const agentOptions = ref<AgentListItem[]>([])
const selectedAgentId = ref<number | null>(null)
const loadingAgents = ref(false)

function scrollToBottom() {
  nextTick(() => {
    const el = messageListRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function finishSending() {
  isSending.value = false
  currentAbortController.value = null
  stoppingMessageIndex.value = null
}

function handleStop() {
  if (!isSending.value || !currentAbortController.value) return
  stoppingMessageIndex.value = messages.value.length - 1
  currentAbortController.value.abort()
}

function handleKeydown(e: Event | KeyboardEvent) {
  const ke = e as KeyboardEvent
  if (ke.key === 'Enter' && !ke.shiftKey) {
    ke.preventDefault()
    handleSend()
  }
}

function resetSessionState() {
  messages.value = []
  inputText.value = ''
  sessionId.value = ''
  sessionStatus.value = 'idle'
  sessionError.value = ''
  isSending.value = false
  currentAbortController.value = null
  stoppingMessageIndex.value = null
}

async function loadAgents() {
  loadingAgents.value = true
  sessionError.value = ''
  try {
    const response = await getAgentList({ page: 1, size: 100, enabled: 1 })
    agentOptions.value = response.list || []

    if (!agentOptions.value.length) {
      selectedAgentId.value = null
      sessionStatus.value = 'failed'
      sessionError.value = 'No enabled agent is available. Please create and enable an agent first.'
      return
    }

    const savedAgentId = Number(localStorage.getItem('chatAgentId') || '')
    const matchedAgent = agentOptions.value.find(agent => agent.id === savedAgentId)
    selectedAgentId.value = matchedAgent?.id ?? agentOptions.value[0].id
    sessionStatus.value = 'idle'
  } catch (e: any) {
    selectedAgentId.value = null
    sessionStatus.value = 'failed'
    sessionError.value = e.message || 'Failed to load the agent list'
    ElMessage.error('Failed to load the agent list: ' + sessionError.value)
  } finally {
    loadingAgents.value = false
  }
}

async function handleCreateSession() {
  if (!selectedAgentId.value) {
    ElMessage.warning('Please select an agent first')
    return
  }

  sessionStatus.value = 'creating'
  sessionError.value = ''
  sessionId.value = ''
  messages.value = []
  inputText.value = ''

  try {
    const data = await chatApi.createSession(selectedAgentId.value)
    const createdSessionId = String(data.id || data.data?.id || '')
    if (!createdSessionId) {
      throw new Error('The create session API did not return a session id')
    }

    sessionId.value = createdSessionId
    sessionStatus.value = 'ready'
    localStorage.setItem('chatAgentId', String(selectedAgentId.value))
  } catch (e: any) {
    sessionStatus.value = 'failed'
    sessionError.value = e.message || 'Failed to create the session'
    ElMessage.error('Failed to create the session: ' + sessionError.value)
  }
}

function handleAgentChange(agentId: number | null) {
  selectedAgentId.value = agentId
  if (agentId == null) {
    localStorage.removeItem('chatAgentId')
  } else {
    localStorage.setItem('chatAgentId', String(agentId))
  }
  resetSessionState()
}

async function handleSend() {
  const content = inputText.value.trim()
  if (!content || isSending.value) return

  if (sessionStatus.value !== 'ready' || !sessionId.value) {
    ElMessage.warning('Please create a session before sending a message')
    return
  }

  inputText.value = ''
  messages.value.push({ role: 'user', content })
  scrollToBottom()

  const aiIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', status: 'loading' })
  scrollToBottom()

  isSending.value = true
  const abortController = new AbortController()
  currentAbortController.value = abortController

  try {
    await chatApi.streamSendMessage(
      sessionId.value,
      { message: content },
      {
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
          aiMsg.errorMessage = data.message || data.content || 'Streaming response failed'
          aiMsg.content = aiMsg.errorMessage
          finishSending()
          ElMessage.error(aiMsg.errorMessage)
        },
      },
      abortController.signal
    )
  } catch (err: any) {
    const aiMsg = messages.value[aiIndex]
    const isAbort = err?.name === 'AbortError'

    if (aiMsg) {
      if (isAbort && stoppingMessageIndex.value === aiIndex) {
        aiMsg.status = 'stopped'
        if (!aiMsg.content) {
          aiMsg.content = 'Generation stopped'
        }
        aiMsg.htmlContent = renderMarkdown(aiMsg.content)
      } else {
        aiMsg.status = 'error'
        const errorMessage = err.message || 'Send failed, please try again later'
        aiMsg.errorMessage = errorMessage
        aiMsg.content = errorMessage
        ElMessage.error(errorMessage)
      }
    }

    finishSending()
  }
}

function retryCreateSession() {
  handleCreateSession()
}

onMounted(() => {
  loadAgents()
})
</script>

<template>
  <div class="chat-page">
    <div class="chat-header">
      <div class="chat-header-left">
        <el-select
          data-testid="chat-agent-select"
          :model-value="selectedAgentId"
          placeholder="Select agent"
          filterable
          clearable
          style="width: 240px"
          :loading="loadingAgents"
          @update:modelValue="handleAgentChange"
        >
          <el-option
            v-for="agent in agentOptions"
            :key="agent.id"
            :label="agent.name"
            :value="agent.id"
          />
        </el-select>
        <el-button
          data-testid="chat-create-session-button"
          type="primary"
          :loading="sessionStatus === 'creating'"
          :disabled="loadingAgents || !selectedAgentId"
          @click="handleCreateSession"
        >
          Create Session
        </el-button>
      </div>

      <div class="session-state">
        <el-tag v-if="sessionStatus === 'idle'" type="info" size="small">No session yet</el-tag>
        <el-tag v-else-if="sessionStatus === 'creating'" type="info" size="small">Creating session</el-tag>
        <el-tag v-else-if="sessionStatus === 'failed'" type="danger" size="small">Session creation failed</el-tag>
        <el-tag v-else data-testid="chat-session-tag" type="success" size="small">Session: {{ sessionId }}</el-tag>
        <span v-if="sessionError" class="session-error">{{ sessionError }}</span>
      </div>

      <el-button
        v-if="sessionError && selectedAgentId"
        size="small"
        type="primary"
        :icon="RefreshRight"
        @click="retryCreateSession"
      >
        Retry
      </el-button>
    </div>

    <div ref="messageListRef" class="message-list">
      <div v-if="sessionStatus === 'ready' && messages.length === 0" class="empty-state">
        <div class="empty-title">The session is ready</div>
        <div class="empty-subtitle">Enter a message below to start chatting.</div>
      </div>

      <div v-else-if="sessionStatus !== 'ready'" class="empty-state">
        <div class="empty-title">Select an agent and create a session first</div>
        <div class="empty-subtitle">In pure UI mode, both agent selection and session creation are completed in the page.</div>
      </div>

      <template v-for="(msg, _index) in messages" :key="_index">
        <div :class="['message-row', msg.role]">
          <div class="message-bubble">
            <template v-if="msg.status === 'loading'">
              <div class="loading-dots" aria-label="Assistant is thinking">
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
                <div v-if="msg.status === 'stopped'" class="message-meta">Generation stopped</div>
              </div>
            </template>
          </div>
        </div>
      </template>
    </div>

    <div class="input-area">
      <el-input
        data-testid="chat-message-input"
        v-model="inputText"
        type="textarea"
        :rows="3"
        resize="none"
        placeholder="Enter a message. Press Enter to send and Shift+Enter for a new line."
        :disabled="isSending || sessionStatus !== 'ready'"
        @keydown="handleKeydown"
      />
      <el-button
        :type="isSending ? 'danger' : 'primary'"
        class="send-btn"
        :icon="isSending ? Close : Position"
        :disabled="(!isSending && !inputText.trim()) || sessionStatus !== 'ready'"
        @click="isSending ? handleStop() : handleSend()"
      >
        {{ isSending ? 'Stop' : 'Send' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
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

.chat-header-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
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

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

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

.message-row {
  display: flex;
  width: 100%;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: min(72%, 760px);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  font-size: var(--text-sm);
  line-height: 1.6;
  word-break: break-word;
}

.message-row.user .message-bubble {
  background-color: var(--color-primary-500);
  color: var(--color-text-inverse);
  border-bottom-right-radius: var(--radius-sm);
}

.message-row.assistant .message-bubble {
  background-color: var(--color-bg-elevated);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border-default);
  border-bottom-left-radius: var(--radius-sm);
  min-width: 96px;
  min-height: 44px;
}

.message-content {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
}

.message-error {
  color: var(--color-error);
}

.message-meta {
  margin-top: var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.loading-dots {
  display: flex;
  gap: 6px;
  align-items: center;
  height: 20px;
}

.loading-dots span {
  display: inline-block;
  width: 8px;
  height: 8px;
  background-color: var(--color-text-tertiary);
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.input-area {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background-color: var(--color-bg-elevated);
  border-top: 1px solid var(--color-border-default);
  flex-shrink: 0;
}

.input-area .el-textarea {
  flex: 1;
}

.send-btn {
  align-self: flex-end;
  width: 88px;
}

.markdown-body {
  line-height: 1.6;
  min-width: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 12px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}

.markdown-body :deep(p) {
  margin: 8px 0;
}

.markdown-body :deep(pre) {
  margin: 8px 0;
  padding: var(--space-3) var(--space-4);
  background-color: var(--color-bg-muted);
  border-radius: var(--radius-md);
  overflow-x: auto;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: var(--text-xs);
  line-height: 1.5;
}

.markdown-body :deep(code) {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: var(--text-xs);
  background-color: var(--color-bg-muted);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}

.markdown-body :deep(pre code) {
  background-color: transparent;
  padding: 0;
  border-radius: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0;
  padding-left: 24px;
}
</style>
