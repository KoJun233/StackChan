import type { Delegation } from '@/api/modules/delegations'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import { DelegationSubmissionError } from '@/api/modules/delegations'
import DetailForm from './index.vue'

const api = vi.hoisted(() => ({ listDevices: vi.fn(), listRoles: vi.fn(), getDeviceActiveRole: vi.fn(), getDelegation: vi.fn(), createDelegation: vi.fn(), updateDelegation: vi.fn() }))
const state = vi.hoisted(() => ({ model: null as Record<string, unknown> | null, valid: true }))
vi.mock('@/api/modules/delegations', async original => ({ ...await original<typeof import('@/api/modules/delegations')>(), ...api }))
vi.mock('@/api/modules/devices', async original => ({ ...await original<typeof import('@/api/modules/devices')>(), listDevices: api.listDevices }))
vi.mock('@/api/modules/roles', async original => ({ ...await original<typeof import('@/api/modules/roles')>(), listRoles: api.listRoles, getDeviceActiveRole: api.getDeviceActiveRole }))
vi.mock('@fantastic-admin/components', async () => {
  const { defineComponent, h, watchEffect } = await import('vue')
  const box = defineComponent({ props: { title: String, description: String, label: String }, setup: (props, { slots }) => () => h('div', [props.title, props.description, props.label, slots.trigger?.({ open: true }), slots.default?.(), slots.action?.()]) })
  return {
    FaForm: defineComponent({
      props: { model: Object },
      setup: (props, { expose, slots }) => {
        watchEffect(() => {
          state.model = props.model as Record<string, unknown>
        })
        expose({ validate: async () => ({ valid: state.valid }) })
        return () => h('div', slots.default?.())
      },
    }),
    FaFormItem: box,
    FaCard: box,
    FaAlert: box,
    FaCollapsible: box,
    FaButton: defineComponent({ setup: (_, { slots }) => () => h('button', slots.default?.()) }),
    FaInput: box,
    FaTextarea: box,
    FaSwitch: box,
    FaSelect: defineComponent({ props: { disabled: Boolean }, setup: props => () => h('select', { disabled: props.disabled }) }),
    useFaToast: () => ({ success: vi.fn(), error: vi.fn() }),
  }
})

const task = { id: 'task', deviceId: 'device', roleId: 'role', title: '每天鼓励', description: '每天上午九点写一句鼓励。', instruction: '每天上午九点写一句鼓励。', understanding: '我会每天上午九点写一句鼓励，保存在控制台。', sourceUrls: [], scheduleType: 'DAILY', daysOfWeek: [], localTime: '09:00:00', zoneId: 'Asia/Shanghai', deliveryMode: 'CONSOLE', enabled: true, onceAt: null, intervalMinutes: null, nextRunAt: '2026-10-10T01:00:00Z', createdAt: '', updatedAt: '', lastRun: null, requiresToolEvidence: false } satisfies Delegation
const cleanups: Array<() => void> = []
async function mount(props: { id?: string, example?: boolean } = {}) {
  const container = document.createElement('div')
  const saved = vi.fn()
  const app = createApp(DetailForm, { ...props, onSaved: saved })
  app.directive('loading', () => {})
  const instance = app.mount(container) as unknown as { submit: () => Promise<boolean> }
  cleanups.push(() => app.unmount())
  await vi.waitFor(() => expect(api.listRoles).toHaveBeenCalled())
  await nextTick()
  await nextTick()
  return { container, instance, saved }
}

