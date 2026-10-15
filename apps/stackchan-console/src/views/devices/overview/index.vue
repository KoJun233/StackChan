<script setup lang="ts">
import type { Device, VoiceQuality, VoiceTurn, VoiceTurnStatus } from '@/api/modules/devices'
import type { MemoryUsageReference } from '@/api/modules/personaMemory'
import { getDeviceVoiceQuality, listDevices, listDeviceVoiceTurns, stopDeviceMotion } from '@/api/modules/devices'
import { getMemoryUsage } from '@/api/modules/personaMemory'
import { formatVoiceDuration, voiceCaptureLimitLabel, voiceDiagnosticCompleteness, voiceEndpointFeedbackLatency, voiceQualityPercentiles, voiceResponseLatency, voiceResponseLatencyExplanation, voiceStageSource, voiceStageTiming } from './voiceDiagnostics'

defineOptions({ name: 'DeviceOverview' })
const devices = ref<Device[]>([])
const loading = ref(false)
const commandDeviceId = ref<string | null>(null)
const selectedDevice = ref<Device | null>(null)
const voiceTurns = ref<VoiceTurn[]>([])
const voiceTurnsLoading = ref(false)
const voiceTurnsError = ref('')
const voiceQuality = ref<VoiceQuality | null>(null)
const voiceQualityLoading = ref(false)
const voiceQualityError = ref('')
let voiceTurnsRequest = 0
const memoryUsageByTurn = ref<Record<string, MemoryUsageReference[]>>({})
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
  TOUCH_STARTED: '触摸发起',
  LISTENING: '开始聆听',
  FOLLOW_UP_LISTENING: '跟进聆听',
  SPEECH_CAPTURED: '采集到语音',
  UPLOAD_STARTED: '开始上传',
  REQUEST_RECEIVED: '服务端已接收',
  ASR_COMPLETED: '语音识别完成',
  LLM_COMPLETED: '回答生成完成',
  TTS_COMPLETED: '语音合成完成',
  PLAYBACK_STARTED: '开始播放',
  PLAYBACK_COMPLETED: '播放完成',
  FOLLOW_UP_TIMEOUT: '跟进超时',
  CONVERSATION_ENDED: '会话结束',
  LISTENING_RESUMED: '恢复聆听',
  MANUAL_INPUT_READY: '手动输入就绪',
  CANCELLED: '回合已取消',
  FAILED: '回合失败',
}
const statusLabels: Record<VoiceTurnStatus, string> = {
  IN_PROGRESS: '处理中',
  RESPONSE_READY: '回复已就绪',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  FAILED: '失败',
}
async function showVoiceTurns(device: Device) {
  const request = ++voiceTurnsRequest
  selectedDevice.value = device
  voiceTurns.value = []
  memoryUsageByTurn.value = {}
  voiceTurnsLoading.value = true
  voiceTurnsError.value = ''
  voiceQuality.value = null
  voiceQualityError.value = ''
  void loadVoiceQuality(device.id, request)
  try {
    const turns = await listDeviceVoiceTurns(device.id)
    if (request !== voiceTurnsRequest) {
      return
    }
    const usageEntries = await Promise.all(turns.map(async (turn) => {
      try {
        const usage = await getMemoryUsage(turn.turnId)
        return [turn.turnId, usage.memories] as const
      }
      catch {
        return [turn.turnId, []] as const
      }
    }))
    if (request !== voiceTurnsRequest) {
      return
    }
    voiceTurns.value = turns
    memoryUsageByTurn.value = Object.fromEntries(usageEntries)
  }
  catch (error) {
    if (request !== voiceTurnsRequest) {
      return
    }
    voiceTurnsError.value = error instanceof Error ? error.message : '无法获取最近语音回合。'
    voiceTurns.value = []
    memoryUsageByTurn.value = {}
    useFaToast().error('加载交互诊断失败', { description: error instanceof Error ? error.message : '无法获取最近语音回合。' })
  }
  finally {
    if (request === voiceTurnsRequest) {
      voiceTurnsLoading.value = false
    }
  }
}
async function loadVoiceQuality(deviceId: string, request: number) {
  voiceQualityLoading.value = true
  try {
    const quality = await getDeviceVoiceQuality(deviceId)
    if (request === voiceTurnsRequest) {
      voiceQuality.value = quality
    }
  }
  catch (error) {
    if (request === voiceTurnsRequest) {
      voiceQualityError.value = error instanceof Error ? error.message : '无法获取语音质量汇总。'
    }
  }
  finally {
    if (request === voiceTurnsRequest) {
      voiceQualityLoading.value = false
    }
  }
}
onMounted(load)
</script>

