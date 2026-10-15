<script setup lang="ts">
import type { FormExpose } from '@fantastic-admin/components'
import type { Delegation } from '@/api/modules/delegations'
import { toTypedSchema } from '@vee-validate/zod'
import { createDelegation, DelegationOperationPendingError, DelegationSubmissionError, getDelegation, newDelegationOperationKey, updateDelegation } from '@/api/modules/delegations'
import { listDevices } from '@/api/modules/devices'
import { getDeviceActiveRole, listRoles } from '@/api/modules/roles'
import { aiDailyExample, delegationFormSchema, delegationInput, emptyDelegationModel, formatDelegationTime, modelFromDelegation, scheduleLabel, taskExamples } from '../../helpers'

const props = withDefaults(defineProps<{ id?: string, example?: boolean }>(), { id: '', example: false })
const emit = defineEmits<{ saved: [task: Delegation] }>()
const formRef = useTemplateRef<FormExpose>('formRef')
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const clarification = ref('')
const saveError = ref('')
const acceptedTask = ref<Delegation | null>(null)
const deviceOptions = ref<{ label: string, value: string }[]>([])
const roleOptions = ref<{ label: string, value: string }[]>([])
const model = ref(emptyDelegationModel())
const validationSchema = toTypedSchema(delegationFormSchema)
const deliveryOptions = [{ label: '按任务描述理解', value: 'AUTO' }, { label: '控制台', value: 'CONSOLE' }, { label: 'Bark 手机推送', value: 'BARK' }]
const deliveryChanged = ref(false)
const uncertain = ref(false)
let creationKey = ''
let requestId = 0
let roleRequestId = 0

function applyExample(description: string) {
  model.value.description = description
  clarification.value = ''
  saveError.value = ''
  creationKey = ''
  uncertain.value = false
}

async function selectActiveRole(deviceId: string) {
  const request = ++roleRequestId
  if (!deviceId || props.id) {
    return
  }
  const selectedRole = model.value.roleId
  try {
    const role = await getDeviceActiveRole(deviceId)
    if (request === roleRequestId && deviceId === model.value.deviceId && model.value.roleId === selectedRole && roleOptions.value.some(option => option.value === role.id)) {
      model.value.roleId = role.id
    }
  }
  catch {
    // A single available role remains selected; otherwise the user can choose one.
  }
}

async function load() {
  const request = ++requestId
  ++roleRequestId
  loading.value = true
  loadError.value = ''
  clarification.value = ''
  saveError.value = ''
  creationKey = ''
  uncertain.value = false
  try {
    const [devices, roles, task] = await Promise.all([
      listDevices(),
      listRoles(),
      props.id ? getDelegation(props.id) : Promise.resolve(null),
    ])
    if (request !== requestId) {
      return
    }
    deviceOptions.value = devices.map(device => ({ label: `${device.displayName}（${device.online ? '在线' : '离线'}）`, value: device.id }))
    roleOptions.value = roles.filter(role => !role.archivedAt || role.id === task?.roleId).map(role => ({ label: `${role.name}${role.archivedAt ? '（已归档）' : ''}`, value: role.id }))
    model.value = task
      ? modelFromDelegation(task)
      : { ...(props.example ? aiDailyExample() : emptyDelegationModel()), deviceId: devices.length === 1 ? devices[0].id : '', roleId: roleOptions.value.length === 1 ? roleOptions.value[0].value : '' }
    acceptedTask.value = task
    deliveryChanged.value = false
    if (!task && model.value.deviceId) {
      await selectActiveRole(model.value.deviceId)
    }
  }
  catch (error) {
    if (request === requestId) {
      loadError.value = error instanceof Error ? error.message : '无法读取任务或伙伴。'
    }
  }
  finally {
    if (request === requestId) {
      loading.value = false
    }
  }
}

async function submit(): Promise<boolean> {
  if (loading.value || saving.value || loadError.value || acceptedTask.value?.cancelledAt || !(await formRef.value?.validate())?.valid) {
    return false
  }
  saving.value = true
  clarification.value = ''
  saveError.value = ''
  try {
    const input = delegationInput(model.value)
    if (deliveryChanged.value && model.value.deliveryMode !== 'AUTO') {
      input.deliveryMode = model.value.deliveryMode
    }
    if (!props.id && !creationKey) {
      creationKey = newDelegationOperationKey()
    }
    const task = props.id ? await updateDelegation(props.id, input) : await createDelegation(input, creationKey)
    uncertain.value = false
    creationKey = ''
    acceptedTask.value = task
    model.value = modelFromDelegation(task)
    deliveryChanged.value = false
    emit('saved', task)
    useFaToast().success(task.enabled ? '伙伴已接下任务' : '任务已保存', { description: task.understanding })
    return true
  }
  catch (error) {
    uncertain.value = error instanceof DelegationOperationPendingError
    if (!uncertain.value) {
      creationKey = ''
    }
    if (error instanceof DelegationSubmissionError && error.code === 'DELEGATION_CLARIFICATION_REQUIRED') {
      clarification.value = error.clarification || error.message
    }
    else {
      saveError.value = error instanceof DelegationSubmissionError && error.code === 'DELEGATION_PUSH_NOT_CONFIGURED'
        ? '请先在持续委托列表展开「手机通知设置」，保存设备 Key 并启用 Bark，再回来交给伙伴。也可以将描述改为在控制台阅读。'
        : error instanceof Error ? error.message : '任务未能保存，请稍后重试。'
    }
    return false
  }
  finally {
    saving.value = false
  }
}

