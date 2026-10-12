<script setup lang="ts">
import type { FormExpose, TableColumn } from '@fantastic-admin/components'
import type { BarkSettings, BarkSettingsInput, Delegation } from '@/api/modules/delegations'
import type { Device } from '@/api/modules/devices'
import type { CompanionRole } from '@/api/modules/roles'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { deleteDelegation, getBarkSettings, listDelegations, pauseDelegation, resumeDelegation, runDelegation, updateBarkSettings } from '@/api/modules/delegations'
import { listDevices } from '@/api/modules/devices'
import { listRoles } from '@/api/modules/roles'
import eventBus from '@/utils/eventBus'
import { deliveryStatusLabel, formatDelegationTime, runStatusLabel, safeSourceUrl, scheduleLabel } from './helpers'

defineOptions({ name: 'CompanionDelegationList' })
const router = useRouter()
const { pagination, getParams, onSizeChange, onCurrentChange } = usePagination()
const loading = ref(false)
const actionBusy = ref(false)
const listError = ref('')
const dataList = ref<Delegation[]>([])
const devices = ref<Device[]>([])
const roles = ref<CompanionRole[]>([])
const search = ref({ deviceId: '', roleId: '' })
const deviceOptions = computed(() => [{ label: '全部设备', value: '' }, ...devices.value.map(item => ({ label: item.displayName, value: item.id }))])
const roleOptions = computed(() => [{ label: '全部伙伴', value: '' }, ...roles.value.map(item => ({ label: item.name, value: item.id }))])
const deviceNames = computed(() => new Map(devices.value.map(item => [item.id, item.displayName])))
const roleNames = computed(() => new Map(roles.value.map(item => [item.id, item.name])))
const tableColumns = computed<TableColumn<Delegation>[]>(() => [
  { accessorKey: 'title', header: '任务', minWidth: 240 },
  { id: 'partner', header: '设备 / 伙伴', minWidth: 150 },
  { id: 'schedule', header: '计划 / 下次执行', minWidth: 210 },
  { id: 'lastRun', header: '上次执行', minWidth: 160 },
  { id: 'delivery', header: '交付', minWidth: 140 },
  { id: 'operation', header: '操作', width: 190, align: 'center', fixed: 'right' },
])
let listRequest = 0

const barkFormRef = useTemplateRef<FormExpose>('barkFormRef')
const barkSettings = ref<BarkSettings | null>(null)
const barkLoading = ref(false)
const barkSaving = ref(false)
const barkError = ref('')
const barkModel = ref<BarkSettingsInput>({ serverUrl: 'https://api.day.app', deviceKey: '', clearDeviceKey: false, enabled: false })
const barkSchema = toTypedSchema(z.object({
  serverUrl: z.string().trim().refine(value => Boolean(safeSourceUrl(value)), '请输入不含凭据的 HTTPS 服务地址'),
  deviceKey: z.string().trim().max(512, '设备 Key 过长'),
  clearDeviceKey: z.boolean(),
  enabled: z.boolean(),
}))
watch(() => barkModel.value.clearDeviceKey, (clear) => {
  if (clear) {
    barkModel.value.deviceKey = ''
  }
})

async function loadBark() {
  if (barkLoading.value || barkSaving.value) {
    return
  }
  barkLoading.value = true
  barkError.value = ''
  try {
    const settings = await getBarkSettings()
    barkSettings.value = settings
    barkModel.value = { serverUrl: settings.serverUrl, deviceKey: '', clearDeviceKey: false, enabled: settings.enabled }
  }
  catch (error) {
    barkError.value = error instanceof Error ? error.message : '无法读取 Bark 设置。'
  }
  finally {
    barkLoading.value = false
  }
}

async function saveBark() {
  if (barkSaving.value || barkLoading.value || barkError.value || !(await barkFormRef.value?.validate())?.valid) {
    return
  }
  if (barkModel.value.enabled && (barkModel.value.clearDeviceKey || (!barkSettings.value?.keyConfigured && !barkModel.value.deviceKey.trim()))) {
    useFaToast().error('请填写设备 Key', { description: '启用 Bark 需要设备 Key；清除 Key 时请同时关闭 Bark。' })
    return
  }
  barkSaving.value = true
  try {
    const settings = await updateBarkSettings({ ...barkModel.value, serverUrl: barkModel.value.serverUrl.trim(), deviceKey: barkModel.value.deviceKey.trim() })
    barkSettings.value = settings
    barkModel.value = { serverUrl: settings.serverUrl, deviceKey: '', clearDeviceKey: false, enabled: settings.enabled }
    barkFormRef.value?.resetFields({ values: barkModel.value })
    useFaToast().success('Bark 设置已保存')
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存 Bark 设置。' })
  }
  finally {
    barkSaving.value = false
  }
}

