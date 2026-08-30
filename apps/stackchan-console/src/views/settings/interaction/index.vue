<script setup lang="ts">
import type { BodyMotion, Device } from '@/api/modules/devices'
import type { MissedReminderPolicy, ProactiveTopicCooldown, SaveInteractionSettingsInput } from '@/api/modules/interactions'
import type { ICloudCalendarConnection, SaveWorkdaySettingsInput, WorkdayMetrics, WorkdayPilotReport, WorkdayRestAction, WorkdayRuntime, WorkdayWeather, WorkdayWeatherLocationInput } from '@/api/modules/workday'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import {
  calibrateDeviceBody,
  configureDeviceBodyMotion,
  listDevices,
  playDeviceBodyMotion,
  stopDeviceMotion,
} from '@/api/modules/devices'
import {
  getInteractionSettings,
  listProactiveTopics,
  resumeProactiveTopic,
  saveInteractionSettings,
  stopDeviceAudio,
} from '@/api/modules/interactions'
import { currentTimeZone } from '@/api/modules/reminders'
import {
  connectICloudCalendar,
  disconnectICloudCalendar,
  getICloudCalendarConnection,
  getWorkdayMetrics,
  getWorkdayPilot,
  getWorkdayRuntime,
  getWorkdaySettings,
  getWorkdayWeather,
  markWorkdayFalseTrigger,
  respondToWorkdayRest,
  restartWorkdayPilot,
  saveWorkdaySettings,
  startWorkday,
  startWorkdayPilot,
  stopWorkday,
  syncICloudCalendar,
  syncWorkdayWeather,
  testICloudCalendarConnection,
  testWorkdayWeather,
  undoWorkdayFalseTrigger,
  updateAllowedICloudCalendars,
} from '@/api/modules/workday'

defineOptions({ name: 'InteractionSettings' })

interface WorkdayFormFields {
  absenceSuspendMinutes: number
  focusMinutes: number
  latitude: string
  locationName: string
  longitude: string
  rearrivalMinutes: number
  restMinutes: number
  workdayEnabled: boolean
  workdayFriday: boolean
  workdayMonday: boolean
  workdaySaturday: boolean
  workdaySunday: boolean
  workdayThursday: boolean
  workdayTuesday: boolean
  workdayWednesday: boolean
  workdayZoneId: string
  workEnd: string
  workStart: string
}

interface InteractionFormModel extends SaveInteractionSettingsInput, WorkdayFormFields {
  deviceId: string
}

const devices = ref<Device[]>([])
const loading = ref(false)
const stopping = ref(false)
const topicCooldowns = ref<ProactiveTopicCooldown[]>([])
const resumingTopic = ref('')
const model = ref<InteractionFormModel>(defaults())
const workdayRuntime = ref<WorkdayRuntime | null>(null)
const workdayMetrics = ref<WorkdayMetrics | null>(null)
const workdayPilot = ref<WorkdayPilotReport | null>(null)
const workdayWeather = ref<WorkdayWeather | null>(null)
const icloudConnection = ref<ICloudCalendarConnection | null>(null)
const icloudAccountEmail = ref('')
const icloudAppSpecificPassword = ref('')
const calendarAction = ref<'connect' | 'disconnect' | 'save' | 'sync' | 'test' | ''>('')
const weatherAction = ref<'sync' | 'test' | ''>('')
const workdayAction = ref<'start' | 'stop' | WorkdayRestAction | ''>('')
const pilotAction = ref<'start' | 'restart' | 'mark' | 'undo' | ''>('')
const bodyAction = ref<'calibrate' | 'disable' | 'enable' | BodyMotion | ''>('')

const bodyMotions: { label: string, value: BodyMotion }[] = [
  { label: '唤醒', value: 'WAKE' },
  { label: '看向主人', value: 'LOOK_USER' },
  { label: '轻点头', value: 'NOD_SMALL' },
  { label: '思考', value: 'THINK' },
  { label: '困倦', value: 'DROWSY' },
]

const runtimeStateLabels: Record<WorkdayRuntime['state'], string> = {
  OFF: '未开始',
  STARTING: '等待在场确认',
  ACTIVE_PRESENT: '在场专注',
  ACTIVE_ABSENT: '离开暂停',
  REST_PROMPTED: '等待休息选择',
  RESTING: '休息中',
  SKIPPED_FOR_DAY: '今日跳过休息提醒',
}

const briefStatusLabels = {
  PENDING: '已占位，等待生成',
  SUCCESS: '成功',
  PARTIAL: '部分成功',
  FAILED: '失败',
  CANCELLED: '已取消',
} as const

const deviceOptions = computed(() => devices.value.map(device => ({
  label: `${device.displayName} · ${device.online ? '在线' : '离线'}`,
  value: device.id,
})))

