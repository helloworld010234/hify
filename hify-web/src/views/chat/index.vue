<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage, ElButton, ElInput, ElTag } from 'element-plus'
import { Close, Position, RefreshRight } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { chatApi } from '../../api/chat'
import type { ChatStreamEvent } from '../../api/chat'

marked.setOptions({
  gfm: true,
  breaks: true,
})

function renderMarkdown(content: string): string {
  if (!content) return ''
  const rawHtml = marked.parse(content, { async: false }) as string
  return DOMPurify.sanitize(rawHtml)
}

type SessionStatus = 'creating' | 'ready' | 'failed'
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
const sessionStatus = ref<SessionStatus>('creating')
const sessionError = ref('')
const stoppingMessageIndex = ref<number | null>(null)

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

async function handleSend() {
  const content = inputText.value.trim()
  if (!content || isSending.value) return

  if (sessionStatus.value !== 'ready' || !sessionId.value) {
    ElMessage.warning('会话准备完成后再发送消息')
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
          aiMsg.errorMessage = data.message || data.content || '流式响应出错'
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
          aiMsg.content = '已停止生成'
        }
        aiMsg.htmlContent = renderMarkdown(aiMsg.content)
      } else {
        aiMsg.status = 'error'
        const errorMessage = err.message || '发送失败，请稍后重试'
        aiMsg.errorMessage = errorMessage
        aiMsg.content = errorMessage
        ElMessage.error(errorMessage)
      }
    }

    finishSending()
  }
}

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

function retryCreateSession() {
  localStorage.removeItem('chatSessionId')
  initializeSession(true)
}

onMounted(() => {
  initializeSession()
})
</script>

<template>
  <div class="chat-page">
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
        :icon="RefreshRight"
        @click="retryCreateSession"
      >
        重试
      </el-button>
    </div>

    <div ref="messageListRef" class="message-list">
      <div v-if="sessionStatus === 'ready' && messages.length === 0" class="empty-state">
        <div class="empty-title">当前会话已准备好</div>
        <div class="empty-subtitle">输入消息后即可开始对话。</div>
      </div>

      <template v-for="(msg, _index) in messages" :key="_index">
        <div :class="['message-row', msg.role]">
          <div class="message-bubble">
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
          </div>
        </div>
      </template>
    </div>

    <div class="input-area">
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
        :icon="isSending ? Close : Position"
        :disabled="(!isSending && !inputText.trim()) || sessionStatus !== 'ready'"
        @click="isSending ? handleStop() : handleSend()"
      >
        {{ isSending ? '停止' : '发送' }}
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
  min-width: 88px;
}

.markdown-body {
  line-height: 1.6;
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

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding-left: 12px;
  border-left: 4px solid var(--color-border-strong);
  color: var(--color-text-secondary);
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--color-border-strong);
  padding: 6px 12px;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--color-border-default);
  margin: 12px 0;
}

@media (max-width: 720px) {
  .chat-page {
    height: calc(100vh - 32px);
    min-height: 520px;
  }

  .message-bubble {
    max-width: 88%;
  }

  .input-area {
    flex-direction: column;
  }

  .send-btn {
    align-self: stretch;
  }
}
</style>
