<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { Device } from '@/api/modules/devices'
import type {
  LongTermMemory,
  MemoryCategory,
  MemoryConfirmationStatus,
  MemoryScopeType,
} from '@/api/modules/personaMemory'
import { listDevices } from '@/api/modules/devices'
import {
  clearMemories,
  confirmMemory,
  deleteMemory,
  listMemories,
  rejectMemory,
  setMemoryEnabled,
} from '@/api/modules/personaMemory'
import eventBus from '@/utils/eventBus'

defineOptions({ name: 'CompanionMemoryList' })

const router = useRouter()
const { pagination, getParams, onSizeChange, onCurrentChange } = usePagination()
const tableAutoHeight = ref(false)
const loading = ref(false)
const dataList = ref<LongTermMemory[]>([])
const devices = ref<Device[]>([])
const batch = ref({
  enable: true,
  selectionDataList: [] as LongTermMemory[],
})

const searchDefault = {
  query: '',
  category: '' as MemoryCategory | '',
  confirmationStatus: '' as MemoryConfirmationStatus | '',
  scopeType: '' as MemoryScopeType | '',
}
const search = ref({ ...searchDefault })

const categoryOptions = [
  { label: '全部类型', value: '' },
  { label: '用户档案', value: 'USER_PROFILE' },
  { label: '事件记忆', value: 'EVENT' },
]
const statusOptions = [
  { label: '全部确认状态', value: '' },
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已拒绝', value: 'REJECTED' },
]
const scopeOptions = [
  { label: '全部范围', value: '' },
  { label: '全局', value: 'GLOBAL' },
  { label: '指定设备', value: 'DEVICE' },
]

const deviceNames = computed(() => new Map(devices.value.map(device => [device.id, device.displayName])))

const tableColumns = computed<TableColumn<LongTermMemory>[]>(() => [
  ...(batch.value.enable
    ? [{ type: 'selection', fixed: 'left', width: 48 } satisfies TableColumn<LongTermMemory>]
    : []),
  { accessorKey: 'title', header: '记忆', minWidth: 260 },
  { id: 'category', header: '类型', width: 110, align: 'center' },
  { id: 'scope', header: '作用范围', minWidth: 150 },
  { id: 'status', header: '确认状态', width: 110, align: 'center' },
  { id: 'enabled', header: '上下文', width: 100, align: 'center' },
  { id: 'updatedAt', header: '更新时间', minWidth: 170 },
  { id: 'operation', header: '操作', width: 210, align: 'center', fixed: 'right' },
])

function categoryLabel(category: MemoryCategory) {
  return category === 'USER_PROFILE' ? '用户档案' : '事件记忆'
}

function statusLabel(status: MemoryConfirmationStatus) {
  return {
    PENDING: '待确认',
    CONFIRMED: '已确认',
    REJECTED: '已拒绝',
  }[status]
}

