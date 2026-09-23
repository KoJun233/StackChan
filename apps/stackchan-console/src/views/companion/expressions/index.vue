<script setup lang="ts">
import type { Device } from '@/api/modules/devices'
import type { DeviceExpressionPack, ExpressionFrameRateMode, ExpressionPack, ExpressionPreviewCategory, ExpressionState, LifecycleClip } from '@/api/modules/expressionPacks'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { listDevices } from '@/api/modules/devices'
import { activateExpressionPack, createExpressionPack, createLifecycleExpressionPack, deactivateExpressionPack, deleteExpressionPack, expressionPreviewUrl, expressionStates, getDeviceExpressionPack, getExpressionFrameRate, lifecycleClips, lifecycleClipUrl, listExpressionPacks, previewExpression, updateExpressionFrameRate } from '@/api/modules/expressionPacks'
import { expressionFpsMaximum, expressionFpsMinimum, frameRateRangeToSlider, sliderToFrameRateRange } from './expressionFrameRateSlider'
import { expressionStateFromFilename } from './expressionPackStaging'
import { currentExpressionLabel } from './selectionLabel'

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
  name: string
}
interface UploadRequest {
  file: File
  onProgress: (percent: number) => void
}
const stateGuidance: Record<ExpressionState, string> = {
  error: '可恢复的小故障，建议使用橙色警示，避免恐怖图案或危险红屏。',
  idle: '长时间显示的中性形象，伙伴、构图和背景应作为其他状态的基准。',
  listening: '突出正在听你说话，建议使用青蓝提示或更专注的眼神。',
  no_speech: '表达温和疑惑或没听清，不要表现成严重故障。',
  offline: '低活跃、安静休眠的状态，避免与聆听状态使用相同配色。',
  processing: '表达思考过程，可使用偏移视线、点状进度或琥珀提示。',
  speaking: '明确表达正在播报，建议张嘴、声波或有节奏的视觉线索。',
  success: '用于确认完成，建议使用开心表情或绿色积极反馈。',
}
const loading = ref(false)
const actionPackId = ref('')
const activePage = ref<'create' | 'lifecycle' | 'manage'>('manage')
const activeState = ref<ExpressionState>('idle')
const bulkImportFiles = ref<StagedFile[]>([])
const bulkAssignments = new Map<ExpressionState, File>()
const previewObjectUrls = new Set<string>()
const devices = ref<Device[]>([])
const packs = ref<ExpressionPack[]>([])
const selectedDeviceId = ref('')
const selection = ref<DeviceExpressionPack | null>(null)
const model = ref<ExpressionPackFormModel>(defaults())
const lifecycleName = ref('')
const lifecycleDescription = ref('')
const lifecycleFiles = ref<Record<LifecycleClip, StagedFile[]>>({
  boot_appear: [],
  wake: [],
  role_switch: [],
})
const lifecycleFrameDelays = ref<Record<LifecycleClip, number[]>>({
  boot_appear: [33],
  wake: [33],
  role_switch: [33],
})
let selectionRefreshTimer: ReturnType<typeof setTimeout> | undefined
const validationSchema = toTypedSchema(z.object({
  name: z.string().trim().min(1, '请输入资源包名称').max(80, '名称最多 80 个字符'),
  description: z.string().trim().max(240, '说明最多 240 字'),
  idle: z.array(z.custom<StagedFile>()).length(1, '请上传待机状态图'),
  listening: z.array(z.custom<StagedFile>()).length(1, '请上传聆听状态图'),
  processing: z.array(z.custom<StagedFile>()).length(1, '请上传处理中状态图'),
  speaking: z.array(z.custom<StagedFile>()).length(1, '请上传播报状态图'),
  success: z.array(z.custom<StagedFile>()).length(1, '请上传成功状态图'),
  no_speech: z.array(z.custom<StagedFile>()).length(1, '请上传没听清状态图'),
  offline: z.array(z.custom<StagedFile>()).length(1, '请上传离线状态图'),
  error: z.array(z.custom<StagedFile>()).length(1, '请上传异常状态图'),
}))
const pageTabs = [
  { icon: 'i-ep:monitor', label: '当前形象', value: 'manage' },
  { icon: 'i-ep:picture', label: '制作与导入 PNG', value: 'create' },
  { icon: 'i-ep:video-play', label: '制作生命周期动画', value: 'lifecycle' },
]
const stateTabs = expressionStates.map(state => ({ label: state.label, value: state.value }))
interface DynamicPreview {
  category: ExpressionPreviewCategory
  label: string
  value: string
}
function previewItems(category: ExpressionPreviewCategory, items: [
  string,
  string,
][]): DynamicPreview[] {
  return items.map(([label, value]) => ({ category, label, value }))
}
const dynamicEmotions = previewItems('EMOTION', [
  ['中性', 'NEUTRAL'],
  ['开心', 'HAPPY'],
  ['喜爱', 'LOVING'],
  ['难过', 'SAD'],
  ['生气', 'ANGRY'],
  ['惊讶', 'SURPRISED'],
  ['困惑', 'CONFUSED'],
  ['害羞', 'SHY'],
  ['疲倦', 'TIRED'],
  ['专注', 'FOCUSED'],
  ['紧张', 'NERVOUS'],
  ['满足', 'CONTENT'],
])
const dynamicStates = previewItems('SYSTEM', [
  ['待机', 'IDLE'],
  ['倾听', 'LISTENING'],
  ['思考', 'PROCESSING'],
  ['说话', 'SPEAKING'],
  ['完成', 'SUCCESS'],
  ['没听清', 'NO_SPEECH'],
  ['错误', 'RECOVERABLE_ERROR'],
  ['离线', 'OFFLINE'],
  ['更新中', 'UPDATING'],
])
const dynamicBehaviors = previewItems('BEHAVIOR', [
  ['开机出现', 'BOOT_APPEAR'],
  ['苏醒', 'WAKE'],
  ['待机呼吸', 'IDLE_BREATHE'],
  ['靠近好奇*', 'PROXIMITY_CURIOUS'],
  ['摇晃眩晕', 'SHAKE_DIZZY'],
  ['困倦睡眠', 'DROWSY_SLEEP'],
  ['伙伴切换', 'ROLE_SWITCH'],
])
const fpsModeOptions = [
  { label: '自适应范围', value: 'ADAPTIVE' },
  { label: '固定帧率', value: 'FIXED' },
]
const fpsMode = ref<ExpressionFrameRateMode>('ADAPTIVE')
const fixedFps = ref(60)
const minimumFps = ref(30)
const maximumFps = ref(60)
const fixedFpsSlider = computed<number[]>({
  get: () => [fixedFps.value],
  set: (values) => { fixedFps.value = sliderToFrameRateRange(values)[0] },
})
const adaptiveFpsRange = computed<number[]>({
  get: () => frameRateRangeToSlider(minimumFps.value, maximumFps.value),
  set: (values) => {
    const [minimum, maximum] = sliderToFrameRateRange(values)
    minimumFps.value = minimum
    maximumFps.value = maximum
  },
})
const fpsSaving = ref(false)
const previewing = ref('')
const deviceOptions = computed(() => devices.value.map(device => ({
  label: `${device.displayName} · ${device.online ? '在线' : '离线'}`,
  value: device.id,
})))
const completedStateCount = computed(() => expressionStates.filter(({ value }) => model.value[value].length === 1).length)
const missingStateLabels = computed(() => expressionStates
  .filter(({ value }) => model.value[value].length !== 1)
  .map(({ label }) => label))
