import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick } from 'vue'
import { useCompanionSettings } from './useCompanionSettings'

const api = vi.hoisted(() => ({
  listDevices: vi.fn(),
  saveInteractionSettings: vi.fn(),
  saveWorkdaySettings: vi.fn(),
  getICloudCalendarEvents: vi.fn(),
  getWorkdaySettings: vi.fn(),
  getInteractionSettings: vi.fn(),
  getWorkdayRuntime: vi.fn(),
  getWorkdayMetrics: vi.fn(),
  getWorkdayPilot: vi.fn(),
  getWorkdayWeather: vi.fn(),
  getICloudCalendarConnection: vi.fn(),
  startWorkday: vi.fn(),
  startWorkdayPilot: vi.fn(),
  confirm: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}))
vi.mock('vue-router', async original => ({
  ...await original<typeof import('vue-router')>(),
  useRoute: () => ({ query: {} }),
  onBeforeRouteLeave: vi.fn(),
  onBeforeRouteUpdate: vi.fn(),
}))
vi.mock('@/api/modules/devices', async original => ({
  ...await original<typeof import('@/api/modules/devices')>(),
  listDevices: api.listDevices,
}))
vi.mock('@/api/modules/interactions', async original => ({
  ...await original<typeof import('@/api/modules/interactions')>(),
  saveInteractionSettings: api.saveInteractionSettings,
  getInteractionSettings: api.getInteractionSettings,
}))
vi.mock('@/api/modules/workday', async original => ({
  ...await original<typeof import('@/api/modules/workday')>(),
  saveWorkdaySettings: api.saveWorkdaySettings,
  getICloudCalendarEvents: api.getICloudCalendarEvents,
  getWorkdaySettings: api.getWorkdaySettings,
  getWorkdayRuntime: api.getWorkdayRuntime,
  getWorkdayMetrics: api.getWorkdayMetrics,
  getWorkdayPilot: api.getWorkdayPilot,
  getWorkdayWeather: api.getWorkdayWeather,
  getICloudCalendarConnection: api.getICloudCalendarConnection,
  startWorkday: api.startWorkday,
  startWorkdayPilot: api.startWorkdayPilot,
}))
vi.mock('@fantastic-admin/components', async original => ({
  ...await original<typeof import('@fantastic-admin/components')>(),
  useFaToast: () => ({ success: api.success, error: api.error }),
  useFaModal: () => ({ confirm: api.confirm }),
}))
const cleanups: Array<() => void> = []
const deviceA = '550e8400-e29b-41d4-a716-446655440000'
const deviceB = '550e8400-e29b-41d4-a716-446655440001'
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
async function mountSettings(scope: 'care' | 'workday') {
  let state!: ReturnType<typeof useCompanionSettings>
  const validatedForm = defineComponent({
    setup(_, { expose }) {
      expose({ validate: async () => ({ valid: true }) })
      return () => h('form')
    },
  })
  const app = createApp(defineComponent({
    setup() {
      state = useCompanionSettings(scope)
      return () => h(validatedForm, { ref: 'settingsForm' })
    },
  }))
  app.mount(document.createElement('div'))
  cleanups.push(() => app.unmount())
  await nextTick()
  state.model.value.deviceId = deviceA
  state.settingsReady.value = true
  state.savedSnapshot.value = JSON.stringify(state.model.value)
  return state
}
describe('independent companion settings', () => {
  it('preserves a successful work action when the metrics refresh fails', async () => {
    const state = await mountSettings('workday')
    api.startWorkday.mockResolvedValue({ state: 'STARTING', deviceId: deviceA })
    api.getWorkdayMetrics.mockRejectedValue(new Error('metrics unavailable'))
    await state.beginWorkday()
    expect(state.workdayRuntime.value?.state).toBe('STARTING')
    expect(api.success).toHaveBeenCalledWith('工作状态已更新')
    expect(api.error).not.toHaveBeenCalled()
    expect(state.auxiliaryErrors.value).toContain('工作指标未获取')
    expect(state.workdayMetrics.value).toBeNull()
  })

  it('preserves a successful observation action when the metrics refresh fails', async () => {
    const state = await mountSettings('workday')
    api.startWorkdayPilot.mockResolvedValue({ status: 'COLLECTING', started: true })
    api.getWorkdayMetrics.mockRejectedValue(new Error('metrics unavailable'))
    state.startPilot()
    await api.confirm.mock.calls[0][0].onConfirm()
    expect(api.startWorkdayPilot).toHaveBeenCalledWith(deviceA)
    expect(state.workdayPilot.value?.status).toBe('COLLECTING')
    expect(api.success).toHaveBeenCalledWith('十四天观察已开始')
    expect(api.error).not.toHaveBeenCalled()
    expect(state.auxiliaryErrors.value).toContain('工作指标未获取')
  })

  it('does not redirect a pending confirmation to a newly selected robot', async () => {
    const state = await mountSettings('workday')
    state.devices.value = [{ id: deviceA, displayName: '书桌机器人' }] as typeof state.devices.value
    state.startPilot()
    const confirmation = api.confirm.mock.calls[0][0]
    expect(confirmation.content).toContain('书桌机器人')
    state.model.value.deviceId = deviceB
    await confirmation.onConfirm()
    expect(api.startWorkdayPilot).not.toHaveBeenCalled()
    expect(api.error).toHaveBeenCalledWith('操作未执行', expect.any(Object))
  })

  it('loads and saves work rules despite failed observation, calendar and weather endpoints', async () => {
    const state = await mountSettings('workday')
    api.getWorkdaySettings.mockResolvedValue({
      enabled: true,
      workDaysMask: 31,
      workStart: '09:00',
      workEnd: '18:00',
      focusMinutes: 50,
      restMinutes: 10,
      absenceSuspendMinutes: 10,
      rearrivalMinutes: 45,
      locationName: '',
      latitude: null,
      longitude: null,
      zoneId: 'Asia/Shanghai',
    })
    for (const request of [api.getWorkdayRuntime, api.getWorkdayMetrics, api.getWorkdayPilot, api.getWorkdayWeather, api.getICloudCalendarConnection]) {
      request.mockRejectedValue(new Error('unavailable'))
    }
    await state.loadSettings(deviceA)
    await vi.waitFor(() => expect(state.auxiliaryErrors.value).toHaveLength(5))
    expect(state.settingsReady.value).toBe(true)
    expect(state.settingsError.value).toBe('')
    expect(api.getInteractionSettings).not.toHaveBeenCalled()
    state.model.value.focusMinutes = 40
    await state.submit(state.model.value)
    expect(api.saveWorkdaySettings).toHaveBeenCalledWith(deviceA, expect.objectContaining({ focusMinutes: 40 }))
    expect(api.success).toHaveBeenCalledWith('工作规则已保存')
    expect(state.dirty.value).toBe(false)
    expect(api.getWorkdayWeather).toHaveBeenCalledOnce()
  })
  beforeEach(() => {
    vi.clearAllMocks()
    api.listDevices.mockResolvedValue([])
    api.saveInteractionSettings.mockResolvedValue({})
    api.saveWorkdaySettings.mockResolvedValue({})
  })
  afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()))
  it('saves care without validating or writing hidden workday fields', async () => {
    const state = await mountSettings('care')
    state.model.value.workStart = ''
    state.model.value.volumePercent = 65
    await state.submit(state.model.value)
    expect(api.saveInteractionSettings).toHaveBeenCalledWith(deviceA, expect.objectContaining({ volumePercent: 65 }))
    expect(api.saveWorkdaySettings).not.toHaveBeenCalled()
    expect(state.dirty.value).toBe(false)
  })
  it('saves work rules independently of hidden care fields', async () => {
    const state = await mountSettings('workday')
    state.model.value.proactiveContent = ''
    state.model.value.focusMinutes = 45
    await state.submit(state.model.value)
    expect(api.saveWorkdaySettings).toHaveBeenCalledWith(deviceA, expect.objectContaining({ focusMinutes: 45 }))
    expect(api.saveInteractionSettings).not.toHaveBeenCalled()
    expect(state.dirty.value).toBe(false)
  })
  it('waits for persistence before leaving and preserves edits when saving fails', async () => {
    const state = await mountSettings('care')
    state.model.value.volumePercent = 65
    const pending = deferred<object>()
    api.saveInteractionSettings.mockReturnValueOnce(pending.promise)
    const leave = state.confirmLeave()
    const save = state.saveAndLeave()
    await vi.waitFor(() => expect(api.saveInteractionSettings).toHaveBeenCalledOnce())
    expect(state.leaveDialog.value).toBe(true)
    expect(state.dirty.value).toBe(true)
    pending.resolve({})
    await save
    expect(await leave).toBe(true)
    state.model.value.volumePercent = 70
    api.saveInteractionSettings.mockRejectedValueOnce(new Error('offline'))
    const stay = state.confirmLeave()
    await state.saveAndLeave()
    expect(state.leaveDialog.value).toBe(true)
    expect(state.dirty.value).toBe(true)
    state.finishLeave(false)
    expect(await stay).toBe(false)
  })
  it('ignores calendar responses from the previously selected robot', async () => {
    const state = await mountSettings('workday')
    const previous = deferred<object[]>()
    api.getICloudCalendarEvents.mockReturnValueOnce(previous.promise).mockResolvedValueOnce([{ id: 'new-device' }])
    const oldRequest = state.loadCalendarEvents(deviceA, true)
    state.model.value.deviceId = deviceB
    await state.loadCalendarEvents(deviceB, true)
    previous.resolve([{ id: 'old-device' }])
    await oldRequest
    expect(state.calendarEvents.value).toEqual([{ id: 'new-device' }])
    expect(state.calendarEventsLoading.value).toBe(false)
  })
})
