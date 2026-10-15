import type { Delegation, DelegationProjectSummary, DelegationRun } from '@/api/modules/delegations'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import { DelegationOperationPendingError, DelegationSubmissionError } from '@/api/modules/delegations'
import DelegationDetail from './detail.vue'

const api = vi.hoisted(() => ({ listDelegationRuns: vi.fn(), retryDelegationDelivery: vi.fn(), getDelegationProjectSummary: vi.fn(), submitDelegationFeedback: vi.fn(), runDelegationRevision: vi.fn(), listRoles: vi.fn(), confirm: vi.fn() }))
const state = vi.hoisted(() => ({ model: null as Record<string, unknown> | null, route: null as { params: { id: string }, query: object } | null }))
vi.mock('@/api/modules/delegations', async original => ({ ...await original<typeof import('@/api/modules/delegations')>(), ...api }))
vi.mock('./components/DetailForm/index.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/api/modules/roles', async original => ({ ...await original<typeof import('@/api/modules/roles')>(), listRoles: api.listRoles }))
vi.mock('vue-router', async (original) => {
  const { reactive } = await import('vue')
  state.route = reactive({ params: { id: 'task' }, query: {} })
  return { ...await original<typeof import('vue-router')>(), useRoute: () => state.route, useRouter: () => ({ back: vi.fn() }) }
})
vi.mock('@fantastic-admin/components', async () => {
  const { defineComponent, h, watchEffect } = await import('vue')
  const box = defineComponent({ props: { title: String, description: String }, setup: (props, { slots }) => () => h('div', [props.title, props.description, slots.default?.(), slots.action?.()]) })
  return {
    FaFixedBar: box,
    FaPageHeader: box,
    FaPageMain: box,
    FaCard: box,
    FaCollapsible: box,
    FaAlert: box,
    FaTag: box,
    FaIcon: box,
    FaPagination: box,
    FaDropdown: defineComponent({ props: { items: Array }, setup: (props, { slots }) => () => h('div', [slots.default?.(), ...(props.items as Array<Array<{ label: string, disabled?: boolean, handle: () => void }>> || []).flat().map(item => h('button', { disabled: item.disabled, onClick: item.handle }, item.label))]) }),
    FaTextarea: box,
    FaFormItem: box,
    FaModal: defineComponent({ props: { modelValue: Boolean }, setup: (props, { slots }) => () => props.modelValue ? h('div', { role: 'dialog' }, [slots.default?.(), slots.footer?.()]) : null }),
    FaForm: defineComponent({ props: { model: Object }, setup: (props, { slots, expose }) => {
      watchEffect(() => state.model = props.model as Record<string, unknown>)
      expose({ validate: async () => ({ valid: Boolean(String(props.model?.comment || '').trim()) }) })
      return () => h('form', slots.default?.())
    } }),
    FaButton: defineComponent({ setup: (_, { slots }) => () => h('button', slots.default?.()) }),
    useFaToast: () => ({ error: vi.fn(), success: vi.fn() }),
    useFaModal: () => ({ confirm: api.confirm }),
  }
})
const cleanups: Array<() => void> = []
function projectFixture(runOverrides: Partial<DelegationRun> = {}, taskOverrides: Partial<Delegation> = {}): DelegationProjectSummary {
  const run: DelegationRun = { id: 'run', taskId: 'task', taskTitle: '公开方案比较', scheduledDate: '2026-10-15', scheduledAt: null, occurrenceKey: 'once:1', status: 'SUCCEEDED', output: '任务理解\n方案比较\n建议与理由\n缺失信息与限制\n下一步供你决定', steps: [], evidence: [], items: [], sourceWarnings: [], errorCode: null, deliveryStatus: 'NONE', deliveryAttempts: 0, createdAt: '2026-10-15T01:00:00Z', completedAt: '2026-10-15T01:01:00Z', taskRevision: 1, attempt: 1, review: { reviewState: 'UNREVIEWED', resourceVersion: 1, supersedesRunId: null, acknowledgedAt: null }, ...runOverrides }
  const task: Delegation = { id: 'task', deviceId: 'device', roleId: 'role', title: '公开方案比较', description: '现在比较', instruction: '比较', understanding: '先比较公开资料', sourceUrls: ['https://example.com/a', 'https://example.com/b'], scheduleType: 'ONCE', daysOfWeek: [], localTime: '00:00:00', zoneId: 'Asia/Shanghai', onceAt: '2026-10-15T01:00:00Z', intervalMinutes: null, deliveryMode: 'CONSOLE', requiresToolEvidence: true, enabled: true, nextRunAt: null, createdAt: '', updatedAt: '', lastRun: run, revision: 1, cancelledAt: null, cancellationReason: null, ...taskOverrides }
  return { task, latestRun: run, review: run.review!, summary: '方案 A 线缆较少，尺寸来源未提供。', scope: task.sourceUrls, sourceCount: 2, missingInformation: '桌面尺寸', nextDecision: '可以认可比较，或调整偏好。', feedbackHistory: [] }
}
async function mountProject() {
  const container = document.createElement('div')
  document.body.append(container)
  const app = createApp(DelegationDetail)
  app.directive('loading', () => {})
  app.mount(container)
  cleanups.push(() => {
    app.unmount()
    container.remove()
  })
  await vi.waitFor(() => expect(container.textContent).toContain('公开方案比较'))
  return container
}
function click(container: HTMLElement, label: string) {
  const button = Array.from(container.querySelectorAll('button')).find(item => item.textContent?.trim() === label)
  expect(button).toBeTruthy()
  button!.click()
}
describe('历史成果安全阅读', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    state.route!.params.id = 'task'
    api.getDelegationProjectSummary.mockResolvedValue(projectFixture())
    api.listDelegationRuns.mockResolvedValue({ list: [], total: 0 })
    api.listRoles.mockResolvedValue([{ id: 'role', name: '当前伙伴' }])
    api.submitDelegationFeedback.mockResolvedValue({ feedbackId: 'feedback', taskId: 'task', runId: 'run', revision: 1, resourceVersion: 2, reviewState: 'ACCEPTED', newRunId: null })
    api.runDelegationRevision.mockResolvedValue({ id: 'revision-run' })
  })
  afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()))
  it('未知投递不显示重试，外部 HTML 作为文字，脚本来源链接不渲染', async () => {
    api.getDelegationProjectSummary.mockResolvedValue({ ...projectFixture(), latestRun: null, review: null })
    api.listDelegationRuns.mockResolvedValue({ list: [{ id: 'run', taskTitle: '日报', taskId: 'task', scheduledDate: '2026-10-09', status: 'SUCCEEDED', deliveryStatus: 'UNKNOWN', deliveryAttempts: 1, sourceWarnings: [], errorCode: null, createdAt: '2026-10-09T00:30:00Z', completedAt: null, items: [{ title: '<img src=x onerror=alert(1)>', summary: '<script>alert(1)</script>', impact: '有影响', sourceName: '来源', url: 'javascript:alert(1)', publishedAt: null }, { title: '可信来源', summary: '摘要', impact: '说明', sourceName: '官方', url: 'https://example.com/article', publishedAt: null }] }], total: 1 })
    const container = document.createElement('div')
    const app = createApp(DelegationDetail)
    app.mount(container)
    cleanups.push(() => app.unmount())
    await vi.waitFor(() => expect(container.textContent).toContain('投递结果不明'))
    expect(container.textContent).toContain('<script>alert(1)</script>')
    expect(container.querySelector('script, img')).toBeNull()
    expect(container.textContent).not.toContain('重新投递')
    expect(container.querySelectorAll('a')).toHaveLength(1)
    expect(container.querySelector('a')?.getAttribute('href')).toBe('https://example.com/article')
    expect(container.querySelector('a')?.getAttribute('rel')).toBe('noopener noreferrer')
    expect(api.retryDelegationDelivery).not.toHaveBeenCalled()
  })
  it('无需来源的通用结果、步骤和内部证据也完整显示，未执行不冒充完成', async () => {
    api.getDelegationProjectSummary.mockResolvedValue({ ...projectFixture(), latestRun: null, review: null })
    api.listDelegationRuns.mockResolvedValue({ list: [{ id: 'run', taskTitle: '回顾待办', taskId: 'task', scheduledDate: '2026-10-09', scheduledAt: '2026-10-09T10:00:00Z', occurrenceKey: 'daily:2026-10-09', status: 'BLOCKED', deliveryStatus: 'NONE', deliveryAttempts: 0, sourceWarnings: [], errorCode: null, createdAt: '2026-10-09T10:00:00Z', completedAt: null, output: '当前伙伴没有待办读取权限，需要先授权。<script>alert(1)</script>', steps: [{ toolName: '读取个人待办', status: 'BLOCKED', summary: '所需工具尚未授权。' }], evidence: [{ title: '权限检查', url: null, detail: '没有发出写入或外部请求。' }], items: [] }], total: 1 })
    const container = document.createElement('div')
    const app = createApp(DelegationDetail)
    app.mount(container)
    cleanups.push(() => app.unmount())
    await vi.waitFor(() => expect(container.textContent).toContain('当前伙伴没有待办读取权限'))
    expect(container.textContent).toContain('伙伴需要你的帮助')
    expect(container.textContent).toContain('所需工具尚未授权')
    expect(container.textContent).toContain('权限检查')
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelectorAll('a')).toHaveLength(0)
    expect(container.textContent).not.toContain('影响：')
    expect(container.textContent).not.toContain('重新投递')
  })
  it('首屏显示来源、缺失项、待阅；认可只提交明确成果和所见版本', async () => {
    const container = await mountProject()
    expect(container.textContent).toContain('比较已生成，等你看看')
    expect(container.textContent).toContain('已读取来源 2 份')
    expect(container.textContent).toContain('待确认：桌面尺寸')
    const accept = Array.from(container.querySelectorAll('button')).find(button => button.textContent?.trim() === '这份结果可以了')!
    accept.focus()
    expect(document.activeElement).toBe(accept)
    accept.click()
    await vi.waitFor(() => expect(api.submitDelegationFeedback).toHaveBeenCalledOnce())
    expect(api.submitDelegationFeedback).toHaveBeenCalledWith('task', 'run', { kind: 'ACCEPT_RESULT', comment: '', expectedRevision: 1, resourceVersion: 1 }, expect.any(String), expect.objectContaining({ deviceId: 'device', roleId: 'role' }))
    expect(api.runDelegationRevision).not.toHaveBeenCalled()
  })
  it('修改偏好明确保存并重新比较，同一反馈一次完成，不二次调用运行接口', async () => {
    const container = await mountProject()
    click(container, '修改偏好')
    await nextTick()
    expect(container.querySelector('[role="dialog"]')?.textContent).toContain('不自动记成长期偏好')
    state.model!.comment = '线缆少优先，预算先不猜'
    click(container, '保存并重新比较')
    await vi.waitFor(() => expect(api.submitDelegationFeedback).toHaveBeenCalledOnce())
    expect(api.submitDelegationFeedback.mock.calls[0][2]).toEqual({ kind: 'REVISE_PREFERENCES', comment: '线缆少优先，预算先不猜', expectedRevision: 1, resourceVersion: 1, runMode: 'SAVE_AND_RUN' })
    expect(api.runDelegationRevision).not.toHaveBeenCalled()
  })
  it('只保存偏好后通过修订入口继续比较，不重建任务或重发反馈', async () => {
    const container = await mountProject()
    click(container, '修改偏好')
    await nextTick()
    state.model!.comment = '更看重容易整理'
    api.getDelegationProjectSummary.mockResolvedValue(projectFixture({ review: { reviewState: 'REVISION_REQUESTED', resourceVersion: 2, supersedesRunId: null, acknowledgedAt: null } }, { revision: 2 }))
    click(container, '只保存，稍后执行')
    await vi.waitFor(() => expect(container.textContent).toContain('继续比较'))
    expect(api.submitDelegationFeedback.mock.calls[0][2].runMode).toBe('SAVE_ONLY')
    click(container, '继续比较')
    await vi.waitFor(() => expect(api.runDelegationRevision).toHaveBeenCalledWith('task', 2, expect.any(String), expect.objectContaining({ id: 'task' })))
    expect(api.submitDelegationFeedback).toHaveBeenCalledOnce()
  })
  it('反馈冲突刷新当前成果，旧偏好成果不能直接认可', async () => {
    const container = await mountProject()
    api.submitDelegationFeedback.mockRejectedValue(new DelegationSubmissionError('REVISION_CONFLICT', 'changed', '', 409))
    api.getDelegationProjectSummary.mockResolvedValue(projectFixture({}, { revision: 2 }))
    click(container, '这份结果可以了')
    await vi.waitFor(() => expect(container.textContent).toContain('成果已更新，请先查看最新版本'))
    expect(container.textContent).toContain('来自旧偏好')
    const accept = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find(button => button.textContent?.trim() === '这份结果可以了')!
    expect(accept.disabled).toBe(true)
    expect(api.submitDelegationFeedback).toHaveBeenCalledOnce()
  })
  it('反馈状态不明时按钮锁定，核对使用原键和原成果', async () => {
    api.submitDelegationFeedback.mockRejectedValueOnce(new DelegationOperationPendingError('unknown')).mockResolvedValueOnce({ recovered: true })
    const container = await mountProject()
    click(container, '这份结果可以了')
    await vi.waitFor(() => expect(container.textContent).toContain('核对保存状态'))
    const first = api.submitDelegationFeedback.mock.calls[0]
    const accept = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find(button => button.textContent?.trim() === '这份结果可以了')!
    expect(accept.disabled).toBe(true)
    click(container, '核对保存状态')
    await vi.waitFor(() => expect(api.submitDelegationFeedback).toHaveBeenCalledTimes(2))
    expect(api.submitDelegationFeedback.mock.calls[1]).toEqual(first)
  })
  it('断网刷新保留已加载成果，显示当前连接中断', async () => {
    const container = await mountProject()
    api.getDelegationProjectSummary.mockRejectedValue(new TypeError('离线'))
    click(container, '刷新记录')
    await vi.waitFor(() => expect(container.textContent).toContain('当前连接中断，保留上次成果'))
    expect(container.textContent).toContain('方案 A 线缆较少')
  })
  it('切换另一任务后旧伙伴响应晚到，不闪现旧成果', async () => {
    let resolveOld!: (value: DelegationProjectSummary) => void
    api.getDelegationProjectSummary.mockReturnValueOnce(new Promise((resolve) => {
      resolveOld = resolve
    }))
    const container = document.createElement('div')
    const app = createApp(DelegationDetail)
    app.directive('loading', () => {})
    app.mount(container)
    cleanups.push(() => app.unmount())
    await vi.waitFor(() => expect(api.getDelegationProjectSummary).toHaveBeenCalled())
    api.getDelegationProjectSummary.mockResolvedValue(projectFixture({}, { id: 'other-task', roleId: 'other-role', title: '新伙伴成果' }))
    state.route!.params.id = 'other-task'
    await vi.waitFor(() => expect(container.textContent).toContain('新伙伴成果'))
    resolveOld(projectFixture({}, { title: '旧伙伴私人成果' }))
    await nextTick()
    await nextTick()
    expect(container.textContent).not.toContain('旧伙伴私人成果')
  })
  it('已取消任务保留成果与原因，隐藏全部反馈和继续执行', async () => {
    api.getDelegationProjectSummary.mockResolvedValue(projectFixture({}, { enabled: false, cancelledAt: '2026-10-15T02:00:00Z', cancellationReason: '用户已明确取消比较' }))
    const container = await mountProject()
    expect(container.textContent).toContain('用户已明确取消比较')
    expect(container.textContent).toContain('方案 A 线缆较少')
    expect(container.textContent).not.toContain('这份结果可以了')
    expect(container.textContent).not.toContain('继续比较')
  })
  it('取消与物理删除不同，先确认且反馈锁定看到的成果版本', async () => {
    const container = await mountProject()
    click(container, '取消任务')
    expect(api.submitDelegationFeedback).not.toHaveBeenCalled()
    const confirmation = api.confirm.mock.calls[0][0]
    expect(confirmation.content).toContain('已有成果仍可查看')
    api.getDelegationProjectSummary.mockResolvedValue(projectFixture({}, { revision: 2 }))
    await confirmation.onConfirm()
    expect(api.submitDelegationFeedback.mock.calls[0][2]).toEqual({ kind: 'CANCEL', comment: '', expectedRevision: 1, resourceVersion: 1 })
  })
  it('来源详情区保留实际读取时间和安全重定向的输入对应', async () => {
    const result = projectFixture({ evidence: [{ title: '实际读到的资料', url: 'https://example.com/final', inputUrl: 'https://example.com/original', readAt: '2026-10-15T01:00:30Z', detail: '来源未提供尺寸' }] })
    api.getDelegationProjectSummary.mockResolvedValue(result)
    api.listDelegationRuns.mockResolvedValue({ list: [result.latestRun], total: 1 })
    const container = await mountProject()
    const history = container.querySelector('[aria-label="执行记录"]')!
    expect(history.textContent).toContain('读取时间：')
    expect(history.querySelector('a[href="https://example.com/final"]')).toBeTruthy()
    expect(history.querySelector('a[href="https://example.com/original"]')?.getAttribute('rel')).toBe('noopener noreferrer')
  })
})