const selectedDevice = computed(() => devices.value.find(device => device.id === selectedDeviceId.value))
const selectionLoading = ref(false)
const selectionError = ref('')
const frameRateError = ref('')
const frameRateReady = ref(false)
let selectionRequest = 0
const activePack = computed(() => packs.value.find(pack => pack.id === selection.value?.packId))
const currentLabel = computed(() => selectionLoading.value ? '正在读取…' : currentExpressionLabel(selection.value, activePack.value, selectedDevice.value?.dynamicExpressionSupported))
const completedLifecycleCount = computed(() => lifecycleClips.filter(({ value }) => lifecycleFiles.value[value].length === 1).length)
const selectionStatus = computed(() => ({
  ACTIVE: '已启用',
  DISABLED: '使用内置形象',
  FAILED: '安装失败',
  INSTALLING: '正在安装',
  READY: '等待设备',
}[selection.value?.status ?? 'DISABLED']))
const previewDisabledReason = computed(() => {
  if (selectionLoading.value || !selection.value) {
    return '请先获取目标机器人的形象状态。'
  }
  if (previewing.value || actionPackId.value) {
    return '正在执行当前操作，请稍候。'
  }
  if (!selectedDevice.value?.online || !selectedDevice.value.commandAvailable) {
    return '机器人离线或命令通道不可用。'
  }
  if (!selectedDevice.value.dynamicExpressionSupported) {
    return '当前固件不支持动态表情。'
  }
  if (selection.value?.enabled && activePack.value?.packType === 'STATIC_PNG') {
    return '请先恢复动态球体，再进行真机预览。'
  }
  return ''
})
function defaults(): ExpressionPackFormModel {
  return {
    name: '',
    description: '',
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
}
function createPreviewUrl(file: File) {
  const url = URL.createObjectURL(file)
  previewObjectUrls.add(url)
  return url
}
function revokePreviewUrl(url?: string) {
  if (!url?.startsWith('blob:')) {
    return
  }
  URL.revokeObjectURL(url)
  previewObjectUrls.delete(url)
}
async function stageImage({ file, onProgress }: UploadRequest) {
  try {
    await validateImage(file)
    onProgress(100)
    return { url: createPreviewUrl(file) }
  }
  catch (error) {
    useFaToast().error('图片不可用', { description: error instanceof Error ? error.message : '无法读取图片。' })
    throw error
  }
}
async function stageBulkImage({ file, onProgress }: UploadRequest) {
  try {
    const state = expressionStateFromFilename(file.name)
    if (!state) {
      throw new Error(`无法从文件名识别状态：${file.name}`)
    }
    await validateImage(file)
    revokePreviewUrl(model.value[state][0]?.url)
    const url = createPreviewUrl(file)
    model.value[state] = [{ file, name: file.name, size: file.size, status: 'success', progress: 100, url }]
    bulkAssignments.set(state, file)
    onProgress(100)
    return { url: '' }
  }
  catch (error) {
    useFaToast().error('批量导入失败', { description: error instanceof Error ? error.message : '无法读取图片。' })
    throw error
  }
}
async function stageLifecycleClip({ file, onProgress }: UploadRequest) {
  if (file.size < 24 || file.size > 384 * 1024 || !file.name.toLowerCase().endsWith('.eaf')) {
    useFaToast().error('动画不可用', { description: '请选择不超过 384 KiB 的 .eaf 文件。' })
    throw new Error('invalid EAF clip')
  }
  const header = new Uint8Array(await file.slice(0, 16).arrayBuffer())
  if (header[0] !== 0x89 || String.fromCharCode(...header.slice(1, 4)) !== 'EAF') {
    useFaToast().error('动画不可用', { description: '文件不是有效的 EAF 动画。' })
    throw new Error('invalid EAF signature')
  }
  onProgress(100)
  return { url: '' }
}
function stagedFileField(componentField: Record<string, any>) {
  return {
    ...componentField,
    modelValue: (componentField.modelValue as StagedFile[] | undefined) ?? [],
  }
}
function revokeObjectUrls() {
  previewObjectUrls.forEach(url => URL.revokeObjectURL(url))
  previewObjectUrls.clear()
}
function resetCreationForm() {
  revokeObjectUrls()
  model.value = defaults()
  bulkImportFiles.value = []
  bulkAssignments.clear()
  activeState.value = 'idle'
}
async function load() {
  loading.value = true
  try {
    const [deviceList, packList] = await Promise.all([listDevices(), listExpressionPacks()])
    devices.value = deviceList
    packs.value = packList
    const nextDevice = deviceList.some(device => device.id === selectedDeviceId.value) ? selectedDeviceId.value : deviceList[0]?.id ?? ''
    if (nextDevice !== selectedDeviceId.value) {
      selectedDeviceId.value = nextDevice
    }
    else {
      await loadSelection()
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取表情资源包。' })
  }
  finally {
    loading.value = false
  }
}
async function loadSelection() {
  const request = ++selectionRequest
  const deviceId = selectedDeviceId.value
  selection.value = null
  selectionError.value = ''
  frameRateError.value = ''
  frameRateReady.value = false
  if (selectionRefreshTimer) {
    clearTimeout(selectionRefreshTimer)
  }
  selectionLoading.value = !!deviceId
  if (!deviceId) {
    return
  }
  const [nextSelection, frameRate] = await Promise.allSettled([
    getDeviceExpressionPack(deviceId),
    getExpressionFrameRate(deviceId),
  ])
  if (request !== selectionRequest || deviceId !== selectedDeviceId.value) {
    return
  }
  selectionLoading.value = false
  if (nextSelection.status === 'fulfilled') {
    selection.value = nextSelection.value
    scheduleSelectionRefresh()
  }
  else {
    selectionError.value = '形象状态未获取，请重试。'
  }
  if (frameRate.status === 'fulfilled') {
    fpsMode.value = frameRate.value.mode
    minimumFps.value = frameRate.value.minFps
    maximumFps.value = frameRate.value.maxFps
    fixedFps.value = frameRate.value.maxFps
    frameRateReady.value = true
  }
  else {
    frameRateError.value = '帧率策略未获取，请重试后再编辑。'
  }
}
async function saveFrameRate() {
  if (!selectedDeviceId.value || !frameRateReady.value || fpsSaving.value) {
    return
  }
  const minFps = fpsMode.value === 'FIXED' ? fixedFps.value : minimumFps.value
  const maxFps = fpsMode.value === 'FIXED' ? fixedFps.value : maximumFps.value
  if (minFps > maxFps) {
    useFaToast().error('帧率范围无效', { description: '最低帧率不能高于最高帧率。' })
    return
  }
  fpsSaving.value = true
  try {
    const result = await updateExpressionFrameRate(selectedDeviceId.value, { mode: fpsMode.value, minFps, maxFps })
    useFaToast().success('帧率策略已保存', {
      description: result.applied ? '已立即下发到机器人。' : '设备离线，重连后会自动应用。',
    })
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存帧率策略。' })
  }
  finally {
    fpsSaving.value = false
  }
}
async function preview(item: DynamicPreview) {
  if (!selectedDeviceId.value || previewDisabledReason.value) {
    return
  }
  previewing.value = `${item.category}:${item.value}`
  try {
    await previewExpression(selectedDeviceId.value, { category: item.category, value: item.value, durationSeconds: 5 })
    useFaToast().success(`正在预览“${item.label}”`, { description: '机器人将在 5 秒后回到当前真实状态。' })
  }
  catch (error) {
    useFaToast().error('预览失败', { description: error instanceof Error ? error.message : '机器人未接受预览命令。' })
  }
  finally {
    previewing.value = ''
  }
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
  for (const { value: state, label } of expressionStates) {
    const file = values[state][0]?.file
    if (!file) {
      activeState.value = state
      useFaToast().error('生成失败', { description: `${label}状态图不可用。` })
      return
    }
    images[state] = file
  }
  loading.value = true
  try {
    await createExpressionPack({ name: values.name, description: values.description, images })
    packs.value = await listExpressionPacks()
    resetCreationForm()
    activePage.value = 'manage'
    useFaToast().success('资源包已生成', { description: '现在可以选择机器人并安全启用；创建资源包本身不会改变设备。' })
  }
  catch (error) {
    useFaToast().error('生成失败', { description: error instanceof Error ? error.message : '无法生成资源包。' })
  }
  finally {
    loading.value = false
  }
}
async function submitLifecycle() {
  const name = lifecycleName.value.trim()
  if (!name || name.length > 80 || lifecycleDescription.value.trim().length > 240 || completedLifecycleCount.value === 0) {
    useFaToast().error('生成失败', { description: '请填写名称并至少上传一个生命周期片段。' })
    return
  }
  const clips: Partial<Record<LifecycleClip, {
    file: File
    frameDelayMs: number
  }>> = {}
  lifecycleClips.forEach(({ value }) => {
    const file = lifecycleFiles.value[value][0]?.file
    if (file) {
      clips[value] = { file, frameDelayMs: lifecycleFrameDelays.value[value][0] ?? 33 }
    }
  })
  loading.value = true
  try {
    await createLifecycleExpressionPack({ clips, description: lifecycleDescription.value.trim(), name })
    packs.value = await listExpressionPacks()
    lifecycleName.value = ''
    lifecycleDescription.value = ''
    lifecycleFiles.value = { boot_appear: [], wake: [], role_switch: [] }
    lifecycleFrameDelays.value = { boot_appear: [33], wake: [33], role_switch: [33] }
    activePage.value = 'manage'
    useFaToast().success('生命周期动画包已生成', { description: '启用后仍由原生动态球体负责持续表情。' })
  }
  catch (error) {
    useFaToast().error('生成失败', { description: error instanceof Error ? error.message : '无法生成动画包。' })
  }
  finally {
    loading.value = false
  }
}
function confirmActivate(pack: ExpressionPack) {
  const deviceId = selectedDeviceId.value
  const deviceName = selectedDevice.value?.displayName
  if (!deviceId || !deviceName || !selection.value) {
    return
  }
  useFaModal().confirm({
    title: `为 ${deviceName} 更换形象`,
    content: `将启用「${pack.name}」，替换这台机器人当前的自定义素材。PNG 与生命周期动画不能同时启用；设备校验成功后切换，失败继续使用原形象。`,
    confirmButtonText: '确认更换',
    onConfirm: async () => {
      if (deviceId !== selectedDeviceId.value) {
        useFaToast().error('目标已变化，请重新选择机器人后操作。')
        return
      }
      await activate(pack)
    },
  })
}

async function activate(pack: ExpressionPack) {
  if (!selectedDeviceId.value || !selection.value || actionPackId.value) {
    return
  }
  const deviceId = selectedDeviceId.value
  actionPackId.value = pack.id
  try {
    const result = await activateExpressionPack(pack.id, deviceId)
    if (deviceId !== selectedDeviceId.value) {
      return
    }
    ++selectionRequest
    selection.value = result
    scheduleSelectionRefresh()
    useFaToast().success('已进入安装队列', { description: '在线设备校验完成后自动切换；失败时继续使用原表情。' })
  }
  catch (error) {
    useFaToast().error('启用失败', { description: error instanceof Error ? error.message : '无法启用资源包。' })
  }
  finally {
    actionPackId.value = ''
  }
}
async function deactivate() {
  if (!selectedDeviceId.value || !selection.value || actionPackId.value) {
    return
  }
  const deviceId = selectedDeviceId.value
  actionPackId.value = 'deactivate'
  try {
    const result = await deactivateExpressionPack(deviceId)
    if (deviceId !== selectedDeviceId.value) {
      return
    }
    ++selectionRequest
    selection.value = result
    useFaToast().success('已保存内置形象设置', { description: '机器人连接后应用。' })
  }
  catch (error) {
    useFaToast().error('停用失败', { description: error instanceof Error ? error.message : '无法停用资源包。' })
  }
  finally {
    actionPackId.value = ''
  }
}
function confirmRemove(pack: ExpressionPack) {
  useFaModal().confirm({
    title: `删除「${pack.name}」`,
    content: '将删除这套素材的预览图和设备下载文件，不能撤销。仍被机器人使用的素材不能删除，请先停用。',
    confirmButtonText: '确认删除素材',
    onConfirm: () => remove(pack),
  })
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
watch(selectedDeviceId, async (deviceId, previous) => {
  if (deviceId !== previous) {
    await loadSelection()
  }
})
watch(bulkImportFiles, (files) => {
  const retainedFiles = new Set(files.flatMap(item => item.file ? [item.file] : []))
  bulkAssignments.forEach((file, state) => {
    if (retainedFiles.has(file)) {
      return
    }
    const current = model.value[state][0]
    if (current?.file === file) {
      revokePreviewUrl(current.url)
      model.value[state] = []
    }
    bulkAssignments.delete(state)
  })
}, { deep: true })
onMounted(load)
onUnmounted(() => {
  ++selectionRequest
  if (selectionRefreshTimer) {
    clearTimeout(selectionRefreshTimer)
  }
  revokeObjectUrls()
})
</script>

<template>
  <AppPageShell title="表情与形象" description="查看当前形象，选择表情或导入自己的素材。" width="normal">
    <AppLoading :loading="loading">
      <FaCollapsible>
        <template #trigger>
          <span class="text-sm underline">了解素材格式与替换规则</span>
        </template>        <FaAlert
          title="动态球体与静态资源包并存"
          description="动态球体始终是默认和安全回退。静态 PNG 会接管稳定状态；EAF V2 只播放开机、苏醒和伙伴切换的有限片段，结束后自动回到动态球体。两类资源共享 A/B 槽，任一设备只能启用一个。"
          class="mb-6"
        />
      </FaCollapsible>
      <FaTabs v-model="activePage" :list="pageTabs">
        <template #create>
          <FaForm id="expression-pack-form" :model="model" :validation-schema="validationSchema" class="space-y-6" @submit="submit">
            <div class="gap-6 grid grid-cols-1 xl:grid-cols-3">
              <FaCard title="资源包信息" description="资源包与机器人相互独立，生成完成后再到管理页选择目标设备。" class="xl:col-span-2">
                <div class="gap-x-8 gap-y-6 grid grid-cols-1 md:grid-cols-2">
                  <FaFormItem name="name" label="资源包名称" required>
                    <FaInput placeholder="例如：橘猫伙伴" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="description" label="说明">
                    <FaTextarea rows="2" placeholder="记录伙伴、画风和版本，最多 240 字。" class="w-full" />
                  </FaFormItem>
                </div>
              </FaCard>

              <FaCard title="批量导入" description="选择包含八张图片的文件夹，页面会按中英文文件名自动归位。">
                <FaFileUpload
                  v-model="bulkImportFiles"
                  directory
                  multiple
                  :max="24"
                  :http-request="stageBulkImage"
                  :after-upload="response => response.url"
                  description="选择文件夹：idle.png、listening.png…"
                />
                <div class="text-sm mt-4 flex items-center justify-between">
                  <span>已就绪 {{ completedStateCount }}/8</span>
                  <FaTag :variant="completedStateCount === 8 ? 'default' : 'secondary'">
                    {{ completedStateCount === 8 ? '可以生成' : `还差 ${8 - completedStateCount} 张` }}
                  </FaTag>
                </div>
                <p v-if="missingStateLabels.length" class="text-xs text-muted-foreground mt-2">
                  待补充：{{ missingStateLabels.join('、') }}
                </p>
              </FaCard>
            </div>

            <FaCard title="八状态预览与校验" description="一次只处理一个状态；也可以先批量导入，再逐项检查和替换。">
              <FaTabs v-model="activeState" :list="stateTabs" list-class="flex flex-wrap">
                <template v-for="state in expressionStates" :key="state.value" #[state.value]>
                  <div class="gap-6 grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_320px]">
                    <div>
                      <FaAlert :title="`${state.label}状态`" :description="stateGuidance[state.value]" class="mb-4" />
                      <FaFormItem :name="state.value" :label="`${state.label} PNG`" required>
                        <template #default="{ componentField }">
                          <FaFileUpload
                            v-bind="stagedFileField(componentField)"
                            :max="1"
                            :http-request="stageImage"
                            :after-upload="response => response.url"
                            description="拖放、粘贴或点击选择 320×240 PNG"
                          />
                        </template>
                      </FaFormItem>
                    </div>
                    <div class="border rounded-lg bg-black overflow-hidden">
                      <FaImagePreview
                        v-if="model[state.value][0]?.url"
                        :src="model[state.value][0].url!"
                        class="w-full aspect-4/3 object-cover"
                      />
                      <AppEmpty v-else description="尚未选择图片" class="bg-card aspect-4/3" />
                    </div>
                  </div>
                </template>
              </FaTabs>
            </FaCard>

            <FaFixedBar position="bottom" class="flex flex-wrap gap-3 items-center justify-center">
              <span class="text-sm text-muted-foreground">{{ completedStateCount }}/8 个状态已就绪</span>
              <FaButton type="submit" form="expression-pack-form" :loading="loading" :disabled="completedStateCount !== 8">
                生成并校验资源包
              </FaButton>
            </FaFixedBar>
          </FaForm>
        </template>

        <template #lifecycle>
          <div class="space-y-6">
            <FaAlert
              title="受限 EAF 生命周期片段"
              description="只接受 160×160、RLE、最长 5 秒的 EAF。至少提供一个片段；模型不能上传或选择动画，错误、离线、更新和语音交互会立即回退原生球体。"
            />
            <FaCard title="动画包信息" description="同一动画包最多包含开机出现、苏醒和伙伴切换三个片段。">
              <div class="gap-6 grid grid-cols-1 md:grid-cols-2">
                <div><FaLabel>资源包名称</FaLabel><FaInput v-model="lifecycleName" maxlength="80" placeholder="例如：柔和登场动画" class="mt-2 w-full" /></div>
                <div><FaLabel>说明</FaLabel><FaTextarea v-model="lifecycleDescription" maxlength="240" rows="2" placeholder="记录素材来源和版本" class="mt-2 w-full" /></div>
              </div>
            </FaCard>
            <div class="gap-6 grid grid-cols-1 xl:grid-cols-3">
              <FaCard v-for="clip in lifecycleClips" :key="clip.value" :title="clip.label" description="单次播放，缺少该片段时直接使用原生动画。">
                <FaFileUpload
                  v-model="lifecycleFiles[clip.value]"
                  :max="1"
                  accept=".eaf,application/vnd.espressif.eaf"
                  :http-request="stageLifecycleClip"
                  :after-upload="response => response.url"
                  description="选择不超过 384 KiB 的 .eaf"
                />
                <div class="mt-5 flex gap-4 items-center justify-between">
                  <FaLabel>每帧间隔</FaLabel>
                  <span class="text-sm font-medium">{{ lifecycleFrameDelays[clip.value][0] }} ms</span>
                </div>
                <FaSlider v-model="lifecycleFrameDelays[clip.value]" :min="16" :max="100" :step="1" class="mt-4" />
                <p class="text-xs text-muted-foreground mt-3">
                  约 {{ Math.round(1000 / (lifecycleFrameDelays[clip.value][0] || 33)) }} FPS；服务端会再次校验帧数、校验和、尺寸和总时长。
                </p>
              </FaCard>
            </div>
            <div class="flex justify-end">
              <FaButton :loading="loading" :disabled="completedLifecycleCount === 0" @click="submitLifecycle">
                生成并校验动画包（{{ completedLifecycleCount }}/3）
              </FaButton>
            </div>
          </div>
        </template>

        <template #manage>
          <div class="space-y-6">
            <FaAlert v-if="selectionError" title="形象未获取" :description="selectionError">
              <template #action>
                <FaButton variant="outline" @click="loadSelection">
                  重试
                </FaButton>
              </template>
            </FaAlert>
            <FaCard title="目标机器人" description="切换这里的机器人不会改变设备；只有点击资源包的“启用”才会下发安装。">
              <div class="gap-4 grid grid-cols-1 items-end md:grid-cols-2 xl:grid-cols-3">
                <div>
                  <FaLabel>机器人</FaLabel>
                  <FaSelect v-model="selectedDeviceId" :disabled="!!actionPackId || fpsSaving || !!previewing" :options="deviceOptions" placeholder="请选择机器人" class="mt-2 w-full" />
                </div>
                <div class="p-4 border rounded-lg">
                  <div class="text-sm text-muted-foreground">
                    设备报告的形象
                  </div>
                  <div class="font-medium mt-1">
                    {{ currentLabel }}
                  </div>
                </div>
                <div class="p-4 border rounded-lg flex items-center justify-between">
                  <div>
                    <div class="text-sm text-muted-foreground">
                      设备状态
                    </div>
                    <div class="font-medium mt-1">
                      {{ selectionLoading ? '正在读取…' : selection ? selectionStatus : '未获取' }}
                    </div>
                  </div>
                  <FaTag :variant="selectedDevice?.online ? 'default' : 'secondary'">
                    {{ selectedDevice ? (selectedDevice.online ? '在线' : '离线') : '未选择' }}
                  </FaTag>
                </div>
              </div>
              <template #footer>
                <div class="flex justify-end">
                  <FaButton variant="outline" :disabled="!selection?.enabled || !!actionPackId" :loading="actionPackId === 'deactivate'" @click="deactivate">
                    停用当前资源包
                  </FaButton>
                </div>
              </template>
            </FaCard>
            <FaCollapsible>
              <template #trigger>
                <span class="text-sm underline">在这台机器人上预览表情</span>
              </template>              <FaCard title="内置动态语义" description="点击按钮会让所选机器人预览 5 秒；预览不会修改真实会话状态。交互与系统状态仍优先于伙伴情绪。">
                <div class="gap-4 grid md:grid-cols-3">
                  <div>
                    <div class="text-sm font-medium mb-2">
                      12 种伙伴情绪
                    </div><div class="flex flex-wrap gap-2">
                      <FaButton v-for="item in dynamicEmotions" :key="item.value" size="sm" variant="outline" :loading="previewing === `${item.category}:${item.value}`" :disabled="!!previewDisabledReason" @click="preview(item)">
                        {{ item.label }}
                      </FaButton>
                    </div>
                  </div>
                  <div>
                    <div class="text-sm font-medium mb-2">
                      9 种交互/系统状态
                    </div><div class="flex flex-wrap gap-2">
                      <FaButton v-for="item in dynamicStates" :key="item.value" size="sm" variant="outline" :loading="previewing === `${item.category}:${item.value}`" :disabled="!!previewDisabledReason" @click="preview(item)">
                        {{ item.label }}
                      </FaButton>
                    </div>
                  </div>
                  <div>
                    <div class="text-sm font-medium mb-2">
                      6 种生命周期/物理动作
                    </div><div class="flex flex-wrap gap-2">
                      <FaButton v-for="item in dynamicBehaviors" :key="item.value" size="sm" variant="outline" :loading="previewing === `${item.category}:${item.value}`" :disabled="!!previewDisabledReason" @click="preview(item)">
                        {{ item.label }}
                      </FaButton>
                    </div>
                  </div>
                </div>
                <FaAlert v-if="previewDisabledReason" class="mt-4" title="暂时无法真机预览" :description="previewDisabledReason" />
                <p class="text-xs text-muted-foreground mt-3">
                  CoreS3 当前支持触摸与摇晃；本机未安装接近传感器，因此“靠近好奇”会由能力协商保持关闭。
                </p>
              </FaCard>
            </FaCollapsible>

            <FaCollapsible>
              <template #trigger>
                <span class="text-sm underline">高级：显示帧率</span>
              </template>
              <FaCard title="动态球体帧率" description="固定模式保持目标帧率；自适应模式只在设定范围内调节。屏保始终使用 20 FPS 以降低功耗。">
                <FaAlert v-if="frameRateError" title="帧率未获取" :description="frameRateError">
                  <template #action>
                    <FaButton @click="loadSelection">
                      重试
                    </FaButton>
                  </template>
                </FaAlert>
                <div class="gap-4 grid grid-cols-1 items-end md:grid-cols-3">
                  <div>
                    <FaLabel>调节方式</FaLabel>
                    <FaSelect v-model="fpsMode" :options="fpsModeOptions" class="mt-2 w-full" />
                  </div>
                  <div v-if="fpsMode === 'FIXED'">
                    <div class="flex gap-4 items-center justify-between">
                      <FaLabel>固定帧率</FaLabel>
                      <span class="text-sm font-medium">{{ fixedFps }} FPS</span>
                    </div>
                    <FaSlider v-model="fixedFpsSlider" :min="expressionFpsMinimum" :max="expressionFpsMaximum" :step="1" class="mt-4" />
                    <div class="text-xs text-muted-foreground mt-2 flex justify-between">
                      <span>1</span><span>15</span><span>30</span><span>45</span><span>60</span>
                    </div>
                  </div>
                  <template v-else>
                    <div class="md:col-span-1">
                      <div class="flex gap-4 items-center justify-between">
                        <FaLabel>自适应范围</FaLabel>
                        <span class="text-sm font-medium">{{ minimumFps }}–{{ maximumFps }} FPS</span>
                      </div>
                      <FaSlider v-model="adaptiveFpsRange" :min="expressionFpsMinimum" :max="expressionFpsMaximum" :step="1" class="mt-4" />
                      <div class="text-xs text-muted-foreground mt-2 flex justify-between">
                        <span>1</span><span>15</span><span>30</span><span>45</span><span>60</span>
                      </div>
                    </div>
                  </template>
                  <FaButton :loading="fpsSaving" :disabled="!selectedDeviceId || !frameRateReady || selectionLoading" @click="saveFrameRate">
                    保存并应用
                  </FaButton>
                </div>
                <p class="text-xs text-muted-foreground mt-3">
                  固定 60 FPS 只锁定调度目标；若单帧绘制超过 16.7 ms，实际帧率仍会低于 60，并可能出现丢帧。建议先试固定 60，再根据诊断选择 30–60 自适应。
                </p>
              </FaCard>
            </FaCollapsible>

            <AppEmpty v-if="packs.length === 0" description="还没有自定义素材，仍可使用内置动态形象。" />
            <div v-else class="gap-6 grid grid-cols-1 xl:grid-cols-2">
              <FaCard v-for="pack in packs" :key="pack.id" :title="pack.name" :description="pack.description ?? undefined">
                <div v-if="pack.packType === 'STATIC_PNG'" class="gap-2 grid grid-cols-4">
                  <figure v-for="state in expressionStates" :key="state.value" class="border rounded-lg bg-black overflow-hidden">
                    <FaImagePreview :src="expressionPreviewUrl(pack.id, state.value)" class="w-full aspect-4/3 object-cover" />
                    <figcaption class="text-xs px-1 py-1 text-center bg-card">
                      {{ state.label }}
                    </figcaption>
                  </figure>
                </div>
                <div v-else class="space-y-3">
                  <FaAlert title="有限 EAF 生命周期动画" description="片段结束、取消或遇到高优先级状态后自动恢复原生动态球体。" />
                  <div class="flex flex-wrap gap-2">
                    <a v-for="clip in pack.clips" :key="clip.name" :href="lifecycleClipUrl(pack.id, clip.name)" download>
                      <FaTag>{{ lifecycleClips.find(item => item.value === clip.name)?.label }} · {{ clip.frameCount }} 帧 · {{ clip.frameDelayMs }} ms</FaTag>
                    </a>
                  </div>
                </div>
                <template #footer>
                  <div class="flex flex-wrap gap-3 items-center justify-between">
                    <span class="text-xs text-muted-foreground">{{ pack.packType === 'LIFECYCLE_EAF' ? 'EAF V2' : 'PNG V1' }} · {{ Math.ceil(pack.artifactSize / 1024) }} KiB</span>
                    <div class="flex gap-2">
                      <FaButton
                        variant="outline"
                        :loading="actionPackId === pack.id"
                        :disabled="!selectedDeviceId || !selection || !!actionPackId || (selection?.packId === pack.id && selection.enabled) || (pack.packType === 'LIFECYCLE_EAF' && !selectedDevice?.lifecycleClipSupported)"
                        @click="confirmActivate(pack)"
                      >
                        {{ selection?.packId === pack.id && selection.enabled ? '当前使用' : '启用到此机器人' }}
                      </FaButton>
                      <FaButton
                        variant="destructive"
                        :loading="actionPackId === pack.id"
                        :disabled="!!actionPackId || (selection?.packId === pack.id && selection.enabled)"
                        @click="confirmRemove(pack)"
                      >
                        删除
                      </FaButton>
                    </div>
                  </div>
                </template>
              </FaCard>
            </div>
          </div>
        </template>
      </FaTabs>
    </AppLoading>
  </AppPageShell>
</template>
