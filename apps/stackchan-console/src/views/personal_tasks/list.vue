<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { Device } from '@/api/modules/devices'
import type { PersonalTask, PersonalTaskPriority, PersonalTaskStatus } from '@/api/modules/personalTasks'
import { listDevices } from '@/api/modules/devices'
import {
  completePersonalTask,
  deletePersonalTask,
  listPersonalTasks,
  reopenPersonalTask,
} from '@/api/modules/personalTasks'
import { listRoles } from '@/api/modules/roles'
import eventBus from '@/utils/eventBus'

defineOptions({ name: 'PersonalTaskList' })

const router = useRouter()
const { pagination, getParams, onSizeChange, onCurrentChange } = usePagination()
const tableAutoHeight = ref(false)
const loading = ref(false)
const dataList = ref<PersonalTask[]>([])
const devices = ref<Device[]>([])
const roleNames = ref(new Map<string, string>())
const batch = ref({ enable: true, selectionDataList: [] as PersonalTask[] })

const searchDefault = { query: '', status: 'OPEN' as PersonalTaskStatus | '', priority: '' as PersonalTaskPriority | '', roleId: '' }
const search = ref({ ...searchDefault })

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '未完成', value: 'OPEN' },
  { label: '已完成', value: 'COMPLETED' },
]
const priorityOptions = [
  { label: '全部优先级', value: '' },
  { label: '高', value: 'HIGH' },
  { label: '普通', value: 'NORMAL' },
  { label: '低', value: 'LOW' },
]
const roleOptions = computed(() => [
  { label: '全部角色', value: '' },
  ...Array.from(roleNames.value.entries()).map(([value, label]) => ({ label, value })),
])
const deviceNames = computed(() => new Map(devices.value.map(device => [device.id, device.displayName])))

