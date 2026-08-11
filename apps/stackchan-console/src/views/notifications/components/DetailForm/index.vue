<script setup lang="ts">
import type { FormExpose } from '@fantastic-admin/components'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import type { Device } from '@/api/modules/devices'
import { listDevices } from '@/api/modules/devices'
import {
  createNotificationIntegration,
  getNotificationIntegration,
  updateNotificationIntegration,
} from '@/api/modules/notificationIntegrations'

const props = withDefaults(defineProps<{ id?: string }>(), { id: '' })
const formRef = useTemplateRef<FormExpose>('formRef')
const loading = ref(false)
const devices = ref<Device[]>([])
const model = ref({ id: props.id, name: '', deviceId: '', enabled: true })

const validationSchema = toTypedSchema(z.object({
  name: z.string().trim().min(1, '请输入集成名称').max(120, '名称不能超过 120 个字符'),
  deviceId: z.string().uuid('请选择目标设备'),
  enabled: z.boolean(),
}))

const deviceOptions = computed(() => [
  { label: '请选择固定目标设备', value: '' },
  ...devices.value.map(device => ({
    label: `${device.displayName}（${device.online ? '在线' : '离线'}）`,
    value: device.id,
  })),
])

async function resetForRoute(id: string) {
  loading.value = true
  try {
    if (id) {
      const integration = await getNotificationIntegration(id)
      model.value = {
        id: integration.id,
        name: integration.name,
        deviceId: integration.deviceId,
        enabled: integration.enabled,
      }
    }
    else {
      model.value = {
        id: '',
        name: '',
        deviceId: devices.value.length === 1 ? devices.value[0].id : '',
        enabled: true,
      }
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取通知集成。' })
  }
  finally {
    loading.value = false
  }
}

async function submit(): Promise<boolean> {
  const result = await formRef.value?.validate()
  if (!result?.valid) return false
  loading.value = true
  try {
    const input = { name: model.value.name.trim(), deviceId: model.value.deviceId, enabled: model.value.enabled }
    if (model.value.id) {
      await updateNotificationIntegration(model.value.id, input)
      useFaToast().success('集成已更新')
    }
    else {
      await createNotificationIntegration(input)
      useFaToast().success('集成已创建')
    }
    return true
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存通知集成。' })
    return false
  }
  finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    devices.value = await listDevices()
    await resetForRoute(props.id)
  }
  catch (error) {
    useFaToast().error('设备加载失败', { description: error instanceof Error ? error.message : '无法获取设备列表。' })
  }
})

watch(() => props.id, id => resetForRoute(id))

defineExpose({ submit })
</script>

<template>
  <div v-loading="loading">
    <FaForm ref="formRef" :model="model" :validation-schema="validationSchema" :label-width="120" class="grid gap-6" scroll-to-error>
      <FaFormItem name="name" label="集成名称" required description="例如 Codex、Claude Code 或 CI。">
        <FaInput placeholder="请输入调用方名称" class="w-full" />
      </FaFormItem>
      <FaFormItem name="deviceId" label="目标设备" required description="令牌签发后仍固定使用这里选择的设备。">
        <FaSelect :options="deviceOptions" class="w-full" />
      </FaFormItem>
      <FaFormItem name="enabled" label="启用状态" description="停用后所有现有令牌立即拒绝新请求，已入队通知继续保留。">
        <FaSwitch v-model="model.enabled" />
      </FaFormItem>
      <FaAlert title="最小权限" description="外部令牌只能创建通知和查询本集成通知状态，不能访问设备、聊天、提醒 CRUD 或管理员接口。" />
    </FaForm>
  </div>
</template>
