import { afterEach, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, reactive } from 'vue'
import DeliveryTimelineView from './DeliveryTimeline.vue'

const api = vi.hoisted(() => ({ getDeliveryTimeline: vi.fn() }))
vi.mock('@/api/modules/reminders', () => api)
vi.mock('@fantastic-admin/components', () => {
  const pass = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) })
  return { FaCard: pass, FaIcon: pass, FaTag: pass, FaEmpty: pass, FaAlert: defineComponent({ props: ['description'], setup: props => () => h('div', String(props.description)) }) }
})

const cleanups: (() => void)[] = []
afterEach(() => {
  cleanups.splice(0).forEach(cleanup => cleanup())
  vi.resetAllMocks()
})

it('discards late partner data and clears old content when refresh fails', async () => {
  let resolveOld!: (value: unknown) => void
  api.getDeliveryTimeline.mockImplementationOnce(() => new Promise((resolve) => {
    resolveOld = resolve
  }))
  const props = reactive({ deviceId: 'device', roleId: 'old-role', refreshKey: 0 })
  const host = document.createElement('div')
  document.body.append(host)
  const app = createApp({ render: () => h(DeliveryTimelineView, props) })
  app.mount(host)
  cleanups.push(() => {
    app.unmount()
    host.remove()
  })
  const row = (content: string) => ({ id: content, content, source: 'USER', status: 'PENDING', zoneId: 'UTC', scheduledAt: '2026-09-14T00:00:00Z' })
  api.getDeliveryTimeline.mockResolvedValueOnce({ upcoming: [row('新伙伴提醒')], upcomingTotal: 21, recent: [], recentHasMore: false })
  props.roleId = 'new-role'
  await nextTick()
  await nextTick()
  await nextTick()
  expect(host.textContent).toContain('新伙伴提醒')
  expect(host.textContent).toContain('21')
  resolveOld({ upcoming: [row('旧伙伴提醒')], upcomingTotal: 1, recent: [] })
  await nextTick()
  await nextTick()
  expect(host.textContent).not.toContain('旧伙伴提醒')
  api.getDeliveryTimeline.mockRejectedValueOnce(new Error('暂时无法读取'))
  props.refreshKey++
  await nextTick()
  await nextTick()
  await nextTick()
  expect(host.textContent).toContain('暂时无法读取')
  expect(host.textContent).not.toContain('新伙伴提醒')
  expect(api.getDeliveryTimeline).toHaveBeenLastCalledWith('device', 'new-role')
})
