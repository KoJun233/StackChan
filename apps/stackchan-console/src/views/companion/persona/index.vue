<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { Device } from '@/api/modules/devices'
import type { CompanionRole } from '@/api/modules/roles'
import { listDevices } from '@/api/modules/devices'
import { archiveRole, getDeviceActiveRole, listRoles, restoreRole, setDeviceActiveRole } from '@/api/modules/roles'

defineOptions({ name: 'CompanionRoleList' })

const router = useRouter()
const loading = ref(false)
const roles = ref<CompanionRole[]>([])
const devices = ref<Device[]>([])
const activeRoles = ref<Record<string, string>>({})
const columns: TableColumn<CompanionRole>[] = [
  { accessorKey: 'name', header: '角色名称', minWidth: 180 },
  { id: 'style', header: '交流风格', minWidth: 190 },
  { id: 'status', header: '状态', width: 110, align: 'center' },
  { id: 'updatedAt', header: '更新时间', minWidth: 180 },
  { id: 'operation', header: '操作', width: 200, align: 'center', fixed: 'right' },
]
const availableRoleOptions = computed(() => roles.value.filter(role => !role.archivedAt)
  .map(role => ({ label: role.name, value: role.id })))

function formatTime(value: string) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }
function toneLabel(role: CompanionRole) {
  const tone = { WARM: '温暖', CALM: '平静', LIVELY: '活泼', PROFESSIONAL: '专业' }[role.tone]
  const length = { SHORT: '简短', BALANCED: '适中', DETAILED: '详细' }[role.replyLength]
  return `${tone} · ${length}`
}

async function load() {
  loading.value = true
  try {
    [roles.value, devices.value] = await Promise.all([listRoles(), listDevices()])
    const mappings = await Promise.all(devices.value.map(async device => [device.id, (await getDeviceActiveRole(device.id)).id] as const))
    activeRoles.value = Object.fromEntries(mappings)
  }
  catch (error) { useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取角色。' }) }
  finally { loading.value = false }
}

async function switchRole(deviceId: string, roleId: string) {
  try {
    await setDeviceActiveRole(deviceId, roleId)
    activeRoles.value[deviceId] = roleId
    useFaToast().success('设备角色已切换', { description: '下一轮语音会话将使用该角色的独立历史与记忆。' })
  }
  catch (error) { await load(); useFaToast().error('切换失败', { description: error instanceof Error ? error.message : '设备当前无法切换角色。' }) }
}

function changeArchive(role: CompanionRole) {
  const archive = !role.archivedAt
  useFaModal().confirm({
    title: archive ? '归档角色' : '恢复角色',
    content: archive ? `归档「${role.name}」后，相关设备将切回默认角色，未来提醒会取消，通知集成会停用。` : `恢复「${role.name}」不会自动恢复提醒或令牌。`,
    onConfirm: async () => {
      try { await (archive ? archiveRole(role.id) : restoreRole(role.id)); await load(); useFaToast().success(archive ? '角色已归档' : '角色已恢复') }
      catch (error) { useFaToast().error('操作失败', { description: error instanceof Error ? error.message : '无法更新角色。' }) }
    },
  })
}

onMounted(load)
</script>

<template>
  <FaPageMain title="角色管理" description="角色分别保存人设、对话、长期记忆、提醒和主动互动状态；设备设置与模型密钥继续共享。">
    <div class="space-y-4">
      <FaCard title="设备当前角色">
        <div class="gap-4 grid md:grid-cols-2 xl:grid-cols-3">
          <div v-for="device in devices" :key="device.id" class="border rounded-lg p-4 space-y-2">
            <div class="font-medium">{{ device.displayName }}</div>
            <FaSelect :model-value="activeRoles[device.id]" :options="availableRoleOptions" class="w-full" @update:model-value="switchRole(device.id, String($event))" />
          </div>
        </div>
      </FaCard>
      <FaCard>
        <template #header>
          <div class="flex items-center justify-between">
            <div><div class="font-semibold">角色容器</div><div class="text-sm text-muted-foreground">已归档角色的数据保留只读，不进入新会话上下文。</div></div>
            <FaButton @click="router.push({ name: 'companionRoleDetail' })"><FaIcon name="i-ep:plus" />新增角色</FaButton>
          </div>
        </template>
        <FaLoading :loading="loading">
          <FaTable :data="roles" :columns="columns">
            <template #cell-style="{ row }">{{ toneLabel(row.original) }}</template>
            <template #cell-status="{ row }"><FaTag :variant="row.original.archivedAt ? 'secondary' : 'default'">{{ row.original.defaultRole ? '默认' : row.original.archivedAt ? '已归档' : '可用' }}</FaTag></template>
            <template #cell-updatedAt="{ row }">{{ formatTime(row.original.updatedAt) }}</template>
            <template #cell-operation="{ row }">
              <div class="flex-center gap-2"><FaButton size="sm" variant="outline" @click="router.push({ name: 'companionRoleDetail', params: { id: row.original.id } })">编辑</FaButton><FaButton v-if="!row.original.defaultRole" size="sm" :variant="row.original.archivedAt ? 'default' : 'destructive'" @click="changeArchive(row.original)">{{ row.original.archivedAt ? '恢复' : '归档' }}</FaButton></div>
            </template>
          </FaTable>
        </FaLoading>
      </FaCard>
    </div>
  </FaPageMain>
</template>
