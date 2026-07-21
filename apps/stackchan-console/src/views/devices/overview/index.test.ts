import { createApp, defineComponent, h } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

const deviceApi = vi.hoisted(() => ({
  listDevices: vi.fn(),
  stopDeviceMotion: vi.fn(),
}))

vi.mock('@/api/modules/devices', () => deviceApi)

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

import DeviceOverview from './index.vue'

describe('device overview command availability', () => {
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

    const heartbeatOnlyButton = container.querySelector<HTMLButtonElement>('[data-device-id="heartbeat-only"] button')
    const connectedButton = container.querySelector<HTMLButtonElement>('[data-device-id="connected"] button')
    expect(heartbeatOnlyButton?.disabled).toBe(true)
    expect(connectedButton?.disabled).toBe(false)
  })
})
