<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { Device } from '@/api/modules/devices'
import type {
  ExternalNotification,
  ExternalNotificationStatus,
  IssuedNotificationToken,
  NotificationIntegration,
  NotificationTokenMetadata,
} from '@/api/modules/notificationIntegrations'
import { listDevices } from '@/api/modules/devices'
import {
  deleteExternalNotification,
  deleteNotificationIntegration,
  issueNotificationToken,
  listExternalNotifications,
  listNotificationIntegrations,
  revokeNotificationToken,
  testNotificationIntegration,
} from '@/api/modules/notificationIntegrations'
import eventBus from '@/utils/eventBus'

defineOptions({ name: 'NotificationIntegrationList' })

const router = useRouter()
const { pagination, getParams, onSizeChange, onCurrentChange } = usePagination()
const tableAutoHeight = ref(false)
const loading = ref(false)
const notificationLoading = ref(false)
const actionId = ref('')
const devices = ref<Device[]>([])
const integrations = ref<NotificationIntegration[]>([])
const notifications = ref<ExternalNotification[]>([])
const tokenModalOpen = ref(false)
const tokenIntegration = ref<NotificationIntegration | null>(null)
const tokenExpiresAtLocal = ref('')
const issuedToken = ref<IssuedNotificationToken | null>(null)
const testIntegrationId = ref('')
const testContent = ref('任务已完成，可以查看结果。')
const search = ref({ integrationId: '', status: '' as ExternalNotificationStatus | '' })

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待发送', value: 'PENDING' },
  { label: '已派发', value: 'DISPATCHED' },
  { label: '已送达', value: 'DELIVERED' },
  { label: '失败', value: 'FAILED' },
  { label: '已过期', value: 'EXPIRED' },
  { label: '已取消', value: 'CANCELLED' },
]

const deviceNames = computed(() => new Map(devices.value.map(device => [device.id, device.displayName])))
const integrationNames = computed(() => new Map(integrations.value.map(item => [item.id, item.name])))
const integrationOptions = computed(() => [
  { label: '全部集成', value: '' },
  ...integrations.value.map(item => ({ label: item.name, value: item.id })),
])
const testIntegrationOptions = computed(() => integrations.value
  .filter(item => item.enabled)
  .map(item => ({ label: `${item.name} · ${deviceNames.value.get(item.deviceId) ?? item.deviceId}`, value: item.id })))

const integrationColumns = computed<TableColumn<NotificationIntegration>[]>(() => [
  { accessorKey: 'name', header: '集成名称', minWidth: 180 },
  { id: 'device', header: '固定目标设备', minWidth: 180 },
  { id: 'enabled', header: '状态', width: 100, align: 'center' },
  { id: 'tokens', header: '有效令牌', width: 110, align: 'center' },
  { id: 'updatedAt', header: '更新时间', minWidth: 180 },
  { id: 'operation', header: '操作', width: 220, align: 'center', fixed: 'right' },
])

const notificationColumns = computed<TableColumn<ExternalNotification>[]>(() => [
  { accessorKey: 'content', header: '通知正文', minWidth: 260 },
  { id: 'integration', header: '来源集成', minWidth: 150 },
  { id: 'status', header: '状态', width: 120, align: 'center' },
  { accessorKey: 'attemptCount', header: '尝试', width: 80, align: 'center' },
  { id: 'createdAt', header: '创建时间', minWidth: 180 },
  { id: 'expiresAt', header: '过期时间', minWidth: 180 },
  { id: 'operation', header: '操作', width: 80, align: 'center', fixed: 'right' },
])

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function isTokenActive(token: NotificationTokenMetadata) {
  return token.revokedAt === null && (token.expiresAt === null || Date.parse(token.expiresAt) > Date.now())
}

function activeTokens(integration: NotificationIntegration) {
  return integration.tokens.filter(isTokenActive)
}

function statusLabel(status: ExternalNotificationStatus) {
  return {
    PENDING: '待发送',
    DISPATCHED: '已派发',
    DELIVERED: '已送达',
    FAILED: '失败',
    EXPIRED: '已过期',
    CANCELLED: '已取消',
  }[status]
}

function statusVariant(status: ExternalNotificationStatus): 'default' | 'destructive' | 'outline' | 'secondary' {
  if (status === 'FAILED' || status === 'EXPIRED') return 'destructive'
  if (status === 'DISPATCHED') return 'default'
  if (status === 'PENDING') return 'secondary'
  return 'outline'
}

