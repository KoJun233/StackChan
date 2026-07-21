<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { Device } from '@/api/modules/devices'
import type { Reminder, ReminderStatus } from '@/api/modules/reminders'
import { listDevices } from '@/api/modules/devices'
import { deleteReminder, listReminders } from '@/api/modules/reminders'
import eventBus from '@/utils/eventBus'

defineOptions({ name: 'ReminderList' })

const router = useRouter()
const { pagination, getParams, onSizeChange, onCurrentChange } = usePagination()
const tableAutoHeight = ref(false)
const loading = ref(false)
const dataList = ref<Reminder[]>([])
const devices = ref<Device[]>([])

const searchDefault = {
  content: '',
  status: '' as ReminderStatus | '',
}
const search = ref({ ...searchDefault })
const batch = ref({
  enable: true,
  selectionDataList: [] as Reminder[],
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待发送', value: 'PENDING' },
  { label: '已派发', value: 'DISPATCHED' },
  { label: '已送达', value: 'DELIVERED' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' },
]

const deviceNames = computed(() => new Map(devices.value.map(device => [device.id, device.displayName])))

const tableColumns = computed<TableColumn<Reminder>[]>(() => [
  ...(batch.value.enable
    ? [{ type: 'selection', fixed: 'left', width: 48 } satisfies TableColumn<Reminder>]
    : []),
  { accessorKey: 'content', header: '提醒内容', minWidth: 240 },
  { id: 'device', header: '目标设备', minWidth: 150 },
  { id: 'scheduledAt', header: '提醒时间', minWidth: 180 },
  { id: 'status', header: '状态', width: 120, align: 'center' },
  { accessorKey: 'attemptCount', header: '尝试次数', width: 100, align: 'center' },
  { id: 'operation', header: '操作', width: 120, align: 'center', fixed: 'right' },
])

function searchReset() {
  Object.assign(search.value, searchDefault)
}

function statusLabel(status: ReminderStatus) {
  return {
    PENDING: '待发送',
    DISPATCHED: '已派发',
    DELIVERED: '已送达',
    FAILED: '失败',
    CANCELLED: '已取消',
  }[status]
}

function statusVariant(status: ReminderStatus): 'default' | 'destructive' | 'outline' | 'secondary' {
  if (status === 'FAILED') {
    return 'destructive'
  }
  if (status === 'DISPATCHED') {
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

async function getDataList() {
  loading.value = true
  try {
    const params = getParams()
    const result = await listReminders({
      from: params.from,
      limit: params.limit,
      content: search.value.content,
      status: search.value.status,
    })
    dataList.value = result.list
    pagination.value.total = result.total
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法获取提醒列表。' })
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
  router.push({ name: 'reminderDetail' })
}

function onEdit(row: Reminder) {
  router.push({ name: 'reminderDetail', params: { id: row.id } })
}

function confirmDelete(rows: Reminder[]) {
  if (!rows.length) {
    return
  }
  useFaModal().confirm({
    title: '确认信息',
    content: rows.length === 1
      ? `确认删除「${rows[0].content}」吗？`
      : `确认删除选中的 ${rows.length} 条提醒吗？`,
    onConfirm: async () => {
      try {
        await Promise.all(rows.map(row => deleteReminder(row.id)))
        batch.value.selectionDataList = []
        await getDataList()
        useFaToast().success(rows.length === 1 ? '删除成功' : '批量删除成功')
      }
      catch (error) {
        useFaToast().error('删除失败', { description: error instanceof Error ? error.message : '无法删除提醒。' })
      }
    },
  })
}

onMounted(() => {
  loadDevices()
  getDataList()
  eventBus.on('get-data-list', getDataList)
})

onBeforeUnmount(() => {
  eventBus.off('get-data-list')
})
</script>

<template>
  <div :class="{ 'absolute flex flex-col size-full': tableAutoHeight }">
    <FaPageHeader title="提醒管理" class="mb-0" />
    <FaPageMain :class="{ 'flex-1 overflow-auto': tableAutoHeight }" :main-class="{ 'flex-1 flex flex-col overflow-auto': tableAutoHeight }">
      <FaSearchBar :show-toggle="false">
        <template #default="{ fold, toggle }">
          <div class="gap-x-8 gap-y-2 grid grid-cols-[repeat(auto-fit,minmax(300px,1fr))]">
            <FaLabel label="提醒内容" class="col-span-1">
              <FaInput
                v-model="search.content"
                placeholder="请输入提醒内容，支持模糊查询"
                clearable
                class="w-full"
                @keydown.enter="currentChange()"
                @clear="currentChange()"
              />
            </FaLabel>
            <FaLabel v-show="!fold" label="状态" class="col-span-1">
              <FaSelect v-model="search.status" :options="statusOptions" class="w-full" @change="currentChange()" />
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
              新增提醒
            </FaButton>
            <FaDropdown
              v-if="batch.enable"
              :items="[[{ label: '批量删除', variant: 'destructive', disabled: !batch.selectionDataList.length, handle: () => confirmDelete(batch.selectionDataList) }]]"
            >
              <FaButton variant="outline" :disabled="!batch.selectionDataList.length">
                批量操作
                <FaIcon name="i-ep:arrow-down" />
              </FaButton>
            </FaDropdown>
          </div>
        </template>
        <template #cell-content="{ row }">
          <div class="max-w-100 truncate" :title="row.original.content">
            {{ row.original.content }}
          </div>
        </template>
        <template #cell-device="{ row }">
          {{ deviceNames.get(row.original.deviceId) || row.original.deviceId }}
        </template>
        <template #cell-scheduledAt="{ row }">
          <div>{{ formatTime(row.original.scheduledAt) }}</div>
          <div class="text-xs text-muted-foreground">
            {{ row.original.zoneId }}
          </div>
        </template>
        <template #cell-status="{ row }">
          <div class="flex flex-col gap-1 items-center">
            <FaTag :variant="statusVariant(row.original.status)">
              {{ statusLabel(row.original.status) }}
            </FaTag>
            <span v-if="row.original.failureCode" class="text-xs text-destructive">
              {{ row.original.failureCode }}
            </span>
          </div>
        </template>
        <template #cell-operation="{ row }">
          <div class="flex-center gap-2">
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