<template>
  <AppPageShell title="设备与日常控制" description="安全停止只发送停机命令，不会启用身体动作。">
    <FaCard>
      <template #header>
        <div class="flex gap-4 items-center justify-between">
          <div>
            <h2 class="text-base font-semibold">
              设备状态
            </h2>
            <p class="text-sm text-muted-foreground">
              查看设备在线状态、固件与安全状态。
            </p>
          </div>
          <FaButton variant="outline" size="sm" :loading="loading" @click="load">
            刷新
          </FaButton>
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
            <FaButton variant="outline" size="sm" as-child>
              <RouterLink :to="{ path: '/settings/interaction', query: { deviceId: row.original.id, tab: 'device' } }">
                音量与交互
              </RouterLink>
            </FaButton>
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
        <div class="flex gap-4 items-center justify-between">
          <div>
            <h2 class="text-base font-semibold">
              {{ selectedDevice.displayName }} 的最近语音回合
            </h2>
            <p class="text-sm text-muted-foreground">
              仅显示阶段、耗时和安全失败类别；不保存音频、识别文本或机器人回复。
            </p>
          </div>
          <FaButton variant="outline" size="sm" :loading="voiceTurnsLoading || voiceQualityLoading" @click="showVoiceTurns(selectedDevice)">
            刷新
          </FaButton>
        </div>
      </template>

      <section class="mb-4 p-4 border border-border rounded-lg" data-voice-quality>
        <h3 class="text-sm font-medium">
          最近 24 小时的语音质量
        </h3>
        <p v-if="voiceQualityLoading" class="text-sm text-muted-foreground mt-2">
          正在获取汇总…
        </p>
        <FaAlert v-else-if="voiceQualityError" class="mt-2" variant="destructive" title="质量汇总未获取" :description="voiceQualityError" />
        <div v-else-if="voiceQuality" class="text-sm mt-2 space-y-2">
          <p class="text-xs text-muted-foreground">
            {{ new Date(voiceQuality.from).toLocaleString('zh-CN') }} 至 {{ new Date(voiceQuality.to).toLocaleString('zh-CN') }}
          </p>
          <p>
            总尝试 {{ voiceQuality.totalAttempts }} · 成功 {{ voiceQuality.successfulTurns }} · 失败 {{ voiceQuality.failedTurns }} · 取消 {{ voiceQuality.cancelledTurns }} · 未结束 {{ voiceQuality.incompleteTurns }}
          </p>
          <p class="text-xs text-muted-foreground">
            首声统计：成功普通短聊 {{ voiceQuality.ordinaryChatSuccessfulTurns }} · 非适用回合 {{ voiceQuality.excludedLatencyTurns }}
          </p>
          <div class="p-3 rounded-md bg-muted/50 space-y-1">
            <p>普通短聊：采音端点至设备开始播放</p>
            <p>{{ voiceQualityPercentiles(voiceQuality.endpointToFirstSound) }}</p>
            <p class="text-xs text-muted-foreground">
              设备单调计时 · 适用成功样本 {{ voiceQuality.endpointToFirstSound.applicableSamples }} · 缺失 {{ voiceQuality.endpointToFirstSound.missingSamples }} · 至少 30 个样本后显示百分位
            </p>
          </div>
          <div class="p-3 rounded-md bg-muted/50 space-y-1">
            <p>端点至本地处理反馈</p>
            <p>{{ voiceQualityPercentiles(voiceQuality.endpointToLocalFeedback) }}</p>
            <p class="text-xs text-muted-foreground">
              设备单调计时 · 适用样本 {{ voiceQuality.endpointToLocalFeedback.applicableSamples }} · 缺失 {{ voiceQuality.endpointToLocalFeedback.missingSamples }} · 包含后续失败或取消的回合
            </p>
            <p v-if="voiceQuality.endpointToLocalFeedback.maximumMs != null" class="text-xs text-muted-foreground">
              最大 {{ formatVoiceDuration(voiceQuality.endpointToLocalFeedback.maximumMs) }}<span v-if="voiceQuality.endpointToLocalFeedback.overThresholdSamples != null"> · 超过 200ms {{ voiceQuality.endpointToLocalFeedback.overThresholdSamples }} 次</span>
            </p>
          </div>
          <p v-if="Object.keys(voiceQuality.failureCounts).length" class="text-xs text-destructive">
            失败类别：<span v-for="(count, code) in voiceQuality.failureCounts" :key="code" class="mr-3">{{ code }} {{ count }} 次</span>
          </p>
        </div>
        <p class="text-xs text-muted-foreground mt-3">
          真实停说至首声、触摸至扬声器静音：尚无实体测量。设备开始播放记录与服务端耗时均不能替代声学观察；复杂工具任务、取消和失败不混入普通短聊首声百分位。
        </p>
      </section>

      <FaAlert v-if="voiceTurnsError" variant="destructive" title="诊断未获取" :description="voiceTurnsError" />
      <AppEmpty v-else-if="!voiceTurnsLoading && voiceTurns.length === 0" description="暂无语音回合诊断数据" />
      <div v-else class="space-y-4">
        <section
          v-for="turn in voiceTurns"
          :key="turn.turnId"
          class="p-4 border border-border rounded-lg"
          :data-turn-id="turn.turnId"
        >
          <div class="mb-3 flex flex-wrap gap-2 items-center justify-between">
            <div>
              <div class="font-medium">
                {{ new Date(turn.startedAt).toLocaleString('zh-CN') }}
              </div>
              <div class="text-xs text-muted-foreground mt-1">
                回合 {{ turn.turnId.slice(0, 8) }}
              </div>
            </div>
            <div class="text-sm" :class="turn.status === 'FAILED' ? 'text-destructive' : 'text-muted-foreground'">
              {{ statusLabels[turn.status] }}<span v-if="turn.failureCode"> · {{ turn.failureCode }}</span>
            </div>
          </div>
          <div class="text-sm text-muted-foreground mb-3 space-y-1">
            <div>
              采音端点至设备开始播放 {{ formatVoiceDuration(voiceResponseLatency(turn)) }}
              <span class="text-xs block">{{ voiceResponseLatencyExplanation(turn) }}</span>
            </div>
            <div>
              端点至本地处理反馈 {{ formatVoiceDuration(voiceEndpointFeedbackLatency(turn)) }}
              <span v-if="voiceEndpointFeedbackLatency(turn) === null" class="text-xs block">未知：缺少设备单调计时反馈记录</span>
            </div>
            <div>{{ voiceCaptureLimitLabel(turn) }}</div>
            <div>{{ voiceDiagnosticCompleteness(turn) }}</div>
          </div>
          <ol class="gap-2 grid md:grid-cols-2 xl:grid-cols-3">
            <li v-for="event in turn.events" :key="`${event.source}-${event.stage}`" class="text-sm px-3 py-2 rounded-md bg-muted/50">
              <div class="flex gap-2 items-center justify-between">
                <span>{{ stageLabels[event.stage] || event.stage }}</span>
                <span class="text-xs text-muted-foreground">{{ voiceStageTiming(event) }}</span>
              </div>
              <div class="text-xs text-muted-foreground mt-1">
                {{ voiceStageSource(event) }}<span v-if="event.failureCode"> · {{ event.failureCode }}</span>
                <span v-if="event.diagnosticCode && event.diagnosticCode !== 'deterministic_action'" class="block break-all">{{ event.diagnosticCode }}</span>
              </div>
            </li>
          </ol>
          <div v-if="memoryUsageByTurn[turn.turnId]?.length" class="mt-3 p-3 border rounded-md border-dashed">
            <div class="text-sm font-medium">
              本回合引用的长期记忆
            </div>
            <ul class="text-xs text-muted-foreground mt-2 space-y-1">
              <li v-for="memory in memoryUsageByTurn[turn.turnId]" :key="memory.memoryId">
                {{ memory.title }} · 主题 {{ memory.topicKey }} · {{ memory.scopeType === 'DEVICE' ? '当前设备' : '全局' }} · {{ memory.sourceDetail }}
              </li>
            </ul>
            <div class="text-xs text-muted-foreground mt-2">
              这里只按记忆 ID 查询当前来源说明，使用记录本身不复制记忆正文。
            </div>
          </div>
        </section>
      </div>
    </FaCard>
  </AppPageShell>
</template>
