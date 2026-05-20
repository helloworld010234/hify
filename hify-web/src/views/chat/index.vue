<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage, ElButton, ElInput, ElTag } from 'element-plus'
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

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  isError?: boolean
  /** 缓存 Markdown 渲染结果，避免历史消息重复解析 */
  htmlContent?: string
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const isSending = ref(false)
const messageListRef = ref<HTMLDivElement>()
const sessionId = ref('')
const currentAbortController = ref<AbortController | null>(null)

// 自动滚到底
function scrollToBottom() {
  nextTick(() => {
    const el = messageListRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

// 发送键处理
function handleKeydown(e: Event | KeyboardEvent) {
  const ke = e as KeyboardEvent
  if (ke.key === 'Enter' && !ke.shiftKey) {
    ke.preventDefault()
    handleSend()
  }
}

// 核心：发送消息的完整时间线
async function handleSend() {
  const content = inputText.value.trim()
  if (!content || isSending.value) return

  // 必须已存在 sessionId（从 URL 或 localStorage 获取）
  if (!sessionId.value) {
    ElMessage.warning('请先创建或选择会话')
    return
  }

  // 1. 输入框立刻清空
  inputText.value = ''

  // 2. 用户气泡靠右出现
  messages.value.push({ role: 'user', content })
  scrollToBottom()

  // 3. AI 气泡靠左，显示加载动画
  const aiIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', loading: true })
  scrollToBottom()

  // 4. 发送按钮置为不可点击
  isSending.value = true

  // 准备可中断的流
  const abortController = new AbortController()
  currentAbortController.value = abortController

  try {
    await chatApi.streamSendMessage(
      sessionId.value,
      { message: content },
      {
        // delta chunk 逐字追加
        onDelta: (delta: string) => {
          const aiMsg = messages.value[aiIndex]
          if (!aiMsg) return
          aiMsg.content += delta
          // 首次收到内容时关闭 loading 动画，开始显示文字
          if (aiMsg.loading) {
            aiMsg.loading = false
          }
          scrollToBottom()
        },
        // done 事件：关闭 loading，恢复按钮，缓存渲染结果
        onDone: (data: ChatStreamEvent) => {
          const aiMsg = messages.value[aiIndex]
          if (!aiMsg) return
          aiMsg.loading = false
          aiMsg.htmlContent = renderMarkdown(aiMsg.content)
          isSending.value = false
          currentAbortController.value = null
          if (data.latencyMs !== undefined) {
            // 可选：显示耗时
            console.log('Latency:', data.latencyMs, 'ms')
          }
        },
        // error 事件：AI 气泡显示红色错误，恢复按钮
        onError: (data: ChatStreamEvent) => {
          const aiMsg = messages.value[aiIndex]
          if (!aiMsg) return
          aiMsg.loading = false
          aiMsg.isError = true
          aiMsg.content = data.message || data.content || '流式响应出错'
          isSending.value = false
          currentAbortController.value = null
          ElMessage.error(aiMsg.content)
        },
      },
      abortController.signal
    )
  } catch (err: any) {
    // fetch 异常 / HTTP 失败
    const aiMsg = messages.value[aiIndex]
    if (aiMsg) {
      aiMsg.loading = false
      aiMsg.isError = true
      aiMsg.content = err.message || '发送失败，请稍后重试'
    }
    isSending.value = false
    currentAbortController.value = null
    ElMessage.error(err.message || '发送失败')
  }
}

// 初始化 sessionId（优先 URL query，其次 localStorage，都没有则自动创建）
onMounted(async () => {
  const urlParams = new URLSearchParams(window.location.search)
  const sid = urlParams.get('sessionId') || localStorage.getItem('chatSessionId')
  if (sid) {
    sessionId.value = sid
    return
  }

  try {
    const data = await chatApi.createSession(6)
    sessionId.value = String(data.id || data.data?.id || '')
    if (sessionId.value) {
      localStorage.setItem('chatSessionId', sessionId.value)
    }
  } catch (e: any) {
    ElMessage.error('创建会话失败: ' + (e.message || ''))
  }
})
</script>

<template>
  <div class="chat-page">
    <!-- 顶部栏：显示当前 SessionId -->
    <div class="chat-header">
      <el-tag type="info" size="small">Session: {{ sessionId || '创建中…' }}</el-tag>
    </div>

    <!-- 消息列表 -->
    <div class="message-list" ref="messageListRef">
      <template v-for="(msg, _index) in messages" :key="_index">
        <div :class="['message-row', msg.role]">
          <div class="message-bubble">
            <!-- 加载动画 -->
            <template v-if="msg.loading">
              <div class="loading-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </template>
            <!-- 错误提示 -->
            <template v-else-if="msg.isError">
              <div class="message-error">{{ msg.content }}</div>
            </template>
            <!-- 正常内容 -->
            <template v-else>
              <!-- 用户消息：纯文本 -->
              <pre v-if="msg.role === 'user'" class="message-content">{{ msg.content }}</pre>
              <!-- AI 消息：Markdown 渲染（优先使用缓存） -->
              <div v-else class="markdown-body" v-html="msg.htmlContent ?? renderMarkdown(msg.content)"></div>
            </template>
          </div>
        </div>
      </template>
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="3"
        resize="none"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        :disabled="isSending"
        @keydown="handleKeydown"
      />
      <el-button
        type="primary"
        class="send-btn"
        :disabled="!inputText.trim() || isSending"
        @click="handleSend"
      >
        {{ isSending ? '发送中…' : '发送' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f5f5;
}

.chat-header {
  padding: 8px 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.message-row.user .message-bubble {
  background-color: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message-row.assistant .message-bubble {
  background-color: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 4px;
}

.message-content {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
}

.message-error {
  color: #f56c6c;
}

/* 加载动画 */
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
  background-color: #909399;
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
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

/* 输入区域 */
.input-area {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.input-area .el-textarea {
  flex: 1;
}

.send-btn {
  align-self: flex-end;
}

/* Markdown 渲染样式 */
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
  padding: 12px 16px;
  background-color: #f6f8fa;
  border-radius: 6px;
  overflow-x: auto;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.markdown-body :deep(code) {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  background-color: #f0f0f0;
  padding: 2px 6px;
  border-radius: 4px;
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
  border-left: 4px solid #dcdfe6;
  color: #606266;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 6px 12px;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #e4e7ed;
  margin: 12px 0;
}
</style>
