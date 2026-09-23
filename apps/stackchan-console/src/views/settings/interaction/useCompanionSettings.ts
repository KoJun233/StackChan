import type { Ref } from 'vue'
import type { BodyMotion, Device } from '@/api/modules/devices'
import type { MissedReminderPolicy, ProactivePause, ProactiveTopicCooldown, SaveInteractionSettingsInput } from '@/api/modules/interactions'
import type { ICloudCalendarConnection, ICloudCalendarEvent, SaveWorkdaySettingsInput, WorkdayMetrics, WorkdayPilotReport, WorkdayRestAction, WorkdayRuntime, WorkdayWeather, WorkdayWeatherLocationInput } from '@/api/modules/workday'
import { toTypedSchema } from '@vee-validate/zod'
import { useNow } from '@vueuse/core'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import * as z from 'zod'
import { calibrateDeviceBody, configureDeviceBodyMotion, listDevices, playDeviceBodyMotion, stopDeviceMotion } from '@/api/modules/devices'
import { getInteractionSettings, getProactivePause, listProactiveTopics, pauseProactive, resumeProactive, resumeProactiveTopic, saveInteractionSettings, stopDeviceAudio } from '@/api/modules/interactions'
import { currentTimeZone } from '@/api/modules/reminders'
import { getDeviceActiveRole, listRoles } from '@/api/modules/roles'
import { connectICloudCalendar, disconnectICloudCalendar, getICloudCalendarConnection, getICloudCalendarEvents, getWorkdayMetrics, getWorkdayPilot, getWorkdayRuntime, getWorkdaySettings, getWorkdayWeather, markWorkdayFalseTrigger, respondToWorkdayRest, restartWorkdayPilot, saveWorkdaySettings, startWorkday, startWorkdayPilot, stopWorkday, syncICloudCalendar, syncWorkdayWeather, testICloudCalendarConnection, testWorkdayWeather, undoWorkdayFalseTrigger, updateAllowedICloudCalendars } from '@/api/modules/workday'

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
type SettingsSection = 'care' | 'connections' | 'device' | 'pilot' | 'workday-overview' | 'workday-rules'
export interface SettingsFormHandle {
  validate: () => Promise<{ valid: boolean }>
}