const selectedDevice = computed(() => devices.value.find(device => device.id === model.value.deviceId) ?? null)

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
  workdayEnabled: z.boolean(),
  workdayMonday: z.boolean(),
  workdayTuesday: z.boolean(),
  workdayWednesday: z.boolean(),
  workdayThursday: z.boolean(),
  workdayFriday: z.boolean(),
  workdaySaturday: z.boolean(),
  workdaySunday: z.boolean(),
  workStart: z.string().min(1, '请选择工作开始时间'),
  workEnd: z.string().min(1, '请选择工作结束时间'),
  focusMinutes: z.number().int().min(15).max(180),
  restMinutes: z.number().int().min(5).max(60),
  absenceSuspendMinutes: z.number().int().min(1).max(60),
  rearrivalMinutes: z.number().int().min(5).max(240),
  locationName: z.string().trim().max(120),
  latitude: z.string().trim(),
  longitude: z.string().trim(),
  workdayZoneId: z.string().trim().min(1, '时区不能为空'),
}).superRefine((values, context) => {
  const weekdays = [
    values.workdayMonday,
    values.workdayTuesday,
    values.workdayWednesday,
    values.workdayThursday,
    values.workdayFriday,
    values.workdaySaturday,
    values.workdaySunday,
  ]
  if (!weekdays.some(Boolean)) {
    context.addIssue({ code: 'custom', message: '至少选择一个工作日', path: ['workdayMonday'] })
  }
  if (values.workStart === values.workEnd) {
    context.addIssue({ code: 'custom', message: '开始和结束时间不能相同', path: ['workEnd'] })
  }
  if (values.rearrivalMinutes < values.absenceSuspendMinutes) {
    context.addIssue({ code: 'custom', message: '返场阈值不能小于离开阈值', path: ['rearrivalMinutes'] })
  }
  if ((values.latitude === '') !== (values.longitude === '')) {
    context.addIssue({ code: 'custom', message: '经纬度必须同时填写或同时留空', path: ['latitude'] })
  }
  validateCoordinate(values.latitude, -90, 90, '纬度应在 -90 到 90 之间', 'latitude', context)
  validateCoordinate(values.longitude, -180, 180, '经度应在 -180 到 180 之间', 'longitude', context)
}))

function validateCoordinate(
  value: string,
  minimum: number,
  maximum: number,
  message: string,
  path: 'latitude' | 'longitude',
  context: z.RefinementCtx,
) {
  if (value === '') {
    return
  }
  const number = Number(value)
  if (!Number.isFinite(number) || number < minimum || number > maximum) {
    context.addIssue({ code: 'custom', message, path: [path] })
  }
}

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
    workdayEnabled: false,
    workdayMonday: true,
    workdayTuesday: true,
    workdayWednesday: true,
    workdayThursday: true,
    workdayFriday: true,
    workdaySaturday: false,
    workdaySunday: false,
    workStart: '09:00',
    workEnd: '18:00',
    focusMinutes: 50,
    restMinutes: 10,
    absenceSuspendMinutes: 10,
    rearrivalMinutes: 45,
    locationName: '',
    latitude: '',
    longitude: '',
    workdayZoneId: currentTimeZone(),
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
    const [settings, workday, runtime, metrics, pilot, calendar, weather] = await Promise.all([
      getInteractionSettings(deviceId),
      getWorkdaySettings(deviceId),
      getWorkdayRuntime(deviceId),
      getWorkdayMetrics(deviceId),
      getWorkdayPilot(deviceId),
      getICloudCalendarConnection(deviceId),
      getWorkdayWeather(deviceId),
    ])
    workdayRuntime.value = runtime
    workdayMetrics.value = metrics
    workdayPilot.value = pilot
    workdayWeather.value = weather
    icloudConnection.value = calendar
    icloudAccountEmail.value = ''
    icloudAppSpecificPassword.value = ''
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
      workdayEnabled: workday.enabled,
      workdayMonday: includesWorkday(workday.workDaysMask, 0),
      workdayTuesday: includesWorkday(workday.workDaysMask, 1),
      workdayWednesday: includesWorkday(workday.workDaysMask, 2),
      workdayThursday: includesWorkday(workday.workDaysMask, 3),
      workdayFriday: includesWorkday(workday.workDaysMask, 4),
      workdaySaturday: includesWorkday(workday.workDaysMask, 5),
      workdaySunday: includesWorkday(workday.workDaysMask, 6),
      workStart: workday.workStart.slice(0, 5),
      workEnd: workday.workEnd.slice(0, 5),
      focusMinutes: workday.focusMinutes,
      restMinutes: workday.restMinutes,
      absenceSuspendMinutes: workday.absenceSuspendMinutes,
      rearrivalMinutes: workday.rearrivalMinutes,
      locationName: workday.locationName,
      latitude: workday.latitude?.toString() ?? '',
      longitude: workday.longitude?.toString() ?? '',
      workdayZoneId: workday.zoneId,
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
  if (!model.value.deviceId) {
    return
  }
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
    const interactionInput: SaveInteractionSettingsInput = {
      volumePercent: values.volumePercent,
      nightMode: values.nightMode,
      continuousConversationEnabled: values.continuousConversationEnabled,
      followUpWindowSeconds: values.followUpWindowSeconds,
      dndEnabled: values.dndEnabled,
      dndStart: values.dndStart,
      dndEnd: values.dndEnd,
      zoneId: values.zoneId,
      missedReminderPolicy: values.missedReminderPolicy,
      missedSnoozeMinutes: values.missedSnoozeMinutes,
      proactiveEnabled: values.proactiveEnabled,
      proactiveStart: values.proactiveStart,
      proactiveEnd: values.proactiveEnd,
      proactiveMinIntervalMinutes: values.proactiveMinIntervalMinutes,
      proactivePersonalizationEnabled: values.proactivePersonalizationEnabled,
      proactiveDailyLimit: values.proactiveDailyLimit,
      proactiveContent: values.proactiveContent,
    }
    const workdayInput: SaveWorkdaySettingsInput = {
      enabled: values.workdayEnabled,
      workDaysMask: workDaysMask(values),
      workStart: values.workStart,
      workEnd: values.workEnd,
      focusMinutes: values.focusMinutes,
      restMinutes: values.restMinutes,
      absenceSuspendMinutes: values.absenceSuspendMinutes,
      rearrivalMinutes: values.rearrivalMinutes,
      locationName: values.locationName.trim(),
      latitude: optionalCoordinate(values.latitude),
      longitude: optionalCoordinate(values.longitude),
      zoneId: values.workdayZoneId.trim(),
    }
    await Promise.all([
      saveInteractionSettings(values.deviceId, interactionInput),
      saveWorkdaySettings(values.deviceId, workdayInput),
    ])
    workdayWeather.value = await getWorkdayWeather(values.deviceId)
    useFaToast().success('设置已保存', { description: '工作日陪伴保持默认关闭；启用后按固定规则运行。' })
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存交互设置。' })
  }
  finally {
    loading.value = false
  }
}

