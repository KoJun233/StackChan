import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp } from 'vue'
import DelegationDetail from './detail.vue'

const api = vi.hoisted(() => ({ listDelegationRuns: vi.fn(), retryDelegationDelivery: vi.fn() }))
vi.mock('@/api/modules/delegations', async original => ({ ...await original<typeof import('@/api/modules/delegations')>(), ...api }))
vi.mock('./components/DetailForm/index.vue', () => ({ default: { template: '<div />' } }))
vi.mock('vue-router', async original => ({ ...await original<typeof import('vue-router')>(), useRoute: () => ({ params: { id: 'task' }, query: {} }), useRouter: () => ({ back: vi.fn() }) }))
vi.mock('@fantastic-admin/components', async () => {
  const { defineComponent, h } = await import('vue')
  const box = defineComponent({ props: { title: String, description: String }, setup: (props, { slots }) => () => h('div', [props.title, props.description, slots.default?.(), slots.action?.()]) })
  return { FaFixedBar: box, FaPageHeader: box, FaPageMain: box, FaCard: box, FaCollapsible: box, FaAlert: box, FaTag: box, FaIcon: box, FaPagination: box, FaButton: defineComponent({ setup: (_, { slots }) => () => h('button', slots.default?.()) }), useFaToast: () => ({ error: vi.fn(), success: vi.fn() }), useFaModal: () => ({ confirm: vi.fn() }) }
})
const cleanups: Array<() => void> = []
describe('历史成果安全阅读', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()))
  it('未知投递不显示重试，外部 HTML 作为文字，脚本来源链接不渲染', async () => {
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
})
