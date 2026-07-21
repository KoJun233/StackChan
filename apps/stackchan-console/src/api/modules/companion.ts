import { apiJson, csrfHeaders, notifySessionExpired, responseError } from '../client'

export interface Conversation {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export type ConversationRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
export type GenerationStatus = 'STREAMING' | 'COMPLETED' | 'INTERRUPTED' | 'FAILED'

export interface ConversationMessage {
  id: string
  role: ConversationRole
  content: string
  generationStatus: GenerationStatus
  createdAt: string
  completedAt: string | null
}

export interface StreamMessageInput {
  clientMessageId: string
  content: string
}

export interface MessageStartedEvent {
  conversationId: string
  userMessageId: string
  assistantMessageId: string
}

export interface DeltaEvent {
  messageId: string
  text: string
}

export interface CompletedEvent {
  messageId: string
  content: string
}

export interface InterruptedEvent {
  messageId: string
  content: string
}

export class StreamMessageServerError extends Error {
  constructor(public readonly code: string, message: string) {
    super(message)
    this.name = 'StreamMessageServerError'
  }
}

export interface StreamMessageHandlers {
  onCompleted?: (event: CompletedEvent) => void
  onDelta?: (event: DeltaEvent) => void
  onError?: (error: Error) => void
  onInterrupted?: (event: InterruptedEvent) => void
  onMessage?: (event: MessageStartedEvent) => void
}

export function createConversation(): Promise<Conversation> {
  return apiJson('/api/v1/conversations', { method: 'POST' })
}

export function listConversations(): Promise<Conversation[]> {
  return apiJson('/api/v1/conversations')
}

export function getConversationMessages(conversationId: string): Promise<ConversationMessage[]> {
  return apiJson(`/api/v1/conversations/${conversationId}/messages`)
}

export async function streamMessage(
  conversationId: string,
  input: StreamMessageInput,
  handlers: StreamMessageHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`/api/v1/conversations/${conversationId}/messages:stream`, {
    method: 'POST',
    credentials: 'same-origin',
    signal,
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...csrfHeaders(),
    },
    body: JSON.stringify(input),
  })
  if (response.status === 401) {
    notifySessionExpired()
  }
  if (!response.ok) {
    throw await responseError(response, '消息发送失败，请稍后重试。')
  }
  if (!response.body) {
    throw new Error('连接已建立，但没有收到回复内容。')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  async function processFrame(frame: string): Promise<void> {
    const lines = frame.split('\n')
    const eventName = lines.find(line => line.startsWith('event:'))?.slice(6).trim() || 'message'
    const data = lines
      .filter(line => line.startsWith('data:'))
      .map(line => line.slice(5).trimStart())
      .join('\n')
    if (!data) {
      return
    }
    let event: unknown
    try {
      event = JSON.parse(data)
    }
    catch {
      throw new Error('收到无法识别的流式回复。')
    }

    if (eventName === 'message') {
      handlers.onMessage?.(event as MessageStartedEvent)
    }
    else if (eventName === 'delta') {
      handlers.onDelta?.(event as DeltaEvent)
    }
    else if (eventName === 'completed') {
      handlers.onCompleted?.(event as CompletedEvent)
    }
    else if (eventName === 'interrupted') {
      handlers.onInterrupted?.(event as InterruptedEvent)
    }
    else if (eventName === 'error') {
      const code = (event as { code: string }).code
      const message = (event as { message?: string }).message || '模型服务暂时不可用，请稍后重试。'
      const error = new StreamMessageServerError(code, message)
      handlers.onError?.(error)
      throw error
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done }).replaceAll('\r\n', '\n')
      let separatorIndex = buffer.indexOf('\n\n')
      while (separatorIndex >= 0) {
        const frame = buffer.slice(0, separatorIndex)
        buffer = buffer.slice(separatorIndex + 2)
        await processFrame(frame)
        separatorIndex = buffer.indexOf('\n\n')
      }
      if (done) {
        if (buffer.trim()) {
          await processFrame(buffer)
        }
        break
      }
    }
  }
  finally {
    reader.releaseLock()
  }
}
