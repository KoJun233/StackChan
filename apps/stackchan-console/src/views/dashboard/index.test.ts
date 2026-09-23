import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, watch } from 'vue'
import Dashboard from './index.vue'

const api = vi.hoisted(() => ({ health: vi.fn(), roles: vi.fn(), active: vi.fn(), tasks: vi.fn(), workday: vi.fn() }))
vi.mock('@/api/modules/systemHealth', () => ({ getSystemHealth: api.health }))
vi.mock('@/api/modules/roles', () => ({ listRoles: api.roles, getDeviceActiveRole: api.active }))
vi.mock('@/api/modules/personalTasks', () => ({ listPersonalTasks: api.tasks }))
vi.mock('@/api/modules/workday', () => ({ getWorkdayRuntime: api.workday }))
vi.mock('./DeliveryTimeline.vue', () => ({ default: defineComponent({ render: () => h('div') }) }))
vi.mock('vue-router', async original => ({ ...await original<typeof import('vue-router')>(), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@fantastic-admin/components', () => {
  const box = defineComponent({
    props: { title: String, description: String },
    setup: (props, { slots }) => () => h('section', [props.title, props.description, slots.default?.(), slots.footer?.(), slots.actions?.(), slots.action?.()]),
  })
  return {
    FaPageHeader: box,
    FaPageMain: box,
    FaButton: box,
    FaAlert: box,
    FaSelect: defineComponent({
      props: { modelValue: String, options: Array },
      emits: ['change', 'update:modelValue'],
      setup(props, { emit }) {
        // The real FaSelect emits change for programmatic model updates too.
        watch(() => props.modelValue, value => emit('change', value))
        return () => h('div', props.modelValue)
      },
    }),
    FaCard: box,
    FaEmpty: box,
    FaTag: box,
    FaIcon: box,
  }
})

const cleanups: Array<() => void> = []
function mountDashboard() {
  const container = document.createElement('div')
  const app = createApp(Dashboard)
  app.mount(container)
  cleanups.push(() => app.unmount())
  return container
}

describe('dashboard partial availability', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.health.mockResolvedValue({ devices: [{ id: 'robot', displayName: '桌面机器人', online: true }], providers: [], recentSafeErrors: [], notifications: { failedLast24Hours: 0 } })
    api.roles.mockResolvedValue([{ id: 'partner', name: '小伙伴', tone: 'WARM', defaultRole: true }])
    api.active.mockResolvedValue({ id: 'partner' })
    api.tasks.mockResolvedValue({ list: [], total: 0 })
    api.workday.mockResolvedValue({ state: 'OFF' })
  })
  afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()))

  it('keeps the initial device partner when selectors emit programmatic changes', async () => {
    const container = mountDashboard()
    await vi.waitFor(() => expect(container.textContent).toContain('设备当前伙伴：小伙伴'))
    await vi.waitFor(() => expect(container.textContent).toContain('没有开放待办'))
    expect(api.active).toHaveBeenCalledTimes(1)
    expect(api.tasks).toHaveBeenCalledTimes(1)
    expect(container.textContent).not.toContain('选择伙伴后查看事务')
  })

  it('keeps partner and tasks usable when health fails, without inventing an online state', async () => {
    api.health.mockRejectedValue(new Error('offline'))
    const container = mountDashboard()
    await vi.waitFor(() => expect(container.textContent).toContain('没有开放待办'))
    expect(container.textContent).toContain('小伙伴')
    expect(container.textContent).toContain('设备状态未获取')
    expect(container.textContent).not.toContain('还没有连接机器人')
    expect(container.textContent).not.toContain('0 项供应商')
    expect(api.tasks).toHaveBeenCalledWith(expect.objectContaining({ roleId: 'partner' }))
    expect(api.workday).not.toHaveBeenCalled()
  })

  it('shows a genuine empty task result even if only the workday endpoint fails', async () => {
    api.workday.mockRejectedValue(new Error('unavailable'))
    const container = mountDashboard()
    await vi.waitFor(() => expect(container.textContent).toContain('工作状态未获取'))
    expect(container.textContent).toContain('没有开放待办')
    expect(container.textContent).toContain('全部设备有 0 项开放待办')
    expect(container.textContent).not.toContain('待办未获取')
  })

  it('does not report zero tasks or an empty list when the task endpoint fails', async () => {
    api.tasks.mockRejectedValue(new Error('unavailable'))
    const container = mountDashboard()
    await vi.waitFor(() => expect(container.textContent).toContain('待办未获取'))
    expect(container.textContent).not.toContain('没有开放待办')
    expect(container.textContent).not.toContain('全部设备有 0 项')
    expect(container.textContent).toContain('尚未开始')
  })

  it('keeps device and work status available when the partner list fails', async () => {
    api.roles.mockRejectedValue(new Error('unavailable'))
    const container = mountDashboard()
    await vi.waitFor(() => expect(container.textContent).toContain('尚未开始'))
    expect(container.textContent).toContain('伙伴列表未获取')
    expect(container.textContent).toContain('桌面机器人 · 在线')
    expect(api.tasks).not.toHaveBeenCalled()
  })
})
