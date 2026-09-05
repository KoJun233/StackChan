<script setup lang="ts">
import type { TdChatItemMeta } from '@tdesign-vue-next/chat'
import TChatActionbar from '@tdesign-vue-next/chat/es/chat-actionbar'
import TChatContent from '@tdesign-vue-next/chat/es/chat-content'
import TChatList from '@tdesign-vue-next/chat/es/chat-list'
import { listRoles } from '@/api/modules/roles'
import '@tdesign-vue-next/chat/es/style/index.css'
import 'tdesign-vue-next/es/style/index.css'

defineOptions({ name: 'CompanionChat' })

const conversationStore = useConversationStore()
const { activeConversationId, activeRoleId, conversations, errorMessage, isLoading, isSending, lastFailedInput, messages } = storeToRefs(conversationStore)
const draft = ref('')
const roleOptions = ref<{ label: string, value: string }[]>([])

const chatItems = computed<TdChatItemMeta[]>(() => messages.value.map(message => ({
  name: message.role === 'USER' ? '你' : 'StackChan',
  role: message.role === 'USER' ? 'user' : 'assistant',
  datetime: formatMessageTime(message.createdAt),
  content: [{
    type: message.role === 'USER' ? 'text' : 'markdown',
    data: message.content,
  }],
  status: message.generationStatus === 'FAILED' ? 'error' : undefined,
})))

function formatMessageTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function chatContent(index: number) {
  const message = messages.value[index]
  if (!message) {
    return { type: 'text' as const, data: '' }
  }
  if (!message.content && message.generationStatus === 'STREAMING') {
    return { type: 'text' as const, data: '正在思考…' }
  }
  return {
    type: message.role === 'USER' ? 'text' as const : 'markdown' as const,
    data: message.content,
  }
}

async function send(value = draft.value) {
  const content = value.trim()
  if (!content) {
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

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return
  }
  event.preventDefault()
  void send()
}

onMounted(async () => {
  try {
    const roles = await listRoles()
    roleOptions.value = roles.filter(role => !role.archivedAt).map(role => ({ label: role.name, value: role.id }))
    if (!activeRoleId.value) {
      activeRoleId.value = roles.find(role => role.defaultRole)?.id ?? roleOptions.value[0]?.value
    }
    await conversationStore.loadConversations()
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法加载历史对话。' })
  }
})
</script>

<template>
  <AppPageShell title="陪伴聊天" description="选择一个角色继续历史对话；消息由现有服务端会话与流式生成能力管理。">
    <div class="chat-layout">
      <FaCard title="会话" description="角色与历史会话不会混在同一条时间线中。" class="session-panel min-h-0" content-class="flex min-h-0 flex-1 flex-col">
        <FaSelect :model-value="activeRoleId" :options="roleOptions" class="mb-3 w-full" :disabled="isSending" @update:model-value="conversationStore.selectRole(String($event))" />
        <template #action>
          <FaButton size="sm" :disabled="isSending" @click="conversationStore.startNewConversation">
            <FaIcon name="i-ri:add-line" />
            新对话
          </FaButton>
        </template>
        <FaScrollArea class="h-[160px] lg:flex-1 lg:h-auto lg:min-h-0">
          <FaEmpty v-if="!conversations.length" description="还没有对话" />
          <div v-else class="pr-2 space-y-1">
            <FaButton
              v-for="conversation in conversations"
              :key="conversation.id"
              class="w-full truncate justify-start"
              :disabled="isSending"
              :variant="activeConversationId === conversation.id ? 'secondary' : 'ghost'"
              @click="conversationStore.selectConversation(conversation.id)"
            >
              {{ conversation.title }}
            </FaButton>
          </div>
        </FaScrollArea>
      </FaCard>

      <FaCard
        title="当前对话"
        description="支持 Markdown、复制回复、自动滚动、停止生成与失败重试。"
        class="stackchan-chat min-h-0 overflow-hidden"
        content-class="flex min-h-0 flex-1 flex-col"
      >
        <FaLoading :loading="isLoading" class="h-full">
          <div class="chat-workspace">
            <TChatList
              v-if="chatItems.length"
              :data="chatItems"
              layout="both"
              auto-scroll
              default-scroll-to="bottom"
              show-scroll-button
              :clear-history="false"
              :text-loading="false"
              class="chat-message-list"
            >
              <template #content="{ index }">
                <TChatContent
                  :role="messages[index]?.role === 'USER' ? 'user' : 'assistant'"
                  :content="chatContent(index)"
                  :status="messages[index]?.generationStatus === 'FAILED' ? 'error' : ''"
                  :markdown-props="{ engine: 'marked', options: { breaks: true, gfm: true } }"
                />
                <p v-if="messages[index]?.generationStatus === 'FAILED'" class="text-xs text-destructive mt-1">
                  回复失败
                </p>
                <p v-else-if="messages[index]?.generationStatus === 'INTERRUPTED'" class="text-xs text-muted-foreground mt-1">
                  回复已取消
                </p>
              </template>
              <template #actionbar="{ index }">
                <TChatActionbar
                  v-if="messages[index]?.role === 'ASSISTANT' && messages[index]?.content"
                  :content="messages[index].content"
                  :action-bar="['copy']"
                />
              </template>
            </TChatList>
            <FaEmpty v-else class="chat-empty" description="和你的机器人说点什么吧" />
            <div class="chat-composer">
              <FaAlert v-if="errorMessage" variant="destructive" title="消息发送失败" :description="errorMessage" class="mb-3">
                <template v-if="lastFailedInput" #action>
                  <FaButton size="sm" variant="outline" @click="retry">
                    重试
                  </FaButton>
                </template>
              </FaAlert>
              <div class="composer-field">
                <FaTextarea
                  v-model="draft"
                  :disabled="isSending"
                  aria-label="对话消息"
                  placeholder="输入你想和机器人说的话…"
                  class="w-full"
                  input-class="min-h-20 max-h-40"
                  @keydown="handleComposerKeydown"
                />
                <div class="composer-actions">
                  <span class="text-xs text-muted-foreground">Enter 发送 · Shift + Enter 换行</span>
                  <FaButton v-if="isSending" variant="destructive" @click="conversationStore.cancel">
                    <FaIcon name="i-ri:stop-circle-line" />
                    停止生成
                  </FaButton>
                  <FaButton v-else :disabled="!draft.trim()" @click="send()">
                    <FaIcon name="i-ri:send-plane-2-line" />
                    发送
                  </FaButton>
                </div>
              </div>
            </div>
          </div>
        </FaLoading>
      </FaCard>
    </div>
  </AppPageShell>
