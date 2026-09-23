<script setup lang="ts">
import type { Conversation } from '@/api/modules/companion'

defineProps<{
  roleId?: string
  conversationId?: string
  roles: { label: string, value: string }[]
  conversations: Conversation[]
  busy: boolean
  creating: boolean
  loading: boolean
}>()
const emit = defineEmits<{
  role: [id: string]
  conversation: [id: string]
  create: []
}>()
</script>

<template>
  <div class="flex flex-col gap-3 h-full min-h-0">
    <label class="text-sm font-medium" for="chat-partner">聊天伙伴</label>
    <FaSelect id="chat-partner" :model-value="roleId" :options="roles" class="w-full" :disabled="busy" @update:model-value="emit('role', String($event))" />
    <FaButton variant="outline" :disabled="busy || !roleId" :loading="creating" @click="emit('create')">
      <FaIcon name="i-ri:add-line" />新对话
    </FaButton>
    <p v-if="loading && !conversations.length" class="text-sm text-muted-foreground" role="status">
      正在读取历史…
    </p>
    <AppEmpty v-else-if="!conversations.length" description="还没有对话" />
    <FaScrollArea v-else class="flex-1 min-h-0">
      <div class="pr-2 space-y-1">
        <FaButton v-for="conversation in conversations" :key="conversation.id" class="w-full truncate justify-start" :disabled="busy" :variant="conversationId === conversation.id ? 'secondary' : 'ghost'" :aria-current="conversationId === conversation.id ? 'true' : undefined" @click="emit('conversation', conversation.id)">
          {{ conversation.title }}
        </FaButton>
      </div>
    </FaScrollArea>
  </div>
</template>
