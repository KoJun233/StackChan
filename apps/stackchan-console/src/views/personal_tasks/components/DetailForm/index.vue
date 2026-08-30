<script setup lang="ts">
import type { FormExpose } from '@fantastic-admin/components'
import type { Device } from '@/api/modules/devices'
import type { PersonalTaskPriority } from '@/api/modules/personalTasks'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { listDevices } from '@/api/modules/devices'
import { createPersonalTask, getPersonalTask, updatePersonalTask } from '@/api/modules/personalTasks'
import { currentTimeZone, toLocalDateTimeValue, toReminderInstant } from '@/api/modules/reminders'
import { listRoles } from '@/api/modules/roles'
import { isCompanionRoleId } from '@/utils/roleId'

export interface Props { id?: string }
const props = withDefaults(defineProps<Props>(), { id: '' })

interface DetailFormModel {
  deviceId: string
  dueAtLocal: string
  dueEnabled: boolean
  id: string
  notes: string
  priority: PersonalTaskPriority
  roleId: string
  title: string
  zoneId: string
}

const formRef = useTemplateRef<FormExpose>('formRef')
const loading = ref(false)
const devices = ref<Device[]>([])
const roleOptions = ref<{ label: string, value: string }[]>([])
const model = ref<DetailFormModel>(emptyModel(props.id))

const deviceOptions = computed(() => [
  { label: '请选择目标设备', value: '' },
  ...devices.value.map(device => ({ label: `${device.displayName}（${device.online ? '在线' : '离线'}）`, value: device.id })),
])
const priorityOptions = [
  { label: '高', value: 'HIGH' },
  { label: '普通', value: 'NORMAL' },
  { label: '低', value: 'LOW' },
]

const validationSchema = toTypedSchema(z.object({
  deviceId: z.string().uuid('请选择目标设备'),
  roleId: z.string().refine(isCompanionRoleId, '请选择角色'),
  title: z.string().trim().min(1, '请输入待办标题').max(200, '标题不能超过 200 个字符'),
  notes: z.string().max(2000, '备注不能超过 2000 个字符'),
  priority: z.enum(['HIGH', 'NORMAL', 'LOW']),
  dueEnabled: z.boolean(),
  dueAtLocal: z.string(),
  zoneId: z.string().trim().min(1, '无法确定当前时区'),
}).superRefine((value, context) => {
  if (value.dueEnabled && !value.dueAtLocal) {
    context.addIssue({ code: 'custom', path: ['dueAtLocal'], message: '请选择截止时间' })
  }
}))

function defaultDueAt() {
  const date = new Date(Date.now() + 24 * 60 * 60_000)
  date.setSeconds(0, 0)
  return toLocalDateTimeValue(date.toISOString())
}

function emptyModel(id = ''): DetailFormModel {
  return { id, deviceId: '', roleId: '', title: '', notes: '', priority: 'NORMAL', dueEnabled: false, dueAtLocal: defaultDueAt(), zoneId: currentTimeZone() }
}

async function loadReferences() {
  const [deviceList, roles] = await Promise.all([listDevices(), listRoles()])
  devices.value = deviceList
  roleOptions.value = roles.filter(role => !role.archivedAt).map(role => ({ label: role.name, value: role.id }))
}

async function resetForRoute(id: string) {
  if (!id) {
    model.value = emptyModel()
    model.value.deviceId = devices.value.length === 1 ? devices.value[0].id : ''
    model.value.roleId = roleOptions.value[0]?.value ?? ''
    return
  }
  loading.value = true
  try {
    const task = await getPersonalTask(id)
    model.value = {
      id: task.id,
      deviceId: task.deviceId,
      roleId: task.roleId,
      title: task.title,
      notes: task.notes ?? '',
      priority: task.priority,
      dueEnabled: Boolean(task.dueAt),
      dueAtLocal: task.dueAt ? toLocalDateTimeValue(task.dueAt) : defaultDueAt(),
      zoneId: task.zoneId,
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取待办。' })
  }
  finally {
    loading.value = false
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
      roleId: model.value.roleId,
      title: model.value.title.trim(),
      notes: model.value.notes.trim() || null,
      priority: model.value.priority,
      dueAt: model.value.dueEnabled ? toReminderInstant(model.value.dueAtLocal) : null,
      zoneId: model.value.zoneId,
    }
    if (model.value.id) {
      await updatePersonalTask(model.value.id, input)
      useFaToast().success('编辑成功')
    }
    else {
      await createPersonalTask(input)
      useFaToast().success('新增成功')
    }
    return true
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存待办。' })
    return false
  }
  finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    await loadReferences()
    await resetForRoute(props.id)
  }
  catch (error) {
    useFaToast().error('基础数据加载失败', { description: error instanceof Error ? error.message : '无法获取设备或角色。' })
  }
})

watch(() => props.id, id => resetForRoute(id))
defineExpose({ submit })
</script>

<template>
  <div v-loading="loading">
    <FaForm ref="formRef" :model="model" :validation-schema="validationSchema" label-placement="right" :label-width="120" class="gap-6 grid" scroll-to-error>
      <FaFormItem name="deviceId" label="目标设备" required description="待办和截止提醒只对所选机器人生效。">
        <FaSelect :options="deviceOptions" :disabled="Boolean(model.id)" class="w-full" />
      </FaFormItem>
      <FaFormItem name="roleId" label="归属角色" required description="创建后不可改绑角色。">
        <FaSelect :options="roleOptions" :disabled="Boolean(model.id)" class="w-full" />
      </FaFormItem>
      <FaFormItem name="title" label="待办标题" required>
        <FaInput placeholder="例如：整理下周会议材料" class="w-full" />
      </FaFormItem>
      <FaFormItem name="notes" label="备注">
        <FaTextarea rows="5" align="block" placeholder="可选，不会发送给 Agent" class="w-full" />
      </FaFormItem>
      <FaFormItem name="priority" label="优先级" required>
        <FaSelect :options="priorityOptions" class="w-full" />
      </FaFormItem>
      <FaFormItem name="dueEnabled" label="截止提醒" description="启用后会复用机器人可靠提醒；完成待办会取消尚未播报的提醒。">
        <FaSwitch />
      </FaFormItem>
      <FaFormItem v-if="model.dueEnabled" name="dueAtLocal" label="截止时间" required>
        <FaInput type="datetime-local" class="w-full" />
      </FaFormItem>
      <FaFormItem name="zoneId" label="时区" required>
        <FaInput readonly class="w-full" />
      </FaFormItem>
    </FaForm>
  </div>
</template>