async function loadIntegrations() {
  loading.value = true
  try {
    integrations.value = await listNotificationIntegrations()
    if (!testIntegrationId.value || !integrations.value.some(item => item.id === testIntegrationId.value && item.enabled)) {
      testIntegrationId.value = integrations.value.find(item => item.enabled)?.id ?? ''
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法获取通知集成。' })
  }
  finally {
    loading.value = false
  }
}

async function loadNotifications() {
  notificationLoading.value = true
  try {
    const params = getParams()
    const result = await listExternalNotifications({
      from: params.from,
      limit: params.limit,
      integrationId: search.value.integrationId || undefined,
      status: search.value.status,
    })
    notifications.value = result.list
    pagination.value.total = result.total
  }
  catch (error) {
    useFaToast().error('队列加载失败', { description: error instanceof Error ? error.message : '无法获取外部通知。' })
  }
  finally {
    notificationLoading.value = false
  }
}

function onCreate() {
  router.push({ name: 'notificationIntegrationDetail' })
}

function onEdit(row: NotificationIntegration) {
  router.push({ name: 'notificationIntegrationDetail', params: { id: row.id } })
}

function openTokenModal(row: NotificationIntegration) {
  tokenIntegration.value = row
  tokenExpiresAtLocal.value = ''
  issuedToken.value = null
  tokenModalOpen.value = true
}

function closeTokenModal() {
  tokenModalOpen.value = false
  issuedToken.value = null
  tokenIntegration.value = null
  tokenExpiresAtLocal.value = ''
}

async function issueToken() {
  if (!tokenIntegration.value) return
  let expiresAt: string | null = null
  if (tokenExpiresAtLocal.value) {
    const parsed = new Date(tokenExpiresAtLocal.value)
    if (Number.isNaN(parsed.getTime())) {
      useFaToast().error('到期时间无效')
      return
    }
    expiresAt = parsed.toISOString()
  }
  actionId.value = tokenIntegration.value.id
  try {
    issuedToken.value = await issueNotificationToken(tokenIntegration.value.id, expiresAt)
    await loadIntegrations()
  }
  catch (error) {
    useFaToast().error('签发失败', { description: error instanceof Error ? error.message : '无法签发令牌。' })
  }
  finally {
    actionId.value = ''
  }
}

async function copyIssuedToken() {
  if (!issuedToken.value) return
  try {
    await navigator.clipboard.writeText(issuedToken.value.token)
    useFaToast().success('令牌已复制', { description: '关闭窗口后将无法再次查看。' })
  }
  catch {
    useFaToast().error('复制失败', { description: '请手工复制后立即存入调用方的秘密存储。' })
  }
}

function confirmRevoke(integration: NotificationIntegration, token: NotificationTokenMetadata) {
  useFaModal().confirm({
    title: '撤销通知令牌',
    content: `确认撤销 ${formatTime(token.createdAt)} 签发的令牌吗？调用方将立即无法再使用它。`,
    onConfirm: async () => {
      try {
        await revokeNotificationToken(integration.id, token.id)
        await loadIntegrations()
        useFaToast().success('令牌已撤销')
      }
      catch (error) {
        useFaToast().error('撤销失败', { description: error instanceof Error ? error.message : '无法撤销令牌。' })
      }
    },
  })
}

function confirmDeleteIntegration(integration: NotificationIntegration) {
  useFaModal().confirm({
    title: '删除通知集成',
    content: `确认删除“${integration.name}”吗？该集成的全部令牌和通知队列/历史都会永久删除。`,
    confirmButtonText: '确认删除',
    onConfirm: async () => {
      actionId.value = `integration:${integration.id}`
      try {
        await deleteNotificationIntegration(integration.id)
        if (search.value.integrationId === integration.id) search.value.integrationId = ''
        await Promise.all([loadIntegrations(), loadNotifications()])
        useFaToast().success('集成已删除')
      }
      catch (error) {
        useFaToast().error('删除失败', { description: error instanceof Error ? error.message : '无法删除通知集成。' })
      }
      finally {
        actionId.value = ''
      }
    },
  })
}

function confirmDeleteNotification(notification: ExternalNotification) {
  useFaModal().confirm({
    title: '删除通知记录',
    content: `确认永久删除这条${statusLabel(notification.status)}通知吗？此操作无法撤销。`,
    confirmButtonText: '确认删除',
    onConfirm: async () => {
      actionId.value = `notification:${notification.id}`
      try {
        await deleteExternalNotification(notification.id)
        await loadNotifications()
        useFaToast().success('通知记录已删除')
      }
      catch (error) {
        useFaToast().error('删除失败', { description: error instanceof Error ? error.message : '无法删除通知记录。' })
      }
      finally {
        actionId.value = ''
      }
    },
  })
}

async function sendTest() {
  const content = testContent.value.trim()
  if (!testIntegrationId.value || !content || content.length > 500) {
    useFaToast().error('请填写完整', { description: '请选择启用的集成，并填写 1–500 字测试正文。' })
    return
  }
  actionId.value = testIntegrationId.value
  try {
    await testNotificationIntegration(testIntegrationId.value, content)
    await loadNotifications()
    useFaToast().success('测试通知已入队', { description: '将遵守免打扰、忙碌、离线和过期规则。' })
  }
  catch (error) {
    useFaToast().error('测试失败', { description: error instanceof Error ? error.message : '无法创建测试通知。' })
  }
  finally {
    actionId.value = ''
  }
}

function sizeChange(size: number) {
  onSizeChange(size).then(loadNotifications)
}

function currentChange(page = 1) {
  onCurrentChange(page).then(loadNotifications)
}

function resetSearch() {
  search.value = { integrationId: '', status: '' }
  currentChange()
}

onMounted(async () => {
  try {
    devices.value = await listDevices()
  }
  catch (error) {
    useFaToast().error('设备加载失败', { description: error instanceof Error ? error.message : '无法获取设备。' })
  }
  await Promise.all([loadIntegrations(), loadNotifications()])
  eventBus.on('get-notification-integrations', loadIntegrations)
})

onBeforeUnmount(() => eventBus.off('get-notification-integrations'))
</script>

<template>
  <div :class="{ 'absolute flex flex-col size-full': tableAutoHeight }">
    <FaPageHeader title="外部通知" description="为 Codex、Claude Code 或 CI 创建最小权限入口，并可靠投递原文语音通知。" />
    <FaPageMain>
      <FaAlert
        class="mb-5"
        title="令牌只显示一次"
        description="令牌只允许创建和查询自身通知。生产必须使用 HTTPS；不要把令牌写入仓库、URL、日志、Pinia 或 localStorage。"
      />
      <FaTable v-loading="loading" row-key="id" border stripe :columns="integrationColumns" :data="integrations">
        <template #toolbar>
          <FaButton @click="onCreate">新增集成</FaButton>
        </template>
        <template #cell-device="{ row }">
          {{ deviceNames.get(row.original.deviceId) ?? row.original.deviceId }}
        </template>
        <template #cell-enabled="{ row }">
          <FaTag :variant="row.original.enabled ? 'default' : 'outline'">{{ row.original.enabled ? '启用' : '停用' }}</FaTag>
        </template>
        <template #cell-tokens="{ row }">
          {{ activeTokens(row.original).length }} / {{ row.original.tokens.length }}
        </template>
        <template #cell-updatedAt="{ row }">{{ formatTime(row.original.updatedAt) }}</template>
        <template #cell-operation="{ row }">
          <div class="flex-center gap-2">
            <FaButton variant="outline" size="icon-sm" @click="onEdit(row.original)"><FaIcon name="i-ri:edit-line" /></FaButton>
            <FaButton variant="outline" size="icon-sm" title="签发令牌" @click="openTokenModal(row.original)"><FaIcon name="i-ri:key-2-line" /></FaButton>
            <FaDropdown :items="[[
              ...activeTokens(row.original).map(token => ({ label: `撤销 ${formatTime(token.createdAt)} 的令牌`, variant: 'destructive' as const, handle: () => confirmRevoke(row.original, token) })),
            ]]">
              <FaButton variant="outline" size="icon-sm" :disabled="!activeTokens(row.original).length"><FaIcon name="i-ri:more-line" /></FaButton>
            </FaDropdown>
            <FaButton
              variant="destructive"
              size="icon-sm"
              title="删除集成"
              :loading="actionId === `integration:${row.original.id}`"
              @click="confirmDeleteIntegration(row.original)"
            ><FaIcon name="i-ri:delete-bin-line" /></FaButton>
          </div>
        </template>
      </FaTable>
    </FaPageMain>

    <FaPageMain title="测试播报" description="显式创建一条真实外部通知；正文不会经过 LLM 改写。">
      <div class="grid gap-3 lg:grid-cols-[minmax(220px,0.7fr)_minmax(320px,2fr)_auto] lg:items-end">
        <FaLabel label="启用集成"><FaSelect v-model="testIntegrationId" :options="testIntegrationOptions" placeholder="请选择集成" /></FaLabel>
        <FaLabel label="测试正文"><FaInput v-model="testContent" maxlength="500" /></FaLabel>
        <FaButton :loading="actionId === testIntegrationId" :disabled="!testIntegrationId" @click="sendTest"><FaIcon name="i-ri:volume-up-line" />加入播报队列</FaButton>
      </div>
    </FaPageMain>

    <FaPageMain title="通知队列">
      <FaSearchBar :show-toggle="false">
        <template #default>
          <div class="grid gap-3 md:grid-cols-[1fr_1fr_auto] md:items-end">
            <FaLabel label="来源集成"><FaSelect v-model="search.integrationId" :options="integrationOptions" @change="currentChange()" /></FaLabel>
            <FaLabel label="状态"><FaSelect v-model="search.status" :options="statusOptions" @change="currentChange()" /></FaLabel>
            <div class="flex gap-2 justify-end"><FaButton variant="outline" @click="resetSearch">重置</FaButton><FaButton @click="currentChange()">筛选</FaButton></div>
          </div>
        </template>
      </FaSearchBar>
      <div class="mx--4 my-3 border-t border-t-dashed" />
      <FaTable v-loading="notificationLoading" row-key="id" border stripe :columns="notificationColumns" :data="notifications">
        <template #cell-content="{ row }"><div class="max-w-120 truncate" :title="row.original.content">{{ row.original.content }}</div></template>
        <template #cell-integration="{ row }">{{ integrationNames.get(row.original.integrationId) ?? row.original.integrationId }}</template>
        <template #cell-status="{ row }">
          <div class="flex flex-col gap-1 items-center"><FaTag :variant="statusVariant(row.original.status)">{{ statusLabel(row.original.status) }}</FaTag><span v-if="row.original.failureCode" class="text-xs text-destructive">{{ row.original.failureCode }}</span></div>
        </template>
        <template #cell-createdAt="{ row }">{{ formatTime(row.original.createdAt) }}</template>
        <template #cell-expiresAt="{ row }">{{ formatTime(row.original.expiresAt) }}</template>
        <template #cell-operation="{ row }">
          <FaButton
            variant="destructive"
            size="icon-sm"
            :disabled="row.original.status === 'DISPATCHED'"
            :loading="actionId === `notification:${row.original.id}`"
            :title="row.original.status === 'DISPATCHED' ? '正在播报，暂时不能删除' : '删除通知记录'"
            @click="confirmDeleteNotification(row.original)"
          ><FaIcon name="i-ri:delete-bin-line" /></FaButton>
        </template>
      </FaTable>
      <FaPagination :page="pagination.page" :size="pagination.size" :total="pagination.total" class="mt-2" @page-change="currentChange" @size-change="sizeChange" />
    </FaPageMain>

    <FaModal v-model="tokenModalOpen" title="签发外部通知令牌" :footer="false" @closed="closeTokenModal">
      <div v-if="!issuedToken" class="space-y-4">
        <FaAlert title="一次性展示" description="签发成功后请立即复制。服务端只保存摘要，关闭窗口后无法恢复明文。" />
        <FaLabel label="到期时间（可选，最长一年）"><FaInput v-model="tokenExpiresAtLocal" type="datetime-local" /></FaLabel>
        <div class="flex justify-end gap-2"><FaButton variant="outline" @click="closeTokenModal">取消</FaButton><FaButton :loading="actionId === tokenIntegration?.id" @click="issueToken">签发令牌</FaButton></div>
      </div>
      <div v-else class="space-y-4">
        <FaAlert variant="destructive" title="离开后不可再次查看" description="只复制到调用方的秘密存储，不要截图、记录到日志或提交到仓库。" />
        <FaTextarea :model-value="issuedToken.token" readonly rows="4" align="block" class="font-mono" />
        <div class="text-sm text-muted-foreground">到期：{{ formatTime(issuedToken.metadata.expiresAt) }}</div>
        <div class="flex justify-end gap-2"><FaButton @click="copyIssuedToken"><FaIcon name="i-ri:file-copy-line" />复制令牌</FaButton><FaButton variant="outline" @click="closeTokenModal">我已安全保存</FaButton></div>
      </div>
    </FaModal>
  </div>
</template>
