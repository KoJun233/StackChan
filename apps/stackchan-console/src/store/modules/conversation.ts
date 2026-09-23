import type { Conversation, ConversationMessage, GenerationStatus, MessageStartedEvent } from '@/api/modules/companion'
import { createConversation, getConversationMessages, listConversations, streamMessage, StreamMessageServerError } from '@/api/modules/companion'

type RetryMode = 'RECONCILE' | 'REGENERATE'
interface FailedRequest {
  conversationId?: string
  roleId?: string
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
  const isCreating = ref(false)
  const historyError = ref('')
  let historyRequest = 0
  let creation: Promise<Conversation> | undefined
  function clearFailure() {
    errorMessage.value = ''
    failedRequest.value = undefined
  }
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
    if (isSending.value || isCreating.value) {
      return
    }
    const request = ++historyRequest
    const roleId = activeRoleId.value
    isLoading.value = true
    historyError.value = ''
    try {
      const result = await listConversations(roleId)
      if (request !== historyRequest || roleId !== activeRoleId.value) {
        return
      }
      conversations.value = result
      const target = result.find(item => item.id === activeConversationId.value) ?? result[0]
      if (target) {
        await selectConversation(target.id)
      }
      else {
        activeConversationId.value = undefined
        messages.value = []
        clearFailure()
      }
    }
    catch (error) {
      if (request !== historyRequest || roleId !== activeRoleId.value) {
        return
      }
      historyError.value = error instanceof Error ? error.message : '无法加载历史对话。'
      throw error
    }
    finally {
      if (request === historyRequest) {
        isLoading.value = false
      }
    }
  }
  async function selectConversation(conversationId: string) {
    if (isSending.value || isCreating.value) {
      return
    }
    const request = ++historyRequest
    const roleId = activeRoleId.value
    if (activeConversationId.value !== conversationId) {
      errorMessage.value = ''
      failedRequest.value = undefined
    }
    activeConversationId.value = conversationId
    messages.value = []
    isLoading.value = true
    historyError.value = ''
    try {
      const result = await getConversationMessages(conversationId)
      if (request === historyRequest && activeRoleId.value === roleId) {
        messages.value = result
      }
    }
    catch (error) {
      if (request !== historyRequest || activeRoleId.value !== roleId) {
        return
      }
      historyError.value = error instanceof Error ? error.message : '无法加载当前对话。'
      throw error
    }
    finally {
      if (request === historyRequest) {
        isLoading.value = false
      }
    }
  }
  async function createForCurrentRole() {
    if (creation) {
      return creation
    }
    const roleId = activeRoleId.value
    ++historyRequest
    isLoading.value = false
    isCreating.value = true
    creation = (async () => {
      try {
        const conversation = await createConversation(roleId)
        if (roleId !== activeRoleId.value) {
          throw new Error('伙伴已切换，请重新创建对话。')
        }
        addConversation(conversation)
        activeConversationId.value = conversation.id
        messages.value = []
        historyError.value = ''
        clearFailure()
        return conversation
      }
      finally {
        isCreating.value = false
        creation = undefined
      }
    })()
    return creation
  }
  async function startNewConversation() {
    if (isSending.value) {
      return
    }
    return createForCurrentRole()
  }
  async function selectRole(roleId: string) {
    if (isSending.value || isCreating.value) {
      return
    }
    ++historyRequest
    clearFailure()
    historyError.value = ''
    activeRoleId.value = roleId
    activeConversationId.value = undefined
    conversations.value = []
    messages.value = []
    await loadConversations()
  }
  async function ensureActiveConversation() {
    if (activeConversationId.value) {
      return activeConversationId.value
    }
    const conversation = await createForCurrentRole()
    return conversation.id
  }
  async function send(content: string) {
    const text = content.trim()
    if (!text || isSending.value || isCreating.value || isLoading.value || historyError.value) {
      return
    }
    await sendRequest(text, crypto.randomUUID())
  }
  async function sendRequest(content: string, clientMessageId: string) {
    let conversationId = activeConversationId.value
    const roleId = activeRoleId.value
    const controller = new AbortController()
    let assistantMessageId = ''
    let terminalReceived = false
    abortController.value = controller
    isSending.value = true
    errorMessage.value = ''
    try {
      conversationId = await ensureActiveConversation()
      if (controller.signal.aborted) {
        throw new DOMException('已取消', 'AbortError')
      }
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
        roleId,
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
    if (!request || isSending.value || isCreating.value || isLoading.value) {
      return
    }
    if (request.roleId !== activeRoleId.value || request.conversationId !== activeConversationId.value) {
      clearFailure()
      return
    }
    await sendRequest(request.content, request.retryMode === 'RECONCILE' ? request.clientMessageId : crypto.randomUUID())
  }
  function cancel() {
    abortController.value?.abort()
  }
  return {
    conversations,
    messages,
    activeConversationId,
    activeRoleId,
    isLoading,
    isSending,
    isCreating,
    historyError,
    errorMessage,
    lastFailedInput,
    loadConversations,
    selectConversation,
    selectRole,
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
