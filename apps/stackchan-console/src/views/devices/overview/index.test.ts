import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h } from 'vue'

import DeviceOverview from './index.vue'

const deviceApi = vi.hoisted(() => ({
  getDeviceVoiceQuality: vi.fn(),
  listDevices: vi.fn(),
  listDeviceVoiceTurns: vi.fn(),
  stopDeviceMotion: vi.fn(),
}))

function quality(deviceId: string, totalAttempts = 0) {
  const metric = {
    measurementSource: 'DEVICE_MONOTONIC',
    applicableSamples: 0,
    missingSamples: 0,
    minimumSamples: 30,
    p50Ms: null,
    p95Ms: null,
  }
  return {
    deviceId,
    from: '2026-10-12T00:00:00Z',
    to: '2026-10-13T00:00:00Z',
    totalAttempts,
    successfulTurns: totalAttempts,
    failedTurns: 0,
    cancelledTurns: 0,
    incompleteTurns: 0,
    failureCounts: {},
    ordinaryChatSuccessfulTurns: totalAttempts,
    excludedLatencyTurns: 0,
    endpointToFirstSound: metric,
    endpointToLocalFeedback: metric,
    physicalObservationRequired: true,
  }
}

const memoryApi = vi.hoisted(() => ({
  getMemoryUsage: vi.fn().mockResolvedValue({ turnId: '', memories: [] }),
}))

vi.mock('@/api/modules/devices', () => deviceApi)
vi.mock('@/api/modules/personaMemory', () => memoryApi)

vi.mock('@fantastic-admin/components', () => {
  const passthrough = defineComponent({
    setup(_, { slots }) {
      return () => h('div', [slots.title?.(), slots.header?.(), slots.default?.()])
    },
  })
  return {
    FaButton: defineComponent({
      inheritAttrs: false,
      props: { disabled: Boolean, loading: Boolean },
      emits: ['click'],
      setup(props, { attrs, emit, slots }) {
        return () => h('button', {
          ...attrs,
          disabled: props.disabled,
          onClick: () => emit('click'),
        }, slots.default?.())
      },
    }),
    FaCard: passthrough,
    FaAlert: defineComponent({
      props: { title: String, description: String },
      setup: props => () => h('div', [props.title, props.description]),
    }),
    FaEmpty: passthrough,
    FaPageHeader: passthrough,
    FaPageMain: passthrough,
    FaTable: defineComponent({
      props: { data: { type: Array, default: () => [] } },
      setup(props, { slots }) {
        return () => h('div', (props.data as any[]).map(row => h(
          'div',
          { 'data-device-id': row.id },
          slots['cell-actions']?.({ row: { original: row } }),
        )))
      },
    }),
    useFaToast: () => ({ error: vi.fn(), success: vi.fn() }),
  }
})

