<script setup lang="ts">
import type { DeliveryTimeline, Reminder } from '@/api/modules/reminders'
import { getDeliveryTimeline } from '@/api/modules/reminders'

const props = defineProps<{ deviceId: string, roleId: string, refreshKey: number }>()
const data = ref<DeliveryTimeline>()
const loading = ref(false)
const error = ref('')
let request = 0

watch(() => [props.deviceId, props.roleId, props.refreshKey], async () => {
  const current = ++request
  data.value = undefined
  error.value = ''
  loading.value = false
  if (!props.deviceId || !props.roleId) {
    return
  }
  loading.value = true
  try {
    const result = await getDeliveryTimeline(props.deviceId, props.roleId)
    if (current === request) {
      data.value = result
    }
  }
  catch (cause) {
    if (current === request) {
      error.value = cause instanceof Error ? cause.message : '播报视图加载失败'
    }
  }
  finally {
    if (current === request) {
      loading.value = false
    }
  }
}, { immediate: true })

function sourceLabel(item: Reminder) {
  if (item.source === 'EXTERNAL') {
    return '外部通知'
  }
  if (item.source === 'USER') {
    return '个人提醒（含待办截止）'
  }
  const key = item.proactiveTopicKey ?? ''
  if (key.startsWith('workday:brief:')) {
    return '工作简报'
  }
  if (key.startsWith('workday:rest:')) {
    return '工作休息'
  }
  if (key.startsWith('workday:')) {
    return '工作场景提示'
  }
  return item.proactiveSourceName ? '兴趣资讯开场' : '主动陪伴'
}

function time(value: string | null, zoneId: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { timeZone: zoneId }) : '暂无记录'
}
</script>

<template>
  <FaCard title="将要提醒什么 · 刚才说了什么" description="只展示所选设备与伙伴。排期可能因离线、免打扰或忙碌顺延；播完不代表任务完成，也不代表外部业务已执行。">
    <AppLoading :loading="loading">
      <FaAlert v-if="error" variant="destructive" title="未能读取播报" :description="error" />
      <AppEmpty v-else-if="!deviceId || !roleId" description="请先选择设备和伙伴" />
      <div v-else-if="data" class="gap-6 grid md:grid-cols-2">
        <div>
          <p class="font-medium mb-3">
            等待或正在投递：{{ data.upcomingTotal }} 条
          </p>
          <AppEmpty v-if="!data.upcoming.length" description="没有等待投递的消息" />
          <div v-for="item in data.upcoming" :key="item.id" class="mb-3 p-3 border rounded">
            <FaTag variant="secondary">
              {{ sourceLabel(item) }}
            </FaTag>
            <p class="my-2 whitespace-pre-wrap break-words">
              {{ item.content }}
            </p>
            <p class="text-xs text-muted-foreground">
              {{ item.status === 'DISPATCHED' ? '已下发，等待播放确认' : '待投递' }} · {{ time(item.scheduledAt, item.zoneId) }}（{{ item.zoneId }}）
            </p>
          </div>
          <p v-if="data.upcomingTotal > data.upcoming.length" class="text-sm text-muted-foreground">
            仅展示排期最近的 {{ data.upcoming.length }} 条。
          </p>
        </div>
        <div>
          <p class="font-medium mb-3">
            最近 30 分钟成功播报
          </p>
          <AppEmpty v-if="!data.recent.length" description="近期没有成功播报记录" />
          <div v-for="item in data.recent" :key="item.id" class="mb-3 p-3 border rounded">
            <FaTag variant="outline">
              {{ sourceLabel(item) }}
            </FaTag>
            <p class="my-2 whitespace-pre-wrap break-words">
              {{ item.content }}
            </p>
            <p class="text-xs text-muted-foreground">
              播完于 {{ time(item.lastCompletedAt, item.zoneId) }}（{{ item.zoneId }}）
            </p>
          </div>
          <p v-if="data.recentHasMore" class="text-sm text-muted-foreground">
            仅展示最近 10 条。周期提醒的上次播报与下次排期可以同时出现。
          </p>
        </div>
      </div>
    </AppLoading>
  </FaCard>
</template>
