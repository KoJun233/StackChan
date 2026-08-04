<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import type { Device } from '@/api/modules/devices'
import { listDevices } from '@/api/modules/devices'
import type { MissedReminderPolicy, ProactiveTopicCooldown, SaveInteractionSettingsInput } from '@/api/modules/interactions'
import {
  getInteractionSettings,
  listProactiveTopics,
  resumeProactiveTopic,
  saveInteractionSettings,
  stopDeviceAudio,
} from '@/api/modules/interactions'
import { currentTimeZone } from '@/api/modules/reminders'

defineOptions({ name: 'InteractionSettings' })

interface InteractionFormModel extends SaveInteractionSettingsInput {
  deviceId: string
}

const devices = ref<Device[]>([])
const loading = ref(false)
const stopping = ref(false)
const topicCooldowns = ref<ProactiveTopicCooldown[]>([])
const resumingTopic = ref('')
const model = ref<InteractionFormModel>(defaults())

const deviceOptions = computed(() => devices.value.map(device => ({
  label: `${device.displayName} · ${device.online ? '在线' : '离线'}`,
  value: device.id,
})))

const missedPolicyOptions: { label: string, value: MissedReminderPolicy }[] = [
  { label: '恢复在线后立即播放', value: 'PLAY_NOW' },
  { label: '按指定分钟数稍后重试', value: 'SNOOZE' },
  { label: '跳过错过的这一轮', value: 'SKIP' },
]

const validationSchema = toTypedSchema(z.object({
  deviceId: z.string().uuid('请选择目标机器人'),
  volumePercent: z.number().int().min(0).max(100),
  nightMode: z.boolean(),
  continuousConversationEnabled: z.boolean(),
  followUpWindowSeconds: z.number().int().min(3).max(8),
  dndEnabled: z.boolean(),
  dndStart: z.string().min(1, '请选择免打扰开始时间'),
  dndEnd: z.string().min(1, '请选择免打扰结束时间'),
  zoneId: z.string().trim().min(1, '时区不能为空'),
  missedReminderPolicy: z.enum(['PLAY_NOW', 'SNOOZE', 'SKIP']),
  missedSnoozeMinutes: z.number().int().min(1).max(1440),
  proactiveEnabled: z.boolean(),
  proactiveStart: z.string().min(1, '请选择主动问候开始时间'),
  proactiveEnd: z.string().min(1, '请选择主动问候结束时间'),
  proactiveMinIntervalMinutes: z.number().int().min(30).max(1440),
  proactivePersonalizationEnabled: z.boolean(),
  proactiveDailyLimit: z.number().int().min(1).max(10),
  proactiveContent: z.string().trim().min(1, '请输入主动问候内容').max(500),
}))

function defaults(): InteractionFormModel {
  return {
    deviceId: '',
    volumePercent: 50,
    nightMode: false,
    continuousConversationEnabled: false,
    followUpWindowSeconds: 8,
    dndEnabled: false,
    dndStart: '22:00',
    dndEnd: '07:00',
    zoneId: currentTimeZone(),
    missedReminderPolicy: 'PLAY_NOW',
    missedSnoozeMinutes: 10,
    proactiveEnabled: false,
    proactiveStart: '09:00',
    proactiveEnd: '21:00',
    proactiveMinIntervalMinutes: 240,
    proactivePersonalizationEnabled: false,
    proactiveDailyLimit: 2,
    proactiveContent: '你好呀，记得休息一下，也可以和我聊聊天。',
  }
}