function statusVariant(status: MemoryConfirmationStatus): 'default' | 'destructive' | 'outline' | 'secondary' {
  if (status === 'CONFIRMED') {
    return 'default'
  }
  if (status === 'PENDING') {
    return 'secondary'
  }
  return 'outline'
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function searchReset() {
  Object.assign(search.value, searchDefault)
}

async function getDataList() {
  loading.value = true
  try {
    const params = getParams()
    const result = await listMemories({
      from: params.from,
      limit: params.limit,
      query: search.value.query,
      category: search.value.category,
      confirmationStatus: search.value.confirmationStatus,
      scopeType: search.value.scopeType,
    })
    dataList.value = result.list
    pagination.value.total = result.total
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法获取长期记忆列表。' })
  }
  finally {
    loading.value = false
  }
}

async function loadDevices() {
  try {
    devices.value = await listDevices()
  }
  catch (error) {
    useFaToast().error('设备加载失败', { description: error instanceof Error ? error.message : '无法获取设备列表。' })
  }
}

function sizeChange(size: number) {
  onSizeChange(size).then(() => getDataList())
}

function currentChange(page = 1) {
  onCurrentChange(page).then(() => getDataList())
}

function onCreate() {
  router.push({ name: 'companionMemoryDetail' })
}

function onEdit(memory: LongTermMemory) {
  router.push({ name: 'companionMemoryDetail', params: { id: memory.id } })
}

async function applyAction(action: () => Promise<unknown>, success: string) {
  try {
    await action()
    await getDataList()
    useFaToast().success(success)
  }
  catch (error) {
    useFaToast().error('操作失败', { description: error instanceof Error ? error.message : '无法更新长期记忆。' })
  }
}

function confirmDelete(rows: LongTermMemory[]) {
  if (!rows.length) {
    return
  }
  useFaModal().confirm({
    title: '删除长期记忆',
    content: rows.length === 1
      ? `确认删除「${rows[0].title}」吗？删除后下一轮对话将不再加载它。`
      : `确认删除选中的 ${rows.length} 条记忆吗？删除后无法恢复。`,
    onConfirm: async () => {
      try {
        await Promise.all(rows.map(row => deleteMemory(row.id)))
        batch.value.selectionDataList = []
        await getDataList()
        useFaToast().success(rows.length === 1 ? '记忆已删除' : '选中记忆已删除')
      }
      catch (error) {
        useFaToast().error('删除失败', { description: error instanceof Error ? error.message : '无法删除长期记忆。' })
      }
    },
  })
}

function confirmClearAll() {
  useFaModal().confirm({
    title: '清空全部长期记忆',
    content: '这会删除全局和所有设备的长期记忆，下一轮文本和语音对话都不会再加载它们。此操作无法恢复。',
    confirmButtonText: '确认清空',
    onConfirm: async () => {
      try {
        const result = await clearMemories()
        batch.value.selectionDataList = []
        await currentChange()
        useFaToast().success('长期记忆已清空', { description: `共删除 ${result.deletedCount} 条记忆。` })
      }
      catch (error) {
        useFaToast().error('清空失败', { description: error instanceof Error ? error.message : '无法清空长期记忆。' })
      }
    },
  })
}

onMounted(() => {
  loadDevices()
  getDataList()
  eventBus.on('get-memory-list', getDataList)
})

onBeforeUnmount(() => eventBus.off('get-memory-list'))
</script>

<template>
  <div :class="{ 'absolute flex flex-col size-full': tableAutoHeight }">
    <FaPageHeader title="长期记忆" class="mb-0" />
    <FaPageMain :class="{ 'flex-1 overflow-auto': tableAutoHeight }" :main-class="{ 'flex-1 flex flex-col overflow-auto': tableAutoHeight }">
      <FaAlert
        title="只有已确认且启用的记忆会进入对话"
        description="模型建议默认处于待确认状态；已拒绝、已停用或已删除的内容不会进入后续文本和设备语音上下文。"
        class="mb-4"
      />
      <FaSearchBar :show-toggle="false">
        <template #default="{ fold, toggle }">
          <div class="gap-x-8 gap-y-2 grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))]">
            <FaLabel label="关键词" class="col-span-1">
              <FaInput
                v-model="search.query"
                placeholder="搜索标题或内容"
                clearable
                class="w-full"
                @keydown.enter="currentChange()"
                @clear="currentChange()"
              />
            </FaLabel>
            <FaLabel label="确认状态" class="col-span-1">
              <FaSelect v-model="search.confirmationStatus" :options="statusOptions" class="w-full" @change="currentChange()" />
            </FaLabel>
            <FaLabel v-show="!fold" label="记忆类型" class="col-span-1">
              <FaSelect v-model="search.category" :options="categoryOptions" class="w-full" @change="currentChange()" />
            </FaLabel>
            <FaLabel v-show="!fold" label="作用范围" class="col-span-1">
              <FaSelect v-model="search.scopeType" :options="scopeOptions" class="w-full" @change="currentChange()" />
            </FaLabel>
            <div class="flex gap-2 col-end--1 justify-end">
              <FaButton variant="outline" @click="searchReset(); currentChange()">
                重置
              </FaButton>
              <FaButton @click="currentChange()">
                <FaIcon name="i-ri:search-line" />
                筛选
              </FaButton>
              <FaButton variant="ghost" @click="toggle">
                {{ fold ? '展开' : '收起' }}
                <FaIcon :name="fold ? 'i-ep:caret-bottom' : 'i-ep:caret-top'" />
              </FaButton>
            </div>
          </div>
        </template>
      </FaSearchBar>
      <div class="mx--4 my-3 border-t border-t-dashed" />
      <FaTable
        v-loading="loading"
        table-root-class="rounded-lg overflow-hidden"
        :class="{ 'min-h-0 flex-1': tableAutoHeight }"
        row-key="id"
        selectable
        multiple
        stripe
        column-visibility
        border
        :columns="tableColumns"
        :data="dataList"
        @selection-change="batch.selectionDataList = $event"
      >
        <template #toolbar>
          <div class="flex flex-1 gap-2 items-center">
            <FaButton @click="onCreate">
              新增记忆
            </FaButton>
            <FaDropdown
              v-if="batch.enable"
              :items="[[
                { label: '批量删除', variant: 'destructive', disabled: !batch.selectionDataList.length, handle: () => confirmDelete(batch.selectionDataList) },
                { label: '清空全部记忆', variant: 'destructive', handle: confirmClearAll },
              ]]"
            >
              <FaButton variant="outline">
                批量操作
                <FaIcon name="i-ep:arrow-down" />
              </FaButton>
            </FaDropdown>
          </div>
        </template>
        <template #cell-title="{ row }">
          <div class="max-w-110">
            <div class="font-medium truncate" :title="row.original.title">
              {{ row.original.title }}
            </div>
            <div class="text-sm text-muted-foreground line-clamp-2" :title="row.original.content">
              {{ row.original.content }}
            </div>
            <div class="text-xs text-muted-foreground mt-1" :title="row.original.sourceDetail">
              {{ row.original.sourceDetail }}
            </div>
          </div>
        </template>
        <template #cell-category="{ row }">
          {{ categoryLabel(row.original.category) }}
        </template>
        <template #cell-scope="{ row }">
          <span v-if="row.original.scopeType === 'GLOBAL'">全局共享</span>
          <span v-else>{{ deviceNames.get(row.original.deviceId || '') || '指定设备' }}</span>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="statusVariant(row.original.confirmationStatus)">
            {{ statusLabel(row.original.confirmationStatus) }}
          </FaTag>
        </template>
        <template #cell-enabled="{ row }">
          <FaTag :variant="row.original.enabled ? 'default' : 'outline'">
            {{ row.original.enabled ? '已启用' : '未启用' }}
          </FaTag>
        </template>
        <template #cell-updatedAt="{ row }">
          {{ formatTime(row.original.updatedAt) }}
        </template>
        <template #cell-operation="{ row }">
          <div class="flex-center gap-2">
            <template v-if="row.original.confirmationStatus === 'PENDING'">
              <FaButton size="sm" @click="applyAction(() => confirmMemory(row.original.id), '记忆已确认并启用')">
                确认
              </FaButton>
              <FaButton size="sm" variant="outline" @click="applyAction(() => rejectMemory(row.original.id), '记忆建议已拒绝')">
                拒绝
              </FaButton>
            </template>
            <FaButton
              v-else-if="row.original.confirmationStatus === 'CONFIRMED'"
              size="sm"
              variant="outline"
              @click="applyAction(() => setMemoryEnabled(row.original.id, !row.original.enabled), row.original.enabled ? '记忆已停用' : '记忆已启用')"
            >
              {{ row.original.enabled ? '停用' : '启用' }}
            </FaButton>
            <FaButton variant="outline" size="icon-sm" @click="onEdit(row.original)">
              <FaIcon name="i-ri:edit-line" />
            </FaButton>
            <FaDropdown :items="[[{ label: '删除', variant: 'destructive', handle: () => confirmDelete([row.original]) }]]">
              <FaButton variant="outline" size="icon-sm">
                <FaIcon name="i-ri:more-line" />
              </FaButton>
            </FaDropdown>
          </div>
        </template>
      </FaTable>
      <FaPagination
        :page="pagination.page"
        :size="pagination.size"
        :total="pagination.total"
        class="mt-2"
        @page-change="currentChange"
        @size-change="sizeChange"
      />
    </FaPageMain>
  </div>
</template>