async function getDataList() {
  const request = ++listRequest
  loading.value = true
  listError.value = ''
  dataList.value = []
  pagination.value.total = 0
  try {
    const result = await listDelegations({ ...getParams(), deviceId: search.value.deviceId || undefined, roleId: search.value.roleId || undefined })
    if (request === listRequest) {
      dataList.value = result.list
      pagination.value.total = result.total
    }
  }
  catch (error) {
    if (request === listRequest) {
      listError.value = error instanceof Error ? error.message : '无法读取持续委托。'
    }
  }
  finally {
    if (request === listRequest) {
      loading.value = false
    }
  }
}

function currentChange(page = 1) {
  return onCurrentChange(page).then(getDataList)
}
function sizeChange(size: number) {
  return onSizeChange(size).then(getDataList)
}
function openDetail(task?: Delegation, example = false) {
  router.push({ name: 'companionDelegationDetail', params: task ? { id: task.id } : {}, query: example ? { example: 'ai-daily' } : {} })
}
async function applyAction(action: () => Promise<unknown>, message: string) {
  if (actionBusy.value) {
    return
  }
  actionBusy.value = true
  try {
    await action()
    await getDataList()
    useFaToast().success(message)
  }
  catch (error) {
    useFaToast().error('操作失败', { description: error instanceof Error ? error.message : '无法更新委托。' })
  }
  finally {
    actionBusy.value = false
  }
}
function execute(task: Delegation) {
  useFaModal().confirm({
    title: '立即执行一次',
    content: `让伙伴现在执行「${task.title}」。已完成的同一计划不会重复执行。${task.deliveryMode === 'BARK' ? '执行完成后按任务安排将结果发送到手机。' : '执行记录保存在控制台。'}`,
    confirmButtonText: '立即执行',
    onConfirm: async () => {
      await applyAction(() => runDelegation(task.id), '任务已提交，可在执行记录中查看进展')
    },
  })
}
function remove(task: Delegation) {
  useFaModal().confirm({
    title: '删除任务',
    content: `确认删除「${task.title}」和执行记录吗？删除后停止继续工作，页面无法撤销。`,
    onConfirm: () => applyAction(() => deleteDelegation(task.id), '委托已删除'),
  })
}
onMounted(() => {
  Promise.all([listDevices(), listRoles()]).then(([deviceList, roleList]) => {
    devices.value = deviceList
    roles.value = roleList
  }).catch(error => useFaToast().error('设备或伙伴加载失败', { description: error instanceof Error ? error.message : '请刷新后重试。' }))
  getDataList()
  loadBark()
  eventBus.on('get-delegation-list', getDataList)
})
onBeforeUnmount(() => {
  ++listRequest
  eventBus.off('get-delegation-list', getDataList)
  barkModel.value.deviceKey = ''
})
onDeactivated(() => {
  barkModel.value.deviceKey = ''
})
</script>