async function loadDevices() {
  loading.value = true
  try {
    devices.value = await listDevices()
    model.value.deviceId = devices.value[0]?.id ?? ''
    if (model.value.deviceId) {
      await loadSettings(model.value.deviceId)
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取机器人列表。' })
  }
  finally {
    loading.value = false
  }
}

async function loadSettings(deviceId: string) {
  if (!deviceId) {
    return
  }
  loading.value = true
  try {
    const settings = await getInteractionSettings(deviceId)
    model.value = {
      deviceId,
      volumePercent: settings.volumePercent,
      nightMode: settings.nightMode,
      continuousConversationEnabled: settings.continuousConversationEnabled,
      followUpWindowSeconds: settings.followUpWindowSeconds,
      dndEnabled: settings.dndEnabled,
      dndStart: settings.dndStart.slice(0, 5),
      dndEnd: settings.dndEnd.slice(0, 5),
      zoneId: settings.zoneId,
      missedReminderPolicy: settings.missedReminderPolicy,
      missedSnoozeMinutes: settings.missedSnoozeMinutes,
      proactiveEnabled: settings.proactiveEnabled,
      proactiveStart: settings.proactiveStart.slice(0, 5),
      proactiveEnd: settings.proactiveEnd.slice(0, 5),
      proactiveMinIntervalMinutes: settings.proactiveMinIntervalMinutes,
      proactivePersonalizationEnabled: settings.proactivePersonalizationEnabled ?? false,
      proactiveDailyLimit: settings.proactiveDailyLimit,
      proactiveContent: settings.proactiveContent,
    }
    try {
      topicCooldowns.value = await listProactiveTopics(deviceId)
    }
    catch {
      topicCooldowns.value = []
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取交互设置。' })
  }
  finally {
    loading.value = false
  }
}

async function resumeTopic(topicKey: string) {
  if (!model.value.deviceId) return
  resumingTopic.value = topicKey
  try {
    await resumeProactiveTopic(model.value.deviceId, topicKey)
    topicCooldowns.value = await listProactiveTopics(model.value.deviceId)
    useFaToast().success('已解除主题冷却')
  }
  catch (error) {
    useFaToast().error('操作失败', { description: error instanceof Error ? error.message : '无法解除主题冷却。' })
  }
  finally {
    resumingTopic.value = ''
  }
}

async function submit(values: InteractionFormModel) {
  loading.value = true
  try {
    const { deviceId, ...input } = values
    await saveInteractionSettings(deviceId, input)
    useFaToast().success('设置已保存', { description: '在线机器人会立即收到本地呈现与连续对话配置。' })
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存交互设置。' })
  }
  finally {
    loading.value = false
  }
}

async function stopAudio() {
  if (!model.value.deviceId) {
    return
  }
  stopping.value = true
  try {
    const result = await stopDeviceAudio(model.value.deviceId)
    if (!result.accepted) {
      throw new Error('机器人当前离线，停止命令未发送。')
    }
    useFaToast().success('已发送停止命令')
  }
  catch (error) {
    useFaToast().error('停止失败', { description: error instanceof Error ? error.message : '无法停止当前播报。' })
  }
  finally {
    stopping.value = false
  }
}

watch(() => model.value.deviceId, (deviceId, previous) => {
  if (deviceId && deviceId !== previous) {
    loadSettings(deviceId)
  }
})

onMounted(loadDevices)
</script>

<template>
  <div>
    <FaPageHeader title="交互与主动陪伴" />
    <FaPageMain>
      <FaLoading :loading="loading">
        <FaForm
          id="interaction-settings-form"
          :model="model"
          :validation-schema="validationSchema"
          keep-values-on-unmount
          scroll-to-error
          class="mx-auto grid max-w-5xl grid-cols-1 gap-6 md:grid-cols-2"
          @submit="submit"
        >
          <FaCard title="目标机器人与本地呈现" class="md:col-span-2">
            <div class="grid grid-cols-1 gap-x-8 gap-y-6 md:grid-cols-2">
              <FaFormItem name="deviceId" label="目标机器人" required>
                <FaSelect v-model="model.deviceId" :options="deviceOptions" class="w-full" />
              </FaFormItem>
              <FaFormItem name="volumePercent" label="播报音量" required description="0 为静音，100 为最大音量。">
                <FaNumberField v-model="model.volumePercent" :min="0" :max="100" :step="5" class="w-full" />
              </FaFormItem>
              <FaFormItem name="nightMode" label="夜间显示" description="降低非屏保状态下的屏幕亮度。">
                <FaSwitch v-model="model.nightMode" />
              </FaFormItem>
              <div class="flex items-end justify-end">
                <FaButton type="button" variant="destructive" :loading="stopping" @click="stopAudio">
                  <FaIcon name="i-ri:stop-circle-line" />
                  立即停止播报
                </FaButton>
              </div>
            </div>
            <div class="mt-6 grid grid-cols-1 gap-x-8 gap-y-6 border-t pt-6 md:grid-cols-2">
              <FaFormItem
                name="continuousConversationEnabled"
                label="连续对话"
                description="回答后仅在本地 VAD 检测到有效语音才上传；默认关闭。"
              >
                <FaSwitch v-model="model.continuousConversationEnabled" />
              </FaFormItem>
              <FaFormItem
                name="followUpWindowSeconds"
                label="跟进聆听窗口（秒）"
                required
                description="允许 3–8 秒；每次会话最多 3 个跟进回合且总计不超过 2 分钟。"
              >
                <FaNumberField v-model="model.followUpWindowSeconds" :min="3" :max="8" class="w-full" />
              </FaFormItem>
            </div>
          </FaCard>

          <FaCard title="免打扰与离线提醒">
            <div class="grid gap-6">
              <FaAlert title="播报边界" description="免打扰期间不会播放提醒或主动问候；到结束时间后再按规则处理。" />
              <FaFormItem name="dndEnabled" label="启用免打扰">
                <FaSwitch v-model="model.dndEnabled" />
              </FaFormItem>
              <FaFormItem name="dndStart" label="开始时间" required>
                <FaInput v-model="model.dndStart" type="time" class="w-full" />
              </FaFormItem>
              <FaFormItem name="dndEnd" label="结束时间" required>
                <FaInput v-model="model.dndEnd" type="time" class="w-full" />
              </FaFormItem>
              <FaFormItem name="zoneId" label="规则时区" required description="使用 IANA 时区处理跨日和夏令时。">
                <FaInput v-model="model.zoneId" class="w-full" />
              </FaFormItem>
              <FaFormItem name="missedReminderPolicy" label="离线错过后" required>
                <FaSelect v-model="model.missedReminderPolicy" :options="missedPolicyOptions" class="w-full" />
              </FaFormItem>
              <FaFormItem v-if="model.missedReminderPolicy === 'SNOOZE'" name="missedSnoozeMinutes" label="稍后分钟数" required>
                <FaNumberField v-model="model.missedSnoozeMinutes" :min="1" :max="1440" class="w-full" />
              </FaFormItem>
            </div>
          </FaCard>

          <FaCard title="有限主动问候">
            <div class="grid gap-6">
              <FaAlert title="默认关闭" description="问候由这里的固定时间窗、间隔和每日上限决定，不由模型自行决定何时打扰。" />
              <FaFormItem name="proactiveEnabled" label="允许主动问候">
                <FaSwitch v-model="model.proactiveEnabled" />
              </FaFormItem>
              <FaFormItem
                name="proactivePersonalizationEnabled"
                label="使用确认记忆生成一句个性化措辞"
                description="默认关闭。只读取已确认、启用且允许主动提及的一条记忆；规则不通过时不会调用模型。"
              >
                <FaSwitch v-model="model.proactivePersonalizationEnabled" />
              </FaFormItem>
              <FaFormItem name="proactiveStart" label="允许开始" required>
                <FaInput v-model="model.proactiveStart" type="time" class="w-full" />
              </FaFormItem>
              <FaFormItem name="proactiveEnd" label="允许结束" required>
                <FaInput v-model="model.proactiveEnd" type="time" class="w-full" />
              </FaFormItem>
              <FaFormItem name="proactiveMinIntervalMinutes" label="最小间隔（分钟）" required>
                <FaNumberField v-model="model.proactiveMinIntervalMinutes" :min="30" :max="1440" :step="30" class="w-full" />
              </FaFormItem>
              <FaFormItem name="proactiveDailyLimit" label="每日最多次数" required>
                <FaNumberField v-model="model.proactiveDailyLimit" :min="1" :max="10" class="w-full" />
              </FaFormItem>
              <FaFormItem name="proactiveContent" label="固定问候内容" required>
                <FaTextarea v-model="model.proactiveContent" rows="4" align="block" class="w-full" />
              </FaFormItem>
              <div class="border-t pt-5">
                <div class="mb-3 text-sm font-medium">最近主动主题</div>
                <FaAlert
                  v-if="topicCooldowns.length === 0"
                  title="暂无主题记录"
                  description="个性化主题成功进入主动提醒后才会出现；使用记录不复制记忆正文。"
                />
                <div v-for="topic in topicCooldowns" :key="topic.topicKey" class="mb-3 rounded-md border p-3 text-sm">
                  <div class="flex items-center justify-between gap-3">
                    <div class="min-w-0">
                      <div class="truncate font-medium">{{ topic.topicKey }}</div>
                      <div class="mt-1 text-xs text-muted-foreground">
                        冷却至 {{ new Date(topic.cooldownUntil).toLocaleString() }}
                        <span v-if="topic.userMuted"> · 用户已要求不再主动提及</span>
                      </div>
                    </div>
                    <FaButton
                      v-if="topic.userMuted || new Date(topic.cooldownUntil).getTime() > Date.now()"
                      type="button"
                      size="sm"
                      variant="outline"
                      :loading="resumingTopic === topic.topicKey"
                      @click="resumeTopic(topic.topicKey)"
                    >
                      解除冷却
                    </FaButton>
                  </div>
                </div>
              </div>
            </div>
          </FaCard>

          <FaFixedBar position="bottom" class="md:col-span-2 flex justify-center">
            <FaButton type="submit" form="interaction-settings-form" :loading="loading">
              保存交互设置
            </FaButton>
          </FaFixedBar>
        </FaForm>
      </FaLoading>
    </FaPageMain>
  </div>
</template>