const tableColumns = computed<TableColumn<PersonalTask>[]>(() => [
  { type: 'selection', fixed: 'left', width: 48 },
  { id: 'title', header: '待办', minSize: 220 },
  { id: 'priority', header: '优先级', width: 90, align: 'center' },
  { id: 'dueAt', header: '截止与提醒', width: 190 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { id: 'device', header: '设备', width: 150 },
  { id: 'role', header: '角色', width: 130 },
  { accessorKey: 'updatedAt', header: '更新时间', width: 180 },
  { id: 'operation', header: '操作', width: 120, align: 'center', fixed: 'right' },
])

function searchReset() {
  Object.assign(search.value, searchDefault)
}

async function loadReferences() {
  try {
    const [deviceList, roles] = await Promise.all([listDevices(), listRoles()])
    devices.value = deviceList
    roleNames.value = new Map(roles.filter(role => !role.archivedAt).map(role => [role.id, role.name]))
  }
  catch (error) {
    useFaToast().error('基础数据加载失败', { description: error instanceof Error ? error.message : '无法获取设备或角色。' })
  }
}

async function getDataList() {
  loading.value = true
  try {
    const result = await listPersonalTasks({
      ...getParams(),
      query: search.value.query,
      status: search.value.status,
      priority: search.value.priority,
      roleId: search.value.roleId || undefined,
    })
    dataList.value = result.list
    pagination.value.total = result.total
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取待办。' })
  }
  finally {
    loading.value = false
  }
}

function currentChange(page = 1) {
  onCurrentChange(page).then(getDataList)
}

function sizeChange(size: number) {
  onSizeChange(size).then(getDataList)
}

function onCreate() {
  router.push({ name: 'personalTaskDetail' })
}

function onEdit(task: PersonalTask) {
  router.push({ name: 'personalTaskDetail', params: { id: task.id } })
}

async function toggleCompleted(task: PersonalTask) {
  try {
    if (task.status === 'OPEN') {
      await completePersonalTask(task.id)
    }
    else {
      await reopenPersonalTask(task.id)
    }
    await getDataList()
    useFaToast().success(task.status === 'OPEN' ? '已完成待办' : '已重新打开待办')
  }
  catch (error) {
    useFaToast().error('操作失败', { description: error instanceof Error ? error.message : '无法更新待办状态。' })
  }
}

function confirmDelete(rows: PersonalTask[]) {
  if (!rows.length) {
    return
  }
  useFaModal().confirm({
    title: '确认信息',
    content: rows.length === 1 ? `确认删除「${rows[0].title}」吗？` : `确认删除选中的 ${rows.length} 条待办吗？`,
    onConfirm: async () => {
      try {
        await Promise.all(rows.map(row => deletePersonalTask(row.id)))
        batch.value.selectionDataList = []
        await getDataList()
        useFaToast().success(rows.length === 1 ? '删除成功' : '批量删除成功')
      }
      catch (error) {
        useFaToast().error('删除失败', { description: error instanceof Error ? error.message : '无法删除待办。' })
      }
    },
  })
}

function formatTime(value: string | null) {
  if (!value) {
    return '未设置'
  }
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function priorityLabel(priority: PersonalTaskPriority) {
  return { HIGH: '高', NORMAL: '普通', LOW: '低' }[priority]
}

function priorityVariant(priority: PersonalTaskPriority): 'default' | 'destructive' | 'secondary' {
  return priority === 'HIGH' ? 'destructive' : priority === 'LOW' ? 'secondary' : 'default'
}

onMounted(() => {
  loadReferences()
  getDataList()
  eventBus.on('get-personal-task-list', getDataList)
})

onBeforeUnmount(() => eventBus.off('get-personal-task-list'))
</script>

<template>
  <div :class="{ 'absolute flex flex-col size-full': tableAutoHeight }">
    <FaPageHeader title="个人待办" description="集中安排个人事项、截止时间与优先级；完成状态会保留在本地服务端。" class="mb-0" />
    <FaPageMain :class="{ 'flex-1 overflow-auto': tableAutoHeight }" :main-class="{ 'flex-1 flex flex-col overflow-auto': tableAutoHeight }">
      <FaSearchBar :show-toggle="false">
        <template #default="{ fold, toggle }">
          <div class="gap-x-8 gap-y-2 grid grid-cols-[repeat(auto-fit,minmax(300px,1fr))]">
            <FaLabel label="标题或备注" class="col-span-1">
              <FaInput v-model="search.query" placeholder="请输入关键词" clearable class="w-full" @keydown.enter="currentChange()" @clear="currentChange()" />
            </FaLabel>
            <FaLabel v-show="!fold" label="状态" class="col-span-1">
              <FaSelect v-model="search.status" :options="statusOptions" class="w-full" @change="currentChange()" />
            </FaLabel>
            <FaLabel v-show="!fold" label="优先级" class="col-span-1">
              <FaSelect v-model="search.priority" :options="priorityOptions" class="w-full" @change="currentChange()" />
            </FaLabel>
            <FaLabel v-show="!fold" label="角色" class="col-span-1">
              <FaSelect v-model="search.roleId" :options="roleOptions" class="w-full" @change="currentChange()" />
            </FaLabel>
            <div class="flex gap-2 col-end--1 justify-end">
              <FaButton variant="outline" @click="searchReset(); currentChange()">
                重置
              </FaButton>
              <FaButton @click="currentChange()">
                <FaIcon name="i-ri:search-line" />筛选
              </FaButton>
              <FaButton variant="ghost" @click="toggle">
                {{ fold ? '展开' : '收起' }}<FaIcon :name="fold ? 'i-ep:caret-bottom' : 'i-ep:caret-top'" />
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
              新增待办
            </FaButton>
            <FaDropdown :items="[[{ label: '批量删除', variant: 'destructive', disabled: !batch.selectionDataList.length, handle: () => confirmDelete(batch.selectionDataList) }]]">
              <FaButton variant="outline" :disabled="!batch.selectionDataList.length">
                批量操作<FaIcon name="i-ep:arrow-down" />
              </FaButton>
            </FaDropdown>
          </div>
        </template>
        <template #cell-title="{ row }">
          <div class="max-w-100">
            <div class="font-medium truncate" :class="{ 'line-through text-muted-foreground': row.original.status === 'COMPLETED' }" :title="row.original.title">
              {{ row.original.title }}
            </div>
            <div v-if="row.original.notes" class="text-xs text-muted-foreground truncate" :title="row.original.notes">
              {{ row.original.notes }}
            </div>
          </div>
        </template>
        <template #cell-priority="{ row }">
          <FaTag :variant="priorityVariant(row.original.priority)">
            {{ priorityLabel(row.original.priority) }}
          </FaTag>
        </template>
        <template #cell-dueAt="{ row }">
          <div>{{ formatTime(row.original.dueAt) }}</div>
          <div v-if="row.original.reminderId" class="text-xs text-muted-foreground">
            已关联机器人提醒
          </div>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="row.original.status === 'OPEN' ? 'default' : 'secondary'">
            {{ row.original.status === 'OPEN' ? '未完成' : '已完成' }}
          </FaTag>
        </template>
        <template #cell-device="{ row }">
          {{ deviceNames.get(row.original.deviceId) || row.original.deviceId }}
        </template>
        <template #cell-role="{ row }">
          {{ roleNames.get(row.original.roleId) || row.original.roleId }}
        </template>
        <template #cell-updatedAt="{ row }">
          {{ formatTime(row.original.updatedAt) }}
        </template>
        <template #cell-operation="{ row }">
          <div class="flex-center gap-2">
            <FaButton variant="outline" size="icon-sm" @click="onEdit(row.original)">
              <FaIcon name="i-ri:edit-line" />
            </FaButton>
            <FaDropdown
              :items="[[
                { label: row.original.status === 'OPEN' ? '标记完成' : '重新打开', handle: () => toggleCompleted(row.original) },
                { label: '删除', variant: 'destructive', handle: () => confirmDelete([row.original]) },
              ]]"
            >
              <FaButton variant="outline" size="icon-sm">
                <FaIcon name="i-ri:more-line" />
              </FaButton>
            </FaDropdown>
          </div>
        </template>
      </FaTable>
      <FaPagination :page="pagination.page" :size="pagination.size" :total="pagination.total" class="mt-2" @page-change="currentChange" @size-change="sizeChange" />
    </FaPageMain>
  </div>
</template>
