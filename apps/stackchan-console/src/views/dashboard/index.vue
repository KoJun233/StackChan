<script setup lang="ts">
import type { PersonalTask } from '@/api/modules/personalTasks'
import type { CompanionRole } from '@/api/modules/roles'
import type { SystemHealth } from '@/api/modules/systemHealth'
import type { WorkdayRuntime } from '@/api/modules/workday'
import { listPersonalTasks } from '@/api/modules/personalTasks'
import { getDeviceActiveRole, listRoles } from '@/api/modules/roles'
import { getSystemHealth } from '@/api/modules/systemHealth'
import { getWorkdayRuntime } from '@/api/modules/workday'
import DeliveryTimelineView from './DeliveryTimeline.vue'

defineOptions({ name: 'DashboardHome' })
const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const tasksError = ref('')
const workdayError = ref('')
const deviceActiveRole = ref('')
const health = ref<SystemHealth>()
const openTasks = ref<PersonalTask[]>([])
const openTaskTotal = ref<number>()
const roles = ref<CompanionRole[]>([])
const selectedDevice = ref('')
const selectedRole = ref('')
const refreshKey = ref(0)
const deviceOptions = computed(() => (health.value?.devices ?? []).map(device => ({ label: device.displayName, value: device.id })))
const roleOptions = computed(() => roles.value.filter(role => !role.archivedAt).map(role => ({ label: role.name, value: role.id })))
const selectedRoleName = computed(() => roles.value.find(role => role.id === selectedRole.value)?.name ?? '未选择')
let request = 0
const workdayRuntime = ref<WorkdayRuntime>()
const currentDevice = computed(() => health.value?.devices.find(device => device.id === selectedDevice.value))
const currentRole = computed(() => roles.value.find(role => role.id === selectedRole.value))
const activeRoleName = computed(() => roles.value.find(role => role.id === deviceActiveRole.value)?.name ?? '未获取')
const riskCount = computed(() => {
  if (!health.value) {
    return undefined
  }
  return health.value.recentSafeErrors.length
    + health.value.providers.filter(provider => provider.connectivity.status === 'FAILED').length
    + health.value.notifications.failedLast24Hours
})
const workdayStateLabel = computed(() => {
  const state = workdayRuntime.value?.state
  if (!state) {
    return '未加载'
  }
  return ({
    ACTIVE_ABSENT: '工作中 · 暂时离开',
    ACTIVE_PRESENT: '工作中 · 在场',
    OFF: '尚未开始',
    REST_PROMPTED: '等待休息回应',
    RESTING: '休息中',
    SKIPPED_FOR_DAY: '今日已跳过',
    STARTING: '正在开始',
  } as Record<string, string>)[state] ?? state
})
async function loadDashboard() {
  const current = ++request
  const previousRole = selectedRole.value
  loading.value = true
  loadError.value = ''
  health.value = undefined
  deviceActiveRole.value = ''
  openTasks.value = []
  openTaskTotal.value = undefined
  workdayRuntime.value = undefined
  try {
    const [healthResult, roleResult] = await Promise.allSettled([
      getSystemHealth(),
      listRoles(),
    ])
    if (current !== request) {
      return
    }
    if (roleResult.status === 'fulfilled') {
      roles.value = roleResult.value
      if (!roleOptions.value.some(role => role.value === selectedRole.value)) {
        selectedRole.value = roles.value.find(role => role.defaultRole && !role.archivedAt)?.id ?? roleOptions.value[0]?.value ?? ''
      }
    }
    else {
      roles.value = []
      selectedRole.value = ''
      loadError.value = '伙伴列表未获取。'
    }
    if (healthResult.status === 'fulfilled') {
      health.value = healthResult.value
      if (!healthResult.value.devices.some(device => device.id === selectedDevice.value)) {
        selectedDevice.value = (healthResult.value.devices.find(item => item.online) ?? healthResult.value.devices[0])?.id ?? ''
      }
    }
    else {
      selectedDevice.value = ''
      loadError.value += '设备与健康状态未获取，请刷新重试。'
    }
    if (selectedDevice.value) {
      deviceActiveRole.value = ''
      try {
        const active = await getDeviceActiveRole(selectedDevice.value)
        if (current !== request) {
          return
        }
        deviceActiveRole.value = active.id
        if (!previousRole && roleOptions.value.some(role => role.value === active.id)) {
          selectedRole.value = active.id
        }
      }
      catch {
        if (current !== request) {
          return
        }
        loadError.value += '设备当前伙伴未获取。'
      }
    }
    await loadScope()
  }
  catch (error) {
    if (current === request) {
      loadError.value = error instanceof Error ? error.message : '今日概览加载失败。'
    }
  }
  finally {
    if (current === request) {
      loading.value = false
    }
  }
}
async function loadScope() {
  const current = ++request
  openTasks.value = []
  openTaskTotal.value = undefined
  workdayRuntime.value = undefined
  tasksError.value = ''
  workdayError.value = ''
  refreshKey.value++
  if (!selectedDevice.value && !selectedRole.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [tasks, workday] = await Promise.allSettled([
      selectedRole.value ? listPersonalTasks({ from: 0, limit: 5, status: 'OPEN', roleId: selectedRole.value }) : Promise.resolve(undefined),
      selectedDevice.value ? getWorkdayRuntime(selectedDevice.value) : Promise.resolve(undefined),
    ])
    if (current !== request) {
      return
    }
    if (tasks.status === 'fulfilled' && tasks.value) {
      openTasks.value = tasks.value.list
      openTaskTotal.value = tasks.value.total
    }
    else if (tasks.status === 'rejected') {
      tasksError.value = '待办未获取，请重试。'
    }
    if (workday.status === 'fulfilled') {
      workdayRuntime.value = workday.value
    }
    else {
      workdayError.value = '工作状态未获取。'
    }
  }
  catch (error) {
    if (current === request) {
      loadError.value = error instanceof Error ? error.message : '伙伴概览加载失败。'
    }
  }
  finally {
    if (current === request) {
      loading.value = false
    }
  }
}
async function selectDevice() {
  const current = ++request
  selectedRole.value = ''
  deviceActiveRole.value = ''
  openTasks.value = []
  openTaskTotal.value = undefined
  workdayRuntime.value = undefined
  if (!selectedDevice.value) {
    return
  }
  loading.value = true
  try {
    const active = await getDeviceActiveRole(selectedDevice.value)
    if (current !== request) {
      return
    }
    selectedRole.value = active.id
    deviceActiveRole.value = active.id
    await loadScope()
  }
  catch (error) {
    if (current === request) {
      loadError.value = error instanceof Error ? error.message : '无法读取设备伙伴。'
    }
  }
  finally {
    if (current === request) {
      loading.value = false
    }
  }
}
function formatTime(value: string | null) {
  if (!value) {
    return '暂无记录'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}
onMounted(loadDashboard)
</script>

<template>
  <AppPageShell title="伙伴首页">
    <template #actions>
      <FaButton variant="outline" :loading="loading" @click="loadDashboard">
        刷新
      </FaButton>
    </template>
    <FaAlert v-if="loadError" variant="destructive" title="部分信息未获取" :description="loadError" />
    <div class="scope-bar">
      <label class="min-w-0 space-y-1"><span class="text-sm font-medium">机器人</span><FaSelect v-model="selectedDevice" :options="deviceOptions" class="w-full" placeholder="选择机器人" @update:model-value="selectDevice" /></label>
      <label class="min-w-0 space-y-1"><span class="text-sm font-medium">查看伙伴</span><FaSelect v-model="selectedRole" :options="roleOptions" class="w-full" placeholder="选择伙伴" @update:model-value="loadScope" /></label>
      <p class="text-xs text-muted-foreground sm:col-span-2">
        切换查看范围不会改变机器人正在使用的伙伴。
      </p>
    </div>
    <section class="partner-welcome" aria-label="伙伴与机器人状态">
      <AppPartnerPortrait :name="selectedRoleName" :color="currentRole?.expressionThemeColor" />
      <div class="flex-1 min-w-0 space-y-3">
        <h2 class="text-2xl font-semibold break-words">
          {{ currentRole ? currentRole.name : loading ? '正在寻找你的伙伴…' : '让陪伴从这里开始' }}
        </h2>
        <p class="text-sm text-muted-foreground">
          {{ currentRole ? ({ WARM: '温和地听你说，也陪你聊聊日常。', LIVELY: '带着好奇心，分享今天的新鲜事。' } as Record<string, string>)[currentRole.tone] ?? '随时聊聊，慢慢了解彼此。' : '连接机器人，选择一个伙伴。' }}
        </p>
        <p class="text-sm" role="status">
          {{ currentDevice ? `${currentDevice.displayName} · ${currentDevice.online ? '在线' : '离线'}` : health ? '还没有连接机器人' : '设备状态未获取' }}<span v-if="currentDevice"> · 设备当前伙伴：{{ activeRoleName }}</span>
        </p>
        <div class="flex flex-wrap gap-2">
          <FaButton :disabled="!selectedRole" @click="router.push({ path: '/companion/chat', query: { roleId: selectedRole } })">
            <FaIcon name="i-ri:chat-smile-3-line" />开始聊天
          </FaButton>
          <FaButton variant="outline" :disabled="!selectedDevice" @click="router.push({ path: '/settings/interaction', query: { deviceId: selectedDevice, roleId: selectedRole } })">
            陪伴方式
          </FaButton>
          <FaButton variant="ghost" @click="router.push('/companion/persona')">
            管理伙伴
          </FaButton>
          <FaButton v-if="health && !health.devices.length" variant="outline" @click="router.push('/devices/pairing')">
            连接机器人
          </FaButton>
        </div>
      </div>
    </section>
    <DeliveryTimelineView :device-id="selectedDevice" :role-id="selectedRole" :refresh-key="refreshKey" />
    <div class="gap-5 grid lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
      <FaCard title="近期事务" :description="openTaskTotal === undefined ? '' : `这个伙伴在全部设备有 ${openTaskTotal} 项开放待办，最多展示 5 项。`">
        <div v-if="loading" class="text-sm text-muted-foreground py-4" role="status">
          正在读取待办…
        </div>
        <FaAlert v-else-if="tasksError" title="待办未获取" :description="tasksError">
          <template #action>
            <FaButton variant="outline" @click="loadScope">
              重试
            </FaButton>
          </template>
        </FaAlert>
        <AppEmpty v-else-if="openTaskTotal === undefined" description="选择伙伴后查看事务" />
        <AppEmpty v-else-if="!openTasks.length" description="没有开放待办，安心享受这一刻。" />
        <div v-else class="divide-y">
          <FaButton v-for="task in openTasks" :key="task.id" variant="ghost" class="py-3 text-left gap-3 h-auto w-full justify-between" @click="router.push(`/personal-tasks/detail/${task.id}`)">
            <span class="min-w-0"><span class="font-medium block truncate">{{ task.title }}</span><span class="text-xs text-muted-foreground">截止：{{ formatTime(task.dueAt) }}</span></span>
            <FaTag v-if="task.priority === 'HIGH'" variant="secondary">
              优先
            </FaTag>
          </FaButton>
        </div>
        <template #footer>
          <div class="flex flex-wrap gap-2">
            <FaButton variant="outline" @click="router.push('/personal-tasks')">
              全部待办
            </FaButton><FaButton variant="ghost" @click="router.push('/reminders')">
              查看提醒
            </FaButton>
          </div>
        </template>
      </FaCard>
      <FaCard title="工作陪伴" description="需要专注和休息提示时再开启。">
        <p class="text-sm" role="status">
          {{ workdayError || (loading ? '正在读取…' : workdayStateLabel) }}
        </p>
        <p v-if="workdayRuntime && workdayRuntime.state !== 'OFF'" class="text-sm text-muted-foreground mt-2">
          本轮专注 {{ Math.floor(workdayRuntime.focusSeconds / 60) }} 分钟
        </p>
        <template #footer>
          <FaButton variant="outline" @click="router.push({ path: '/companion/workday', query: { deviceId: selectedDevice } })">
            打开工作陪伴
          </FaButton>
        </template>
      </FaCard>
    </div>
    <FaAlert v-if="riskCount" title="机器人有需要关注的状态" :description="`发现 ${riskCount} 项供应商、通知或运行异常。`">
      <template #action>
        <FaButton variant="outline" @click="router.push('/devices/health')">
          查看运行健康
        </FaButton>
      </template>
    </FaAlert>
  </AppPageShell>
</template>

<style scoped>
.scope-bar {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  max-width: 640px;
}

.partner-welcome {
  display: flex;
  gap: 24px;
  align-items: center;
  padding: 24px;
  background: oklch(var(--card));
  border-radius: 24px;
}

@media (width <= 639px) {
  .scope-bar {
    grid-template-columns: minmax(0, 1fr);
  }

  .partner-welcome {
    gap: 12px;
    align-items: flex-start;
    padding: 16px;
  }

  .partner-welcome :deep(.partner-portrait) {
    width: 64px;
  }
}
</style>
