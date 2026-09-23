<script setup lang="ts">
import type { Device } from '@/api/modules/devices'
import type { CompanionRole } from '@/api/modules/roles'
import { listDevices } from '@/api/modules/devices'
import { archiveRole, deleteRole, getDeviceActiveRole, listRoles, restoreRole, setDeviceActiveRole } from '@/api/modules/roles'

defineOptions({ name: 'CompanionRoleList' })
const router = useRouter()
const loading = ref(false)
const roles = ref<CompanionRole[]>([])
const devices = ref<Device[]>([])
const activeRoles = ref<Record<string, string>>({})
const pendingRoles = ref<Record<string, string>>({})
const switchingDevice = ref('')
const availableRoles = computed(() => roles.value.filter(role => !role.archivedAt))
const archivedRoles = computed(() => roles.value.filter(role => !!role.archivedAt))
const availableRoleOptions = computed(() => roles.value.filter(role => !role.archivedAt)
  .map(role => ({ label: role.name, value: role.id })))
function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
function toneLabel(role: CompanionRole) {
  const tone = { WARM: '温暖', CALM: '平静', LIVELY: '活泼', PROFESSIONAL: '专业' }[role.tone]
  const length = { SHORT: '简短', BALANCED: '适中', DETAILED: '详细' }[role.replyLength]
  return `${tone} · ${length}`
}
function deleteAvailableAt(role: CompanionRole) {
  return role.archivedAt ? new Date(role.archivedAt).getTime() + 7 * 24 * 60 * 60 * 1000 : Number.POSITIVE_INFINITY
}
function canDelete(role: CompanionRole) {
  return !role.defaultRole && Date.now() >= deleteAvailableAt(role)
}
function deleteHint(role: CompanionRole) {
  if (!role.archivedAt) {
    return ''
  }
  return canDelete(role)
    ? '可永久删除'
    : `${new Date(deleteAvailableAt(role)).toLocaleDateString('zh-CN')} 后可删除`
}
async function load() {
  loading.value = true
  try {
    [roles.value, devices.value] = await Promise.all([listRoles(), listDevices()])
    const mappings = await Promise.all(devices.value.map(async device => [device.id, (await getDeviceActiveRole(device.id)).id] as const))
    activeRoles.value = Object.fromEntries(mappings)
    pendingRoles.value = { ...activeRoles.value }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取伙伴。' })
  }
  finally {
    loading.value = false
  }
}
async function switchRole(deviceId: string, roleId: string) {
  if (switchingDevice.value) {
    return
  }
  switchingDevice.value = deviceId
  try {
    const saved = await setDeviceActiveRole(deviceId, roleId)
    activeRoles.value[deviceId] = saved.id
    pendingRoles.value[deviceId] = saved.id
    useFaToast().success('设备伙伴设置已保存', { description: '下一轮语音会话将使用该伙伴的独立历史与记忆。' })
  }
  catch (error) {
    await load()
    useFaToast().error('切换失败', { description: error instanceof Error ? error.message : '设备当前无法切换伙伴。' })
  }
  finally {
    switchingDevice.value = ''
  }
}
function confirmSwitch(device: Device) {
  const roleId = pendingRoles.value[device.id]
  const next = roles.value.find(role => role.id === roleId && !role.archivedAt)
  if (!next || roleId === activeRoles.value[device.id]) {
    return
  }
  const current = roles.value.find(role => role.id === activeRoles.value[device.id])?.name ?? '未获取'
  useFaModal().confirm({ title: '切换机器人的伙伴', content: `将「${device.displayName}」的伙伴从「${current}」切换为「${next.name}」？伙伴之间的聊天和记忆保持独立。`, confirmButtonText: '确认切换', onConfirm: () => switchRole(device.id, roleId) })
}
function changeArchive(role: CompanionRole) {
  const archive = !role.archivedAt
  useFaModal().confirm({
    title: archive ? '归档伙伴' : '恢复伙伴',
    content: archive ? `归档「${role.name}」后，相关设备将切回默认伙伴，未来提醒会取消，通知集成会停用。` : `恢复「${role.name}」不会自动恢复提醒或令牌。`,
    onConfirm: async () => {
      try {
        await (archive ? archiveRole(role.id) : restoreRole(role.id))
        await load()
        useFaToast().success(archive ? '伙伴已归档' : '伙伴已恢复')
      }
      catch (error) {
        useFaToast().error('操作失败', { description: error instanceof Error ? error.message : '无法更新伙伴。' })
      }
    },
  })
}
function confirmDelete(role: CompanionRole) {
  useFaModal().confirm({
    title: `永久删除「${role.name}」`,
    content: '该伙伴的对话、消息、长期记忆、提醒、通知集成、个人任务和语音操作记录都会永久删除，且无法从管理页面恢复。是否继续？',
    confirmButtonText: '永久删除',
    onConfirm: async () => {
      try {
        await deleteRole(role.id)
        await load()
        useFaToast().success('伙伴已永久删除')
      }
      catch (error) {
        useFaToast().error('删除失败', { description: error instanceof Error ? error.message : '暂时无法永久删除该伙伴。' })
      }
    },
  })
}
onMounted(load)
</script>

