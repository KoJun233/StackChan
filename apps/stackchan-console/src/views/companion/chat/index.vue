<script setup lang="ts">
defineOptions({ name: 'CompanionChat' })

const conversationStore = useConversationStore()
const { activeConversationId, conversations, errorMessage, isLoading, isSending, lastFailedInput, messages } = storeToRefs(conversationStore)
const draft = ref('')

async function send() {
  const content = draft.value
  if (!content.trim()) {
    return
  }
  draft.value = ''
  try {
    await conversationStore.send(content)
  }
  catch (error) {
    useFaToast().error('发送失败', { description: error instanceof Error ? error.message : '消息发送失败，请稍后重试。' })
  }
}

async function retry() {
  try {
    await conversationStore.retryFailed()
  }
  catch (error) {
    useFaToast().error('重试失败', {
      description: error instanceof Error ? error.message : '消息发送失败，请稍后重试。',
    })
  }
}

onMounted(async () => {
  try {
    await conversationStore.loadConversations()
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法加载历史对话。' })
  }
})
</script>

<template>
  <FaPageMain title="陪伴聊天" description="你的对话会保存到服务器，便于之后建立长期记忆。">
    <div class="gap-4 grid min-h-[620px] lg:grid-cols-[260px_minmax(0,1fr)]">
      <FaCard class="min-h-0">
        <template #action>
          <FaButton size="sm" :disabled="isSending" @click="conversationStore.startNewConversation">新对话</FaButton>
        </template>
        <FaScrollArea class="h-[540px]">
          <FaEmpty v-if="!conversations.length" description="还没有对话" />
          <div v-else class="space-y-1">
            <FaButton
              v-for="conversation in conversations"
              :key="conversation.id"
              class="w-full justify-start"
              :disabled="isSending"
              :variant="activeConversationId === conversation.id ? 'secondary' : 'ghost'"
              @click="conversationStore.selectConversation(conversation.id)"
            >
              {{ conversation.title }}
            </FaButton>
          </div>
        </FaScrollArea>
      </FaCard>
      <FaCard class="min-h-0 flex flex-col">
        <FaLoading :loading="isLoading">
          <FaScrollArea class="h-[430px] pr-3">
            <FaEmpty v-if="!messages.length" description="和你的机器人说点什么吧" />
            <div v-else class="space-y-4">
              <div v-for="message in messages" :key="message.id" :class="message.role === 'USER' ? 'items-end' : 'items-start'" class="flex flex-col">
                <div class="max-w-[85%] rounded-xl px-4 py-3" :class="message.role === 'USER' ? 'bg-primary text-primary-foreground' : 'bg-muted'">
                  {{ message.content || (message.generationStatus === 'STREAMING' ? '正在思考…' : '') }}
                </div>
                <span v-if="message.generationStatus === 'FAILED'" class="mt-1 text-xs text-destructive">回复失败</span>
                <span v-else-if="message.generationStatus === 'INTERRUPTED'" class="mt-1 text-xs text-muted-foreground">回复已取消</span>
              </div>
            </div>
          </FaScrollArea>
        </FaLoading>
        <div class="mt-4 border-t pt-4">
          <div v-if="errorMessage" class="mb-3 text-sm text-destructive flex items-center justify-between">
            <span>{{ errorMessage }}</span>
            <FaButton v-if="lastFailedInput" size="sm" variant="outline" @click="retry">重试</FaButton>
          </div>
          <FaTextarea v-model="draft" :disabled="isSending" rows="3" placeholder="输入你想和机器人说的话…" @keydown.ctrl.enter.prevent="send" />
          <div class="mt-3 flex justify-end gap-3">
            <span v-if="isSending" class="mr-auto text-sm text-muted-foreground">正在回复…</span>
            <FaButton v-if="isSending" variant="outline" @click="conversationStore.cancel">停止生成</FaButton>
            <FaButton :disabled="!draft.trim() || isSending" @click="send">发送</FaButton>
          </div>
        </div>
      </FaCard>
    </div>
  </FaPageMain>
</template>
