import { apiJson } from '../client'

export interface WorkdaySettings {
  absenceSuspendMinutes: number
  deviceId: string
  enabled: boolean
  focusMinutes: number
  latitude: number | null
  locationName: string
  longitude: number | null
  rearrivalMinutes: number
  restMinutes: number
  updatedAt: string | null
  weatherLocationConfigured: boolean
  workDaysMask: number
  workEnd: string
  workStart: string
  zoneId: string
}

export type WorkdayRuntimeState
  = | 'OFF'
    | 'STARTING'
    | 'ACTIVE_PRESENT'
    | 'ACTIVE_ABSENT'
    | 'REST_PROMPTED'
    | 'RESTING'
    | 'SKIPPED_FOR_DAY'

export type WorkdayBriefStatus = 'PENDING' | 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'CANCELLED'
export type WorkdayRestAction = 'START_REST' | 'SNOOZE' | 'SKIP_FOR_DAY'

export interface WorkdayRuntime {
  absenceStartedAt: string | null
  briefStatus: WorkdayBriefStatus | null
  deviceId: string
  focusSeconds: number
  focusTargetSeconds: number
  present: boolean
  restUntil: string | null
  snoozedUntil: string | null
  startedAt: string | null
  state: WorkdayRuntimeState
  stateChangedAt: string | null
  updatedAt: string
  workDate: string | null
}

export interface WorkdayMetricSummary {
  activeWorkdays: number
  briefCancelledCount: number
  briefFailedCount: number
  briefPartialCount: number
  briefSuccessCount: number
  calendarFailureCount: number
  deviceRestartCount: number
  falseTriggerCount: number
  focusSeconds: number
  motionFailedCount: number
  motionRejectedCount: number
  restSkippedCount: number
  restSnoozedCount: number
  restStartedCount: number
  sessionEndCount: number
  sessionStartCount: number
  weatherFailureCount: number
}

export interface WorkdayDailyMetric extends Omit<WorkdayMetricSummary, 'activeWorkdays'> {
  calendarLastFailureCode: string | null
  weatherLastFailureCode: string | null
  workDate: string
}

export interface WorkdayMetrics {
  daily: WorkdayDailyMetric[]
  days: number
  deviceId: string
  from: string
  summary: WorkdayMetricSummary
  to: string
}

export type WorkdayPilotStatus = 'NOT_STARTED' | 'COLLECTING' | 'PASS' | 'FAIL'

export interface WorkdayPilotReport {
  activeDaysPass: boolean
  activeWorkdays: number
  activeWorkdayTarget: number
  briefFrequencyPass: boolean
  calendarFailureCount: number
  deviceId: string
  deviceRestartCount: number
  elapsedDays: number
  elapsedPlannedWorkdays: number
  endsOn: string | null
  externalFailureAttributionComplete: boolean
  falseTriggerPass: boolean
  falseTriggerWeeklyLimit: number
  firstWeekFalseTriggers: number
  maximumDailyBriefs: number
  motionFailedCount: number
  motionRejectedCount: number
  motionSafetyPass: boolean
  plannedWorkdays: number
  secondWeekFalseTriggers: number
  stabilityPass: boolean
  started: boolean
  startedOn: string | null
  status: WorkdayPilotStatus
  updatedAt: string | null
  weatherFailureCount: number
  windowComplete: boolean
  workDaysMask: number | null
  zoneId: string | null
}

export type ICloudCalendarConnectionStatus = 'CONFIGURED' | 'CONNECTED' | 'AUTH_FAILED' | 'ERROR'
export type ICloudCalendarFailureCode
  = | 'AUTHENTICATION_FAILED'
    | 'DISCOVERY_FAILED'
    | 'SYNC_FAILED'
    | 'RESPONSE_TOO_LARGE'
    | 'INVALID_RESPONSE'

export interface ICloudCalendarItem {
  allowed: boolean
  displayName: string
  id: string
  updatedAt: string
}

export interface ICloudCalendarConnection {
  account: string | null
  appSpecificPasswordConfigured: boolean
  cacheExpiresAt: string | null
  cachedEventCount: number
  calendars: ICloudCalendarItem[]
  configured: boolean
  deviceId: string
  lastFailureCode: ICloudCalendarFailureCode | null
  lastSyncedAt: string | null
  lastTestedAt: string | null
  status: ICloudCalendarConnectionStatus | null
}

export interface ICloudCalendarConnectionInput {
  accountEmail: string
  appSpecificPassword: string
}

export interface ICloudCalendarConnectionTest {
  account: string
  calendarNames: string[]
  discoveredCalendarCount: number
  ok: boolean
}

