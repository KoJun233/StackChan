<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { Device } from '@/api/modules/devices'
import type { ConversationMessage } from '@/api/modules/companion'
import type { BackupStatus, PersonalDataConversation } from '@/api/modules/personalData'
import { listDevices } from '@/api/modules/devices'
import {
  deletePersonalDataConversation,
  deletePersonalDataMessage,
  exportPersonalDataConversations,
  getBackupStatus,
  getPersonalDataMessages,
  listPersonalDataConversations,
} from '@/api/modules/personalData'

defineOptions({ name: 'CompanionPersonalData' })

const { pagination, getParams, onCurrentChange, onSizeChange } = usePagination()
const loading = ref(false)
const messagesLoading = ref(false)
const exporting = ref(false)
const conversations = ref<PersonalDataConversation[]>([])
const messages = ref<ConversationMessage[]>([])
const devices = ref<Device[]>([])
const selectedConversation = ref<PersonalDataConversation | null>(null)
const backupStatus = ref<BackupStatus | null>(null)
const searchDefault = { query: '', deviceId: '', fromTime: '', toTime: '' }
const search = ref({ ...searchDefault })

const conversationColumns: TableColumn<PersonalDataConversation>[] = [
  { id: 'title', header: '对话', minWidth: 260 },
  { id: 'device', header: '来源', minWidth: 150 },
  { accessorKey: 'messageCount', header: '消息数', width: 90, align: 'center' },
  { id: 'updatedAt', header: '最近更新', minWidth: 170 },
  { id: 'operation', header: '操作', width: 210, align: 'center', fixed: 'right' },
]

const messageColumns: TableColumn<ConversationMessage>[] = [
  { id: 'role', header: '角色', width: 90, align: 'center' },
  { id: 'content', header: '消息正文', minWidth: 360 },
  { id: 'createdAt', header: '时间', minWidth: 170 },
  { id: 'operation', header: '操作', width: 100, align: 'center' },
]