<template>
  <AppPageShell title="伙伴管理" description="不同伙伴各自保留聊天、记忆与事务。">
    <template #actions>
      <FaButton @click="router.push({ name: 'companionRoleDetail' })">
        <FaIcon name="i-ri:add-line" />新增伙伴
      </FaButton>
    </template>
    <FaCard title="机器人正在和谁相处">
      <AppEmpty v-if="!loading && !devices.length" description="连接机器人后，可为它选择伙伴。" />
      <div class="gap-4 grid md:grid-cols-2">
        <section v-for="device in devices" :key="device.id" class="min-w-0 space-y-3">
          <h2 class="font-medium">
            {{ device.displayName }} <span class="text-xs text-muted-foreground">{{ device.online ? '在线' : '离线' }}</span>
          </h2>
          <p class="text-sm text-muted-foreground">
            当前：{{ roles.find(role => role.id === activeRoles[device.id])?.name ?? '未获取' }}
          </p>
          <div class="flex flex-wrap gap-2">
            <FaSelect v-model="pendingRoles[device.id]" :options="availableRoleOptions" :disabled="!!switchingDevice || loading" class="flex-1 min-w-40" :aria-label="`${device.displayName}的伙伴`" />
            <FaButton variant="outline" :disabled="!pendingRoles[device.id] || pendingRoles[device.id] === activeRoles[device.id] || !!switchingDevice" :loading="switchingDevice === device.id" @click="confirmSwitch(device)">
              确认切换
            </FaButton>
          </div>
        </section>
      </div>
    </FaCard>
    <AppLoading :loading="loading">
      <AppEmpty v-if="!availableRoles.length" description="还没有可用伙伴" />
      <div class="gap-5 grid md:grid-cols-2">
        <FaCard v-for="role in availableRoles" :key="role.id">
          <div class="flex gap-4 items-center">
            <AppPartnerPortrait :name="role.name" :color="role.expressionThemeColor" /><div class="min-w-0 space-y-2">
              <h2 class="text-xl font-semibold break-words">
                {{ role.name }}
              </h2><FaTag v-if="role.defaultRole" variant="secondary">
                默认伙伴
              </FaTag><p class="text-sm">
                {{ toneLabel(role) }}
              </p><p class="text-sm text-muted-foreground">
                音色：{{ role.ttsVoiceOverride || '继承全局音色' }}
              </p>
            </div>
          </div>
          <template #footer>
            <div class="flex flex-wrap gap-2">
              <FaButton @click="router.push({ path: '/companion/chat', query: { roleId: role.id } })">
                和它聊聊
              </FaButton><FaButton variant="outline" @click="router.push({ name: 'companionRoleDetail', params: { id: role.id } })">
                编辑伙伴
              </FaButton><FaButton v-if="!role.defaultRole" variant="ghost" @click="changeArchive(role)">
                归档
              </FaButton>
            </div>
          </template>
        </FaCard>
      </div>
    </AppLoading>
    <FaCollapsible v-if="archivedRoles.length">
      <template #trigger>
        <span class="text-sm underline">已归档伙伴（{{ archivedRoles.length }}）</span>
      </template>
      <p class="text-sm text-muted-foreground my-3">
        归档满七天后可手动永久删除；恢复不会自动恢复提醒或令牌。
      </p>
      <div class="space-y-3">
        <FaCard v-for="role in archivedRoles" :key="role.id" :title="role.name" :description="deleteHint(role)">
          <p class="text-sm text-muted-foreground">
            最近更新 {{ formatTime(role.updatedAt) }}
          </p><template #footer>
            <div class="flex flex-wrap gap-2">
              <FaButton variant="outline" @click="router.push({ name: 'companionRoleDetail', params: { id: role.id } })">
                查看
              </FaButton><FaButton @click="changeArchive(role)">
                恢复伙伴
              </FaButton><FaButton variant="destructive" :disabled="!canDelete(role)" @click="confirmDelete(role)">
                永久删除
              </FaButton>
            </div>
          </template>
        </FaCard>
      </div>
    </FaCollapsible>
  </AppPageShell>
</template>
