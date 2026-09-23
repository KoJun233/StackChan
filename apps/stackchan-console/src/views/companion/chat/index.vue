<script setup lang="ts">
import type { TdChatItemMeta } from '@tdesign-vue-next/chat'
import type { CompanionRole } from '@/api/modules/roles'
import TChatActionbar from '@tdesign-vue-next/chat/es/chat-actionbar'
import TChatContent from '@tdesign-vue-next/chat/es/chat-content'
import TChatList from '@tdesign-vue-next/chat/es/chat-list'
import { useMediaQuery } from '@vueuse/core'
import { listRoles } from '@/api/modules/roles'
import ChatHistory from './ChatHistory.vue'
import '@tdesign-vue-next/chat/es/style/index.css'
import 'tdesign-vue-next/es/style/index.css'

defineOptions({ name: 'CompanionChat' })
const conversationStore = useConversationStore()
const { activeConversationId, activeRoleId, conversations, errorMessage, historyError, isCreating, isLoading, isSending, lastFailedInput, messages } = storeToRefs(conversationStore)
const draft = ref('')
const drafts = new Map<string, string>()
const route = useRoute()
const roles = ref<CompanionRole[]>([])
const currentRole = computed(() => roles.value.find(role => role.id === activeRoleId.value))
const partnerName = computed(() => currentRole.value?.name ?? '伙伴')
const historyOpen = ref(false)
const desktop = useMediaQuery('(min-width: 1024px)')
const chatLayout = useTemplateRef<HTMLDivElement>('chatLayout')
const chatHeight = ref('calc(100dvh - 12rem)')
const roleOptions = ref<{
  label: string
  value: string
}[]>([])
const initialized = ref(false)
let appliedRoleQuery = ''
const chatItems = computed<TdChatItemMeta[]>(() => messages.value.map(message => ({
  name: message.role === 'USER' ? '你' : partnerName.value,
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
  if (!content || isSending.value || isCreating.value || isLoading.value || historyError.value) {
    return
  }
  draft.value = ''
  try {
    await conversationStore.send(content)
  }
  catch (error) {
    if (!draft.value) {
      draft.value = content
    }
    useFaToast().error('发送失败', { description: error instanceof Error ? error.message : '消息发送失败，请稍后重试。' })
  }
}
async function newConversation() {
  try {
    await conversationStore.startNewConversation()
    historyOpen.value = false
  }
  catch (error) {
    useFaToast().error('创建失败', { description: error instanceof Error ? error.message : '请重新创建对话。' })
  }
}
async function selectConversation(id: string) {
  try {
    await conversationStore.selectConversation(id)
    historyOpen.value = false
  }
  catch { /* The current history error is shown in the conversation panel. */ }
}
async function selectRole(id: string) {
  if (isSending.value || isCreating.value) {
    return
  }
  drafts.set(activeRoleId.value ?? '', draft.value)
  draft.value = drafts.get(id) ?? ''
  try {
    await conversationStore.selectRole(id)
  }
  catch { /* The current history error is shown in the conversation panel. */ }
}
async function retry() {
  try {
    const failed = lastFailedInput.value
    await conversationStore.retryFailed()
    if (!lastFailedInput.value && draft.value === failed) {
      draft.value = ''
    }
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
    roles.value = await listRoles()
    roleOptions.value = roles.value.filter(role => !role.archivedAt).map(role => ({ label: role.name, value: role.id }))
    const requested = String(route.query.roleId ?? '')
    appliedRoleQuery = requested
    const target = roleOptions.value.some(role => role.value === requested) ? requested : roleOptions.value.some(role => role.value === activeRoleId.value) ? activeRoleId.value : roles.value.find(role => role.defaultRole)?.id ?? roleOptions.value[0]?.value
    if (target && target !== activeRoleId.value) {
      await selectRole(target)
    }
    else {
      await conversationStore.loadConversations()
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法加载历史对话。' })
  }
  finally {
    initialized.value = true
  }
})
watch([() => route.name, () => route.query.roleId, initialized, isSending, isCreating], () => {
  if (route.name !== 'companionChat') {
    appliedRoleQuery = ''
    return
  }
  const requested = String(route.query.roleId ?? '')
  if (initialized.value && !isSending.value && !isCreating.value && requested !== appliedRoleQuery
    && roleOptions.value.some(role => role.value === requested)) {
    appliedRoleQuery = requested
    if (requested !== activeRoleId.value) {
      void selectRole(requested)
    }
  }
})
function measureChat() {
  if (!chatLayout.value) {
    return
  }
  const viewport = window.visualViewport
  const bottom = (viewport?.height ?? window.innerHeight) + (viewport?.offsetTop ?? 0)
  chatHeight.value = `${Math.max(180, bottom - chatLayout.value.getBoundingClientRect().top - 16)}px`
}
onMounted(async () => {
  await nextTick()
  measureChat()
  window.addEventListener('resize', measureChat)
  window.visualViewport?.addEventListener('resize', measureChat)
  window.visualViewport?.addEventListener('scroll', measureChat)
})
onActivated(() => nextTick(measureChat))
onBeforeUnmount(() => {
  window.removeEventListener('resize', measureChat)
  window.visualViewport?.removeEventListener('resize', measureChat)
  window.visualViewport?.removeEventListener('scroll', measureChat)
})
watch(desktop, () => {
  historyOpen.value = false
  nextTick(measureChat)
})
</script>

<template>
  <AppPageShell title="聊天" width="wide">
    <template #actions>
      <FaButton v-if="!desktop" variant="outline" @click="historyOpen = true">
        伙伴与历史
      </FaButton>
    </template>
    <div ref="chatLayout" class="chat-layout" :style="{ height: chatHeight }">
      <FaCard v-if="desktop" class="min-h-0" content-class="h-full min-h-0">
        <ChatHistory :role-id="activeRoleId" :conversation-id="activeConversationId" :roles="roleOptions" :conversations="conversations" :busy="isSending || isCreating" :creating="isCreating" :loading="isLoading" @role="selectRole" @conversation="selectConversation" @create="newConversation" />
      </FaCard>

      <FaCard
        :title="partnerName"
        class="stackchan-chat min-h-0 overflow-hidden max-sm:py-3 max-sm:gap-3"
        header-class="max-sm:px-3"
        content-class="flex min-h-0 flex-1 flex-col max-sm:px-3"
      >
        <template #header>
          <div class="flex gap-2 items-center">
            <AppPartnerPortrait compact :name="partnerName" :color="currentRole?.expressionThemeColor" /><div>
              <h2 class="font-semibold">
                {{ partnerName }}
              </h2><p class="text-xs text-muted-foreground">
                {{ isSending ? '正在回应…' : '聊聊今天，也听听彼此' }}
              </p>
            </div>
          </div>
        </template>
        <AppLoading :loading="isLoading" class="flex-1 h-full min-h-0">
          <div class="chat-workspace">
            <FaAlert v-if="historyError" variant="destructive" title="对话未加载" :description="historyError">
              <template #action>
                <FaButton variant="outline" @click="selectRole(activeRoleId ?? '')">
                  重新加载
                </FaButton>
              </template>
            </FaAlert>
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
            <AppEmpty v-else class="chat-empty" description="和你的机器人说点什么吧" />
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
                  :disabled="isSending || isCreating || isLoading || !!historyError"
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
                  <FaButton v-else :disabled="!draft.trim() || isCreating || isLoading || !!historyError" @click="send()">
                    <FaIcon name="i-ri:send-plane-2-line" />
                    发送
                  </FaButton>
                </div>
              </div>
            </div>
          </div>
        </AppLoading>
      </FaCard>
    </div>
    <FaDrawer v-if="!desktop" v-model="historyOpen" title="伙伴与历史" side="left" :footer="false" open-auto-focus content-class="h-full min-h-0">
      <ChatHistory :role-id="activeRoleId" :conversation-id="activeConversationId" :roles="roleOptions" :conversations="conversations" :busy="isSending || isCreating" :creating="isCreating" :loading="isLoading" @role="selectRole" @conversation="selectConversation" @create="newConversation" />
    </FaDrawer>
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
  min-height: 180px;
  overflow: hidden;
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
  height: 100%;
  min-height: 0;
  overflow-y: auto;
}

.chat-message-list,
.chat-empty {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

.chat-composer {
  flex: 0 0 auto;
  padding-top: 0.5rem;
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
  .chat-empty { padding-block: 0.5rem; }
  .composer-actions > span { display: none; }
  .composer-actions { justify-content: flex-end; }
  .composer-field { padding: 0.5rem; }
}
</style>
