import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick } from 'vue'
import MemoryList from './list.vue'

const api = vi.hoisted(() => ({ listMemories: vi.fn(), listRoles: vi.fn(), listDevices: vi.fn(), changeContinuation: vi.fn() }))
vi.mock('@/api/modules/personaMemory', async original => ({
  ...await original<typeof import('@/api/modules/personaMemory')>(),
  listMemories: api.listMemories,
  changeContinuation: api.changeContinuation,
}))
vi.mock('@/api/modules/roles', async original => ({
  ...await original<typeof import('@/api/modules/roles')>(),
  listRoles: api.listRoles,
}))
vi.mock('@/api/modules/devices', async original => ({
  ...await original<typeof import('@/api/modules/devices')>(),
  listDevices: api.listDevices,
}))
vi.mock('vue-router', async original => ({
  ...await original<typeof import('vue-router')>(),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@fantastic-admin/components', () => {
  const box = defineComponent({
    props: { title: String, description: String, label: String },
    setup: (props, { slots }) => () => h('div', [props.label, props.title, props.description, slots.default?.({ fold: false, toggle: vi.fn() }), slots.action?.()]),
  })
  return {
    FaPageHeader: box,
    FaPageMain: box,
    FaLabel: box,
    FaAlert: box,
    FaButton: box,
    FaDropdown: defineComponent({
      props: { items: Array },
      setup: (props, { slots }) => () => h('div', [slots.default?.(), ...(props.items as Array<Array<{ label: string, handle?: () => void }>> ?? []).flat().map(item => h('button', { onClick: item.handle }, item.label))]),
    }),
    FaIcon: box,
    FaInput: box,
    FaSearchBar: box,
    FaTag: box,
    FaSelect: defineComponent({
      props: { modelValue: String, options: Array },
      emits: ['update:modelValue', 'change'],
      setup: (props, { emit }) => () => h('select', {
        value: props.modelValue,
        onChange: (event: Event) => {
          const value = (event.target as HTMLSelectElement).value
          emit('update:modelValue', value)
          emit('change', value)
        },
      }, (props.options as Array<{ value: string, label: string }>).map(option => h('option', { value: option.value }, option.label))),
    }),
    FaTable: defineComponent({
      props: { data: Array, emptyText: String },
      setup: (props, { slots }) => () => h('div', { 'data-list': '' }, props.data?.length
        ? (props.data as Array<{ title: string, id?: string }>).map(row => row.id
            ? h('div', [slots['cell-title']?.({ row: { original: row } }), slots['cell-operation']?.({ row: { original: row } })])
            : h('p', row.title))
        : props.emptyText),
    }),
    FaPagination: defineComponent({
      props: { total: Number },
      setup: props => () => h('div', { 'data-total': props.total }),
    }),
    useFaToast: () => ({ success: vi.fn(), error: vi.fn() }),
    useFaModal: () => ({ confirm: vi.fn() }),
  }
})

const cleanups: Array<() => void> = []
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
async function mountList() {
  const container = document.createElement('div')
  const app = createApp(MemoryList)
  app.mount(container)
  cleanups.push(() => app.unmount())
  await vi.waitFor(() => expect(api.listMemories).toHaveBeenCalledOnce())
  await nextTick()
  return container
}
describe('memory filter request ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.listDevices.mockResolvedValue([])
    api.listRoles.mockResolvedValue([{ id: 'a', name: '伙伴 A' }, { id: 'b', name: '伙伴 B' }])
  })
  afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()))

  it('keeps the latest partner rows and total when the old filter finishes last', async () => {
    const old = deferred<object>()
    api.listMemories.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ list: [{ title: 'B 的记忆' }], total: 8 })
    const container = await mountList()
    const select = Array.from(container.querySelectorAll('select')).find(element => element.textContent?.includes('伙伴 B'))!
    select.value = 'b'
    select.dispatchEvent(new Event('change'))
    await vi.waitFor(() => expect(api.listMemories).toHaveBeenLastCalledWith(expect.objectContaining({ roleId: 'b' })))
    await vi.waitFor(() => expect(container.querySelector('[data-list]')?.textContent).toContain('B 的记忆'))
    old.resolve({ list: [{ title: '旧筛选记忆' }], total: 99 })
    await old.promise
    await nextTick()
    expect(container.querySelector('[data-list]')?.textContent).not.toContain('旧筛选记忆')
    expect(container.querySelector('[data-total]')?.getAttribute('data-total')).toBe('8')
  })

  it('distinguishes loading failure from a genuinely empty result', async () => {
    api.listMemories.mockRejectedValueOnce(new Error('记忆服务暂不可用'))
    const container = await mountList()
    await vi.waitFor(() => expect(container.textContent).toContain('记忆服务暂不可用'))
    expect(container.querySelector('[data-list]')?.textContent).toBe('列表未获取')
  })

  it('shows facts and allows stopping then explicitly resuming a confirmed topic', async () => {
    const memory = { id: 'topic-a', roleId: 'a', title: '木工', content: '正在做书架', sourceDetail: '用户分享', topicKey: '木工', possibleDuplicateIds: [], confirmationStatus: 'CONFIRMED', enabled: true, continuation: { state: 'ACTIVE', progress: '底座已经做好了' } }
    api.listMemories.mockResolvedValue({ list: [memory], total: 1 })
    api.changeContinuation.mockResolvedValue({})
    const container = await mountList()
    expect(container.textContent).toContain('最近你说：底座已经做好了')
    const stop = Array.from(container.querySelectorAll('button')).find(button => button.textContent === '别再提这件事')!
    api.listMemories.mockResolvedValue({ list: [{ ...memory, continuation: { ...memory.continuation, state: 'STOPPED' } }], total: 1 })
    stop.click()
    await vi.waitFor(() => expect(api.changeContinuation).toHaveBeenCalledWith('topic-a', 'STOP'))
    await vi.waitFor(() => expect(container.textContent).toContain('已停止接续'))
    expect(container.textContent).not.toContain('今天先不聊')
    Array.from(container.querySelectorAll('button')).find(button => button.textContent === '恢复接续')!.click()
    await vi.waitFor(() => expect(api.changeContinuation).toHaveBeenLastCalledWith('topic-a', 'RESUME'))
  })

  it('explains progress consent and hides continuation controls before approval', async () => {
    api.listMemories.mockResolvedValue({ list: [{ id: 'pending', title: '木工', content: '正在做书架', sourceDetail: '用户分享', topicKey: '木工', possibleDuplicateIds: [], confirmationStatus: 'PENDING', continuation: { state: 'PENDING', progress: '' } }], total: 1 })
    const container = await mountList()
    expect(container.textContent).toContain('确认也会允许保存这件事的后续进展')
    expect(container.textContent).not.toContain('今天先不聊')
    expect(container.textContent).not.toContain('恢复接续')
  })
})