const deviceOptions = computed(() => [
  { label: '全部来源', value: '' },
  ...devices.value.map(device => ({ label: device.displayName, value: device.id })),
])

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '暂无记录'
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MiB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GiB`
}

function toInstant(value: string) {
  return value ? new Date(value).toISOString() : undefined
}

function currentFilter(conversationId?: string) {
  const params = getParams()
  return {
    from: params.from,
    limit: params.limit,
    query: search.value.query,
    deviceId: search.value.deviceId || undefined,
    fromTime: toInstant(search.value.fromTime),
    toTime: toInstant(search.value.toTime),
    conversationId,
  }
}

async function loadConversations() {
  loading.value = true
  try {
    const result = await listPersonalDataConversations(currentFilter())
    conversations.value = result.list
    pagination.value.total = result.total
    if (selectedConversation.value && !result.list.some(item => item.id === selectedConversation.value?.id)) {
      selectedConversation.value = null
      messages.value = []
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取对话数据。' })
  }
  finally {
    loading.value = false
  }
}

async function loadMessages(conversation: PersonalDataConversation) {
  selectedConversation.value = conversation
  messagesLoading.value = true
  try {
    messages.value = await getPersonalDataMessages(conversation.id)
  }
  catch (error) {
    useFaToast().error('消息加载失败', { description: error instanceof Error ? error.message : '无法读取消息正文。' })
  }
  finally {
    messagesLoading.value = false
  }
}

async function loadStatus() {
  try {
    backupStatus.value = await getBackupStatus()
  }
  catch (error) {
    useFaToast().error('备份状态加载失败', { description: error instanceof Error ? error.message : '无法读取备份状态。' })
  }
}

function resetSearch() {
  Object.assign(search.value, searchDefault)
  onCurrentChange(1).then(loadConversations)
}

function changePage(page = 1) {
  onCurrentChange(page).then(loadConversations)
}

function changeSize(size: number) {
  onSizeChange(size).then(loadConversations)
}

async function downloadExport(conversationId?: string) {
  exporting.value = true
  try {
    const { blob, fileName } = await exportPersonalDataConversations(currentFilter(conversationId))
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName
    anchor.click()
    URL.revokeObjectURL(url)
    useFaToast().success('对话已导出')
  }
  catch (error) {
    useFaToast().error('导出失败', { description: error instanceof Error ? error.message : '无法导出对话。' })
  }
  finally {
    exporting.value = false
  }
}

function confirmDeleteConversation(conversation: PersonalDataConversation) {
  useFaModal().confirm({
    title: '删除整段对话',
    content: `确认删除「${conversation.title}」及其中 ${conversation.messageCount} 条消息吗？正文将立即从搜索、导出和后续聊天上下文中消失。此操作无法撤销。`,
    confirmButtonText: '确认删除',
    onConfirm: async () => {
      try {
        await deletePersonalDataConversation(conversation.id)
        if (selectedConversation.value?.id === conversation.id) {
          selectedConversation.value = null
          messages.value = []
        }
        await loadConversations()
        useFaToast().success('对话已删除')
      }
      catch (error) {
        useFaToast().error('删除失败', { description: error instanceof Error ? error.message : '无法删除对话。' })
      }
    },
  })
}

function confirmDeleteMessage(message: ConversationMessage) {
  const conversation = selectedConversation.value
  if (!conversation) return
  useFaModal().confirm({
    title: '删除单条消息',
    content: '确认永久删除这条消息吗？它将立即从搜索、导出和后续聊天上下文中消失，其他消息会保留。',
    confirmButtonText: '确认删除',
    onConfirm: async () => {
      try {
        await deletePersonalDataMessage(conversation.id, message.id)
        await Promise.all([loadMessages(conversation), loadConversations()])
        useFaToast().success('消息已删除')
      }
      catch (error) {
        useFaToast().error('删除失败', { description: error instanceof Error ? error.message : '无法删除消息。' })
      }
    },
  })
}

onMounted(() => {
  Promise.all([
    listDevices().then(result => devices.value = result),
    loadConversations(),
    loadStatus(),
  ]).catch(() => undefined)
})
</script>

<template>
  <FaPageMain title="对话与个人数据" description="查找、导出或永久删除保存在 StackChan 中的对话正文。">
    <FaAlert
      title="删除会立即影响后续对话"
      description="删除后的正文不会再进入聊天上下文、搜索或导出。历史备份在保留期内仍可能包含删除前的数据；网页不提供恢复操作。"
      class="mb-4"
    />

    <FaCard title="备份与恢复状态" class="mb-4">
      <template #action>
        <FaButton variant="outline" size="sm" @click="loadStatus">刷新状态</FaButton>
      </template>
      <FaEmpty v-if="!backupStatus?.available" description="尚无成功备份或状态暂不可用" />
      <div v-else class="gap-4 grid md:grid-cols-2 xl:grid-cols-4">
        <div class="rounded-lg border p-4">
          <div class="text-sm text-muted-foreground">最近成功备份</div>
          <div class="mt-1 font-medium">{{ formatTime(backupStatus.lastSuccessfulBackupAt) }}</div>
        </div>
        <div class="rounded-lg border p-4">
          <div class="text-sm text-muted-foreground">最近恢复验证</div>
          <div class="mt-1 font-medium">
            {{ formatTime(backupStatus.lastRestoreVerificationAt) }} ·
            {{ backupStatus.lastRestoreVerificationSuccessful ? '通过' : '失败' }}
          </div>
        </div>
        <div class="rounded-lg border p-4">
          <div class="text-sm text-muted-foreground">保留数量</div>
          <div class="mt-1 font-medium">日备份 {{ backupStatus.dailyBackupCount }}/{{ backupStatus.dailyRetention }} · 周备份 {{ backupStatus.weeklyBackupCount }}/{{ backupStatus.weeklyRetention }}</div>
        </div>
        <div class="rounded-lg border p-4">
          <div class="text-sm text-muted-foreground">备份存储占用</div>
          <div class="mt-1 font-medium">{{ formatBytes(backupStatus.storageBytes) }}</div>
        </div>
      </div>
      <FaAlert
        v-if="backupStatus?.lastFailureAt"
        variant="destructive"
        class="mt-4"
        title="曾发生备份或验证失败"
        :description="`${formatTime(backupStatus.lastFailureAt)} · ${backupStatus.lastFailureCode || 'UNKNOWN'}`"
      />
    </FaCard>

    <FaSearchBar :show-toggle="false">
      <template #default>
        <div class="gap-x-6 gap-y-3 grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))]">
          <FaLabel label="关键词">
            <FaInput v-model="search.query" clearable placeholder="搜索标题或消息正文" @keydown.enter="changePage()" />
          </FaLabel>
          <FaLabel label="设备来源">
            <FaSelect v-model="search.deviceId" :options="deviceOptions" class="w-full" />
          </FaLabel>
          <FaLabel label="更新时间起点">
            <FaInput v-model="search.fromTime" type="datetime-local" />
          </FaLabel>
          <FaLabel label="更新时间终点">
            <FaInput v-model="search.toTime" type="datetime-local" />
          </FaLabel>
          <div class="flex gap-2 col-end--1 justify-end items-end">
            <FaButton variant="outline" @click="resetSearch">重置</FaButton>
            <FaButton @click="changePage()"><FaIcon name="i-ri:search-line" />筛选</FaButton>
            <FaButton variant="secondary" :loading="exporting" @click="downloadExport()"><FaIcon name="i-ri:download-line" />导出当前范围</FaButton>
          </div>
        </div>
      </template>
    </FaSearchBar>

    <FaCard title="对话列表" class="mt-4">
      <FaTable v-loading="loading" :columns="conversationColumns" :data="conversations" row-key="id" stripe border>
        <template #cell-title="{ row }">
          <div class="max-w-100">
            <div class="font-medium truncate">{{ row.original.title }}</div>
            <div class="text-xs text-muted-foreground">{{ row.original.id }}</div>
          </div>
        </template>
        <template #cell-device="{ row }">{{ row.original.deviceName || '网页聊天' }}</template>
        <template #cell-updatedAt="{ row }">{{ formatTime(row.original.updatedAt) }}</template>
        <template #cell-operation="{ row }">
          <div class="flex-center gap-2">
            <FaButton size="sm" variant="outline" @click="loadMessages(row.original)">查看</FaButton>
            <FaDropdown :items="[[
              { label: '导出此对话', handle: () => downloadExport(row.original.id) },
              { label: '删除整段对话', variant: 'destructive', handle: () => confirmDeleteConversation(row.original) },
            ]]">
              <FaButton size="icon-sm" variant="outline"><FaIcon name="i-ri:more-line" /></FaButton>
            </FaDropdown>
          </div>
        </template>
      </FaTable>
      <FaPagination
        :page="pagination.page"
        :size="pagination.size"
        :total="pagination.total"
        class="mt-3"
        @page-change="changePage"
        @size-change="changeSize"
      />
    </FaCard>

    <FaCard v-if="selectedConversation" :title="`消息：${selectedConversation.title}`" class="mt-4">
      <FaTable v-loading="messagesLoading" :columns="messageColumns" :data="messages" row-key="id" stripe border>
        <template #cell-role="{ row }">{{ row.original.role === 'USER' ? '用户' : row.original.role === 'ASSISTANT' ? '机器人' : '系统' }}</template>
        <template #cell-content="{ row }">
          <div class="max-w-180 whitespace-pre-wrap break-words">{{ row.original.content || '（空消息）' }}</div>
        </template>
        <template #cell-createdAt="{ row }">{{ formatTime(row.original.createdAt) }}</template>
        <template #cell-operation="{ row }">
          <FaButton size="sm" variant="destructive" :disabled="row.original.generationStatus === 'STREAMING'" @click="confirmDeleteMessage(row.original)">删除</FaButton>
        </template>
      </FaTable>
    </FaCard>
  </FaPageMain>
</template>
