import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { asyncRoutes, systemRoutes } from './routes'

function flatten(routes: any[]): any[] {
  return routes.flatMap(route => [route, ...flatten(route.children ?? [])])
}
const create = () => createRouter({ history: createMemoryHistory(), routes: asyncRoutes.flatMap(group => group.children ?? []) })

describe('console navigation compatibility', () => {
  it('retains the home redirect', () => {
    expect((systemRoutes as any[]).find(route => route.path === '/')?.children?.find((route: any) => route.path === '')?.redirect).toBe('/dashboard')
  })
  it('keeps all seventeen business URLs and named routes', () => {
    const router = create()
    const pages: Record<string, string> = {
      '/dashboard': 'dashboardHome',
      '/companion/chat': 'companionChat',
      '/companion/workday': 'workdayCompanion',
      '/settings/interaction': 'interactionSettings',
      '/companion/persona': 'companionPersona',
      '/companion/memories': 'companionMemoryList',
      '/companion/expressions': 'companionExpressionPacks',
      '/companion/personal-data': 'companionPersonalData',
      '/reminders': 'reminderList',
      '/personal-tasks': 'personalTaskList',
      '/devices/overview': 'deviceOverview',
      '/devices/pairing': 'devicePairing',
      '/devices/health': 'deviceHealth',
      '/settings/llm': 'llmSettings',
      '/settings/speech': 'speechSettings',
      '/settings/agent': 'agentCapabilities',
      '/notifications': 'notificationIntegrationList',
    }
    for (const [path, name] of Object.entries(pages)) {
      expect(router.resolve(path).name, path).toBe(name)
      expect(router.resolve({ name }).path, name).toBe(path)
      expect(router.resolve({ path, query: { roleId: 'partner', deviceId: 'device' } }).query).toEqual({ roleId: 'partner', deviceId: 'device' })
    }
  })
  it('keeps five detail URLs hidden and preserves their return/cache contracts', () => {
    const router = create()
    const details = [
      ['companionRoleDetail', '/companion/roles/detail', '/companion/persona', 'companionPersona'],
      ['companionMemoryDetail', '/companion/memories/detail', '/companion/memories', 'companionMemoryList'],
      ['reminderDetail', '/reminders/detail', '/reminders', 'reminderList'],
      ['personalTaskDetail', '/personal-tasks/detail', '/personal-tasks', 'personalTaskList'],
      ['notificationIntegrationDetail', '/notifications/detail', '/notifications', 'notificationIntegrationList'],
    ]
    for (const [name, path, activeMenu, noKeepAlive] of details) {
      expect(router.resolve(path).name).toBe(name)
      expect(router.resolve({ name, params: { id: 'existing' } }).path).toBe(`${path}/existing`)
      expect(router.resolve(path).meta).toMatchObject({ menu: false, activeMenu, keepAlive: true, noKeepAlive })
    }
  })
  it('prioritizes home and chat and groups care with partners and work with tasks', () => {
    expect(asyncRoutes[0].children?.map(route => route.name)).toEqual(['dashboard', 'companionChatMenu'])
    const all = flatten(asyncRoutes)
    const partner = all.find(route => route.name === 'companion')
    expect(partner.meta.title).toBe('我的伙伴')
    expect(flatten(partner.children).some(route => route.name === 'interactionSettings')).toBe(true)
    expect(flatten(all.find(route => route.name === 'taskManagement').children).some(route => route.name === 'workdayCompanion')).toBe(true)
    const settings = all.find(route => route.name === 'settings')
    expect(settings.meta).toMatchObject({ title: '设置与数据', expand: false })
    expect(flatten(settings.children).map(route => route.name)).toEqual(expect.arrayContaining(['companionPersonalData', 'agentCapabilities', 'notificationIntegrationList']))
  })
  it('retains the advanced bookmark and unique names', () => {
    const all = flatten(asyncRoutes)
    const names = all.map(route => route.name).filter(Boolean)
    expect(new Set(names).size).toBe(names.length)
    expect(all.find(route => route.name === 'advancedExtensions').redirect).toBe('/settings/agent')
  })
})
