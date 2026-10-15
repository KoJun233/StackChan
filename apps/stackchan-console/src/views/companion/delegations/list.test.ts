import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import DelegationList from './list.vue'

const api = vi.hoisted(() => ({ listDelegations: vi.fn(), listDelegationReviewReady: vi.fn(), getBarkSettings: vi.fn(), updateBarkSettings: vi.fn(), listDevices: vi.fn(), listRoles: vi.fn() }))
vi.mock('@/api/modules/delegations', async original => ({ ...await original<typeof import('@/api/modules/delegations')>(), ...api }))
vi.mock('@/api/modules/devices', async original => ({ ...await original<typeof import('@/api/modules/devices')>(), listDevices: api.listDevices }))
vi.mock('@/api/modules/roles', async original => ({ ...await original<typeof import('@/api/modules/roles')>(), listRoles: api.listRoles }))
vi.mock('vue-router', async original => ({ ...await original<typeof import('vue-router')>(), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@fantastic-admin/components', async () => {
  const { cloneVNode, computed, defineComponent, h, inject, provide } = await import('vue')
  const box = defineComponent({ props: { title: String, description: String, label: String }, setup: (props, { slots }) => () => h('div', [props.title, props.description, props.label, slots.default?.({}), slots.action?.()]) })
  const input = defineComponent({ props: { name: String, modelValue: [String, Boolean], type: String, options: Array }, emits: ['update:modelValue', 'change'], setup: (props, { emit }) => () => props.options
    ? h('select', { name: props.name, value: props.modelValue, onChange: (event: Event) => {
        const value = (event.target as HTMLSelectElement).value
        emit('update:modelValue', value)
        emit('change', value)
      } }, (props.options as Array<{ value: string, label: string }>).map(option => h('option', { value: option.value }, option.label)))
    : h('input', { name: props.name, value: typeof props.modelValue === 'string' ? props.modelValue : undefined, checked: Boolean(props.modelValue), type: props.type || (typeof props.modelValue === 'boolean' ? 'checkbox' : 'text'), onInput: (event: Event) => emit('update:modelValue', typeof props.modelValue === 'boolean' ? (event.target as HTMLInputElement).checked : (event.target as HTMLInputElement).value) }) })
  return {
    FaPageHeader: box,
    FaPageMain: box,
    FaCard: box,
    FaCollapsible: box,
    FaAlert: box,
    FaSearchBar: box,
    FaLabel: box,
    FaIcon: box,
    FaDropdown: box,
    FaTag: box,
    FaSelect: input,
    FaInput: input,
    FaSwitch: input,
    FaButton: defineComponent({ setup: (_, { slots }) => () => h('button', slots.default?.()) }),
    FaForm: defineComponent({
      props: { model: Object },
      setup: (props, { expose, slots }) => {
        provide('test-form-model', computed(() => props.model))
        expose({ validate: async () => ({ valid: true }), resetFields: vi.fn() })
        return () => h('form', slots.default?.())
      },
    }),
    FaFormItem: defineComponent({
      props: { name: String, label: String, description: String },
      setup: (props, { slots }) => {
        const model = inject<{ value: Record<string, unknown> }>('test-form-model')!
        return () => h('label', [props.label, props.description, ...slots.default!().map(child => cloneVNode(child, {
          'name': props.name,
          'modelValue': model.value[props.name!],
          'onUpdate:modelValue': (value: unknown) => {
            model.value[props.name!] = value
          },
        }))])
      },
    }),
    FaTable: defineComponent({ props: { data: Array, emptyText: String }, setup: (props, { slots }) => () => h('div', [slots.toolbar?.(), h('div', { 'data-list': '' }, props.data?.length ? (props.data as Array<{ title: string }>).map(item => h('p', item.title)) : props.emptyText)]) }),
    FaPagination: defineComponent({ props: { total: Number }, setup: props => () => h('div', { 'data-total': props.total }) }),
    useFaToast: () => ({ error: vi.fn(), success: vi.fn() }),
    useFaModal: () => ({ confirm: vi.fn() }),
  }
})

const cleanups: Array<() => void> = []
async function mount() {
  const container = document.createElement('div')
  const app = createApp(DelegationList)
  app.directive('loading', () => {})
  app.mount(container)
  cleanups.push(() => app.unmount())
  await vi.waitFor(() => expect(api.getBarkSettings).toHaveBeenCalledOnce())
  await nextTick()
  return container
}
function setInput(container: HTMLElement, name: string, value: string | boolean) {
  const input = container.querySelector<HTMLInputElement>(`input[name="${name}"]`)!
  if (typeof value === 'boolean') {
    input.checked = value
  }
  else {
    input.value = value
  }
  input.dispatchEvent(new Event('input'))
}
function clickSave(container: HTMLElement) {
  Array.from(container.querySelectorAll('button')).find(button => button.textContent?.trim() === '保存 Bark 设置')!.click()
}
describe('持续委托列表与 Bark 设置', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.listDevices.mockResolvedValue([])
    api.listRoles.mockResolvedValue([{ id: 'a', name: '伙伴 A' }, { id: 'b', name: '伙伴 B' }])
    api.listDelegations.mockResolvedValue({ list: [], total: 0 })
    api.listDelegationReviewReady.mockResolvedValue({ list: [], total: 0 })
    api.getBarkSettings.mockResolvedValue({ serverUrl: 'https://api.day.app', keyConfigured: true, enabled: true, updatedAt: null })
    api.updateBarkSettings.mockResolvedValue({ serverUrl: 'https://api.day.app', keyConfigured: true, enabled: true, updatedAt: null })
  })
  afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()))

  it('key 查询始终为空；保存替换后立即清空，下一次空值保留现有 Key', async () => {
    const container = await mount()
    const keyInput = container.querySelector<HTMLInputElement>('input[name="deviceKey"]')!
    expect(keyInput.type).toBe('password')
    expect(keyInput.value).toBe('')
    setInput(container, 'deviceKey', 'new-test-key')
    await nextTick()
    clickSave(container)
    await vi.waitFor(() => expect(api.updateBarkSettings).toHaveBeenCalledWith({ serverUrl: 'https://api.day.app', deviceKey: 'new-test-key', clearDeviceKey: false, enabled: true }))
    await vi.waitFor(() => expect(container.querySelector<HTMLInputElement>('input[name="deviceKey"]')!.value).toBe(''))
    clickSave(container)
    await vi.waitFor(() => expect(api.updateBarkSettings).toHaveBeenLastCalledWith(expect.objectContaining({ deviceKey: '', clearDeviceKey: false })))
  })

  it('显式清除必须先关闭渠道；不会把已配置 Key 回填', async () => {
    const container = await mount()
    setInput(container, 'deviceKey', 'unused-replacement')
    await nextTick()
    setInput(container, 'clearDeviceKey', true)
    await nextTick()
    expect(container.querySelector<HTMLInputElement>('input[name="deviceKey"]')!.value).toBe('')
    clickSave(container)
    await nextTick()
    expect(api.updateBarkSettings).not.toHaveBeenCalled()
    setInput(container, 'enabled', false)
    await nextTick()
    clickSave(container)
    await vi.waitFor(() => expect(api.updateBarkSettings).toHaveBeenCalledWith(expect.objectContaining({ enabled: false, clearDeviceKey: true, deviceKey: '' })))
  })

  it('旧伙伴查询晚返回，不覆盖新的列表与总数', async () => {
    let resolveOld!: (value: unknown) => void
    api.listDelegations.mockReturnValueOnce(new Promise((resolve) => {
      resolveOld = resolve
    })).mockResolvedValueOnce({ list: [{ title: 'B 的委托' }], total: 8 })
    const container = await mount()
    const select = Array.from(container.querySelectorAll('select')).find(item => item.textContent?.includes('伙伴 B'))!
    select.value = 'b'
    select.dispatchEvent(new Event('change'))
    await vi.waitFor(() => expect(container.querySelector('[data-list]')?.textContent).toContain('B 的委托'))
    resolveOld({ list: [{ title: '旧委托' }], total: 99 })
    await nextTick()
    await nextTick()
    expect(container.querySelector('[data-list]')?.textContent).not.toContain('旧委托')
    expect(container.querySelector('[data-total]')?.getAttribute('data-total')).toBe('8')
  })
  it('首屏最多三张待阅卡，成果、来源和用户认可分别展示', async () => {
    api.listDelegationReviewReady.mockResolvedValue({ list: [1, 2, 3, 4].map(index => ({ id: `task-${index}`, title: `方案比较 ${index}`, roleId: 'a', revision: 1, sourceUrls: ['https://example.com/a'], zoneId: 'Asia/Shanghai', lastRun: { id: `run-${index}`, taskRevision: 1, status: 'SUCCEEDED', output: `比较摘要 ${index}`, evidence: [{ url: 'https://example.com/a' }], createdAt: '2026-10-15T01:00:00Z', review: { reviewState: 'UNREVIEWED', resourceVersion: 1 } } })), total: 4 })
    const container = await mount()
    const cards = container.querySelector('[aria-label="等你查看"]')!
    expect(cards.querySelectorAll('button')).toHaveLength(3)
    expect(cards.textContent).toContain('比较摘要 1')
    expect(cards.textContent).toContain('伙伴：伙伴 A')
    expect(cards.textContent).not.toContain('比较摘要 4')
    expect(cards.textContent).not.toContain('已认可')
    expect(cards.querySelector('a')?.getAttribute('rel')).toBe('noopener noreferrer')
    expect(container.querySelector('[data-list]')?.textContent).toBe('还没有持续委托')
  })
  it('刷新断网时保留当前范围已加载委托，切换范围立即清空', async () => {
    api.listDelegations.mockResolvedValueOnce({ list: [{ title: '已保存的成果' }], total: 1 }).mockRejectedValueOnce(new Error('离线')).mockResolvedValueOnce({ list: [], total: 0 })
    const container = await mount()
    const refresh = Array.from(container.querySelectorAll('button')).find(button => button.textContent?.trim() === '刷新')!
    refresh.click()
    await vi.waitFor(() => expect(container.textContent).toContain('当前连接中断，保留上次成果'))
    expect(container.querySelector('[data-list]')?.textContent).toContain('已保存的成果')
    const select = Array.from(container.querySelectorAll('select')).find(item => item.textContent?.includes('伙伴 B'))!
    select.value = 'b'
    select.dispatchEvent(new Event('change'))
    await vi.waitFor(() => expect(container.querySelector('[data-list]')?.textContent).not.toContain('已保存的成果'))
  })
  it('独立待阅查询的旧伙伴响应晚到，不覆盖当前范围空态', async () => {
    let resolveOld!: (value: unknown) => void
    api.listDelegationReviewReady.mockReturnValueOnce(new Promise((resolve) => {
      resolveOld = resolve
    })).mockResolvedValueOnce({ list: [], total: 0 })
    const container = await mount()
    const select = Array.from(container.querySelectorAll('select')).find(item => item.textContent?.includes('伙伴 B'))!
    select.value = 'b'
    select.dispatchEvent(new Event('change'))
    await vi.waitFor(() => expect(api.listDelegationReviewReady).toHaveBeenLastCalledWith({ deviceId: undefined, roleId: 'b' }))
    resolveOld({ list: [{ title: '旧伙伴私人成果', lastRun: { status: 'SUCCEEDED', review: { reviewState: 'UNREVIEWED' } } }], total: 1 })
    await nextTick()
    await nextTick()
    expect(container.textContent).not.toContain('旧伙伴私人成果')
    expect(container.querySelector('[aria-label="等你查看"]')?.textContent).toContain('还没有成果')
  })
})