watch(() => [props.id, props.example], load, { immediate: true })
watch(() => model.value.deviceId, (deviceId, oldDeviceId) => {
  if (!loading.value && deviceId !== oldDeviceId) {
    selectActiveRole(deviceId)
  }
})
onBeforeUnmount(() => {
  ++requestId
  ++roleRequestId
})
defineExpose({ submit })
</script>

<template>
  <div v-loading="loading || saving" class="space-y-6">
    <FaAlert v-if="loadError" variant="destructive" title="任务未加载" :description="loadError">
      <template #action>
        <FaButton variant="outline" @click="load">
          重试
        </FaButton>
      </template>
    </FaAlert>
    <FaForm ref="formRef" :model="model" :validation-schema="validationSchema" :disabled="loading || saving || uncertain || Boolean(loadError) || Boolean(acceptedTask?.cancelledAt)" label-placement="top" class="gap-6 grid" keep-values-on-unmount scroll-to-error>
      <div v-if="deviceOptions.length > 1 || roleOptions.length > 1" class="gap-4 grid md:grid-cols-2">
        <FaFormItem name="deviceId" label="设备" required>
          <FaSelect :options="deviceOptions" :disabled="Boolean(props.id)" placeholder="选择设备" />
        </FaFormItem>
        <FaFormItem name="roleId" label="伙伴" required>
          <FaSelect :options="roleOptions" :disabled="Boolean(props.id)" placeholder="选择伙伴" />
        </FaFormItem>
      </div>
      <FaAlert v-if="!loading && !deviceOptions.length" title="还没有设备" description="请先配对设备，再交给它一个任务。" />
      <FaAlert v-if="!loading && !roleOptions.length" title="还没有可用伙伴" description="请先创建伙伴或恢复一个已归档伙伴。" />
      <FaFormItem name="description" label="想让伙伴帮你做什么？" required description="可以说「现在做一次」，或交代重复时间。比较公开资料时贴上来源；缺关键信息时，伙伴只追问一项，补齐前任务不会保存。">
        <FaTextarea rows="7" align="block" placeholder="例如：现在比较这两个公开 HTTPS 页面里的方案，优先容易整理，缺少的资料请明确说明。把结果保存在控制台。" />
      </FaFormItem>
      <div v-if="!props.id" class="flex flex-wrap gap-2">
        <FaButton v-for="taskExample in taskExamples" :key="taskExample.label" variant="outline" size="sm" type="button" @click="applyExample(taskExample.description)">
          {{ taskExample.label }}
        </FaButton>
      </div>
      <FaAlert v-if="clarification" title="伙伴想确认一件事" :description="clarification" role="status">
        <template #action>
          <span class="text-sm">将回答补充在上面的任务描述中，再交给伙伴。</span>
        </template>
      </FaAlert>
      <FaAlert v-if="saveError" variant="destructive" :title="uncertain ? '正在核对任务保存状态' : '任务未保存'" :description="saveError" role="alert">
        <template v-if="uncertain" #action>
          <FaButton type="button" :disabled="saving" @click="submit">
            核对保存状态
          </FaButton>
        </template>
      </FaAlert>
      <FaAlert v-if="acceptedTask?.cancelledAt" title="任务已取消" :description="acceptedTask.cancellationReason || '不再执行，已有成果仍可查看。需要继续时请明确新建任务。'" />
      <FaCard v-if="acceptedTask" :title="model.description.trim() === (acceptedTask.description || acceptedTask.instruction) ? '伙伴的安排' : '当前已保存的安排'">
        <p class="text-base leading-7 whitespace-pre-wrap break-words">
          {{ acceptedTask.understanding || acceptedTask.description || acceptedTask.instruction }}
        </p>
        <div class="text-sm text-muted-foreground mt-3 flex flex-wrap gap-x-6 gap-y-2">
          <span>{{ scheduleLabel(acceptedTask) }}</span>
          <span>{{ acceptedTask.deliveryMode === 'BARK' ? 'Bark 手机推送' : '控制台阅读' }}</span>
          <span>{{ acceptedTask.enabled ? `下次执行：${formatDelegationTime(acceptedTask.nextRunAt, acceptedTask.zoneId)}` : '已暂停' }}</span>
        </div>
      </FaCard>
      <FaCollapsible>
        <template #trigger="{ open }">
          <span class="text-sm px-3 py-2 border rounded-md inline-block">{{ open ? '收起' : '展开' }}任务设置</span>
        </template>
        <div class="pt-5 gap-5 grid sm:grid-cols-2">
          <FaFormItem name="zoneId" label="默认时区" description="描述未指定时区时使用。时间和重复方式可以直接在任务描述中修改。">
            <FaInput placeholder="Asia/Shanghai" />
          </FaFormItem>
          <FaFormItem name="deliveryMode" label="交付位置" description="默认按描述理解；在这里选择可以覆盖描述中的交付位置。">
            <FaSelect :options="deliveryOptions" @change="deliveryChanged = true" />
          </FaFormItem>
          <FaFormItem name="enabled" label="启用任务" description="关闭后保存为暂停状态，不定时执行。">
            <FaSwitch />
          </FaFormItem>
        </div>
      </FaCollapsible>
    </FaForm>
  </div>
</template>
