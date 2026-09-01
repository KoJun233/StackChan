<script setup lang="ts">
import type { PersonalTask } from '@/api/modules/personalTasks'
import type { Reminder } from '@/api/modules/reminders'
import type { SystemHealth } from '@/api/modules/systemHealth'
import type { WorkdayRuntime } from '@/api/modules/workday'
import { listPersonalTasks } from '@/api/modules/personalTasks'
import { listReminders } from '@/api/modules/reminders'
import { getSystemHealth } from '@/api/modules/systemHealth'
import { getWorkdayRuntime } from '@/api/modules/workday'

defineOptions({ name: 'DashboardHome' })

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const health = ref<SystemHealth>()
const openTasks = ref<PersonalTask[]>([])
const pendingReminders = ref<Reminder[]>([])
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
  loading.value = true
  loadError.value = ''
  try {
    const [healthResult, tasksResult, remindersResult] = await Promise.all([
      getSystemHealth(),
      listPersonalTasks({ from: 0, limit: 5, status: 'OPEN' }),
      listReminders({ from: 0, limit: 5, status: 'PENDING' }),
    ])
    health.value = healthResult
    openTasks.value = tasksResult.list
    pendingReminders.value = remindersResult.list
    const device = healthResult.devices.find(item => item.online) ?? healthResult.devices[0]
    workdayRuntime.value = device ? await getWorkdayRuntime(device.id) : undefined
  }
  catch (error) {
    loadError.value = error instanceof Error ? error.message : '今日概览加载失败。'
  }
  finally {
    loading.value = false
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
  <AppPageShell title="今日概览" description="先看机器人、工作陪伴和今天需要处理的事项，再进入具体管理页面。">
    <template #actions>
      <FaButton variant="outline" :loading="loading" @click="loadDashboard">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </template>

    <FaAlert v-if="loadError" variant="destructive" title="概览未完整加载" :description="loadError" />

    <div class="gap-3 grid sm:grid-cols-2 xl:grid-cols-4">
      <AppMetricCard label="在线机器人" :value="`${onlineDevices.length}/${health?.devices.length ?? 0}`" icon="i-ri:robot-2-line" :tone="onlineDevices.length ? 'success' : 'warning'" description="在线设备可以立即接收对话与安全命令。" />
      <AppMetricCard label="开放待办" :value="openTasks.length" icon="i-ri:task-line" :tone="openTasks.length ? 'warning' : 'success'" description="这里只展示最前面的 5 条开放待办。" />
      <AppMetricCard label="待投递提醒" :value="pendingReminders.length" icon="i-ri:notification-3-line" description="按计划等待可靠队列投递。" />
      <AppMetricCard label="需要关注" :value="riskCount" icon="i-ri:shield-check-line" :tone="riskCount ? 'danger' : 'success'" description="供应商、通知与安全错误的聚合提醒。" />
    </div>

    <div class="gap-4 grid xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,.8fr)]">
      <FaCard title="工作陪伴" description="高频状态和入口集中在这里，详细规则不会挤占首屏。">
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

      <FaCard title="下一批提醒" description="内容只在管理员会话中展示，不写入浏览器持久化。">
        <FaEmpty v-if="!pendingReminders.length" description="没有待投递提醒" />
        <div v-else class="space-y-2">
          <FaButton v-for="reminder in pendingReminders" :key="reminder.id" type="button" variant="outline" class="p-3 text-left flex-col h-auto w-full items-stretch" @click="router.push(`/reminders/detail/${reminder.id}`)">
            <p class="text-sm font-medium line-clamp-2">
              {{ reminder.content }}
            </p>
            <p class="text-xs text-muted-foreground mt-1">
              计划：{{ formatTime(reminder.scheduledAt) }}
            </p>
          </FaButton>
        </div>
      </FaCard>
    </div>
  </AppPageShell>
</template>
