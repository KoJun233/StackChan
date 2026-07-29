import { createApp, defineComponent, h, nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

const personalDataApi = vi.hoisted(() => ({
  deletePersonalDataConversation: vi.fn(),
  deletePersonalDataMessage: vi.fn(),
  exportPersonalDataConversations: vi.fn(),
  getBackupStatus: vi.fn(),
  getPersonalDataMessages: vi.fn(),
  listPersonalDataConversations: vi.fn(),
}))
const deviceApi = vi.hoisted(() => ({ listDevices: vi.fn() }))

vi.mock('@/api/modules/personalData', () => personalDataApi)
vi.mock('@/api/modules/devices', () => deviceApi)

vi.mock('@fantastic-admin/components', () => {
  const container = defineComponent({
    props: { description: String, title: String },
    setup(props, { slots }) {
      return () => h('section', [
        props.title ? h('h2', props.title) : null,
        props.description ? h('p', props.description) : null,
        slots.action?.(),
        slots.default?.(),
      ])
    },
  })
  return {
    FaAlert: container,
    FaButton: defineComponent({
      emits: ['click'],
      setup(_, { emit, slots }) {
        return () => h('button', { onClick: () => emit('click') }, slots.default?.())
      },
    }),
    FaCard: container,
    FaDropdown: container,
    FaEmpty: container,
    FaIcon: defineComponent({ setup: () => () => h('i') }),
    FaInput: defineComponent({
      props: { modelValue: String },
      emits: ['update:modelValue'],
      setup(props, { emit }) {
        return () => h('input', {
          value: props.modelValue,
          onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
        })
      },
    }),
    FaLabel: container,
    FaPageMain: container,
    FaPagination: defineComponent({ setup: () => () => h('nav') }),
    FaSearchBar: container,
    FaSelect: defineComponent({
      props: { modelValue: String, options: Array },
      setup: () => () => h('select'),
    }),
    FaTable: defineComponent({ setup: () => () => h('div') }),
    useFaModal: () => ({ confirm: vi.fn() }),
    useFaToast: () => ({ error: vi.fn(), success: vi.fn() }),
  }
})

import PersonalDataPage from './index.vue'

describe('personal data management page', () => {
  afterEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  it('loads conversations, devices and safe backup status without exposing a restore action', async () => {
    deviceApi.listDevices.mockResolvedValue([{ id: 'device-1', displayName: '书桌 StackChan' }])
    personalDataApi.listPersonalDataConversations.mockResolvedValue({
      list: [{
        id: 'conversation-1',
        title: '旅行计划',
        deviceId: 'device-1',
        deviceName: '书桌 StackChan',
        messageCount: 2,
        createdAt: '2026-07-29T12:00:00Z',
        updatedAt: '2026-07-29T12:00:00Z',
      }],
      total: 1,
    })
    personalDataApi.getBackupStatus.mockResolvedValue({
      available: true,
      lastAttemptAt: '2026-07-29T12:00:00Z',
      lastSuccessfulBackupAt: '2026-07-29T12:00:00Z',
      lastFailureAt: null,
      lastFailureCode: null,
      lastRestoreVerificationAt: '2026-07-29T12:01:00Z',
      lastRestoreVerificationSuccessful: true,
      lastRestoreVerificationFailureCode: null,
      dailyBackupCount: 1,
      weeklyBackupCount: 1,
      dailyRetention: 7,
      weeklyRetention: 4,
      storageBytes: 4096,
    })

    const host = document.createElement('div')
    document.body.append(host)
    const app = createApp(PersonalDataPage)
    app.mount(host)
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()

    expect(personalDataApi.listPersonalDataConversations).toHaveBeenCalledWith(expect.objectContaining({ from: 0, limit: 10 }))
    expect(deviceApi.listDevices).toHaveBeenCalled()
    expect(personalDataApi.getBackupStatus).toHaveBeenCalled()
    expect(host.textContent).toContain('最近成功备份')
    expect(host.textContent).toContain('日备份 1/7 · 周备份 1/4')
    expect(host.textContent).toContain('网页不提供恢复操作')
    expect(host.textContent).not.toContain('恢复备份')

    app.unmount()
  })
})