describe('自然描述任务的接受与追问', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    state.model = null
    state.valid = true
    api.listDevices.mockResolvedValue([{ id: 'device', displayName: '机器人', online: true }])
    api.listRoles.mockResolvedValue([{ id: 'role', name: '伙伴', archivedAt: null }])
    api.getDeviceActiveRole.mockResolvedValue({ id: 'role' })
    api.createDelegation.mockResolvedValue(task)
    api.updateDelegation.mockResolvedValue(task)
  })
  afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()))

  it('信息齐全时只交代描述，直接接下并显示复述，无资料来源表单', async () => {
    const { instance, container, saved } = await mount()
    state.model!.description = '每天上午九点写一句鼓励。'
    expect(await instance.submit()).toBe(true)
    expect(api.createDelegation).toHaveBeenCalledWith({ deviceId: 'device', roleId: 'role', description: '每天上午九点写一句鼓励。', zoneId: 'Asia/Shanghai', enabled: true })
    await nextTick()
    expect(container.textContent).toContain(task.understanding)
    expect(container.textContent).not.toContain('资料来源')
    expect(container.textContent).not.toContain('最多条数')
    expect(saved).toHaveBeenCalledWith(task)
  })

  it('缺信息时原位追问，描述补全后再次交给伙伴，无二次确认', async () => {
    api.createDelegation.mockRejectedValueOnce(new DelegationSubmissionError('DELEGATION_CLARIFICATION_REQUIRED', '你希望每天几点执行？')).mockResolvedValueOnce(task)
    const { instance, container, saved } = await mount()
    state.model!.description = '每天给我一句鼓励'
    expect(await instance.submit()).toBe(false)
    await nextTick()
    expect(container.textContent).toContain('你希望每天几点执行？')
    expect(saved).not.toHaveBeenCalled()
    expect(state.model!.description).toBe('每天给我一句鼓励')
    state.model!.description = '每天上午九点给我一句鼓励'
    expect(await instance.submit()).toBe(true)
    await nextTick()
    expect(container.textContent).not.toContain('你希望每天几点执行？')
    expect(saved).toHaveBeenCalledOnce()
  })

  it('示例仅填自然描述，推送意图由AI理解', async () => {
    const { instance } = await mount({ example: true })
    expect(state.model!.description).toContain('08:30')
    expect(state.model!.description).toContain('iPhone')
    expect(await instance.submit()).toBe(true)
    expect(api.createDelegation).toHaveBeenCalledWith(expect.objectContaining({ description: expect.stringContaining('5 条') }))
    expect(api.createDelegation.mock.calls[0][0]).not.toHaveProperty('sourceUrls')
    expect(api.createDelegation.mock.calls[0][0]).not.toHaveProperty('scheduleType')
    expect(api.createDelegation.mock.calls[0][0]).not.toHaveProperty('deliveryMode')
  })

  it('多个伙伴时优先设备当前伙伴，修改保持归属并更新描述', async () => {
    api.listRoles.mockResolvedValue([{ id: 'other', name: '另一个伙伴', archivedAt: null }, { id: 'role', name: '当前伙伴', archivedAt: null }])
    const creating = await mount()
    await vi.waitFor(() => expect(state.model!.roleId).toBe('role'))
    expect(api.getDeviceActiveRole).toHaveBeenCalledWith('device')
    expect(await creating.instance.submit()).toBe(true)
    api.getDelegation.mockResolvedValue(task)
    const { instance, container } = await mount({ id: 'task' })
    expect(container.querySelectorAll('select:disabled')).toHaveLength(2)
    state.model!.description = '每个工作日下午六点回顾我的待办。'
    expect(await instance.submit()).toBe(true)
    expect(api.updateDelegation).toHaveBeenCalledWith('task', expect.objectContaining({ deviceId: 'device', roleId: 'role', description: '每个工作日下午六点回顾我的待办。' }))
  })

  it('设备当前伙伴晚返回时保留用户主动选择的伙伴', async () => {
    let resolveRole!: (role: { id: string }) => void
    api.listDevices.mockResolvedValue([{ id: 'device', displayName: '机器人' }, { id: 'other-device', displayName: '另一台' }])
    api.listRoles.mockResolvedValue([{ id: 'role', name: '当前伙伴', archivedAt: null }, { id: 'other', name: '我选择的伙伴', archivedAt: null }])
    api.getDeviceActiveRole.mockReturnValue(new Promise((resolve) => {
      resolveRole = resolve
    }))
    await mount()
    state.model!.deviceId = 'device'
    await nextTick()
    await vi.waitFor(() => expect(api.getDeviceActiveRole).toHaveBeenCalledWith('device'))
    state.model!.roleId = 'other'
    resolveRole({ id: 'role' })
    await nextTick()
    await nextTick()
    expect(state.model!.roleId).toBe('other')
  })

  it('加载或校验失败不能覆盖任务，执行能力缺失不会显示已接下', async () => {
    api.getDelegation.mockRejectedValue(new Error('暂时不可用'))
    const { instance, container } = await mount({ id: 'task' })
    await vi.waitFor(() => expect(container.textContent).toContain('暂时不可用'))
    expect(await instance.submit()).toBe(false)
    expect(api.updateDelegation).not.toHaveBeenCalled()
    const other = await mount()
    state.valid = false
    expect(await other.instance.submit()).toBe(false)
    expect(api.createDelegation).not.toHaveBeenCalled()
    state.valid = true
    api.createDelegation.mockRejectedValue(new DelegationSubmissionError('BARK_NOT_CONFIGURED', '请先配置手机通知。'))
    expect(await other.instance.submit()).toBe(false)
    await nextTick()
    expect(other.container.textContent).toContain('请先配置手机通知。')
    expect(other.saved).not.toHaveBeenCalled()
  })
})