export type WorkdayWeatherStatus = 'READY' | 'ERROR'
export type WorkdayWeatherFailureCode = 'REQUEST_FAILED' | 'RESPONSE_TOO_LARGE' | 'INVALID_RESPONSE'

export interface WorkdayWeatherCurrent {
  apparentTemperature: number
  description: string
  observedAt: string
  precipitation: number
  temperature: number
  weatherCode: number
}

export interface WorkdayWeatherDaily {
  apparentTemperatureMax: number
  apparentTemperatureMin: number
  date: string
  description: string
  precipitationProbabilityMax: number
  precipitationSum: number
  temperatureMax: number
  temperatureMin: number
  weatherCode: number
}

export interface WorkdayWeather {
  cacheExpiresAt: string | null
  configured: boolean
  current: WorkdayWeatherCurrent | null
  daily: WorkdayWeatherDaily[]
  deviceId: string
  fresh: boolean
  lastAttemptedAt: string | null
  lastFailureCode: WorkdayWeatherFailureCode | null
  lastSyncedAt: string | null
  locationName: string
  status: WorkdayWeatherStatus | null
  summary: string | null
  zoneId: string
}

export interface WorkdayWeatherLocationInput {
  latitude: number
  locationName: string
  longitude: number
  zoneId: string
}

export interface WorkdayWeatherTest {
  current: WorkdayWeatherCurrent
  daily: WorkdayWeatherDaily[]
  locationName: string
  observedAt: string
  ok: boolean
  summary: string
  zoneId: string
}

export type SaveWorkdaySettingsInput = Omit<
  WorkdaySettings,
  'deviceId' | 'updatedAt' | 'weatherLocationConfigured'
>

export function getWorkdaySettings(deviceId: string): Promise<WorkdaySettings> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/settings`)
}

export function saveWorkdaySettings(
  deviceId: string,
  input: SaveWorkdaySettingsInput,
): Promise<WorkdaySettings> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/settings`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function getWorkdayRuntime(deviceId: string): Promise<WorkdayRuntime> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/runtime`)
}

export function startWorkday(deviceId: string): Promise<WorkdayRuntime> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/runtime:start`, { method: 'POST' })
}

export function stopWorkday(deviceId: string): Promise<WorkdayRuntime> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/runtime:stop`, { method: 'POST' })
}

export function respondToWorkdayRest(
  deviceId: string,
  action: WorkdayRestAction,
): Promise<WorkdayRuntime> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/rest:respond`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action }),
  })
}

export function getWorkdayMetrics(deviceId: string, days = 90): Promise<WorkdayMetrics> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/metrics?days=${days}`)
}

export function getWorkdayPilot(deviceId: string): Promise<WorkdayPilotReport> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/pilot`)
}

export function startWorkdayPilot(deviceId: string): Promise<WorkdayPilotReport> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/pilot:start`, { method: 'POST' })
}

export function restartWorkdayPilot(deviceId: string): Promise<WorkdayPilotReport> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/pilot:restart`, { method: 'POST' })
}

export function markWorkdayFalseTrigger(deviceId: string): Promise<WorkdayPilotReport> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/pilot/false-trigger`, { method: 'POST' })
}

export function undoWorkdayFalseTrigger(deviceId: string): Promise<WorkdayPilotReport> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/pilot/false-trigger`, { method: 'DELETE' })
}

export function getICloudCalendarConnection(deviceId: string): Promise<ICloudCalendarConnection> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/calendar`)
}

export function testICloudCalendarConnection(
  deviceId: string,
  input: ICloudCalendarConnectionInput,
): Promise<ICloudCalendarConnectionTest> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/calendar/connection:test`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function connectICloudCalendar(
  deviceId: string,
  input: ICloudCalendarConnectionInput,
): Promise<ICloudCalendarConnection> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/calendar/connection`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateAllowedICloudCalendars(
  deviceId: string,
  allowedCalendarIds: string[],
): Promise<ICloudCalendarConnection> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/calendar/calendars`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ allowedCalendarIds }),
  })
}

export function syncICloudCalendar(deviceId: string): Promise<ICloudCalendarConnection> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/calendar/sync`, { method: 'POST' })
}

export function disconnectICloudCalendar(deviceId: string): Promise<void> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/calendar/connection`, { method: 'DELETE' })
}

export function getWorkdayWeather(deviceId: string): Promise<WorkdayWeather> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/weather`)
}

export function testWorkdayWeather(
  deviceId: string,
  input: WorkdayWeatherLocationInput,
): Promise<WorkdayWeatherTest> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/weather/connection:test`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function syncWorkdayWeather(deviceId: string): Promise<WorkdayWeather> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/weather/sync`, { method: 'POST' })
}
