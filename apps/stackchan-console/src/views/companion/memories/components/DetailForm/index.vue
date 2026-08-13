<script setup lang="ts">
import type { FormExpose } from '@fantastic-admin/components'
import type { Device } from '@/api/modules/devices'
import type { MemoryInput } from '@/api/modules/personaMemory'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { listDevices } from '@/api/modules/devices'
import { createMemory, getMemory, updateMemory } from '@/api/modules/personaMemory'
import { listRoles } from '@/api/modules/roles'

export interface Props {
  id?: string
}

const props = withDefaults(defineProps<Props>(), { id: '' })
const formRef = useTemplateRef<FormExpose>('formRef')
const loading = ref(false)
const devices = ref<Device[]>([])
const roleOptions = ref<{ label: string, value: string }[]>([])
const metadata = ref<{ confirmationStatus: string, sourceDetail: string } | null>(null)
const model = ref<MemoryInput>({
  scopeType: 'GLOBAL',
  deviceId: null,
  category: 'USER_PROFILE',
  title: '',
  content: '',
  topicKey: '',
  importance: 3,
  allowProactiveMention: false,
  roleId: '',
})

const scopeOptions = [
  { label: '全局：文本聊天和所有设备共享', value: 'GLOBAL' },
  { label: '设备：仅指定机器人使用', value: 'DEVICE' },
]
const categoryOptions = [
  { label: '用户档案', value: 'USER_PROFILE' },
  { label: '事件记忆', value: 'EVENT' },
]
const deviceOptions = computed(() => [
  { label: '请选择目标设备', value: '' },
  ...devices.value.map(device => ({ label: device.displayName, value: device.id })),
])

const validationSchema = toTypedSchema(z.object({
  scopeType: z.enum(['GLOBAL', 'DEVICE']),
  deviceId: z.string().nullable(),
  category: z.enum(['USER_PROFILE', 'EVENT']),
  title: z.string().trim().min(1, '请输入记忆标题').max(120, '标题不能超过 120 个字符'),
  content: z.string().trim().min(1, '请输入记忆内容').max(2000, '内容不能超过 2000 个字符'),
  topicKey: z.string().trim().max(120, '主题键不能超过 120 个字符'),
  importance: z.number().int().min(1).max(5),
  allowProactiveMention: z.boolean(),
}).superRefine((value, context) => {
  if (value.scopeType === 'DEVICE' && !value.deviceId) {
    context.addIssue({ code: 'custom', message: '请选择目标设备', path: ['deviceId'] })
  }
}))

watch(() => model.value.scopeType, (scopeType) => {
  if (scopeType === 'GLOBAL') {
    model.value.deviceId = null
  }
})

async function loadDevices() {
  try {
    devices.value = await listDevices()
  }
  catch (error) {
    useFaToast().error('设备加载失败', { description: error instanceof Error ? error.message : '无法获取设备列表。' })
  }
}

