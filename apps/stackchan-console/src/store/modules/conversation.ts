import type { Conversation, ConversationMessage, GenerationStatus, MessageStartedEvent } from '@/api/modules/companion'
import {
  createConversation,
  getConversationMessages,
  listConversations,
  streamMessage,
  StreamMessageServerError,
} from '@/api/modules/companion'

type RetryMode = 'RECONCILE' | 'REGENERATE'

interface FailedRequest {
  conversationId: string
  content: string
  clientMessageId: string
  retryMode: RetryMode
}

function now() {
  return new Date().toISOString()
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

export const useConversationStore = defineStore('conversation', () => {
  const conversations = ref<Conversation[]>([])
  const messages = ref<ConversationMessage[]>([])
  const activeConversationId = ref<string>()
  const isLoading = ref(false)
  const isSending = ref(false)
  const errorMessage = ref('')
  const failedRequest = ref<FailedRequest>()
  const lastFailedInput = computed(() => failedRequest.value?.content || '')
  const abortController = ref<AbortController>()
  const activeRoleId = ref<string>()

  function addConversation(conversation: Conversation) {
    const existingIndex = conversations.value.findIndex(item => item.id === conversation.id)
    if (existingIndex >= 0) {
      conversations.value.splice(existingIndex, 1, conversation)
    }
    else {
      conversations.value.unshift(conversation)
    }
  }

  function updateMessage(messageId: string, update: Partial<ConversationMessage>) {
    const message = messages.value.find(item => item.id === messageId)
    if (message) {
      Object.assign(message, update)
    }
  }

  function startMessages(event: MessageStartedEvent, content: string) {
    if (!messages.value.some(message => message.id === event.userMessageId)) {
      messages.value.push({
        id: event.userMessageId,
        role: 'USER',
        content,
        generationStatus: 'COMPLETED',
        createdAt: now(),
        completedAt: now(),
      })
    }
    if (!messages.value.some(message => message.id === event.assistantMessageId)) {
      messages.value.push({
        id: event.assistantMessageId,
        role: 'ASSISTANT',
        content: '',
        generationStatus: 'STREAMING',
        createdAt: now(),
        completedAt: null,
      })
    }
  }

  function finaliseAssistant(messageId: string, status: GenerationStatus, content?: string) {
    updateMessage(messageId, {
      ...(content !== undefined ? { content } : {}),
      generationStatus: status,
      completedAt: now(),
    })
  }

  async function loadConversations() {
    isLoading.value = true
    try {
      conversations.value = await listConversations(activeRoleId.value)
      if (activeConversationId.value && conversations.value.some(item => item.id === activeConversationId.value)) {
        await selectConversation(activeConversationId.value)
      }
      else if (conversations.value[0]) {
        await selectConversation(conversations.value[0].id)
      }
    }
    finally {
      isLoading.value = false
    }
  }

  async function selectConversation(conversationId: string) {
    if (isSending.value) {
      return
    }
    if (activeConversationId.value !== conversationId) {
      errorMessage.value = ''
      failedRequest.value = undefined
    }
    activeConversationId.value = conversationId
    messages.value = await getConversationMessages(conversationId)
  }

  async function startNewConversation(roleId = activeRoleId.value) {
    const conversation = await createConversation(roleId)
    addConversation(conversation)
    activeConversationId.value = conversation.id
    messages.value = []
    errorMessage.value = ''
    failedRequest.value = undefined
    return conversation
  }

  async function selectRole(roleId: string) {
    if (isSending.value) return
    activeRoleId.value = roleId
    activeConversationId.value = undefined
    messages.value = []
    await loadConversations()
  }

  async function ensureActiveConversation() {
    if (activeConversationId.value) {
      return activeConversationId.value
    }
    const conversation = await startNewConversation()
    return conversation.id
  }

  async function send(content: string) {
    const text = content.trim()
    if (!text || isSending.value) {
      return
    }

    await sendRequest(text, crypto.randomUUID())
  }

  async function sendRequest(content: string, clientMessageId: string) {
    const conversationId = await ensureActiveConversation()
    const controller = new AbortController()
    let assistantMessageId = ''
    let terminalReceived = false
    abortController.value = controller
    isSending.value = true
    errorMessage.value = ''
    try {
      await streamMessage(conversationId, {
        clientMessageId,
        content,
      }, {
        onMessage: (event) => {
          assistantMessageId = event.assistantMessageId
          startMessages(event, content)
        },
        onDelta: (event) => {
          updateMessage(event.messageId, {
            content: (messages.value.find(message => message.id === event.messageId)?.content || '') + event.text,
          })
        },
        onCompleted: (event) => {
          terminalReceived = true
          finaliseAssistant(event.messageId, 'COMPLETED', event.content)
          failedRequest.value = undefined
        },
        onInterrupted: (event) => {
          terminalReceived = true
          finaliseAssistant(event.messageId, 'INTERRUPTED', event.content)
          failedRequest.value = undefined
        },
        onError: () => {
          terminalReceived = true
        },
      }, controller.signal)
      if (!terminalReceived) {
        messages.value = await getConversationMessages(conversationId)
        failedRequest.value = undefined
      }
    }
    catch (error) {
      if (isAbortError(error)) {
        failedRequest.value = undefined
        if (assistantMessageId) {
          finaliseAssistant(assistantMessageId, 'INTERRUPTED')
        }
        return
      }
      const message = error instanceof Error ? error.message : '消息发送失败，请稍后重试。'
      errorMessage.value = message
      failedRequest.value = {
        conversationId,
        content,
        clientMessageId,
        retryMode: error instanceof StreamMessageServerError ? 'REGENERATE' : 'RECONCILE',
      }
      if (assistantMessageId) {
        finaliseAssistant(assistantMessageId, 'FAILED')
      }
      throw error
    }
    finally {
      abortController.value = undefined
      isSending.value = false
    }
  }

  async function retryFailed() {
    const request = failedRequest.value
    if (!request || isSending.value) {
      return
    }
    await sendRequest(
      request.content,
      request.retryMode === 'RECONCILE' ? request.clientMessageId : crypto.randomUUID(),
    )
  }

  function cancel() {
    abortController.value?.abort()
  }

  return {
    conversations,
    messages,
    activeConversationId, activeRoleId,
    isLoading,
    isSending,
    errorMessage,
    lastFailedInput,
    loadConversations,
    selectConversation, selectRole,
    startNewConversation,
    send,
    retryFailed,
    cancel,
  }
}, {
  persist: {
    pick: ['activeConversationId', 'activeRoleId'],
  },
})