export function useCompanionSettings(scope: 'care' | 'workday', providedForm?: Readonly<Ref<SettingsFormHandle | null>>) {
  const devices = ref<Device[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const settingsReady = ref(false)
  const settingsError = ref('')
  const auxiliaryErrors = ref<string[]>([])
  const savedSnapshot = ref('')
  const formRef = providedForm ?? useTemplateRef<SettingsFormHandle>('settingsForm')

  const leaveDialog = ref(false)
  let resolveLeave: ((leave: boolean) => void) | undefined
  const stopping = ref(false)
  const topicCooldowns = ref<ProactiveTopicCooldown[]>([])
  const topicRoleId = ref('')
  const proactivePause = ref<ProactivePause | null>(null)
  const pauseAction = ref(false)
  const pauseNow = useNow({ interval: 30000 })
  const proactivePaused = computed(() => !!proactivePause.value?.pausedUntil
    && new Date(proactivePause.value.pausedUntil).getTime() > pauseNow.value.getTime())
  const topicRoleOptions = ref<{
    label: string
    value: string
  }[]>([])
  const topicsLoading = ref(false)
  const topicsError = ref('')
  let topicsRequest = 0
  let settingsRequest = 0
  let calendarRequest = 0
  let extrasRequest = 0
  const proactiveNextAt = ref<string | null>(null)
  const silentPresenceNextAt = ref<string | null>(null)
  const resumingTopic = ref('')
  const model = ref<InteractionFormModel>(defaults())
  const dirty = computed(() => settingsReady.value && savedSnapshot.value !== JSON.stringify(model.value))
  const workdayRuntime = ref<WorkdayRuntime | null>(null)
  const workdayMetrics = ref<WorkdayMetrics | null>(null)
  const workdayPilot = ref<WorkdayPilotReport | null>(null)
  const workdayWeather = ref<WorkdayWeather | null>(null)
  const icloudConnection = ref<ICloudCalendarConnection | null>(null)
  const calendarEvents = ref<ICloudCalendarEvent[]>([])
  const calendarEventsLoading = ref(false)
  const calendarEventsError = ref('')
  const icloudAccountEmail = ref('')
  const icloudAppSpecificPassword = ref('')
  const calendarAction = ref<'connect' | 'disconnect' | 'save' | 'sync' | 'test' | ''>('')
  const weatherAction = ref<'sync' | 'test' | ''>('')
  const workdayAction = ref<'start' | 'stop' | WorkdayRestAction | ''>('')
  const pilotAction = ref<'start' | 'restart' | 'mark' | 'undo' | ''>('')
  const bodyAction = ref<'calibrate' | 'disable' | 'enable' | BodyMotion | ''>('')
  const targetBusy = computed(() => saving.value || stopping.value || pauseAction.value || !!resumingTopic.value
    || !!calendarAction.value || !!weatherAction.value || !!workdayAction.value || !!pilotAction.value || !!bodyAction.value)
  const route = useRoute()
  const activeSection = ref<SettingsSection>('device')
  const isWorkdayPage = computed(() => scope === 'workday')
  const pageTitle = computed(() => isWorkdayPage.value ? '工作陪伴' : '陪伴方式')
  const pageDescription = computed(() => isWorkdayPage.value
    ? '工作模式按需开启，聊天和记忆不依赖它。日历、天气与工程诊断可以稍后配置。'
    : '让陪伴按你喜欢的方式发生。')
  const sectionTabs = computed(() => isWorkdayPage.value
    ? [
        { label: '当前概览', value: 'workday-overview', icon: 'i-ri:focus-2-line' },
        { label: '工作规则', value: 'workday-rules', icon: 'i-ri:calendar-schedule-line' },
        { label: '日历与天气', value: 'connections', icon: 'i-ri:cloud-line' },
        { label: '工程诊断', value: 'pilot', icon: 'i-ri:line-chart-line' },
      ]
    : [
        { label: '设备交互', value: 'device', icon: 'i-ri:robot-2-line' },
        { label: '打扰策略', value: 'care', icon: 'i-ri:notification-off-line' },
      ])
  watch(isWorkdayPage, (workday) => {
    activeSection.value = workday ? 'workday-overview' : route.query.tab === 'device' ? 'device' : 'care'
  }, { immediate: true })
  const bodyMotions: {
    label: string
    value: BodyMotion
  }[] = [
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
  const missedPolicyOptions: {
    label: string
    value: MissedReminderPolicy
  }[] = [
    { label: '恢复在线后立即播放', value: 'PLAY_NOW' },
    { label: '按指定分钟数稍后重试', value: 'SNOOZE' },
    { label: '跳过错过的这一轮', value: 'SKIP' },
  ]
  const careSchema = z.object({
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
    proactiveMinIntervalMinutes: z.number().int().min(60).max(1440),
    proactivePersonalizationEnabled: z.boolean(),
    proactiveDailyLimit: z.number().int().min(1).max(3),
    proactiveContent: z.string().trim().min(1, '请输入主动问候内容').max(500),
    silentPresenceEnabled: z.boolean(),
  }).passthrough()
  const workdaySchema = z.object({
    deviceId: z.string().uuid('请选择目标机器人'),
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
  }).passthrough().superRefine((values, context) => {
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
  })
  const validationSchema = toTypedSchema(scope === 'care' ? careSchema : workdaySchema)
  function validateCoordinate(value: string, minimum: number, maximum: number, message: string, path: 'latitude' | 'longitude', context: z.RefinementCtx) {
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
      proactiveMinIntervalMinutes: 60,
      proactivePersonalizationEnabled: false,
      proactiveDailyLimit: 3,
      proactiveContent: '你好呀，记得休息一下，也可以和我聊聊天。',
      silentPresenceEnabled: false,
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
      const requested = String(route.query.deviceId ?? '')
      model.value.deviceId = devices.value.find(device => device.id === requested)?.id ?? devices.value[0]?.id ?? ''
      if (model.value.deviceId) {
        await loadSettings(model.value.deviceId)
      }
    }
    catch (error) {
      settingsError.value = error instanceof Error ? error.message : '无法读取机器人列表。'
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
    const request = ++settingsRequest
    ++topicsRequest
    ++calendarRequest
    ++extrasRequest
    loading.value = true
    settingsReady.value = false
    settingsError.value = ''
    auxiliaryErrors.value = []
    topicRoleId.value = ''
    proactivePause.value = null
    topicCooldowns.value = []
    topicsError.value = ''
    topicsLoading.value = false
    workdayRuntime.value = null
    workdayMetrics.value = null
    workdayPilot.value = null
    workdayWeather.value = null
    icloudConnection.value = null
    calendarEvents.value = []
    calendarEventsLoading.value = false
    calendarEventsError.value = ''
    icloudAccountEmail.value = ''
    icloudAppSpecificPassword.value = ''
    try {
      if (isWorkdayPage.value) {
        const workday = await getWorkdaySettings(deviceId)
        if (request !== settingsRequest || model.value.deviceId !== deviceId) {
          return
        }
        model.value = { ...defaults(), deviceId, workdayEnabled: workday.enabled, workdayMonday: includesWorkday(workday.workDaysMask, 0), workdayTuesday: includesWorkday(workday.workDaysMask, 1), workdayWednesday: includesWorkday(workday.workDaysMask, 2), workdayThursday: includesWorkday(workday.workDaysMask, 3), workdayFriday: includesWorkday(workday.workDaysMask, 4), workdaySaturday: includesWorkday(workday.workDaysMask, 5), workdaySunday: includesWorkday(workday.workDaysMask, 6), workStart: workday.workStart.slice(0, 5), workEnd: workday.workEnd.slice(0, 5), focusMinutes: workday.focusMinutes, restMinutes: workday.restMinutes, absenceSuspendMinutes: workday.absenceSuspendMinutes, rearrivalMinutes: workday.rearrivalMinutes, locationName: workday.locationName, latitude: workday.latitude?.toString() ?? '', longitude: workday.longitude?.toString() ?? '', workdayZoneId: workday.zoneId }
      }
      else {
        const settings = await getInteractionSettings(deviceId)
        if (request !== settingsRequest || model.value.deviceId !== deviceId) {
          return
        }
        model.value = { ...defaults(), deviceId, volumePercent: settings.volumePercent, nightMode: settings.nightMode, continuousConversationEnabled: settings.continuousConversationEnabled, followUpWindowSeconds: settings.followUpWindowSeconds, dndEnabled: settings.dndEnabled, dndStart: settings.dndStart.slice(0, 5), dndEnd: settings.dndEnd.slice(0, 5), zoneId: settings.zoneId, missedReminderPolicy: settings.missedReminderPolicy, missedSnoozeMinutes: settings.missedSnoozeMinutes, proactiveEnabled: settings.proactiveEnabled, proactiveStart: settings.proactiveStart.slice(0, 5), proactiveEnd: settings.proactiveEnd.slice(0, 5), proactiveMinIntervalMinutes: settings.proactiveMinIntervalMinutes, proactivePersonalizationEnabled: settings.proactivePersonalizationEnabled ?? false, proactiveDailyLimit: settings.proactiveDailyLimit, proactiveContent: settings.proactiveContent, silentPresenceEnabled: settings.silentPresenceEnabled ?? false }
        proactiveNextAt.value = settings.proactiveNextAt ?? null
        silentPresenceNextAt.value = settings.silentPresenceNextAt ?? null
      }
      savedSnapshot.value = JSON.stringify(model.value)
      settingsReady.value = true
      if (isWorkdayPage.value) {
        void loadWorkdayExtras(deviceId, request)
      }
      else {
        void loadActivePartner(deviceId, request)
      }
    }
    catch (error) {
      if (request !== settingsRequest) {
        return
      }
      settingsError.value = error instanceof Error ? error.message : '无法读取设置。'
    }
    finally {
      if (request === settingsRequest) {
        loading.value = false
      }
    }
  }
  async function loadActivePartner(deviceId: string, request: number) {
    topicsLoading.value = true
    try {
      const [active, roles] = await Promise.all([getDeviceActiveRole(deviceId), listRoles()])
      if (request !== settingsRequest) {
        return
      }
      topicRoleOptions.value = roles.filter(role => !role.archivedAt).map(role => ({ label: role.name, value: role.id }))
      const requested = String(route.query.roleId ?? '')
      topicRoleId.value = topicRoleOptions.value.some(role => role.value === requested) ? requested : active.id
      await loadTopics()
    }
    catch {
      if (request === settingsRequest) {
        topicsError.value = '伙伴状态未获取，请重新加载。'
      }
    }
    finally {
      if (request === settingsRequest) {
        topicsLoading.value = false
      }
    }
  }
  async function loadWorkdayExtras(deviceId = model.value.deviceId, request = settingsRequest) {
    const extra = ++extrasRequest
    auxiliaryErrors.value = []
    async function part<T>(label: string, operation: () => Promise<T>, assign: (value: T) => void) {
      try {
        const value = await operation()
        if (extra === extrasRequest && request === settingsRequest && deviceId === model.value.deviceId) {
          assign(value)
        }
      }
      catch {
        if (extra === extrasRequest && request === settingsRequest) {
          auxiliaryErrors.value.push(`${label}未获取`)
        }
      }
    }
    await Promise.all([
      part('工作状态', () => getWorkdayRuntime(deviceId), (value) => { workdayRuntime.value = value }),
      part('工作指标', () => getWorkdayMetrics(deviceId), (value) => { workdayMetrics.value = value }),
      part('工程观察', () => getWorkdayPilot(deviceId), (value) => { workdayPilot.value = value }),
      part('天气', () => getWorkdayWeather(deviceId), (value) => { workdayWeather.value = value }),
      part('日历', () => getICloudCalendarConnection(deviceId), (value) => {
        icloudConnection.value = value
        void loadCalendarEvents(deviceId, value.configured)
      }),
    ])
  }
  async function loadTopics() {
    const deviceId = model.value.deviceId
    const roleId = topicRoleId.value
    const request = ++topicsRequest
    topicCooldowns.value = []
    proactivePause.value = null
    topicsError.value = ''
    if (!deviceId || !roleId) {
      topicsLoading.value = false
      return
    }
    topicsLoading.value = true
    try {
      const [topics, pause] = await Promise.all([listProactiveTopics(deviceId, roleId), getProactivePause(deviceId, roleId)])
      if (request === topicsRequest && deviceId === model.value.deviceId && roleId === topicRoleId.value) {
        topicCooldowns.value = topics
        proactivePause.value = pause
      }
    }
    catch (error) {
      if (request === topicsRequest) {
        topicsError.value = error instanceof Error ? error.message : '无法读取话题。'
      }
    }
    finally {
      if (request === topicsRequest) {
        topicsLoading.value = false
      }
    }
  }
  async function updateProactivePause(action: 'resume' | 'today' | 'hour') {
    const deviceId = model.value.deviceId
    const roleId = topicRoleId.value
    if (!deviceId || !roleId || topicsLoading.value || pauseAction.value || resumingTopic.value) {
      return
    }
    pauseAction.value = true
    try {
      if (action === 'resume') {
        await resumeProactive(deviceId, roleId)
      }
      else {
        await pauseProactive(deviceId, roleId, action === 'today' ? null : 60)
      }
      if (deviceId === model.value.deviceId && roleId === topicRoleId.value) {
        await loadTopics()
      }
      useFaToast().success(action === 'resume' ? '已解除该伙伴的主动暂停' : '已暂停该伙伴的主动聊天')
    }
    catch (error) {
      useFaToast().error('操作失败', { description: error instanceof Error ? error.message : '无法更新主动暂停。' })
    }
    finally {
      pauseAction.value = false
    }
  }
  async function resumeTopic(topicKey: string) {
    const deviceId = model.value.deviceId
    const roleId = topicRoleId.value
    if (!deviceId || !roleId || topicsLoading.value || resumingTopic.value || pauseAction.value) {
      return
    }
    resumingTopic.value = topicKey
    try {
      await resumeProactiveTopic(deviceId, topicKey, roleId)
      if (deviceId === model.value.deviceId && roleId === topicRoleId.value) {
        await loadTopics()
      }
      useFaToast().success('已恢复该伙伴的话题')
    }
    catch (error) {
      useFaToast().error('操作失败', { description: error instanceof Error ? error.message : '无法解除主题冷却。' })
    }
    finally {
      resumingTopic.value = ''
    }
  }
  async function submit(input: unknown) {
    if (!settingsReady.value || saving.value) {
      return
    }
    const parsed = (isWorkdayPage.value ? workdaySchema : careSchema).safeParse(input)
    if (!parsed.success) {
      return
    }
    const values = { ...defaults(), ...parsed.data }
    const snapshot = JSON.stringify(model.value)
    saving.value = true
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
        silentPresenceEnabled: values.silentPresenceEnabled,
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
      if (isWorkdayPage.value) {
        await saveWorkdaySettings(values.deviceId, workdayInput)
      }
      else {
        const saved = await saveInteractionSettings(values.deviceId, interactionInput)
        proactiveNextAt.value = saved.proactiveNextAt ?? null
        silentPresenceNextAt.value = saved.silentPresenceNextAt ?? null
      }
      savedSnapshot.value = snapshot
      useFaToast().success(isWorkdayPage.value ? '工作规则已保存' : '陪伴设置已保存')
    }
    catch (error) {
      useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存交互设置。' })
    }
    finally {
      saving.value = false
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
  function weatherIcon(code?: number) {
    if (code === undefined) {
      return 'i-ri:cloud-line'
    }
    if (code <= 1) {
      return 'i-ri:sun-line'
    }
    if (code <= 3) {
      return 'i-ri:cloudy-2-line'
    }
    if (code <= 48) {
      return 'i-ri:mist-line'
    }
    if (code <= 67 || (code >= 80 && code <= 82)) {
      return 'i-ri:rainy-line'
    }
    if (code <= 77 || code === 85 || code === 86) {
      return 'i-ri:snowy-line'
    }
    return 'i-ri:thunderstorms-line'
  }
  function weatherStatusIcon() {
    if (workdayWeather.value?.status === 'ERROR') {
      return 'i-ri:error-warning-line'
    }
    return workdayWeather.value?.fresh ? 'i-ri:checkbox-circle-line' : 'i-ri:time-line'
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
  async function loadCalendarEvents(deviceId = model.value.deviceId, configured = icloudConnection.value?.configured) {
    calendarEventsError.value = ''
    const request = ++calendarRequest
    calendarEvents.value = []
    calendarEventsLoading.value = false
    if (!deviceId || !configured) {
      calendarEvents.value = []
      return
    }
    calendarEventsLoading.value = true
    try {
      const from = new Date()
      const to = new Date(from.getTime() + 7 * 24 * 60 * 60 * 1000)
      const events = await getICloudCalendarEvents(deviceId, from.toISOString(), to.toISOString())
      if (request === calendarRequest && deviceId === model.value.deviceId) {
        calendarEvents.value = events
      }
    }
    catch (error) {
      if (request !== calendarRequest || deviceId !== model.value.deviceId) {
        return
      }
      calendarEvents.value = []
      calendarEventsError.value = '缓存日程未获取，请重试。'
      useFaToast().error('日程加载失败', {
        description: error instanceof Error ? error.message : '暂时无法读取已缓存日程。',
      })
    }
    finally {
      if (request === calendarRequest) {
        calendarEventsLoading.value = false
      }
    }
  }
  function calendarEventDate(event: ICloudCalendarEvent) {
    return new Intl.DateTimeFormat('zh-CN', {
      month: 'long',
      day: 'numeric',
      weekday: 'short',
      timeZone: model.value.workdayZoneId,
    }).format(new Date(event.startsAt))
  }
  function calendarEventTime(event: ICloudCalendarEvent) {
    if (event.allDay) {
      return '全天'
    }
    const formatter = new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
      timeZone: model.value.workdayZoneId,
    })
    return `${formatter.format(new Date(event.startsAt))}–${formatter.format(new Date(event.endsAt))}`
  }
  async function runCalendarAction(action: 'connect' | 'disconnect' | 'save' | 'sync' | 'test', operation: () => Promise<void>) {
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
      await loadCalendarEvents()
      useFaToast().success('只读日程已同步', {
        description: `缓存 ${icloudConnection.value.cachedEventCount} 条未来 7 天日程，24 小时后自动过期。`,
      })
    })
  }
  async function disconnectCalendar() {
    await runCalendarAction('disconnect', async () => {
      await disconnectICloudCalendar(model.value.deviceId)
      icloudConnection.value = await getICloudCalendarConnection(model.value.deviceId)
      calendarEvents.value = []
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
  async function runWorkdayAction(action: 'start' | 'stop' | WorkdayRestAction, operation: () => Promise<WorkdayRuntime>) {
    if (!model.value.deviceId || workdayAction.value) {
      return
    }
    workdayAction.value = action
    try {
      workdayRuntime.value = await operation()
      useFaToast().success('工作状态已更新')
      await refreshActionMetrics()
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
  async function runPilotAction(action: typeof pilotAction.value, operation: () => Promise<WorkdayPilotReport>, message: string) {
    if (!model.value.deviceId || pilotAction.value) {
      return
    }
    pilotAction.value = action
    try {
      workdayPilot.value = await operation()
      useFaToast().success(message)
      await refreshActionMetrics()
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
  async function refreshActionMetrics() {
    workdayMetrics.value = null
    try {
      workdayMetrics.value = await getWorkdayMetrics(model.value.deviceId)
      auxiliaryErrors.value = auxiliaryErrors.value.filter(error => error !== '工作指标未获取')
    }
    catch {
      if (!auxiliaryErrors.value.includes('工作指标未获取')) {
        auxiliaryErrors.value.push('工作指标未获取')
      }
    }
  }
  function confirmPilotAction(action: 'start' | 'restart' | 'mark', title: string, content: string, confirmButtonText: string, operation: (deviceId: string) => Promise<WorkdayPilotReport>, message: string) {
    const deviceId = model.value.deviceId
    if (!deviceId || targetBusy.value) {
      return
    }
    const deviceName = selectedDevice.value?.displayName ?? deviceId
    useFaModal().confirm({
      title,
      content: `目标机器人：「${deviceName}」。${content}`,
      confirmButtonText,
      onConfirm: async () => {
        if (deviceId !== model.value.deviceId || targetBusy.value) {
          useFaToast().error('操作未执行', { description: '目标或操作状态已变化，请重新确认。' })
          return
        }
        await runPilotAction(action, () => operation(deviceId), message)
      },
    })
  }
  function startPilot() {
    confirmPilotAction('start', '开始十四天私用观察？', '将从设备当前时区的今天开始，并冻结本轮工作日规则快照。建议在当天首次实际使用前开始；同一天已有的聚合计数仍会保留。', '开始观察', startWorkdayPilot, '十四天观察已开始')
  }
  function restartPilot() {
    confirmPilotAction('restart', '重新开始十四天观察？', '历史聚合不会删除，但本轮门槛窗口会从今天重新计算。', '重新开始', restartWorkdayPilot, '十四天观察已重新开始')
  }
  function markFalseTrigger() {
    confirmPilotAction('mark', '标记一次误播报？', '只增加今天的匿名计数，不保存播报正文或原因；标错后可以撤销一次。', '确认标记', markWorkdayFalseTrigger, '已记录一次误播报')
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
  function confirmLeave() {
    if (!dirty.value) {
      return Promise.resolve(true)
    }
    if (resolveLeave) {
      return Promise.resolve(false)
    }
    leaveDialog.value = true
    return new Promise<boolean>((resolve) => {
      resolveLeave = resolve
    })
  }
  function finishLeave(leave: boolean) {
    resolveLeave?.(leave)
    resolveLeave = undefined
    leaveDialog.value = false
  }
  async function saveAndLeave() {
    const result = await formRef.value?.validate()
    if (!result?.valid) {
      return
    }
    await submit(structuredClone(toRaw(model.value)))
    if (!dirty.value) {
      finishLeave(true)
    }
  }
  async function changeDevice(value: unknown) {
    const deviceId = String(value)
    if (deviceId === model.value.deviceId || targetBusy.value) {
      return
    }
    if (!await confirmLeave()) {
      return
    }
    model.value.deviceId = deviceId
    await loadSettings(deviceId)
  }
  onBeforeRouteLeave(async () => !targetBusy.value && await confirmLeave())
  onBeforeRouteUpdate(async (to) => {
    if (targetBusy.value) {
      return false
    }
    const deviceId = String(to.query.deviceId ?? '')
    if (deviceId && deviceId !== model.value.deviceId && devices.value.some(device => device.id === deviceId)) {
      return await confirmLeave()
    }
    return true
  })
  watch(() => route.query.deviceId, async (value) => {
    const deviceId = String(value ?? '')
    if (deviceId !== model.value.deviceId && devices.value.some(device => device.id === deviceId)) {
      model.value.deviceId = deviceId
      await loadSettings(deviceId)
    }
  })
  watch(() => route.query.tab, (value) => {
    if (!isWorkdayPage.value && (value === 'device' || value === 'care')) {
      activeSection.value = value
    }
  })
  watch(() => route.query.roleId, async (value) => {
    const roleId = String(value ?? '')
    if (!isWorkdayPage.value && topicRoleOptions.value.some(role => role.value === roleId)) {
      topicRoleId.value = roleId
      await loadTopics()
    }
  })
  onBeforeUnmount(() => {
    ++settingsRequest
    ++topicsRequest
    ++calendarRequest
    ++extrasRequest
    finishLeave(false)
  })
  function warnBeforeUnload(event: BeforeUnloadEvent) {
    if (dirty.value) {
      event.preventDefault()
      event.returnValue = ''
    }
  }
  onMounted(() => window.addEventListener('beforeunload', warnBeforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', warnBeforeUnload))
  onMounted(loadDevices)
  return {
    targetBusy,
    devices,
    loading,
    saving,
    settingsReady,
    settingsError,
    auxiliaryErrors,
    savedSnapshot,
    formRef,
    dirty,
    leaveDialog,
    resolveLeave,
    stopping,
    topicCooldowns,
    topicRoleId,
    proactivePause,
    pauseAction,
    pauseNow,
    proactivePaused,
    topicRoleOptions,
    topicsLoading,
    topicsError,
    topicsRequest,
    settingsRequest,
    proactiveNextAt,
    silentPresenceNextAt,
    resumingTopic,
    model,
    workdayRuntime,
    workdayMetrics,
    workdayPilot,
    workdayWeather,
    icloudConnection,
    calendarEvents,
    calendarEventsLoading,
    calendarEventsError,
    icloudAccountEmail,
    icloudAppSpecificPassword,
    calendarAction,
    weatherAction,
    workdayAction,
    pilotAction,
    bodyAction,
    route,
    activeSection,
    isWorkdayPage,
    pageTitle,
    pageDescription,
    sectionTabs,
    bodyMotions,
    runtimeStateLabels,
    briefStatusLabels,
    deviceOptions,
    selectedDevice,
    missedPolicyOptions,
    validationSchema,
    validateCoordinate,
    defaults,
    loadDevices,
    loadSettings,
    loadActivePartner,
    loadWorkdayExtras,
    loadTopics,
    updateProactivePause,
    resumeTopic,
    submit,
    includesWorkday,
    workDaysMask,
    optionalCoordinate,
    weatherLocationInput,
    weatherStatusText,
    weatherIcon,
    weatherStatusIcon,
    weatherFailureText,
    runWeatherAction,
    testWeather,
    syncWeather,
    formatFocus,
    formatBriefStatus,
    calendarCredentials,
    calendarStatusText,
    calendarFailureText,
    loadCalendarEvents,
    calendarEventDate,
    calendarEventTime,
    runCalendarAction,
    testCalendar,
    connectCalendar,
    saveAllowedCalendars,
    syncCalendar,
    disconnectCalendar,
    stopAudio,
    runWorkdayAction,
    beginWorkday,
    endWorkday,
    respondToRest,
    runPilotAction,
    startPilot,
    restartPilot,
    markFalseTrigger,
    undoFalseTrigger,
    pilotStatusText,
    runBodyAction,
    calibrateBody,
    configureBody,
    playBody,
    stopBody,
    confirmLeave,
    finishLeave,
    saveAndLeave,
    changeDevice,
    warnBeforeUnload,
  }
}
