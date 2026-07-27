<script setup lang="ts">
import type { FormExpose } from '@fantastic-admin/components'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import type { Device } from '@/api/modules/devices'
import type { ReminderRecurrence } from '@/api/modules/reminders'
import { listDevices } from '@/api/modules/devices'
import {
  createReminder,
  currentTimeZone,
  getReminder,
  toLocalDateTimeValue,
  toReminderInstant,
  updateReminder,
} from '@/api/modules/reminders'

export interface Props {
  id?: string
}

const props = withDefaults(defineProps<Props>(), {
  id: '',
})

interface DetailFormModel {
  content: string
  deviceId: string
  id: string
  recurrenceInterval: number
  recurrenceType: ReminderRecurrence
  scheduledAtLocal: string
  zoneId: string
}

const formRef = useTemplateRef<FormExpose>('formRef')
const loading = ref(false)
const devices = ref<Device[]>([])
const model = ref<DetailFormModel>({
  id: props.id,
  deviceId: '',
  content: '',
  recurrenceType: 'NONE',
  recurrenceInterval: 1,
  scheduledAtLocal: defaultScheduledAt(),
  zoneId: currentTimeZone(),
})

const deviceOptions = computed(() => [
  { label: '请选择目标设备', value: '' },
  ...devices.value.map(device => ({
    label: `${device.displayName}（${device.online ? '在线' : '离线'}）`,
    value: device.id,
  })),
])

const recurrenceOptions = [
  { label: '仅一次', value: 'NONE' },
  { label: '每天', value: 'DAILY' },
  { label: '每周', value: 'WEEKLY' },
]

const validationSchema = toTypedSchema(z.object({
  deviceId: z.string().uuid('请选择目标设备'),
  content: z.string().trim().min(1, '请输入提醒内容').max(1000, '提醒内容不能超过 1000 个字符'),
  scheduledAtLocal: z.string().min(1, '请选择提醒时间'),
  recurrenceType: z.enum(['NONE', 'DAILY', 'WEEKLY']),
  recurrenceInterval: z.number().int().min(1, '重复间隔至少为 1').max(365, '重复间隔不能超过 365'),
  zoneId: z.string().trim().min(1, '无法确定当前时区'),
}))

function defaultScheduledAt() {
  const date = new Date(Date.now() + 30 * 60_000)
  date.setSeconds(0, 0)
  return toLocalDateTimeValue(date.toISOString())
}

async function loadDevices() {
  try {
    devices.value = await listDevices()
  }
  catch (error) {
    useFaToast().error('设备加载失败', { description: error instanceof Error ? error.message : '无法获取设备列表。' })
  }
}

async function loadReminder(id: string) {
  loading.value = true
  try {
    const reminder = await getReminder(id)
    model.value = {
      id: reminder.id,
      deviceId: reminder.deviceId,
      content: reminder.content,
      recurrenceType: reminder.recurrenceType,
      recurrenceInterval: reminder.recurrenceInterval,
      scheduledAtLocal: toLocalDateTimeValue(reminder.scheduledAt),
      zoneId: currentTimeZone(),
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取提醒。' })
  }
  finally {
    loading.value = false
  }
}

async function resetForRoute(id: string) {
  model.value.id = id
  if (id) {
    await loadReminder(id)
  }
  else {
    model.value = {
      id: '',
      deviceId: devices.value.length === 1 ? devices.value[0].id : '',
      content: '',
      recurrenceType: 'NONE',
      recurrenceInterval: 1,
      scheduledAtLocal: defaultScheduledAt(),
      zoneId: currentTimeZone(),
    }
  }
}

async function submit(): Promise<boolean> {
  const result = await formRef.value?.validate()
  if (!result?.valid) {
    return false
  }

  loading.value = true
  try {
    const input = {
      deviceId: model.value.deviceId,
      content: model.value.content.trim(),
      scheduledAt: toReminderInstant(model.value.scheduledAtLocal),
      recurrenceType: model.value.recurrenceType,
      recurrenceInterval: model.value.recurrenceInterval,
      zoneId: model.value.zoneId,
    }
    if (model.value.id) {
      await updateReminder(model.value.id, input)
      useFaToast().success('编辑成功')
    }
    else {
      await createReminder(input)
      useFaToast().success('新增成功')
    }
    return true
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存提醒。' })
    return false
  }
  finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadDevices()
  await resetForRoute(props.id)
})

watch(() => props.id, id => resetForRoute(id))

defineExpose({ submit })
</script>

<template>
  <div v-loading="loading">
    <FaForm
      ref="formRef"
      :model="model"
      :validation-schema="validationSchema"
      label-placement="right"
      :label-width="120"
      class="grid gap-6"
      scroll-to-error
    >
      <FaFormItem name="deviceId" label="目标设备" required>
        <FaSelect :options="deviceOptions" placeholder="请选择要播报提醒的机器人" class="w-full" />
      </FaFormItem>
      <FaFormItem name="content" label="提醒内容" required>
        <FaTextarea rows="5" align="block" placeholder="例如：去拿外卖" class="w-full" />
      </FaFormItem>
      <FaFormItem name="scheduledAtLocal" label="提醒时间" required description="按浏览器当前时区创建提醒。">
        <FaInput type="datetime-local" class="w-full" />
      </FaFormItem>
      <FaFormItem name="recurrenceType" label="重复方式" required description="周期提醒按本地时钟和所选时区推进。">
        <FaSelect v-model="model.recurrenceType" :options="recurrenceOptions" class="w-full" />
      </FaFormItem>
      <FaFormItem
        v-if="model.recurrenceType !== 'NONE'"
        name="recurrenceInterval"
        label="重复间隔"
        required
        :description="model.recurrenceType === 'DAILY' ? '例如 2 表示每 2 天一次。' : '例如 2 表示每 2 周一次。'"
      >
        <FaNumberField v-model="model.recurrenceInterval" :min="1" :max="365" class="w-full" />
      </FaFormItem>
      <FaAlert
        v-if="model.recurrenceType !== 'NONE'"
        title="夏令时处理"
        description="不存在的本地时间会顺延到有效时间；重复出现的本地时间采用时区默认的较早偏移。"
      />
      <FaFormItem name="zoneId" label="时区" required description="用于记录创建提醒时的用户时区。">
        <FaInput readonly class="w-full" />
      </FaFormItem>
    </FaForm>
  </div>
</template>
