export interface SendMessageRequest {
  message: string
  contextStrategy?: 'SLIDING_WINDOW' | 'FIXED_TURNS'
}

export interface ChatStreamEvent {
  type: 'delta' | 'done' | 'error'
  content?: string
  finishReason?: string
  latencyMs?: number
  code?: string
  message?: string
}

export const chatApi = {
  /**
   * 流式发送消息（SSE，fetch 手动消费）
   */
  streamSendMessage: async (
    sessionId: string,
    requestBody: SendMessageRequest,
    callbacks: {
      onDelta: (content: string) => void
      onDone?: (data: ChatStreamEvent) => void
      onError?: (data: ChatStreamEvent) => void
    },
    signal?: AbortSignal
  ): Promise<void> => {
    const res = await fetch(`/api/v1/chat/sessions/${sessionId}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify(requestBody),
      signal,
    })

    if (!res.ok) {
      const errorText = await res.text().catch(() => 'Unknown error')
      throw new Error(`HTTP ${res.status}: ${errorText}`)
    }

    const reader = res.body?.getReader()
    if (!reader) throw new Error('Response body is empty')

    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // 按行分割
        const lines = buffer.split('\n')
        buffer = lines.pop() || '' // 最后一行可能不完整，留到下一轮

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed.startsWith('data:')) continue

          const jsonStr = trimmed.slice(5).trim()
          if (!jsonStr) continue

          if (jsonStr === '[DONE]') {
            continue
          }

          try {
            const data: ChatStreamEvent = JSON.parse(jsonStr)
            if (data.type === 'delta' && typeof data.content === 'string') {
              callbacks.onDelta(data.content)
            } else if (data.type === 'done') {
              callbacks.onDone?.(data)
            } else if (data.type === 'error') {
              callbacks.onError?.(data)
            }
          } catch (e) {
            // ignore parse error for malformed lines
          }
        }
      }

      // 处理 buffer 中剩余的内容
      if (buffer.trim()) {
        const trimmed = buffer.trim()
        if (trimmed.startsWith('data:')) {
          const jsonStr = trimmed.slice(5).trim()
          if (jsonStr && jsonStr !== '[DONE]') {
            try {
              const data: ChatStreamEvent = JSON.parse(jsonStr)
              if (data.type === 'done') callbacks.onDone?.(data)
              else if (data.type === 'error') callbacks.onError?.(data)
            } catch { /* ignore */ }
          }
        }
      }
    } finally {
      reader.cancel().catch(() => { })
    }
  },

  /**
   * 创建新会话（供页面初始化使用）
   */
  createSession: async (agentId?: number) => {
    const res = await fetch('/api/v1/chat/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agentId: agentId ?? 1 })
    })
    if (!res.ok) {
      const err = await res.text().catch(() => '')
      throw new Error(`创建会话失败: ${res.status} ${err}`)
    }
    return res.json()
  }
}
