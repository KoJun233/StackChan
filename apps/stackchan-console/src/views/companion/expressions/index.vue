<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import type { Device } from '@/api/modules/devices'
import { listDevices } from '@/api/modules/devices'
import type { DeviceExpressionPack, ExpressionPack, ExpressionState } from '@/api/modules/expressionPacks'
import {
  activateExpressionPack,
  createExpressionPack,
  deactivateExpressionPack,
  deleteExpressionPack,
  expressionPreviewUrl,
  expressionStates,
  getDeviceExpressionPack,
  listExpressionPacks,
} from '@/api/modules/expressionPacks'

defineOptions({ name: 'CompanionExpressionPacks' })

interface StagedFile {
  file?: File
  name: string
  progress?: number
  size: number
  status?: 'uploading' | 'success' | 'error'
  url?: string
}

type ImageFields = Record<ExpressionState, StagedFile[]>

interface ExpressionPackFormModel extends ImageFields {
  description: string
  deviceId: string
  name: string
}

const loading = ref(false)
const actionPackId = ref('')
const devices = ref<Device[]>([])
const packs = ref<ExpressionPack[]>([])
const selection = ref<DeviceExpressionPack | null>(null)
const model = ref<ExpressionPackFormModel>(defaults())
let selectionRefreshTimer: ReturnType<typeof setTimeout> | undefined

const validationSchema = toTypedSchema(z.object({
  name: z.string().trim().min(1, '请输入资源包名称').max(80, '名称最多 80 个字符'),
  description: z.string().trim().max(240, '说明最多 240 个字符'),
  deviceId: z.string().uuid('请选择目标机器人'),
  idle: z.array(z.custom<StagedFile>()).length(1, '请上传待机状态图'),
  listening: z.array(z.custom<StagedFile>()).length(1, '请上传聆听状态图'),
  processing: z.array(z.custom<StagedFile>()).length(1, '请上传处理中状态图'),
  speaking: z.array(z.custom<StagedFile>()).length(1, '请上传播报状态图'),
  success: z.array(z.custom<StagedFile>()).length(1, '请上传成功状态图'),
  no_speech: z.array(z.custom<StagedFile>()).length(1, '请上传没听清状态图'),
  offline: z.array(z.custom<StagedFile>()).length(1, '请上传离线状态图'),
  error: z.array(z.custom<StagedFile>()).length(1, '请上传异常状态图'),
}))

const deviceOptions = computed(() => devices.value.map(device => ({
  label: `${device.displayName} · ${device.online ? '在线' : '离线'}`,
  value: device.id,
})))

function defaults(): ExpressionPackFormModel {
  return {
    name: '',
    description: '',
    deviceId: '',
    idle: [],
    listening: [],
    processing: [],
    speaking: [],
    success: [],
    no_speech: [],
    offline: [],
    error: [],
  }
}

async function validateImage(file: File) {
  if (file.type !== 'image/png' || file.size > 384 * 1024) {
    throw new Error('请选择不超过 384 KiB 的 PNG 图片。')
  }
  const bitmap = await createImageBitmap(file)
  const valid = bitmap.width === 320 && bitmap.height === 240
  bitmap.close()
  if (!valid) {
    throw new Error('图片尺寸必须为 320×240。')
  }
  return true
}

async function stageImage({ file, onProgress }: { file: File, onProgress: (percent: number) => void }) {
  await validateImage(file)
  onProgress(100)
  return { url: URL.createObjectURL(file) }
}

function stagedFileField(componentField: Record<string, any>) {
  return {
    ...componentField,
    modelValue: (componentField.modelValue as StagedFile[] | undefined) ?? [],
  }
}

function revokeModelUrls(value: ExpressionPackFormModel) {
  expressionStates.forEach(({ value: state }) => {
    value[state].forEach((item) => {
      if (item.url?.startsWith('blob:')) {
        URL.revokeObjectURL(item.url)
      }
    })
  })
}

