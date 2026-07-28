import { describe, expect, it } from 'vitest'
import { asyncRoutes, systemRoutes } from './routes'

function flattenRoutes(routes: any[]): any[] {
  return routes.flatMap(route => [route, ...(route.children ? flattenRoutes(route.children) : [])])
}

describe('stackChan console routes', () => {
  it('redirects the root route to the configured device overview home', () => {
    const rootRoute = (systemRoutes as any[]).find(route => route.path === '/')
    const indexRoute = rootRoute?.children?.find((route: any) => route.path === '')

    expect(indexRoute?.redirect).toBe('/devices/overview')
  })

  it('exposes the Chinese device, companion, speech settings and reminder pages', () => {
    const routes = flattenRoutes(asyncRoutes as any[])

    expect(routes).toEqual(expect.arrayContaining([
      expect.objectContaining({ name: 'deviceOverview', path: 'overview' }),
      expect.objectContaining({ name: 'devicePairing', path: 'pairing', meta: expect.objectContaining({ title: '设备配网' }) }),
      expect.objectContaining({ name: 'companionChat', path: 'chat', meta: expect.objectContaining({ title: '陪伴聊天', keepAlive: true }) }),
      expect.objectContaining({ name: 'companionPersona', path: 'persona', meta: expect.objectContaining({ title: '人设设置' }) }),
      expect.objectContaining({ name: 'companionExpressionPacks', path: 'expressions', meta: expect.objectContaining({ title: '宠物表情包' }) }),
      expect.objectContaining({ name: 'companionMemoryList', path: 'memories', meta: expect.objectContaining({ title: '长期记忆', keepAlive: 'companionMemoryDetail' }) }),
      expect.objectContaining({
        name: 'companionMemoryDetail',
        path: 'memories/detail/:id?',
        meta: expect.objectContaining({ menu: false, activeMenu: '/companion/memories', noKeepAlive: 'companionMemoryList' }),
      }),
      expect.objectContaining({ name: 'llmSettings', path: 'llm', meta: expect.objectContaining({ title: 'AI 配置' }) }),
      expect.objectContaining({ name: 'speechSettings', path: 'speech', meta: expect.objectContaining({ title: '语音配置' }) }),
      expect.objectContaining({ name: 'reminderList', path: '', meta: expect.objectContaining({ keepAlive: 'reminderDetail' }) }),
      expect.objectContaining({
        name: 'reminderDetail',
        path: 'detail/:id?',
        meta: expect.objectContaining({ menu: false, activeMenu: '/reminders', noKeepAlive: 'reminderList' }),
      }),
    ]))
  })
})
