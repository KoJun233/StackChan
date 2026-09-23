import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick } from 'vue'
import Persona from './index.vue'

const api = vi.hoisted(() => ({ roles: vi.fn(), devices: vi.fn(), active: vi.fn(), switch: vi.fn(), confirm: vi.fn(), success: vi.fn(), error: vi.fn(), push: vi.fn() }))
vi.mock('@/api/modules/devices', () => ({ listDevices: api.devices }))
vi.mock('@/api/modules/roles', () => ({ listRoles: api.roles, getDeviceActiveRole: api.active, setDeviceActiveRole: api.switch, archiveRole: vi.fn(), restoreRole: vi.fn(), deleteRole: vi.fn() }))
vi.mock('vue-router', async original => ({ ...await original<typeof import('vue-router')>(), useRouter: () => ({ push: api.push }) }))
vi.mock('@fantastic-admin/components', () => {
  const box = defineComponent({
    props: { title: String, description: String },
    setup: (props, { slots }) => () => h('section', [props.title, props.description, slots.default?.(), slots.footer?.(), slots.action?.()]),
  })
  return {
    FaPageHeader: box,
    FaPageMain: box,
    FaCard: box,
    FaAlert: box,
    FaTag: box,
    FaIcon: box,
    FaCollapsible: box,
    FaButton: defineComponent({
      props: { disabled: Boolean, loading: Boolean },
      emits: ['click'],
      setup: (props, { slots, emit }) => () => h('button', { disabled: props.disabled || props.loading, onClick: () => emit('click') }, slots.default?.()),
    }),
    FaSelect: defineComponent({
      props: { modelValue: String, options: Array, disabled: Boolean },
      emits: ['update:modelValue'],
      setup: (props, { emit }) => () => h('select', {
        value: props.modelValue,
        disabled: props.disabled,
        onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value),
      }, (props.options as Array<{ value: string, label: string }>).map(option => h('option', { value: option.value }, option.label))),
    }),
    useFaModal: () => ({ confirm: api.confirm }),
    useFaToast: () => ({ success: api.success, error: api.error }),
  }
})

const cleanups: Array<() => void> = []
async function mountPersona() {
  const container = document.createElement('div')
  const app = createApp(Persona)
  app.mount(container)
  cleanups.push(() => app.unmount())
  await vi.waitFor(() => expect(container.textContent).toContain('当前：小暖'))
  return container
}
function button(container: HTMLElement, text: string) {
  const result = Array.from(container.querySelectorAll('button')).find(item => item.textContent?.trim() === text)
  expect(result, `button ${text}`).toBeDefined()
  return result!
}
async function stagePartner(container: HTMLElement) {
  const select = container.querySelector('select')!
  select.value = 'partner-b'
  select.dispatchEvent(new Event('change'))
  await nextTick()
}

describe('viewing and assigning partners', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.roles.mockResolvedValue([
      { id: 'partner-a', name: '小暖', defaultRole: true, tone: 'WARM', replyLength: 'SHORT' },
      { id: 'partner-b', name: '小芽', defaultRole: false, tone: 'LIVELY', replyLength: 'BALANCED' },
    ])
    api.devices.mockResolvedValue([{ id: 'robot-a', displayName: '书桌机器人', online: true }])
    api.active.mockResolvedValue({ id: 'partner-a' })
    api.switch.mockResolvedValue({ id: 'partner-b' })
  })
  afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()))

  it('does not assign a device when viewing a chat or staging a selection; confirms the named target first', async () => {
    const container = await mountPersona()
    button(container, '和它聊聊').click()
    expect(api.push).toHaveBeenCalledWith({ path: '/companion/chat', query: { roleId: 'partner-a' } })
    await stagePartner(container)
    expect(api.switch).not.toHaveBeenCalled()
    expect(container.textContent).toContain('当前：小暖')
    button(container, '确认切换').click()
    const confirmation = api.confirm.mock.calls[0][0]
    expect(confirmation.content).toContain('书桌机器人')
    expect(confirmation.content).toContain('从「小暖」切换为「小芽」')
    expect(api.switch).not.toHaveBeenCalled()
    await confirmation.onConfirm()
    expect(api.switch).toHaveBeenCalledExactlyOnceWith('robot-a', 'partner-b')
    expect(container.textContent).toContain('当前：小芽')
  })

  it('uses the server result and prevents concurrent reassignment while pending', async () => {
    let finish!: (result: { id: string }) => void
    api.switch.mockImplementation(() => new Promise((resolve) => {
      finish = resolve
    }))
    const container = await mountPersona()
    await stagePartner(container)
    button(container, '确认切换').click()
    const confirmation = api.confirm.mock.calls[0][0]
    const pending = confirmation.onConfirm()
    await nextTick()
    expect(container.querySelector('select')?.disabled).toBe(true)
    await confirmation.onConfirm()
    expect(api.switch).toHaveBeenCalledOnce()
    finish({ id: 'partner-a' })
    await pending
    expect(container.textContent).toContain('当前：小暖')
    expect(container.querySelector('select')?.value).toBe('partner-a')
  })

  it('retains the authoritative current partner after a rejected switch', async () => {
    api.switch.mockRejectedValue(new Error('设备忙碌'))
    const container = await mountPersona()
    await stagePartner(container)
    button(container, '确认切换').click()
    await api.confirm.mock.calls[0][0].onConfirm()
    expect(container.textContent).toContain('当前：小暖')
    expect(api.success).not.toHaveBeenCalled()
    expect(api.error).toHaveBeenCalledWith('切换失败', expect.objectContaining({ description: '设备忙碌' }))
  })
})