</template>

<style scoped>
.stackchan-chat {
  --td-brand-color: oklch(var(--primary));
  --td-brand-color-hover: color-mix(in oklch, oklch(var(--primary)) 88%, black);
  --td-brand-color-focus: color-mix(in oklch, oklch(var(--primary)) 18%, transparent);
  --td-brand-color-active: color-mix(in oklch, oklch(var(--primary)) 92%, black);
  --td-bg-color-container: oklch(var(--card));
  --td-bg-color-specialcomponent: oklch(var(--muted));
  --td-text-color-primary: oklch(var(--foreground));
  --td-text-color-secondary: oklch(var(--muted-foreground));
  --td-text-color-disabled: color-mix(in oklch, oklch(var(--muted-foreground)) 70%, transparent);
  --td-border-level-1-color: oklch(var(--border));
  --td-border-level-2-color: oklch(var(--border));
}

.chat-layout {
  display: grid;
  gap: 1rem;
  min-height: 640px;
}

.stackchan-chat :deep(.t-chat__list) {
  box-sizing: border-box;
  flex: 1 1 auto;
  height: auto;
  min-height: 0;
  padding: 0.25rem 0.75rem 1rem 0.25rem;
  overflow-y: auto;
  scrollbar-gutter: stable;
  overscroll-behavior: contain;
  touch-action: pan-y;
}

.chat-workspace {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  height: clamp(520px, 70dvh, 680px);
  min-height: 0;
  overflow: hidden;
}

.chat-message-list,
.chat-empty {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

.chat-composer {
  flex: 0 0 auto;
  padding-top: 1rem;
  background: oklch(var(--card));
  border-top: 1px solid oklch(var(--border));
}

.stackchan-chat :deep(.t-chat) {
  flex: 1 1 auto;
  height: auto;
  min-height: 0;
  overflow: hidden;
}

.composer-field {
  padding: 0.75rem;
  background: oklch(var(--background));
  border: 1px solid oklch(var(--border));
  border-radius: 0.75rem;
}

.composer-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  justify-content: space-between;
  padding-top: 0.75rem;
}

@media (width >= 1024px) {
  .chat-layout {
    grid-template-columns: 220px minmax(0, 1fr);
    height: calc(100dvh - 12rem);
  }

  .chat-workspace {
    height: 100%;
  }
}

@media (width >= 1280px) {
  .chat-layout {
    grid-template-columns: 280px minmax(0, 1fr);
  }
}

@media (width <= 639px) {
  .composer-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