function includesWorkday(mask: number, bit: number) {
  return (mask & (1 << bit)) !== 0
}

function workDaysMask(values: WorkdayFormFields) {
  return [
    values.workdayMonday,
    values.workdayTuesday,
    values.workdayWednesday,
    values.workdayThursday,
    values.workdayFriday,
    values.workdaySaturday,
    values.workdaySunday,
  ].reduce((mask, enabled, bit) => enabled ? mask | (1 << bit) : mask, 0)
}

function optionalCoordinate(value: string) {
  return value.trim() === '' ? null : Number(value)
}

function weatherLocationInput(): WorkdayWeatherLocationInput {
  const latitude = optionalCoordinate(model.value.latitude)
  const longitude = optionalCoordinate(model.value.longitude)
  if (latitude === null || longitude === null) {
    throw new Error('请先同时填写纬度和经度。')
  }
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
    || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw new Error('请填写有效的经纬度。')
  }
  const zoneId = model.value.workdayZoneId.trim()
  if (!zoneId) {
    throw new Error('请填写工作日时区。')
  }
  return {
    locationName: model.value.locationName.trim(),
    latitude,
    longitude,
    zoneId,
  }
}

function weatherStatusText() {
  if (!workdayWeather.value?.configured) {
    return '尚未配置固定位置'
  }
  if (workdayWeather.value.status === 'ERROR') {
    return '最近同步失败'
  }
  if (workdayWeather.value.fresh) {
    return '天气缓存可用'
  }
  return workdayWeather.value.lastSyncedAt ? '天气缓存已过期' : '等待首次同步'
}

function weatherFailureText() {
  const failure = workdayWeather.value?.lastFailureCode
  if (!failure) {
    return ''
  }
  return {
    REQUEST_FAILED: '暂时无法访问 Open-Meteo',
    RESPONSE_TOO_LARGE: '天气响应超过安全限制',
    INVALID_RESPONSE: 'Open-Meteo 返回了无法识别的数据',
  }[failure]
}

async function runWeatherAction(action: 'sync' | 'test', operation: () => Promise<void>) {
  if (!model.value.deviceId) {
    return
  }
  weatherAction.value = action
  try {
    await operation()
  }
  catch (error) {
    useFaToast().error('天气操作失败', {
      description: error instanceof Error ? error.message : '暂时无法读取固定位置天气。',
    })
  }
  finally {
    weatherAction.value = ''
  }
}

async function testWeather() {
  await runWeatherAction('test', async () => {
    const result = await testWorkdayWeather(model.value.deviceId, weatherLocationInput())
    useFaToast().success('天气连接测试成功', {
      description: `${result.summary} 测试不会保存位置或天气数据。`,
    })
  })
}

async function syncWeather() {
  await runWeatherAction('sync', async () => {
    workdayWeather.value = await syncWorkdayWeather(model.value.deviceId)
    useFaToast().success('固定位置天气已同步', {
      description: workdayWeather.value.summary ?? '缓存有效期为 1 小时。',
    })
  })
}

function formatFocus(seconds: number) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return hours > 0 ? `${hours} 小时 ${minutes} 分钟` : `${minutes} 分钟`
}

function formatBriefStatus(status: WorkdayRuntime['briefStatus']) {
  return status ? briefStatusLabels[status] : '尚未尝试'
}

function calendarCredentials() {
  return {
    accountEmail: icloudAccountEmail.value.trim(),
    appSpecificPassword: icloudAppSpecificPassword.value.trim(),
  }
}

function calendarStatusText() {
  if (!icloudConnection.value?.configured) {
    return '尚未连接'
  }
  const labels = {
    CONFIGURED: '已配置',
    CONNECTED: '连接正常',
    AUTH_FAILED: '认证失败',
    ERROR: '同步异常',
  } as const
  return icloudConnection.value.status ? labels[icloudConnection.value.status] : '已配置'
}

function calendarFailureText() {
  const failure = icloudConnection.value?.lastFailureCode
  if (!failure) {
    return ''
  }
  return {
    AUTHENTICATION_FAILED: 'Apple 账号或 App 专用密码无效',
    DISCOVERY_FAILED: '无法发现 iCloud 日历',
    SYNC_FAILED: '读取日程失败',
    RESPONSE_TOO_LARGE: '日程响应超过安全限制',
    INVALID_RESPONSE: 'iCloud 返回了无法识别的数据',
  }[failure]
}

async function runCalendarAction(
  action: 'connect' | 'disconnect' | 'save' | 'sync' | 'test',
  operation: () => Promise<void>,
) {
  if (!model.value.deviceId) {
    return
  }
  calendarAction.value = action
  try {
    await operation()
  }
  catch (error) {
    useFaToast().error('iCloud 日历操作失败', {
      description: error instanceof Error ? error.message : '暂时无法访问 iCloud 日历。',
    })
  }
  finally {
    calendarAction.value = ''
  }
}

async function testCalendar() {
  await runCalendarAction('test', async () => {
    const result = await testICloudCalendarConnection(model.value.deviceId, calendarCredentials())
    useFaToast().success('连接测试成功', {
      description: `发现 ${result.discoveredCalendarCount} 个日历；测试不会保存账号或密码。`,
    })
  })
}

async function connectCalendar() {
  await runCalendarAction('connect', async () => {
    icloudConnection.value = await connectICloudCalendar(model.value.deviceId, calendarCredentials())
    icloudAppSpecificPassword.value = ''
    useFaToast().success('已连接 iCloud 日历', { description: '新发现的日历默认不允许读取，请选择后保存。' })
  })
}

