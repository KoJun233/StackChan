<script setup lang="ts">
import type { Device, VoiceTurn, VoiceTurnEvent, VoiceTurnStatus } from '@/api/modules/devices'
import { listDevices, listDeviceVoiceTurns, stopDeviceMotion } from '@/api/modules/devices'

defineOptions({ name: 'DeviceOverview' })

const devices = ref<Device[]>([])
const loading = ref(false)
const commandDeviceId = ref<string | null>(null)
const selectedDevice = ref<Device | null>(null)
const voiceTurns = ref<VoiceTurn[]>([])
const voiceTurnsLoading = ref(false)
const columns = [
  { accessorKey: 'displayName', header: '设备名称' },
  { accessorKey: 'onlineLabel', header: '状态' },
  { accessorKey: 'firmwareVersion', header: '固件版本' },
  { accessorKey: 'safetyState', header: '安全状态' },
  { accessorKey: 'lastSeenLabel', header: '最近心跳' },
  { id: 'actions', header: '安全操作' },
]
const rows = computed(() => devices.value.map(device => ({
  ...device,
  onlineLabel: device.online ? '在线' : '离线',
  lastSeenLabel: device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString('zh-CN') : '从未上报',
})))

async function load() {
  loading.value = true
  try {
    devices.value = await listDevices()
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法获取设备状态。' })
  }
  finally {
    loading.value = false
  }
}

async function stopDevice(device: Device) {
  if (!device.commandAvailable || commandDeviceId.value) {
    return
  }

  commandDeviceId.value = device.id
  try {
    await stopDeviceMotion(device.id)
    useFaToast().success('安全停止命令已发送', { description: '设备将仅接收停机命令，不会启用任何舵机动作。' })
    await load()
  }
  catch (error) {
    useFaToast().error('安全停止失败', { description: error instanceof Error ? error.message : '设备当前无法接收安全停止命令。' })
  }
  finally {
    commandDeviceId.value = null
  }
}

const stageLabels: Record<string, string> = {
  WAKE_DETECTED: '检测到唤醒',
  LISTENING: '开始聆听',
  SPEECH_CAPTURED: '采集到语音',
  UPLOAD_STARTED: '开始上传',
  REQUEST_RECEIVED: '服务端已接收',
  ASR_COMPLETED: '语音识别完成',
  LLM_COMPLETED: '回答生成完成',
  TTS_COMPLETED: '语音合成完成',
  PLAYBACK_STARTED: '开始播放',
  PLAYBACK_COMPLETED: '播放完成',
  LISTENING_RESUMED: '恢复待唤醒',
  FAILED: '回合失败',
}

const statusLabels: Record<VoiceTurnStatus, string> = {
  IN_PROGRESS: '处理中',
  RESPONSE_READY: '回复已就绪',
  COMPLETED: '已完成',
  FAILED: '失败',
}

function stageElapsed(turn: VoiceTurn, event: VoiceTurnEvent) {
  const elapsedMs = event.elapsedMs ?? Math.max(0, Date.parse(event.occurredAt) - Date.parse(turn.startedAt))
  return `+${(elapsedMs / 1000).toFixed(elapsedMs < 10000 ? 2 : 1)} 秒`
}

async function showVoiceTurns(device: Device) {
  selectedDevice.value = device
  voiceTurnsLoading.value = true
  try {
    voiceTurns.value = await listDeviceVoiceTurns(device.id)
  }
  catch (error) {
    voiceTurns.value = []
    useFaToast().error('加载交互诊断失败', { description: error instanceof Error ? error.message : '无法获取最近语音回合。' })
  }
  finally {
    voiceTurnsLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <FaPageMain>
    <template #title>
      <div>
        <div>设备总览</div>
        <p class="mt-1 text-xs text-muted-foreground">在线状态由服务器按最近心跳计算；安全停止只发送停机命令，不会启用任何舵机动作。</p>
      </div>
    </template>
    <FaCard>
      <template #header>
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="text-base font-semibold">设备状态</h2>
            <p class="text-sm text-muted-foreground">查看设备在线状态、固件与安全状态。</p>
          </div>
          <FaButton variant="outline" size="sm" :loading="loading" @click="load">刷新</FaButton>
        </div>
      </template>
      <FaTable
        :columns="columns"
        :data="rows"
        :empty-text="loading ? '正在加载设备…' : '暂无已配对的设备'"
        row-key="id"
        border
      >
        <template #cell-actions="{ row }">
          <div class="flex flex-wrap gap-2">
            <FaButton
              variant="outline"
              size="sm"
              :disabled="!row.original.commandAvailable || commandDeviceId !== null"
              :loading="commandDeviceId === row.original.id"
              @click="stopDevice(row.original)"
            >
              安全停止
            </FaButton>
            <FaButton variant="outline" size="sm" @click="showVoiceTurns(row.original)">
              交互诊断
            </FaButton>
          </div>
        </template>
      </FaTable>
    </FaCard>

    <FaCard v-if="selectedDevice" class="mt-4">
      <template #header>
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="text-base font-semibold">{{ selectedDevice.displayName }} 的最近语音回合</h2>
            <p class="text-sm text-muted-foreground">仅显示阶段、耗时和安全失败类别；不保存音频、识别文本或机器人回复。</p>
          </div>
          <FaButton variant="outline" size="sm" :loading="voiceTurnsLoading" @click="showVoiceTurns(selectedDevice)">刷新</FaButton>
        </div>
      </template>

      <FaEmpty v-if="!voiceTurnsLoading && voiceTurns.length === 0" description="暂无语音回合诊断数据" />
      <div v-else class="space-y-4">
        <section
          v-for="turn in voiceTurns"
          :key="turn.turnId"
          class="rounded-lg border border-border p-4"
          :data-turn-id="turn.turnId"
        >
          <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div>
              <div class="font-medium">{{ new Date(turn.startedAt).toLocaleString('zh-CN') }}</div>
              <div class="mt-1 text-xs text-muted-foreground">回合 {{ turn.turnId.slice(0, 8) }}</div>
            </div>
            <div class="text-sm" :class="turn.status === 'FAILED' ? 'text-destructive' : 'text-muted-foreground'">
              {{ statusLabels[turn.status] }}<span v-if="turn.failureCode"> · {{ turn.failureCode }}</span>
            </div>
          </div>
          <ol class="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
            <li v-for="event in turn.events" :key="`${event.source}-${event.stage}`" class="rounded-md bg-muted/50 px-3 py-2 text-sm">
              <div class="flex items-center justify-between gap-2">
                <span>{{ stageLabels[event.stage] || event.stage }}</span>
                <span class="text-xs text-muted-foreground">{{ stageElapsed(turn, event) }}</span>
              </div>
              <div class="mt-1 text-xs text-muted-foreground">
                {{ event.source === 'DEVICE' ? '设备' : '服务端' }}<span v-if="event.failureCode"> · {{ event.failureCode }}</span>
              </div>
            </li>
          </ol>
        </section>
      </div>
    </FaCard>
  </FaPageMain>
</template>
