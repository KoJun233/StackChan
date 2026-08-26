import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  connectICloudCalendar,
  disconnectICloudCalendar,
  getICloudCalendarConnection,
  getWorkdayMetrics,
  getWorkdayRuntime,
  getWorkdaySettings,
  getWorkdayWeather,
  saveWorkdaySettings,
  syncICloudCalendar,
  syncWorkdayWeather,
  testICloudCalendarConnection,
  testWorkdayWeather,
  updateAllowedICloudCalendars,
} from './workday'

describe('workday settings API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads and saves the selected device settings', async () => {
    const deviceId = 'a88e4a94-8536-4fa1-91ed-8681b597429d'
    const input = {
      absenceSuspendMinutes: 10,
      enabled: true,
      focusMinutes: 50,
      latitude: 31.2304,
      locationName: '上海',
      longitude: 121.4737,
      rearrivalMinutes: 45,
      restMinutes: 10,
      workDaysMask: 31,
      workEnd: '18:00',
      workStart: '09:00',
      zoneId: 'Asia/Shanghai',
    }
    const response = { deviceId, ...input, updatedAt: null, weatherLocationConfigured: true }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(response), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify(response), {
        headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('document', { cookie: '' })
    vi.stubGlobal('fetch', fetchMock)

    await getWorkdaySettings(deviceId)
    await saveWorkdaySettings(deviceId, input)

    expect(fetchMock).toHaveBeenNthCalledWith(1, `/api/v1/workday/${deviceId}/settings`, expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, `/api/v1/workday/${deviceId}/settings`, expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify(input),
    }))
  })

  it('loads runtime and bounded local metrics for the selected device', async () => {
    const deviceId = 'a88e4a94-8536-4fa1-91ed-8681b597429d'
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ deviceId, state: 'OFF' }), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ deviceId, days: 90, daily: [] }), {
        headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('document', { cookie: '' })
    vi.stubGlobal('fetch', fetchMock)

    await getWorkdayRuntime(deviceId)
    await getWorkdayMetrics(deviceId)

    expect(fetchMock).toHaveBeenNthCalledWith(1, `/api/v1/workday/${deviceId}/runtime`, expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, `/api/v1/workday/${deviceId}/metrics?days=90`, expect.any(Object))
  })

  it('uses explicit read-only calendar management endpoints', async () => {
    const deviceId = 'a88e4a94-8536-4fa1-91ed-8681b597429d'
    const credentials = { accountEmail: 'me@icloud.com', appSpecificPassword: 'app-password' }
    const response = { configured: true, deviceId, calendars: [] }
    const fetchMock = vi.fn()
    for (let index = 0; index < 6; index += 1) {
      fetchMock.mockResolvedValueOnce(new Response(JSON.stringify(response), {
        headers: { 'Content-Type': 'application/json' },
      }))
    }
    vi.stubGlobal('document', { cookie: '' })
    vi.stubGlobal('fetch', fetchMock)

    await getICloudCalendarConnection(deviceId)
    await testICloudCalendarConnection(deviceId, credentials)
    await connectICloudCalendar(deviceId, credentials)
    await updateAllowedICloudCalendars(deviceId, ['calendar-id'])
    await syncICloudCalendar(deviceId)
    await disconnectICloudCalendar(deviceId)

    expect(fetchMock).toHaveBeenNthCalledWith(1, `/api/v1/workday/${deviceId}/calendar`, expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, `/api/v1/workday/${deviceId}/calendar/connection:test`, expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, `/api/v1/workday/${deviceId}/calendar/connection`, expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify(credentials),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, `/api/v1/workday/${deviceId}/calendar/calendars`, expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ allowedCalendarIds: ['calendar-id'] }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(5, `/api/v1/workday/${deviceId}/calendar/sync`, expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(6, `/api/v1/workday/${deviceId}/calendar/connection`, expect.objectContaining({ method: 'DELETE' }))
  })

  it('uses fixed-location read-only weather endpoints', async () => {
    const deviceId = 'a88e4a94-8536-4fa1-91ed-8681b597429d'
    const location = {
      latitude: 31.2304,
      locationName: '上海办公室',
      longitude: 121.4737,
      zoneId: 'Asia/Shanghai',
    }
    const response = { configured: true, deviceId, daily: [], fresh: true }
    const fetchMock = vi.fn()
    for (let index = 0; index < 3; index += 1) {
      fetchMock.mockResolvedValueOnce(new Response(JSON.stringify(response), {
        headers: { 'Content-Type': 'application/json' },
      }))
    }
    vi.stubGlobal('document', { cookie: '' })
    vi.stubGlobal('fetch', fetchMock)

    await getWorkdayWeather(deviceId)
    await testWorkdayWeather(deviceId, location)
    await syncWorkdayWeather(deviceId)

    expect(fetchMock).toHaveBeenNthCalledWith(1, `/api/v1/workday/${deviceId}/weather`, expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, `/api/v1/workday/${deviceId}/weather/connection:test`, expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(location),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, `/api/v1/workday/${deviceId}/weather/sync`, expect.objectContaining({ method: 'POST' }))
  })
})