async function load() {
  loading.value = true
  try {
    const [deviceList, packList] = await Promise.all([listDevices(), listExpressionPacks()])
    devices.value = deviceList
    packs.value = packList
    if (!model.value.deviceId) {
      model.value.deviceId = deviceList[0]?.id ?? ''
    }
    await loadSelection()
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取表情资源包。' })
  }
  finally {
    loading.value = false
  }
}

async function loadSelection() {
  selection.value = model.value.deviceId
    ? await getDeviceExpressionPack(model.value.deviceId)
    : null
  scheduleSelectionRefresh()
}

function scheduleSelectionRefresh() {
  if (selectionRefreshTimer) {
    clearTimeout(selectionRefreshTimer)
    selectionRefreshTimer = undefined
  }
  if (selection.value?.status !== 'READY' && selection.value?.status !== 'INSTALLING') {
    return
  }
  selectionRefreshTimer = setTimeout(async () => {
    try {
      await loadSelection()
    }
    catch {
      scheduleSelectionRefresh()
    }
  }, 3000)
}

async function submit(values: ExpressionPackFormModel) {
  const images = {} as Record<ExpressionState, File>
  for (const { value: state } of expressionStates) {
    const file = values[state][0]?.file
    if (!file) {
      useFaToast().error('生成失败', { description: `${expressionStates.find(item => item.value === state)?.label}状态图不可用。` })
      return
    }
    images[state] = file
  }
  loading.value = true
  try {
    await createExpressionPack({ name: values.name, description: values.description, images })
    revokeModelUrls(model.value)
    const deviceId = values.deviceId
    model.value = { ...defaults(), deviceId }
    await load()
    useFaToast().success('资源包已生成', { description: '八个状态均已完成尺寸、格式和摘要校验。' })
  }
  catch (error) {
    useFaToast().error('生成失败', { description: error instanceof Error ? error.message : '无法生成资源包。' })
  }
  finally {
    loading.value = false
  }
}

async function activate(pack: ExpressionPack) {
  if (!model.value.deviceId) {
    return
  }
  actionPackId.value = pack.id
  try {
    selection.value = await activateExpressionPack(pack.id, model.value.deviceId)
    scheduleSelectionRefresh()
    useFaToast().success('已进入安装队列', { description: '在线设备校验完成后自动切换；失败时继续使用默认表情。' })
  }
  catch (error) {
    useFaToast().error('启用失败', { description: error instanceof Error ? error.message : '无法启用资源包。' })
  }
  finally {
    actionPackId.value = ''
  }
}

async function deactivate() {
  if (!model.value.deviceId) {
    return
  }
  actionPackId.value = 'deactivate'
  try {
    selection.value = await deactivateExpressionPack(model.value.deviceId)
    useFaToast().success('已恢复默认表情')
  }
  catch (error) {
    useFaToast().error('停用失败', { description: error instanceof Error ? error.message : '无法停用资源包。' })
  }
  finally {
    actionPackId.value = ''
  }
}

async function remove(pack: ExpressionPack) {
  actionPackId.value = pack.id
  try {
    await deleteExpressionPack(pack.id)
    packs.value = packs.value.filter(item => item.id !== pack.id)
    useFaToast().success('资源包已删除', { description: '预览图和设备下载制品已一并删除。' })
  }
  catch (error) {
    useFaToast().error('删除失败', { description: error instanceof Error ? error.message : '请先从机器人停用该资源包。' })
  }
  finally {
    actionPackId.value = ''
  }
}

watch(() => model.value.deviceId, async (deviceId, previous) => {
  if (deviceId && deviceId !== previous) {
    await loadSelection()
  }
})

onMounted(load)
onUnmounted(() => {
  if (selectionRefreshTimer) {
    clearTimeout(selectionRefreshTimer)
  }
  revokeModelUrls(model.value)
})
</script>

