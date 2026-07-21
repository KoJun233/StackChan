import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const companionApi = vi.hoisted(() => ({
  createConversation: vi.fn(),
  getConversationMessages: vi.fn(),
  listConversations: vi.fn(),
  streamMessage: vi.fn(),
  StreamMessageServerError: class StreamMessageServerError extends Error {
    constructor(public readonly code: string, message: string) {
      super(message)
    }
  },
}))

vi.mock('@/api/modules/companion', () => companionApi)

import { useConversationStore } from './conversation'

describe('conversation store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    companionApi.createConversation.mockReset()
    companionApi.getConversationMessages.mockReset()
    companionApi.listConversations.mockReset()
    companionApi.streamMessage.mockReset()
    companionApi.createConversation.mockResolvedValue({
      id: 'conversation-id',
      title: '新的陪伴对话',
      createdAt: '2026-07-18T00:00:00Z',
      updatedAt: '2026-07-18T00:00:00Z',
    })
  })

  it('sends a message and completes the streamed assistant reply', async () => {
    companionApi.streamMessage.mockImplementation(async (_conversationId, _input, handlers) => {
      handlers.onMessage({
        conversationId: 'conversation-id',
        userMessageId: 'user-message-id',
        assistantMessageId: 'assistant-message-id',
      })
      handlers.onDelta({ messageId: 'assistant-message-id', text: '今天也辛苦了' })
      handlers.onCompleted({ messageId: 'assistant-message-id', content: '今天也辛苦了。' })
    })
    const store = useConversationStore()

    await store.send('今天有点累')

    expect(store.activeConversationId).toBe('conversation-id')
    expect(store.messages).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: 'user-message-id', content: '今天有点累', role: 'USER' }),
      expect.objectContaining({ id: 'assistant-message-id', content: '今天也辛苦了。', generationStatus: 'COMPLETED', role: 'ASSISTANT' }),
    ]))
  })

  it('marks the current streamed reply as interrupted when cancelled', async () => {
    companionApi.streamMessage.mockImplementation(async (_conversationId, _input, handlers, signal) => {
      handlers.onMessage({
        conversationId: 'conversation-id',
        userMessageId: 'user-message-id',
        assistantMessageId: 'assistant-message-id',
      })
      await new Promise((_, reject) => signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))))
    })
    const store = useConversationStore()
    const pending = store.send('先停一下')

    await vi.waitFor(() => expect(store.isSending).toBe(true))
    store.cancel()
    await pending

    expect(store.messages.find(message => message.id === 'assistant-message-id')).toMatchObject({
      generationStatus: 'INTERRUPTED',
    })
  })

  it('does not replace the active messages while a reply is streaming', async () => {
    companionApi.streamMessage.mockImplementation(async (_conversationId, _input, handlers, signal) => {
      handlers.onMessage({
        conversationId: 'conversation-id',
        userMessageId: 'user-message-id',
        assistantMessageId: 'assistant-message-id',
      })
      await new Promise((_, reject) => signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))))
    })
    const store = useConversationStore()
    const pending = store.send('不要切换')

    await vi.waitFor(() => expect(store.isSending).toBe(true))
    await store.selectConversation('another-conversation')

    expect(store.activeConversationId).toBe('conversation-id')
    expect(companionApi.getConversationMessages).not.toHaveBeenCalled()
    store.cancel()
    await pending
  })

  it('reuses the client message id when reconciling a transport failure', async () => {
    const requestIds: string[] = []
    companionApi.streamMessage
      .mockImplementationOnce(async (_conversationId, input) => {
        requestIds.push(input.clientMessageId)
        throw new TypeError('network disconnected')
      })
      .mockImplementationOnce(async (_conversationId, input, handlers) => {
        requestIds.push(input.clientMessageId)
        handlers.onMessage({ conversationId: 'conversation-id', userMessageId: 'u', assistantMessageId: 'a' })
        handlers.onCompleted({ messageId: 'a', content: 'done' })
      })
    const store = useConversationStore()

    await expect(store.send('hello')).rejects.toThrow('network disconnected')
    await store.retryFailed()

    expect(requestIds[1]).toBe(requestIds[0])
    expect(store.lastFailedInput).toBe('')
  })

  it('generates a new client message id after a confirmed server failure', async () => {
    const requestIds: string[] = []
    companionApi.streamMessage
      .mockImplementationOnce(async (_conversationId, input) => {
        requestIds.push(input.clientMessageId)
        throw new companionApi.StreamMessageServerError('provider_unavailable', 'unavailable')
      })
      .mockImplementationOnce(async (_conversationId, input, handlers) => {
        requestIds.push(input.clientMessageId)
        handlers.onMessage({ conversationId: 'conversation-id', userMessageId: 'u', assistantMessageId: 'a' })
        handlers.onCompleted({ messageId: 'a', content: 'done' })
      })
    const store = useConversationStore()

    await expect(store.send('hello')).rejects.toThrow('unavailable')
    await store.retryFailed()

    expect(requestIds[1]).not.toBe(requestIds[0])
  })

  it('refreshes history when the stream ends without a terminal event', async () => {
    const persistedMessages = [{
      id: 'persisted-assistant',
      role: 'ASSISTANT',
      content: 'persisted response',
      generationStatus: 'COMPLETED',
      createdAt: '2026-07-18T00:00:00Z',
      completedAt: '2026-07-18T00:00:01Z',
    }]
    companionApi.streamMessage.mockImplementation(async (_conversationId, _input, handlers) => {
      handlers.onMessage({ conversationId: 'conversation-id', userMessageId: 'u', assistantMessageId: 'a' })
    })
    companionApi.getConversationMessages.mockResolvedValue(persistedMessages)
    const store = useConversationStore()

    await store.send('hello')

    expect(store.messages).toEqual(persistedMessages)
  })

  it('persists interrupted partial content as an interrupted assistant message', async () => {
    companionApi.streamMessage.mockImplementation(async (_conversationId, _input, handlers) => {
      handlers.onMessage({ conversationId: 'conversation-id', userMessageId: 'u', assistantMessageId: 'a' })
      handlers.onDelta({ messageId: 'a', text: 'partial' })
      handlers.onInterrupted({ messageId: 'a', content: 'persisted partial' })
    })
    const store = useConversationStore()

    await store.send('hello')

    expect(store.messages.find(message => message.id === 'a')).toMatchObject({
      content: 'persisted partial',
      generationStatus: 'INTERRUPTED',
    })
    expect(store.lastFailedInput).toBe('')
  })

  it('clears a failed retry before loading a different conversation', async () => {
    companionApi.streamMessage.mockRejectedValue(new TypeError('network disconnected'))
    companionApi.getConversationMessages.mockRejectedValue(new Error('history unavailable'))
    const store = useConversationStore()

    await expect(store.send('hello')).rejects.toThrow('network disconnected')
    await expect(store.selectConversation('another-conversation')).rejects.toThrow('history unavailable')

    expect(store.activeConversationId).toBe('another-conversation')
    expect(store.lastFailedInput).toBe('')
    expect(store.errorMessage).toBe('')
  })

  it('keeps a failed retry when reloading the current conversation', async () => {
    companionApi.streamMessage.mockRejectedValue(new TypeError('network disconnected'))
    companionApi.getConversationMessages.mockResolvedValue([])
    const store = useConversationStore()

    await expect(store.send('hello')).rejects.toThrow('network disconnected')
    await store.selectConversation('conversation-id')

    expect(store.lastFailedInput).toBe('hello')
    expect(store.errorMessage).toBe('network disconnected')
  })
})
