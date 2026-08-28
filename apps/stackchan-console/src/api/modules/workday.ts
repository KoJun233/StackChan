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
  focusSeconds: number
  restSkippedCount: number
  restSnoozedCount: number
  restStartedCount: number
  sessionEndCount: number
  sessionStartCount: number
}

export interface WorkdayDailyMetric extends Omit<WorkdayMetricSummary, 'activeWorkdays'> {
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

export function getWorkdayMetrics(deviceId: string, days = 90): Promise<WorkdayMetrics> {
  return apiJson(`/api/v1/workday/${encodeURIComponent(deviceId)}/metrics?days=${days}`)
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