<template>
  <div>
    <FaPageHeader title="宠物表情资源包" description="生成、预览并安全切换机器人八状态表情；资源异常时自动回退默认机械眼。" />
    <FaPageMain>
      <FaLoading :loading="loading">
        <FaAlert
          title="图片与隐私边界"
          description="仅上传你有权使用的图片。每张必须为 320×240 PNG；图片不会写入运行日志，删除资源包会同时删除预览图和下载制品。"
          class="mb-6"
        />
        <FaForm
          id="expression-pack-form"
          :model="model"
          :validation-schema="validationSchema"
          class="grid grid-cols-1 gap-6 md:grid-cols-2"
          @submit="submit"
        >
          <FaCard title="资源包信息" class="md:col-span-2">
            <div class="grid grid-cols-1 gap-x-8 gap-y-6 md:grid-cols-2">
              <FaFormItem name="name" label="资源包名称" required>
                <FaInput placeholder="例如：橘猫伙伴" class="w-full" />
              </FaFormItem>
              <FaFormItem name="deviceId" label="目标机器人" required>
                <FaSelect :options="deviceOptions" placeholder="请选择机器人" class="w-full" />
              </FaFormItem>
              <FaFormItem name="description" label="说明" class="md:col-span-2">
                <FaTextarea rows="2" placeholder="记录角色、画风和版本，最多 240 字。" class="w-full" />
              </FaFormItem>
            </div>
          </FaCard>

          <FaCard
            v-for="state in expressionStates"
            :key="state.value"
            :title="`${state.label}状态`"
          >
            <FaFormItem :name="state.value" :label="`${state.label} PNG`" required>
              <template #default="{ componentField }">
                <FaFileUpload
                  v-bind="stagedFileField(componentField)"
                  :max="1"
                  :http-request="stageImage"
                  :after-upload="response => response.url"
                  description="拖放或点击选择 320×240 PNG"
                />
              </template>
            </FaFormItem>
          </FaCard>

          <FaFixedBar position="bottom" class="md:col-span-2 flex justify-center">
            <FaButton type="submit" form="expression-pack-form" :loading="loading">
              生成并校验资源包
            </FaButton>
          </FaFixedBar>
        </FaForm>

        <FaDivider class="my-8" />

        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 class="text-lg font-semibold">已生成资源包</h2>
            <p class="text-sm text-muted-foreground">
              当前设备状态：{{ selection?.status ?? 'DISABLED' }}
            </p>
          </div>
          <FaButton
            variant="outline"
            :disabled="!selection?.enabled"
            :loading="actionPackId === 'deactivate'"
            @click="deactivate"
          >
            恢复默认表情
          </FaButton>
        </div>

        <FaEmpty v-if="packs.length === 0" description="还没有资源包，请先上传八个状态图。" />
        <div v-else class="grid grid-cols-1 gap-6 xl:grid-cols-2">
          <FaCard v-for="pack in packs" :key="pack.id" :title="pack.name" :description="pack.description ?? undefined">
            <div class="grid grid-cols-4 gap-2">
              <figure v-for="state in expressionStates" :key="state.value" class="overflow-hidden rounded-lg border bg-black">
                <img
                  :src="expressionPreviewUrl(pack.id, state.value)"
                  :alt="`${pack.name} ${state.label}`"
                  class="aspect-4/3 w-full object-cover"
                  loading="lazy"
                >
                <figcaption class="bg-card px-1 py-1 text-center text-xs">{{ state.label }}</figcaption>
              </figure>
            </div>
            <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
              <span class="text-xs text-muted-foreground">
                v{{ pack.formatVersion }} · {{ Math.ceil(pack.artifactSize / 1024) }} KiB
              </span>
              <div class="flex gap-2">
                <FaButton
                  variant="outline"
                  :loading="actionPackId === pack.id"
                  :disabled="selection?.packId === pack.id && selection.enabled"
                  @click="activate(pack)"
                >
                  {{ selection?.packId === pack.id && selection.enabled ? '已启用' : '启用' }}
                </FaButton>
                <FaButton
                  variant="destructive"
                  :loading="actionPackId === pack.id"
                  :disabled="selection?.packId === pack.id && selection.enabled"
                  @click="remove(pack)"
                >
                  删除
                </FaButton>
              </div>
            </div>
          </FaCard>
        </div>
      </FaLoading>
    </FaPageMain>
  </div>
</template>