async function loadMemory(id: string) {
  loading.value = true
  try {
    const memory = await getMemory(id)
    model.value = {
      scopeType: memory.scopeType,
      deviceId: memory.deviceId,
      category: memory.category,
      title: memory.title,
      content: memory.content,
      topicKey: memory.topicKey,
      importance: memory.importance,
      allowProactiveMention: memory.allowProactiveMention,
      roleId: memory.roleId,
    }
    metadata.value = {
      confirmationStatus: memory.confirmationStatus,
      sourceDetail: memory.sourceDetail,
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取记忆。' })
  }
  finally {
    loading.value = false
  }
}

async function resetForRoute(id: string) {
  metadata.value = null
  if (id) {
    await loadMemory(id)
    return
  }
  model.value = {
    scopeType: 'GLOBAL',
    deviceId: null,
    category: 'USER_PROFILE',
    title: '',
    content: '',
    topicKey: '',
    importance: 3,
    allowProactiveMention: false,
    roleId: roleOptions.value[0]?.value ?? '',
  }
}

async function submit(): Promise<boolean> {
  const result = await formRef.value?.validate()
  if (!result?.valid) {
    return false
  }
  loading.value = true
  try {
    const input: MemoryInput = {
      ...model.value,
      deviceId: model.value.scopeType === 'DEVICE' ? model.value.deviceId : null,
      title: model.value.title.trim(),
      content: model.value.content.trim(),
      topicKey: model.value.topicKey.trim() || model.value.title.trim(),
    }
    if (props.id) {
      await updateMemory(props.id, input)
      useFaToast().success('记忆已更新')
    }
    else {
      await createMemory(input)
      useFaToast().success('记忆已添加', { description: '手工添加视为用户已明确确认，将从下一轮对话生效。' })
    }
    return true
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存记忆。' })
    return false
  }
  finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadDevices()
  roleOptions.value = (await listRoles()).filter(role => !role.archivedAt).map(role => ({ label: role.name, value: role.id }))
  await resetForRoute(props.id)
})

watch(() => props.id, id => resetForRoute(id))

defineExpose({ submit })
</script>

<template>
  <div v-loading="loading" class="space-y-4">
    <FaAlert
      title="不要保存密码、令牌或完整身份凭据"
      description="长期记忆会进入后续对话上下文。请只保存确实需要跨会话使用、并且用户明确同意保留的信息。"
    />
    <FaAlert
      v-if="metadata"
      :title="metadata.confirmationStatus === 'PENDING' ? '这是一条待确认建议' : '记忆来源说明'"
      :description="metadata.sourceDetail"
    />
    <FaForm
      ref="formRef"
      :model="model"
      :validation-schema="validationSchema"
      label-placement="right"
      :label-width="120"
      class="gap-6 grid"
      scroll-to-error
    >
      <FaFormItem name="scopeType" label="作用范围" required>
        <FaSelect v-model="model.scopeType" :options="scopeOptions" class="w-full" />
      </FaFormItem>
      <FaFormItem name="roleId" label="归属角色" required :description="props.id ? '记忆创建后不可改绑角色。' : undefined">
        <FaSelect v-model="model.roleId" :options="roleOptions" :disabled="Boolean(props.id)" class="w-full" />
      </FaFormItem>
      <FaFormItem v-if="model.scopeType === 'DEVICE'" name="deviceId" label="目标设备" required>
        <FaSelect v-model="model.deviceId" :options="deviceOptions" class="w-full" />
      </FaFormItem>
      <FaFormItem name="category" label="记忆类型" required>
        <FaSelect v-model="model.category" :options="categoryOptions" class="w-full" />
      </FaFormItem>
      <FaFormItem name="title" label="记忆标题" required>
        <FaInput v-model="model.title" placeholder="例如：称呼偏好" />
      </FaFormItem>
      <FaFormItem name="content" label="记忆内容" required description="使用明确、可核对的事实描述，不要写成给模型的命令。">
        <FaTextarea v-model="model.content" rows="7" align="block" placeholder="例如：用户喜欢被称为阿俊。" />
      </FaFormItem>
      <FaFormItem name="topicKey" label="主题键" description="留空时使用标题；同一主题使用相同键，确认冲突建议时才会替代旧记忆。">
        <FaInput v-model="model.topicKey" placeholder="例如：称呼偏好" />
      </FaFormItem>
      <FaFormItem name="importance" label="重要度" required description="1 最低，5 最高；只影响有界检索排序。">
        <FaNumberField v-model="model.importance" :min="1" :max="5" class="w-full" />
      </FaFormItem>
      <FaFormItem name="allowProactiveMention" label="允许主动提及" description="默认关闭；仅供后续受限主动关心使用，不会让模型自行决定打扰时间。">
        <FaSwitch v-model="model.allowProactiveMention" />
      </FaFormItem>
    </FaForm>
  </div>
</template>
