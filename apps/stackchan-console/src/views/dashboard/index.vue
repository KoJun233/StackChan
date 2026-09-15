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

const onlineDevices = computed(() => health.value?.devices.filter(device => device.online) ?? [])
const riskCount = computed(() => {
  if (!health.value) {
    return 0
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
  loading.value = true
  loadError.value = ''
  try {
    const [healthResult, roleResult] = await Promise.all([
      getSystemHealth(),
      listRoles(),
    ])
    if (current !== request) {
      return
    }
    health.value = healthResult
    roles.value = roleResult
    if (!healthResult.devices.some(device => device.id === selectedDevice.value)) {
      selectedDevice.value = (healthResult.devices.find(item => item.online) ?? healthResult.devices[0])?.id ?? ''
      selectedRole.value = ''
    }
    if (selectedDevice.value && !selectedRole.value) {
      const active = await getDeviceActiveRole(selectedDevice.value)
      if (current !== request) {
        return
      }
      selectedRole.value = active.id
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
  loadError.value = ''
  refreshKey.value++
  if (!selectedDevice.value || !selectedRole.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [tasks, workday] = await Promise.all([
      listPersonalTasks({ from: 0, limit: 5, status: 'OPEN', roleId: selectedRole.value }),
      getWorkdayRuntime(selectedDevice.value),
    ])
    if (current !== request) {
      return
    }
    openTasks.value = tasks.list
    openTaskTotal.value = tasks.total
    workdayRuntime.value = workday
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
  <AppPageShell title="今日概览" description="先看你的伙伴和即将到来的提醒。工作陪伴按需开启，聊天与记忆不依赖工作模式。">
    <template #actions>
      <FaButton variant="outline" :loading="loading" @click="loadDashboard">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </template>

    <FaAlert v-if="loadError" variant="destructive" title="概览未完整加载" :description="loadError" />

    <FaCard title="查看哪个伙伴" description="首次显示设备当前伙伴；这里切换查看范围，不切换机器人正在扮演的角色。">
      <div class="gap-3 grid sm:grid-cols-2">
        <FaSelect v-model="selectedDevice" :options="deviceOptions" placeholder="选择设备" @change="selectDevice" />
        <FaSelect v-model="selectedRole" :options="roleOptions" placeholder="选择伙伴" @change="loadScope" />
      </div>
    </FaCard>

    <div class="gap-3 grid sm:grid-cols-2 xl:grid-cols-4">
      <AppMetricCard label="在线机器人" :value="`${onlineDevices.length}/${health?.devices.length ?? 0}`" icon="i-ri:robot-2-line" :tone="onlineDevices.length ? 'success' : 'warning'" description="在线设备可以立即接收对话与安全命令。" />
      <AppMetricCard label="伙伴的开放待办" :value="openTaskTotal ?? '未加载'" icon="i-ri:task-line" :tone="openTasks.length ? 'warning' : 'success'" description="所选伙伴在所有设备的完整总数，下方仅展示前 5 条。" />
      <AppMetricCard label="正在查看" :value="selectedRoleName" icon="i-ri:user-heart-line" description="伙伴之间的对话、记忆和事务各自独立。" />
      <AppMetricCard label="需要关注" :value="riskCount" icon="i-ri:shield-check-line" :tone="riskCount ? 'danger' : 'success'" description="供应商、通知与安全错误的聚合提醒。" />
    </div>

    <div class="gap-4 grid xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,.8fr)]">
      <FaCard title="工作陪伴（可选）" description="只显示所选设备的工作状态；需要专注和休息提示时再开启。">
        <div class="gap-3 grid sm:grid-cols-3">
          <AppMetricCard label="当前状态" :value="workdayStateLabel" icon="i-ri:focus-2-line" />
          <AppMetricCard label="本轮专注" :value="`${Math.floor((workdayRuntime?.focusSeconds ?? 0) / 60)} 分钟`" icon="i-ri:timer-line" />
          <AppMetricCard label="最近更新" :value="formatTime(workdayRuntime?.updatedAt ?? null)" icon="i-ri:time-line" />
        </div>
        <template #footer>
          <FaButton @click="router.push('/companion/workday')">
            打开工作陪伴
            <FaIcon name="i-ri:arrow-right-line" />
          </FaButton>
        </template>
      </FaCard>

      <FaCard title="常用入口" description="按日常任务组织，而不是按技术模块堆叠。">
        <div class="gap-2 grid sm:grid-cols-2 xl:grid-cols-1">
          <FaButton variant="outline" class="justify-start" @click="router.push('/companion/chat')">
            <FaIcon name="i-ri:chat-smile-3-line" />陪伴聊天
          </FaButton>
          <FaButton variant="outline" class="justify-start" @click="router.push('/personal-tasks')">
            <FaIcon name="i-ri:task-line" />个人待办
          </FaButton>
          <FaButton variant="outline" class="justify-start" @click="router.push('/devices/overview')">
            <FaIcon name="i-ri:dashboard-2-line" />设备总览
          </FaButton>
          <FaButton variant="outline" class="justify-start" @click="router.push('/devices/health')">
            <FaIcon name="i-ri:heart-pulse-line" />运行健康
          </FaButton>
        </div>
      </FaCard>
    </div>

    <div class="gap-4 grid lg:grid-cols-2">
      <FaCard title="最近开放待办" description="完整筛选、完成与编辑操作仍在待办页面。">
        <FaEmpty v-if="!openTasks.length" description="没有开放待办" />
        <div v-else class="space-y-2">
          <FaButton v-for="task in openTasks" :key="task.id" type="button" variant="outline" class="p-3 text-left flex-col h-auto w-full items-stretch" @click="router.push(`/personal-tasks/detail/${task.id}`)">
            <div class="flex gap-3 items-center justify-between">
              <span class="text-sm font-medium truncate">{{ task.title }}</span>
              <FaTag :variant="task.priority === 'HIGH' ? 'destructive' : 'secondary'">
                {{ task.priority === 'HIGH' ? '高优先级' : task.priority === 'LOW' ? '低优先级' : '普通' }}
              </FaTag>
            </div>
            <p class="text-xs text-muted-foreground mt-1">
              截止：{{ formatTime(task.dueAt) }}
            </p>
          </FaButton>
        </div>
      </FaCard>
    </div>
    <DeliveryTimelineView :device-id="selectedDevice" :role-id="selectedRole" :refresh-key="refreshKey" />
  </AppPageShell>
</template>