<template>
  <AppPageShell title="持续委托" description="告诉伙伴想做什么，它会理解安排，在约定的时间执行并带回结果。">
    <FaCollapsible class="mb-6">
      <template #trigger="{ open }">
        <span class="text-sm px-3 py-2 border rounded-md inline-block">{{ open ? '收起' : '展开' }}手机通知设置</span>
      </template>
      <FaCard title="iPhone 接收 · Bark" class="mt-4">
        <div class="text-sm text-muted-foreground leading-6 mb-4">
          在 iPhone 安装 <a href="https://bark.day.app/" target="_blank" rel="noopener noreferrer" class="text-primary underline">Bark</a>，将 App 提供的设备 Key 填在下方。任务结果会保留在 Bark 历史中；控制台仍需在可访问的网络内打开。
        </div>
        <FaAlert v-if="barkError" variant="destructive" title="Bark 配置未加载" :description="barkError">
          <template #action>
            <FaButton variant="outline" @click="loadBark">
              重试
            </FaButton>
          </template>
        </FaAlert>
        <FaForm ref="barkFormRef" :model="barkModel" :validation-schema="barkSchema" :disabled="barkLoading || barkSaving || Boolean(barkError)" :label-width="120" class="gap-4 grid" label-placement="right" scroll-to-error>
          <div class="gap-4 grid lg:grid-cols-2">
            <FaFormItem name="serverUrl" label="Bark 服务地址" required>
              <FaInput placeholder="https://api.day.app" />
            </FaFormItem>
            <FaFormItem name="deviceKey" label="设备 Key" :description="barkSettings?.keyConfigured ? '已配置；留空保留现有 Key，填写则替换。' : '尚未配置；请从 Bark App 获取 Key。'">
              <FaInput type="password" autocomplete="new-password" placeholder="仅写入，保存后清空" :disabled="barkModel.clearDeviceKey" />
            </FaFormItem>
          </div>
          <div class="gap-4 grid sm:grid-cols-2">
            <FaFormItem name="enabled" label="启用 Bark">
              <FaSwitch />
            </FaFormItem>
            <FaFormItem name="clearDeviceKey" label="清除现有 Key" description="清除前请关闭 Bark。">
              <FaSwitch />
            </FaFormItem>
          </div>
          <div class="flex flex-wrap gap-3 items-center">
            <FaButton type="button" :disabled="barkLoading || barkSaving || Boolean(barkError)" @click="saveBark">
              保存 Bark 设置
            </FaButton>
            <FaTag :variant="barkSettings?.enabled && barkSettings.keyConfigured ? 'default' : 'outline'">
              {{ barkSettings?.enabled && barkSettings.keyConfigured ? '已就绪' : '尚未就绪' }}
            </FaTag>
          </div>
        </FaForm>
      </FaCard>
    </FaCollapsible>
    <FaSearchBar :show-toggle="false">
      <template #default>
        <div class="gap-4 grid sm:grid-cols-3">
          <FaLabel label="设备">
            <FaSelect v-model="search.deviceId" :options="deviceOptions" @change="currentChange()" />
          </FaLabel>
          <FaLabel label="伙伴">
            <FaSelect v-model="search.roleId" :options="roleOptions" @change="currentChange()" />
          </FaLabel>
          <div class="flex gap-2 justify-end">
            <FaButton variant="outline" @click="search = { deviceId: '', roleId: '' }; currentChange()">
              重置
            </FaButton>
            <FaButton @click="currentChange()">
              <FaIcon name="i-ri:search-line" />筛选
            </FaButton>
          </div>
        </div>
      </template>
    </FaSearchBar>
    <div class="my-4 border-t border-dashed" />
    <FaAlert v-if="listError" title="委托未加载" :description="listError" variant="destructive" class="mb-4">
      <template #action>
        <FaButton variant="outline" @click="getDataList">
          重试
        </FaButton>
      </template>
    </FaAlert>
    <FaTable v-loading="loading" row-key="id" stripe column-visibility border :columns="tableColumns" :data="dataList" :empty-text="loading ? '正在读取委托…' : listError ? '列表未获取' : '还没有持续委托'" table-root-class="rounded-lg overflow-hidden">
      <template #toolbar>
        <div class="flex flex-1 flex-wrap gap-2">
          <FaButton @click="openDetail()">
            交代一个任务
          </FaButton><FaButton variant="outline" @click="openDetail(undefined, true)">
            使用 AI 日报示例
          </FaButton><FaButton variant="ghost" @click="getDataList">
            刷新
          </FaButton>
        </div>
      </template>
      <template #cell-title="{ row }">
        <div class="font-medium">
          {{ row.original.title }}
        </div><div class="text-sm text-muted-foreground line-clamp-2">
          {{ row.original.description || row.original.instruction }}
        </div>
      </template>
      <template #cell-partner="{ row }">
        <div>{{ deviceNames.get(row.original.deviceId) || row.original.deviceId }}</div><div class="text-sm text-muted-foreground">
          {{ roleNames.get(row.original.roleId) || row.original.roleId }}
        </div>
      </template>
      <template #cell-schedule="{ row }">
        <FaTag :variant="row.original.enabled ? 'default' : 'outline'">
          {{ row.original.enabled ? '已启用' : '已暂停' }}
        </FaTag><div class="text-sm mt-2">
          {{ scheduleLabel(row.original) }}
        </div><div class="text-xs text-muted-foreground mt-1">
          下次：{{ formatDelegationTime(row.original.nextRunAt, row.original.zoneId) }}
        </div>
      </template>
      <template #cell-lastRun="{ row }">
        <template v-if="row.original.lastRun">
          <div>{{ runStatusLabel(row.original.lastRun.status, Boolean(row.original.lastRun.steps?.length)) }}</div><div class="text-xs text-muted-foreground">
            {{ formatDelegationTime(row.original.lastRun.scheduledAt || row.original.lastRun.createdAt, row.original.zoneId) }}
          </div>
        </template><span v-else class="text-muted-foreground">尚未运行</span>
      </template>
      <template #cell-delivery="{ row }">
        <div>{{ row.original.deliveryMode === 'BARK' ? 'Bark' : '控制台' }}</div><div v-if="row.original.lastRun" class="text-xs text-muted-foreground">
          {{ deliveryStatusLabel(row.original.lastRun.deliveryStatus) }}
        </div>
      </template>
      <template #cell-operation="{ row }">
        <div class="flex-center gap-2">
          <FaButton variant="outline" size="sm" :disabled="actionBusy" @click="openDetail(row.original)">
            任务 / 记录
          </FaButton>
          <FaDropdown :items="[[{ label: row.original.enabled ? '暂停' : '恢复', disabled: actionBusy, handle: () => applyAction(() => row.original.enabled ? pauseDelegation(row.original.id) : resumeDelegation(row.original.id), row.original.enabled ? '任务已暂停' : '任务已恢复') }, { label: '立即执行一次', disabled: actionBusy || !row.original.enabled, handle: () => execute(row.original) }, { label: '删除', variant: 'destructive', disabled: actionBusy, handle: () => remove(row.original) }]]">
            <FaButton variant="outline" size="icon-sm" aria-label="委托操作" :disabled="actionBusy">
              <FaIcon name="i-ri:more-line" />
            </FaButton>
          </FaDropdown>
        </div>
      </template>
    </FaTable>
    <FaPagination :page="pagination.page" :size="pagination.size" :total="pagination.total" class="mt-2" @page-change="currentChange" @size-change="sizeChange" />
  </AppPageShell>
</template>