describe('device overview command availability', () => {
  beforeEach(() => {
    deviceApi.getDeviceVoiceQuality.mockImplementation((deviceId: string) => Promise.resolve(quality(deviceId)))
  })

  it('keeps the newest robot diagnostics when an older request finishes last', async () => {
    let resolveOld!: (value: unknown[]) => void
    const oldResponse = new Promise<unknown[]>((resolve) => {
      resolveOld = resolve
    })
    let resolveOldQuality!: (value: ReturnType<typeof quality>) => void
    const oldQualityResponse = new Promise<ReturnType<typeof quality>>((resolve) => {
      resolveOldQuality = resolve
    })
    deviceApi.listDevices.mockResolvedValue([
      { id: 'a', displayName: '机器人 A', online: true, commandAvailable: true },
      { id: 'b', displayName: '机器人 B', online: true, commandAvailable: true },
    ])
    const turn = (turnId: string) => ({
      turnId,
      status: 'COMPLETED',
      failureCode: null,
      startedAt: '2026-09-20T00:00:00Z',
      updatedAt: '2026-09-20T00:00:01Z',
      events: [],
    })
    deviceApi.listDeviceVoiceTurns.mockImplementation((deviceId: string) => deviceId === 'a' ? oldResponse : Promise.resolve([turn('new-turn')]))
    deviceApi.getDeviceVoiceQuality.mockImplementation((deviceId: string) => deviceId === 'a' ? oldQualityResponse : Promise.resolve(quality('b', 12)))
    const container = document.createElement('div')
    const app = createApp(DeviceOverview)
    app.mount(container)
    await vi.waitFor(() => expect(container.querySelectorAll('[data-device-id]')).toHaveLength(2))
    const clickDiagnostics = (id: string) => Array.from(container.querySelectorAll<HTMLButtonElement>(`[data-device-id="${id}"] button`)).find(button => button.textContent?.includes('交互诊断'))?.click()
    clickDiagnostics('a')
    clickDiagnostics('b')
    await vi.waitFor(() => expect(container.querySelector('[data-turn-id="new-turn"]')).not.toBeNull())
    resolveOld([turn('old-turn')])
    resolveOldQuality(quality('a', 99))
    await vi.waitFor(() => expect(memoryApi.getMemoryUsage).toHaveBeenCalledWith('new-turn'))
    expect(container.querySelector('[data-turn-id="old-turn"]')).toBeNull()
    expect(container.textContent).toContain('机器人 B 的最近语音回合')
    expect(container.querySelector('[data-voice-quality]')?.textContent).toContain('总尝试 12')
    expect(container.querySelector('[data-voice-quality]')?.textContent).not.toContain('总尝试 99')
    app.unmount()
  })

  it('shows a failed diagnostic request as unavailable, not empty', async () => {
    deviceApi.listDevices.mockResolvedValue([{ id: 'a', displayName: '机器人 A', online: true, commandAvailable: true }])
    deviceApi.listDeviceVoiceTurns.mockRejectedValue(new Error('诊断暂不可用'))
    const container = document.createElement('div')
    const app = createApp(DeviceOverview)
    app.mount(container)
    await vi.waitFor(() => expect(container.querySelector('[data-device-id]')).not.toBeNull())
    Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find(button => button.textContent?.includes('交互诊断'))?.click()
    await vi.waitFor(() => expect(container.textContent).toContain('诊断暂不可用'))
    expect(container.textContent).not.toContain('暂无语音回合诊断数据')
    expect(container.querySelector('[data-voice-quality]')?.textContent).toContain('总尝试 0')
    app.unmount()
  })

  it('keeps legacy detail visible when the separate quality summary is unavailable', async () => {
    deviceApi.listDevices.mockResolvedValue([{ id: 'a', displayName: '机器人 A', online: true, commandAvailable: true }])
    deviceApi.listDeviceVoiceTurns.mockResolvedValue([{
      turnId: 'legacy-turn',
      status: 'COMPLETED',
      failureCode: null,
      startedAt: '2026-10-13T00:00:00Z',
      updatedAt: '2026-10-13T00:00:01Z',
      events: [],
    }])
    deviceApi.getDeviceVoiceQuality.mockRejectedValue(new Error('汇总暂不可用'))
    const container = document.createElement('div')
    const app = createApp(DeviceOverview)
    app.mount(container)
    await vi.waitFor(() => expect(container.querySelector('[data-device-id]')).not.toBeNull())
    Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find(button => button.textContent?.includes('交互诊断'))?.click()
    await vi.waitFor(() => expect(container.querySelector('[data-turn-id="legacy-turn"]')).not.toBeNull())
    expect(container.textContent).toContain('汇总暂不可用')
    expect(container.textContent).toContain('未知：缺少设备采音端点记录')
    expect(container.textContent).not.toContain('暂无语音回合诊断数据')
    app.unmount()
  })

  it('shows failures and missing stages while withholding percentiles below thirty samples', async () => {
    deviceApi.listDevices.mockResolvedValue([{ id: 'a', displayName: '机器人 A', online: true, commandAvailable: true }])
    deviceApi.listDeviceVoiceTurns.mockResolvedValue([])
    const summary = quality('a', 34)
    deviceApi.getDeviceVoiceQuality.mockResolvedValue({
      ...summary,
      successfulTurns: 31,
      failedTurns: 3,
      incompleteTurns: 2,
      failureCounts: { ASR_UNAVAILABLE: 3 },
      endpointToFirstSound: { ...summary.endpointToFirstSound, applicableSamples: 29, missingSamples: 2, p50Ms: 2000, p95Ms: 4000 },
    })
    const container = document.createElement('div')
    const app = createApp(DeviceOverview)
    app.mount(container)
    await vi.waitFor(() => expect(container.querySelector('[data-device-id]')).not.toBeNull())
    Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find(button => button.textContent?.includes('交互诊断'))?.click()
    await vi.waitFor(() => expect(container.querySelector('[data-voice-quality]')?.textContent).toContain('总尝试 34'))
    const summaryText = container.querySelector('[data-voice-quality]')?.textContent
    expect(summaryText).toContain('失败 3')
    expect(summaryText).toContain('适用成功样本 29')
    expect(summaryText).toContain('缺失 2')
    expect(summaryText).toContain('样本不足，暂不能判断')
    expect(summaryText).toContain('ASR_UNAVAILABLE 3 次')
    expect(summaryText).toContain('尚无实体测量')
    expect(summaryText).not.toContain('p50 2.00')
    app.unmount()
  })
  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('enables safety stop only for a device with an active command channel', async () => {
    deviceApi.listDevices.mockResolvedValue([
      {
        id: 'heartbeat-only',
        displayName: 'Heartbeat only',
        firmwareVersion: '1.0.0',
        safetyState: 'motion_disabled',
        lastSeenAt: '2026-07-18T12:00:00Z',
        online: true,
        commandAvailable: false,
      },
      {
        id: 'connected',
        displayName: 'Connected',
        firmwareVersion: '1.0.0',
        safetyState: 'motion_disabled',
        lastSeenAt: '2026-07-18T12:00:00Z',
        online: true,
        commandAvailable: true,
      },
    ])
    const container = document.createElement('div')
    document.body.append(container)

    createApp(DeviceOverview).mount(container)
    await vi.waitFor(() => expect(deviceApi.listDevices).toHaveBeenCalledOnce())
    await vi.waitFor(() => expect(container.querySelectorAll('[data-device-id]')).toHaveLength(2))

    const heartbeatOnlyButton = [...container.querySelectorAll<HTMLButtonElement>('[data-device-id="heartbeat-only"] button')].find(button => button.textContent?.includes('安全停止'))
    const connectedButton = [...container.querySelectorAll<HTMLButtonElement>('[data-device-id="connected"] button')].find(button => button.textContent?.includes('安全停止'))
    expect(heartbeatOnlyButton?.disabled).toBe(true)
    expect(connectedButton?.disabled).toBe(false)
  })

  it('shows a privacy-safe recent voice turn timeline', async () => {
    deviceApi.listDevices.mockResolvedValue([{
      id: 'connected',
      displayName: 'Connected',
      firmwareVersion: '1.0.0',
      safetyState: 'motion_disabled',
      lastSeenAt: '2026-07-18T12:00:00Z',
      online: true,
      commandAvailable: true,
    }])
    deviceApi.listDeviceVoiceTurns.mockResolvedValue([{
      turnId: '550e8400-e29b-41d4-a716-446655440000',
      status: 'COMPLETED',
      failureCode: null,
      startedAt: '2026-07-18T12:00:00Z',
      updatedAt: '2026-07-18T12:00:02Z',
      events: [
        {
          stage: 'FOLLOW_UP_LISTENING',
          source: 'DEVICE',
          occurredAt: '2026-07-18T12:00:00Z',
          elapsedMs: 0,
          failureCode: null,
        },
        {
          stage: 'FOLLOW_UP_TIMEOUT',
          source: 'DEVICE',
          occurredAt: '2026-07-18T12:00:02Z',
          elapsedMs: 1998,
          failureCode: null,
        },
        {
          stage: 'CONVERSATION_ENDED',
          source: 'DEVICE',
          occurredAt: '2026-07-18T12:00:02Z',
          elapsedMs: 1999,
          failureCode: null,
        },
        {
          stage: 'LISTENING_RESUMED',
          source: 'DEVICE',
          occurredAt: '2026-07-18T12:00:02Z',
          elapsedMs: 2000,
          failureCode: null,
        },
      ],
    }])
    memoryApi.getMemoryUsage.mockResolvedValue({
      turnId: '550e8400-e29b-41d4-a716-446655440000',
      memories: [{
        memoryId: 'memory-id',
        title: '称呼偏好',
        topicKey: '称呼偏好',
        scopeType: 'GLOBAL',
        source: 'USER_ENTERED',
        sourceDetail: '由管理员在控制台明确添加',
      }],
    })
    const container = document.createElement('div')
    document.body.append(container)

    createApp(DeviceOverview).mount(container)
    await vi.waitFor(() => expect(container.querySelectorAll('[data-device-id]')).toHaveLength(1))
    const buttons = container.querySelectorAll<HTMLButtonElement>('[data-device-id="connected"] button')
    Array.from(buttons).find(button => button.textContent?.includes('交互诊断'))?.click()

    await vi.waitFor(() => expect(deviceApi.listDeviceVoiceTurns).toHaveBeenCalledWith('connected'))
    await vi.waitFor(() => expect(container.querySelector('[data-turn-id]')?.textContent).toContain('恢复聆听'))
    expect(container.querySelector('[data-turn-id]')?.textContent).toContain('跟进聆听')
    expect(container.querySelector('[data-turn-id]')?.textContent).toContain('跟进超时')
    expect(container.querySelector('[data-turn-id]')?.textContent).toContain('会话结束')
    expect(container.textContent).toContain('不保存音频、识别文本或机器人回复')
    expect(container.textContent).toContain('本回合引用的长期记忆')
    expect(container.textContent).toContain('使用记录本身不复制记忆正文')
  })

  it('labels a touch-started cancelled turn without treating it as a failure', async () => {
    deviceApi.listDevices.mockResolvedValue([{
      id: 'connected',
      displayName: 'Connected',
      firmwareVersion: '1.0.0',
      safetyState: 'motion_disabled',
      lastSeenAt: '2026-07-18T12:00:00Z',
      online: true,
      commandAvailable: true,
    }])
    deviceApi.listDeviceVoiceTurns.mockResolvedValue([{
      turnId: '550e8400-e29b-41d4-a716-446655440001',
      status: 'CANCELLED',
      failureCode: null,
      startedAt: '2026-07-18T12:00:00Z',
      updatedAt: '2026-07-18T12:00:01Z',
      events: [
        {
          stage: 'TOUCH_STARTED',
          source: 'DEVICE',
          occurredAt: '2026-07-18T12:00:00Z',
          elapsedMs: 0,
          failureCode: null,
        },
        {
          stage: 'CANCELLED',
          source: 'DEVICE',
          occurredAt: '2026-07-18T12:00:01Z',
          elapsedMs: 1000,
          failureCode: null,
        },
      ],
    }])
    const container = document.createElement('div')
    document.body.append(container)

    createApp(DeviceOverview).mount(container)
    await vi.waitFor(() => expect(container.querySelectorAll('[data-device-id]')).toHaveLength(1))
    const buttons = container.querySelectorAll<HTMLButtonElement>('[data-device-id="connected"] button')
    Array.from(buttons).find(button => button.textContent?.includes('交互诊断'))?.click()

    await vi.waitFor(() => expect(container.querySelector('[data-turn-id]')?.textContent).toContain('已取消'))
    expect(container.querySelector('[data-turn-id]')?.textContent).toContain('触摸发起')
    expect(container.querySelector('[data-turn-id]')?.textContent).toContain('回合已取消')
  })
})