async function saveAllowedCalendars() {
  await runCalendarAction('save', async () => {
    const allowed = icloudConnection.value?.calendars.filter(calendar => calendar.allowed).map(calendar => calendar.id) ?? []
    icloudConnection.value = await updateAllowedICloudCalendars(model.value.deviceId, allowed)
    useFaToast().success('日历白名单已保存')
  })
}

async function syncCalendar() {
  await runCalendarAction('sync', async () => {
    icloudConnection.value = await syncICloudCalendar(model.value.deviceId)
    useFaToast().success('只读日程已同步', {
      description: `缓存 ${icloudConnection.value.cachedEventCount} 条未来 7 天日程，24 小时后自动过期。`,
    })
  })
}

async function disconnectCalendar() {
  await runCalendarAction('disconnect', async () => {
    await disconnectICloudCalendar(model.value.deviceId)
    icloudConnection.value = await getICloudCalendarConnection(model.value.deviceId)
    icloudAccountEmail.value = ''
    icloudAppSpecificPassword.value = ''
    useFaToast().success('已断开 iCloud 日历', { description: '本地凭据、日历白名单和事件缓存已删除。' })
  })
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

async function runWorkdayAction(
  action: 'start' | 'stop' | WorkdayRestAction,
  operation: () => Promise<WorkdayRuntime>,
) {
  if (!model.value.deviceId || workdayAction.value) {
    return
  }
  workdayAction.value = action
  try {
    workdayRuntime.value = await operation()
    workdayMetrics.value = await getWorkdayMetrics(model.value.deviceId)
    useFaToast().success('工作状态已更新')
  }
  catch (error) {
    useFaToast().error('工作状态更新失败', {
      description: error instanceof Error ? error.message : '当前无法执行这项工作操作。',
    })
  }
  finally {
    workdayAction.value = ''
  }
}

async function beginWorkday() {
  await runWorkdayAction('start', () => startWorkday(model.value.deviceId))
}

async function endWorkday() {
  await runWorkdayAction('stop', () => stopWorkday(model.value.deviceId))
}

async function respondToRest(action: WorkdayRestAction) {
  await runWorkdayAction(action, () => respondToWorkdayRest(model.value.deviceId, action))
}

async function runPilotAction(
  action: typeof pilotAction.value,
  operation: () => Promise<WorkdayPilotReport>,
  message: string,
) {
  if (!model.value.deviceId || pilotAction.value) {
    return
  }
  pilotAction.value = action
  try {
    workdayPilot.value = await operation()
    workdayMetrics.value = await getWorkdayMetrics(model.value.deviceId)
    useFaToast().success(message)
  }
  catch (error) {
    useFaToast().error('观察记录更新失败', {
      description: error instanceof Error ? error.message : '暂时无法更新十四天观察记录。',
    })
  }
  finally {
    pilotAction.value = ''
  }
}

function startPilot() {
  useFaModal().confirm({
    title: '开始十四天私用观察？',
    content: '将从设备当前时区的今天开始，并冻结本轮工作日规则快照。建议在当天首次实际使用前开始；同一天已有的聚合计数仍会保留。',
    confirmButtonText: '开始观察',
    onConfirm: () => runPilotAction('start', () => startWorkdayPilot(model.value.deviceId), '十四天观察已开始'),
  })
}

function restartPilot() {
  useFaModal().confirm({
    title: '重新开始十四天观察？',
    content: '历史聚合不会删除，但本轮门槛窗口会从今天重新计算。',
    confirmButtonText: '重新开始',
    onConfirm: () => runPilotAction('restart', () => restartWorkdayPilot(model.value.deviceId), '十四天观察已重新开始'),
  })
}

function markFalseTrigger() {
  useFaModal().confirm({
    title: '标记一次误播报？',
    content: '只增加今天的匿名计数，不保存播报正文或原因；标错后可以撤销一次。',
    confirmButtonText: '确认标记',
    onConfirm: () => runPilotAction('mark', () => markWorkdayFalseTrigger(model.value.deviceId), '已记录一次误播报'),
  })
}

async function undoFalseTrigger() {
  await runPilotAction('undo', () => undoWorkdayFalseTrigger(model.value.deviceId), '已撤销今天的一次误播报标记')
}

function pilotStatusText() {
  if (!workdayPilot.value?.started) {
    return '尚未开始'
  }
  return {
    COLLECTING: '观察中',
    FAIL: '门槛未通过',
    NOT_STARTED: '尚未开始',
    PASS: '门槛通过',
  }[workdayPilot.value.status]
}

async function runBodyAction(action: typeof bodyAction.value, operation: () => Promise<void>, message: string) {
  if (!model.value.deviceId || bodyAction.value) {
    return
  }
  bodyAction.value = action
  try {
    await operation()
    useFaToast().success(message, { description: '命令已送达设备，实际结果以下一次心跳诊断为准。' })
  }
  catch (error) {
    useFaToast().error('身体动作命令失败', {
      description: error instanceof Error ? error.message : '机器人当前无法接收命令。',
    })
  }
  finally {
    bodyAction.value = ''
  }
}

async function calibrateBody() {
  await runBodyAction('calibrate', () => calibrateDeviceBody(model.value.deviceId), '已下发中位校准')
}

async function configureBody(enabled: boolean) {
  await runBodyAction(enabled ? 'enable' : 'disable', () => configureDeviceBodyMotion(model.value.deviceId, enabled), enabled ? '已请求启用身体动作' : '已禁用身体动作')
}

async function playBody(motion: BodyMotion) {
  await runBodyAction(motion, () => playDeviceBodyMotion(model.value.deviceId, motion), '已下发固定动作')
}

async function stopBody() {
  await runBodyAction('disable', () => stopDeviceMotion(model.value.deviceId), '已发送本地停止命令')
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
          class="mx-auto gap-6 grid grid-cols-1 max-w-5xl md:grid-cols-2"
          @submit="submit"
        >
          <FaCard title="目标机器人与本地呈现" class="md:col-span-2">
            <div class="gap-x-8 gap-y-6 grid grid-cols-1 md:grid-cols-2">
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
            <div class="mt-6 pt-6 border-t gap-x-8 gap-y-6 grid grid-cols-1 md:grid-cols-2">
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

          <FaCard title="K151 安全身体感" class="md:col-span-2">
            <div class="gap-5 grid">
              <FaAlert
                title="动作默认关闭，校准不会主动转动舵机"
                description="先扶正头部并执行中位校准，再显式启用。固件只接受下列五种固定动作；录音、播报、升级、离线、触摸停止、超时或反馈异常都会拒绝或立即断电。"
              />
              <div class="gap-4 grid lg:grid-cols-4 sm:grid-cols-2">
                <FaCard title="运动安全状态">
                  <p class="text-lg font-semibold">
                    {{ selectedDevice?.body.motionState === 'RUNNING' ? '动作中' : selectedDevice?.body.motionState === 'ARMED' ? '已启用' : '已禁用' }}
                  </p>
                  <p class="text-sm text-muted-foreground mt-1">
                    {{ selectedDevice?.body.calibrated ? '已校准中位' : '尚未校准' }} · {{ selectedDevice?.body.servoFeedbackSupported ? '反馈正常' : '反馈未验证' }}
                  </p>
                </FaCard>
                <FaCard title="在场感知">
                  <p class="text-lg font-semibold">
                    {{ selectedDevice?.body.proximitySupported ? (selectedDevice.body.present ? '检测到在场' : '当前未在场') : '不支持' }}
                  </p>
                  <p class="text-sm text-muted-foreground mt-1">
                    只上报布尔结果，不保存原始距离
                  </p>
                </FaCard>
                <FaCard title="环境光">
                  <p class="text-lg font-semibold">
                    {{ selectedDevice?.body.ambientLightSupported ? selectedDevice.body.ambientLight : '不可用' }}
                  </p>
                  <p class="text-sm text-muted-foreground mt-1">
                    仅 DARK / DIM / NORMAL / BRIGHT 档位
                  </p>
                </FaCard>
                <FaCard title="最近安全诊断">
                  <p class="text-lg font-semibold">
                    {{ selectedDevice?.body.lastFailureCode ?? 'NONE' }}
                  </p>
                  <p class="text-sm text-muted-foreground mt-1">
                    累计 {{ selectedDevice?.body.failureCount ?? 0 }} 次拒绝或停止
                  </p>
                </FaCard>
              </div>
              <div class="flex flex-wrap gap-3">
                <FaButton
                  type="button"
                  variant="outline"
                  :disabled="!selectedDevice?.online || !selectedDevice?.body.bodyMotionSupported"
                  :loading="bodyAction === 'calibrate'"
                  @click="calibrateBody"
                >
                  校准当前中位
                </FaButton>
                <FaButton
                  type="button"
                  :disabled="!selectedDevice?.online || !selectedDevice?.body.bodyMotionSupported || !selectedDevice?.body.calibrated"
                  :loading="bodyAction === 'enable'"
                  @click="configureBody(true)"
                >
                  显式启用动作
                </FaButton>
                <FaButton
                  type="button"
                  variant="destructive"
                  :disabled="!selectedDevice?.online"
                  :loading="bodyAction === 'disable'"
                  @click="stopBody"
                >
                  立即停止并禁用
                </FaButton>
              </div>
              <div class="pt-5 border-t">
                <div class="text-sm font-medium mb-3">
                  固件内置动作模板
                </div>
                <div class="flex flex-wrap gap-3">
                  <FaButton
                    v-for="motion in bodyMotions"
                    :key="motion.value"
                    type="button"
                    variant="outline"
                    :disabled="!selectedDevice?.online || selectedDevice?.body.motionState !== 'ARMED' || !!bodyAction"
                    :loading="bodyAction === motion.value"
                    @click="playBody(motion.value)"
                  >
                    {{ motion.label }}
                  </FaButton>
                </div>
              </div>
            </div>
          </FaCard>

          <FaCard title="免打扰与离线提醒">
            <div class="gap-6 grid">
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
            <div class="gap-6 grid">
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
              <div class="pt-5 border-t">
                <div class="text-sm font-medium mb-3">
                  最近主动主题
                </div>
                <FaAlert
                  v-if="topicCooldowns.length === 0"
                  title="暂无主题记录"
                  description="个性化主题成功进入主动提醒后才会出现；使用记录不复制记忆正文。"
                />
                <div v-for="topic in topicCooldowns" :key="topic.topicKey" class="text-sm mb-3 p-3 border rounded-md">
                  <div class="flex gap-3 items-center justify-between">
                    <div class="min-w-0">
                      <div class="font-medium truncate">
                        {{ topic.topicKey }}
                      </div>
                      <div class="text-xs text-muted-foreground mt-1">
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

          <FaCard title="工作日桌面陪伴" class="md:col-span-2">
            <div class="gap-6 grid">
              <FaAlert
                title="工作陪伴由你显式开始"
                description="开始后会按设备在场状态累计专注，首次简报读取新鲜天气和允许的日历缓存；离开、免打扰、活动语音和临近日程会抑制打扰。"
              />
              <div class="flex flex-wrap gap-3">
                <FaButton
                  type="button"
                  :loading="workdayAction === 'start'"
                  :disabled="Boolean(workdayAction) || workdayRuntime?.state !== 'OFF' || !selectedDevice?.online"
                  @click="beginWorkday"
                >
                  开始工作
                </FaButton>
                <FaButton
                  type="button"
                  variant="outline"
                  :loading="workdayAction === 'stop'"
                  :disabled="Boolean(workdayAction) || !workdayRuntime || workdayRuntime.state === 'OFF'"
                  @click="endWorkday"
                >
                  结束工作
                </FaButton>
                <template v-if="workdayRuntime?.state === 'REST_PROMPTED'">
                  <FaButton type="button" variant="outline" :loading="workdayAction === 'START_REST'" :disabled="Boolean(workdayAction)" @click="respondToRest('START_REST')">
                    开始休息
                  </FaButton>
                  <FaButton type="button" variant="outline" :loading="workdayAction === 'SNOOZE'" :disabled="Boolean(workdayAction)" @click="respondToRest('SNOOZE')">
                    稍后 10 分钟
                  </FaButton>
                  <FaButton type="button" variant="outline" :loading="workdayAction === 'SKIP_FOR_DAY'" :disabled="Boolean(workdayAction)" @click="respondToRest('SKIP_FOR_DAY')">
                    今天跳过
                  </FaButton>
                </template>
              </div>
              <div class="gap-4 grid lg:grid-cols-4 sm:grid-cols-2">
                <FaCard title="当前状态" description="服务重启后从 PostgreSQL 恢复。">
                  <p class="text-lg font-semibold">
                    {{ workdayRuntime ? runtimeStateLabels[workdayRuntime.state] : '尚未加载' }}
                  </p>
                  <p class="text-sm text-muted-foreground mt-1">
                    {{ workdayRuntime?.workDate ? `工作日 ${workdayRuntime.workDate}` : '当前没有活动工作日' }}
                  </p>
                </FaCard>
                <FaCard title="本轮专注" description="只累计明确在场时间。">
                  <p class="text-lg font-semibold">
                    {{ formatFocus(workdayRuntime?.focusSeconds ?? 0) }}
                  </p>
                  <p class="text-sm text-muted-foreground mt-1">
                    目标 {{ formatFocus(workdayRuntime?.focusTargetSeconds ?? model.focusMinutes * 60) }}
                  </p>
                </FaCard>
                <FaCard title="今日首次简报" description="占位后即使重启也不会重复。">
                  <p class="text-lg font-semibold">
                    {{ formatBriefStatus(workdayRuntime?.briefStatus ?? null) }}
                  </p>
                  <p class="text-sm text-muted-foreground mt-1">
                    成功、部分成功或取消后当天不重复
                  </p>
                </FaCard>
                <FaCard title="近 90 天" description="仅保存本地聚合，不含正文。">
                  <p class="text-lg font-semibold">
                    {{ workdayMetrics?.summary.activeWorkdays ?? 0 }} 个活跃工作日
                  </p>
                  <p class="text-sm text-muted-foreground mt-1">
                    专注 {{ formatFocus(workdayMetrics?.summary.focusSeconds ?? 0) }} · 休息 {{ workdayMetrics?.summary.restStartedCount ?? 0 }} 次
                  </p>
                </FaCard>
              </div>
              <div class="pt-6 border-t gap-4 grid">
                <div class="flex flex-wrap gap-3 items-center justify-between">
                  <div>
                    <h3 class="text-base font-semibold">
                      十四天私用观察
                    </h3>
                    <p class="text-sm text-muted-foreground mt-1">
                      十四天内至少八个计划工作日发生显式工作会话；每周误播报不超过两次，且无动作失败或设备重启。
                    </p>
                  </div>
                  <div class="flex flex-wrap gap-2">
                    <FaButton
                      v-if="!workdayPilot?.started"
                      type="button"
                      :loading="pilotAction === 'start'"
                      :disabled="Boolean(pilotAction)"
                      @click="startPilot"
                    >
                      开始十四天观察
                    </FaButton>
                    <template v-else>
                      <template v-if="!workdayPilot.windowComplete">
                        <FaButton type="button" variant="outline" :loading="pilotAction === 'mark'" :disabled="Boolean(pilotAction)" @click="markFalseTrigger">
                          标记误播报
                        </FaButton>
                        <FaButton type="button" variant="outline" :loading="pilotAction === 'undo'" :disabled="Boolean(pilotAction)" @click="undoFalseTrigger">
                          撤销一次
                        </FaButton>
                      </template>
                      <FaButton type="button" variant="outline" :loading="pilotAction === 'restart'" :disabled="Boolean(pilotAction)" @click="restartPilot">
                        重新开始
                      </FaButton>
                    </template>
                  </div>
                </div>
                <FaAlert
                  v-if="!workdayPilot?.started"
                  title="观察尚未开始"
                  description="开始操作只冻结本轮日期、时区和工作日规则；不会删除已有聚合，也不会启用身体动作。"
                />
                <template v-else>
                  <div class="gap-4 grid lg:grid-cols-4 sm:grid-cols-2">
                    <FaCard title="当前结论" :description="`${workdayPilot.startedOn} 至 ${workdayPilot.endsOn}`">
                      <p class="text-lg font-semibold">
                        {{ pilotStatusText() }}
                      </p>
                      <p class="text-sm text-muted-foreground mt-1">
                        已观察 {{ workdayPilot.elapsedDays }}/14 天
                      </p>
                    </FaCard>
                    <FaCard title="使用进度" :description="`已走过 ${workdayPilot.elapsedPlannedWorkdays}/${workdayPilot.plannedWorkdays} 个计划工作日`">
                      <p class="text-lg font-semibold">
                        {{ workdayPilot.activeWorkdays }}/{{ workdayPilot.activeWorkdayTarget }} 个活跃工作日
                      </p>
                      <p class="text-sm text-muted-foreground mt-1">
                        每日最多 {{ workdayPilot.maximumDailyBriefs }} 次简报
                      </p>
                    </FaCard>
                    <FaCard title="打扰质量" :description="`每周上限 ${workdayPilot.falseTriggerWeeklyLimit} 次`">
                      <p class="text-lg font-semibold">
                        {{ workdayPilot.firstWeekFalseTriggers }} / {{ workdayPilot.secondWeekFalseTriggers }} 次
                      </p>
                      <p class="text-sm text-muted-foreground mt-1">
                        日历失败 {{ workdayPilot.calendarFailureCount }} · 天气失败 {{ workdayPilot.weatherFailureCount }}
                      </p>
                    </FaCard>
                    <FaCard title="设备安全" description="安全拒绝只记录，不算动作失败。">
                      <p class="text-lg font-semibold">
                        失败 {{ workdayPilot.motionFailedCount }} · 重启 {{ workdayPilot.deviceRestartCount }}
                      </p>
                      <p class="text-sm text-muted-foreground mt-1">
                        动作安全拒绝 {{ workdayPilot.motionRejectedCount }} 次
                      </p>
                    </FaCard>
                  </div>
                  <FaAlert
                    :title="workdayPilot.status === 'PASS' ? '十四天门槛已通过' : workdayPilot.status === 'FAIL' ? '观察结束，但至少一项门槛未通过' : '正在收集本地匿名聚合'"
                    :description="workdayPilot.externalFailureAttributionComplete ? '日历和天气失败均保留受控来源与失败码；不保存日程、天气响应或播报正文。' : '存在未归属的外部服务失败，请先排查再判断门槛。'"
                  />
                </template>
              </div>
              <FaFormItem name="workdayEnabled" label="启用工作日模式" description="按所选工作日、时段和设备存在状态运行；默认关闭。">
                <FaSwitch v-model="model.workdayEnabled" />
              </FaFormItem>
              <div>
                <div class="text-sm font-medium mb-3">
                  工作日
                </div>
                <div class="gap-4 grid grid-cols-2 lg:grid-cols-7 sm:grid-cols-4">
                  <FaFormItem name="workdayMonday" label="周一">
                    <FaSwitch v-model="model.workdayMonday" />
                  </FaFormItem>
                  <FaFormItem name="workdayTuesday" label="周二">
                    <FaSwitch v-model="model.workdayTuesday" />
                  </FaFormItem>
                  <FaFormItem name="workdayWednesday" label="周三">
                    <FaSwitch v-model="model.workdayWednesday" />
                  </FaFormItem>
                  <FaFormItem name="workdayThursday" label="周四">
                    <FaSwitch v-model="model.workdayThursday" />
                  </FaFormItem>
                  <FaFormItem name="workdayFriday" label="周五">
                    <FaSwitch v-model="model.workdayFriday" />
                  </FaFormItem>
                  <FaFormItem name="workdaySaturday" label="周六">
                    <FaSwitch v-model="model.workdaySaturday" />
                  </FaFormItem>
                  <FaFormItem name="workdaySunday" label="周日">
                    <FaSwitch v-model="model.workdaySunday" />
                  </FaFormItem>
                </div>
              </div>
              <div class="gap-x-8 gap-y-6 grid grid-cols-1 lg:grid-cols-4 md:grid-cols-2">
                <FaFormItem name="workStart" label="工作开始" required>
                  <FaInput v-model="model.workStart" type="time" class="w-full" />
                </FaFormItem>
                <FaFormItem name="workEnd" label="工作结束" required>
                  <FaInput v-model="model.workEnd" type="time" class="w-full" />
                </FaFormItem>
                <FaFormItem name="focusMinutes" label="专注分钟" required description="15–180 分钟。">
                  <FaNumberField v-model="model.focusMinutes" :min="15" :max="180" :step="5" class="w-full" />
                </FaFormItem>
                <FaFormItem name="restMinutes" label="休息分钟" required description="5–60 分钟。">
                  <FaNumberField v-model="model.restMinutes" :min="5" :max="60" :step="5" class="w-full" />
                </FaFormItem>
                <FaFormItem name="absenceSuspendMinutes" label="离开后暂停（分钟）" required description="连续离开达到阈值才暂停节奏。">
                  <FaNumberField v-model="model.absenceSuspendMinutes" :min="1" :max="60" class="w-full" />
                </FaFormItem>
                <FaFormItem name="rearrivalMinutes" label="返场判定（分钟）" required description="超过此时长后回来才算一次返场。">
                  <FaNumberField v-model="model.rearrivalMinutes" :min="5" :max="240" :step="5" class="w-full" />
                </FaFormItem>
                <FaFormItem name="workdayZoneId" label="工作日时区" required description="使用 IANA 时区处理跨日。">
                  <FaInput v-model="model.workdayZoneId" class="w-full" />
                </FaFormItem>
              </div>
              <div class="pt-6 border-t gap-x-8 gap-y-6 grid grid-cols-1 md:grid-cols-3">
                <FaFormItem name="locationName" label="天气位置名称" description="仅在管理页面配置，例如“上海办公室”。">
                  <FaInput v-model="model.locationName" class="w-full" />
                </FaFormItem>
                <FaFormItem name="latitude" label="纬度" description="与经度同时填写；留空则不启用天气。">
                  <FaInput v-model="model.latitude" type="text" inputmode="decimal" placeholder="例如 31.2304" class="w-full" />
                </FaFormItem>
                <FaFormItem name="longitude" label="经度" description="数据源固定为 Open-Meteo。">
                  <FaInput v-model="model.longitude" type="text" inputmode="decimal" placeholder="例如 121.4737" class="w-full" />
                </FaFormItem>
              </div>
              <div class="pt-6 border-t gap-5 grid">
                <FaAlert
                  title="Open-Meteo 固定位置天气"
                  description="使用当前填写的位置可先做无保存测试；保存设置后再手动同步。服务端每小时刷新一次，只缓存当前及今明两天的必要字段。"
                />
                <div class="flex flex-wrap gap-3 items-center justify-between">
                  <div>
                    <div class="font-medium">
                      {{ weatherStatusText() }}
                      <span v-if="workdayWeather?.locationName" class="text-muted-foreground font-normal"> · {{ workdayWeather.locationName }}</span>
                    </div>
                    <div class="text-sm text-muted-foreground mt-1">
                      <span v-if="weatherFailureText()">{{ weatherFailureText() }} · </span>
                      <span v-if="workdayWeather?.lastSyncedAt">上次同步 {{ new Date(workdayWeather.lastSyncedAt).toLocaleString() }}</span>
                      <span v-else>尚无天气缓存</span>
                      <span v-if="workdayWeather?.cacheExpiresAt"> · 有效至 {{ new Date(workdayWeather.cacheExpiresAt).toLocaleString() }}</span>
                    </div>
                    <p v-if="workdayWeather?.summary" class="text-sm mt-2">
                      {{ workdayWeather.summary }}
                    </p>
                  </div>
                  <a href="https://open-meteo.com/" target="_blank" rel="noreferrer" class="text-sm text-primary underline-offset-4 hover:underline">
                    天气数据：Open-Meteo
                  </a>
                </div>
                <div class="flex flex-wrap gap-3">
                  <FaButton type="button" variant="outline" :loading="weatherAction === 'test'" @click="testWeather">
                    测试当前填写位置
                  </FaButton>
                  <FaButton
                    type="button"
                    :disabled="!workdayWeather?.configured"
                    :loading="weatherAction === 'sync'"
                    @click="syncWeather"
                  >
                    同步已保存位置
                  </FaButton>
                </div>
              </div>
              <div class="pt-6 border-t gap-5 grid">
                <FaAlert
                  title="iCloud 日历只读接入"
                  description="仅使用 CalDAV 读取你允许的日历；请使用 Apple Account 中已验证的电子邮件地址；App 专用密码加密保存且永不回显。"
                />
                <div class="flex flex-wrap gap-3 items-center justify-between">
                  <div>
                    <div class="font-medium">
                      {{ calendarStatusText() }}
                      <span v-if="icloudConnection?.account" class="text-muted-foreground font-normal"> · {{ icloudConnection.account }}</span>
                    </div>
                    <div class="text-sm text-muted-foreground mt-1">
                      <span v-if="calendarFailureText()">{{ calendarFailureText() }} · </span>
                      已缓存 {{ icloudConnection?.cachedEventCount ?? 0 }} 条日程
                      <span v-if="icloudConnection?.lastSyncedAt"> · 上次同步 {{ new Date(icloudConnection.lastSyncedAt).toLocaleString() }}</span>
                    </div>
                  </div>
                  <FaButton
                    v-if="icloudConnection?.configured"
                    type="button"
                    variant="destructive"
                    :loading="calendarAction === 'disconnect'"
                    @click="disconnectCalendar"
                  >
                    断开并清除本地数据
                  </FaButton>
                </div>
                <div class="gap-x-8 gap-y-5 grid grid-cols-1 md:grid-cols-2">
                  <FaFormItem name="icloudAccountEmail" :auto-bind="false" label="Apple 账号邮箱" description="必须是 Apple Account“登录与安全性”中已登记并验证的电子邮件地址。">
                    <FaInput v-model="icloudAccountEmail" type="email" autocomplete="username" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="icloudAppSpecificPassword" :auto-bind="false" label="App 专用密码" description="不是 Apple 账号登录密码；已连接时留空表示不更换。">
                    <FaInput
                      v-model="icloudAppSpecificPassword"
                      type="password"
                      autocomplete="new-password"
                      class="w-full"
                    />
                  </FaFormItem>
                </div>
                <div class="flex flex-wrap gap-3">
                  <FaButton type="button" variant="outline" :loading="calendarAction === 'test'" @click="testCalendar">
                    测试连接
                  </FaButton>
                  <FaButton type="button" :loading="calendarAction === 'connect'" @click="connectCalendar">
                    {{ icloudConnection?.configured ? '重新发现日历' : '连接并发现日历' }}
                  </FaButton>
                  <FaButton
                    type="button"
                    variant="outline"
                    :disabled="!icloudConnection?.configured"
                    :loading="calendarAction === 'sync'"
                    @click="syncCalendar"
                  >
                    同步未来 7 天
                  </FaButton>
                </div>
                <div v-if="icloudConnection?.calendars.length" class="gap-3 grid">
                  <div class="text-sm font-medium">
                    允许机器人读取的日历
                  </div>
                  <div class="gap-3 grid grid-cols-1 md:grid-cols-2">
                    <div
                      v-for="calendar in icloudConnection.calendars"
                      :key="calendar.id"
                      class="p-3 border rounded-md flex items-center justify-between"
                    >
                      <span class="text-sm">{{ calendar.displayName }}</span>
                      <FaSwitch v-model="calendar.allowed" />
                    </div>
                  </div>
                  <div>
                    <FaButton type="button" variant="outline" :loading="calendarAction === 'save'" @click="saveAllowedCalendars">
                      保存日历白名单
                    </FaButton>
                  </div>
                </div>
              </div>
            </div>
          </FaCard>

          <FaFixedBar position="bottom" class="flex justify-center md:col-span-2">
            <FaButton type="submit" form="interaction-settings-form" :loading="loading">
              保存交互与工作日设置
            </FaButton>
          </FaFixedBar>
        </FaForm>
      </FaLoading>
    </FaPageMain>
  </div>
</template>
