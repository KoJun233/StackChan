import { describe, expect, it } from 'vitest'
import { asyncRoutes, systemRoutes } from './routes'

function flattenRoutes(routes: any[]): any[] {
  return routes.flatMap(route => [route, ...(route.children ? flattenRoutes(route.children) : [])])
}

describe('stackChan console routes', () => {
  it('redirects the root route to the daily overview', () => {
    const rootRoute = (systemRoutes as any[]).find(route => route.path === '/')
    const indexRoute = rootRoute?.children?.find((route: any) => route.path === '')

    expect(indexRoute?.redirect).toBe('/dashboard')
  })

  it('exposes the daily, companion, device, task and capability pages', () => {
    const routes = flattenRoutes(asyncRoutes as any[])

    expect(routes).toEqual(expect.arrayContaining([
      expect.objectContaining({ name: 'dashboardHome', path: '', meta: expect.objectContaining({ title: '今日概览' }) }),
      expect.objectContaining({ name: 'companionChat', path: '', meta: expect.objectContaining({ title: '陪伴聊天', keepAlive: true }) }),
      expect.objectContaining({ name: 'workdayCompanion', path: '', meta: expect.objectContaining({ title: '工作陪伴' }) }),
      expect.objectContaining({ name: 'interactionSettings', path: '', meta: expect.objectContaining({ title: '主动关心' }) }),
      expect.objectContaining({ name: 'deviceOverview', path: 'overview' }),
      expect.objectContaining({ name: 'devicePairing', path: 'pairing', meta: expect.objectContaining({ title: '配网与配对' }) }),
      expect.objectContaining({ name: 'deviceHealth', path: 'health', meta: expect.objectContaining({ title: '运行健康' }) }),
      expect.objectContaining({ name: 'companionPersona', path: 'persona', meta: expect.objectContaining({ title: '角色管理' }) }),
      expect.objectContaining({ name: 'companionRoleDetail', path: 'roles/detail/:id?', meta: expect.objectContaining({ menu: false, activeMenu: '/companion/persona' }) }),
      expect.objectContaining({ name: 'companionExpressionPacks', path: 'expressions', meta: expect.objectContaining({ title: '表情与形象' }) }),
      expect.objectContaining({ name: 'companionPersonalData', path: 'personal-data', meta: expect.objectContaining({ title: '对话与个人数据' }) }),
      expect.objectContaining({ name: 'companionMemoryList', path: 'memories', meta: expect.objectContaining({ title: '长期记忆', keepAlive: 'companionMemoryDetail' }) }),
      expect.objectContaining({
        name: 'companionMemoryDetail',
        path: 'memories/detail/:id?',
        meta: expect.objectContaining({ menu: false, activeMenu: '/companion/memories', noKeepAlive: 'companionMemoryList' }),
      }),
      expect.objectContaining({ name: 'llmSettings', path: 'llm', meta: expect.objectContaining({ title: 'AI 配置' }) }),
      expect.objectContaining({ name: 'speechSettings', path: 'speech', meta: expect.objectContaining({ title: '语音配置' }) }),
      expect.objectContaining({ name: 'agentCapabilities', path: 'agent', meta: expect.objectContaining({ title: 'Agent 能力' }) }),
      expect.objectContaining({ name: 'reminderList', path: '', meta: expect.objectContaining({ keepAlive: 'reminderDetail' }) }),
      expect.objectContaining({
        name: 'reminderDetail',
        path: 'detail/:id?',
        meta: expect.objectContaining({ menu: false, activeMenu: '/reminders', noKeepAlive: 'reminderList' }),
      }),
      expect.objectContaining({ name: 'personalTaskList', path: '', meta: expect.objectContaining({ keepAlive: 'personalTaskDetail' }) }),
      expect.objectContaining({
        name: 'personalTaskDetail',
        path: 'detail/:id?',
        meta: expect.objectContaining({ menu: false, activeMenu: '/personal-tasks', noKeepAlive: 'personalTaskList' }),
      }),
    ]))
  })

  it('places proactive care in the daily group', () => {
    const daily = (asyncRoutes as any[]).find(route => route.meta?.title === '今日')
    const companion = (asyncRoutes as any[]).find(route => route.meta?.title === '陪伴')
    const dailyCompanion = daily?.children?.find((route: any) => route.name === 'todayCompanion')

    expect(daily?.children).toEqual([
      expect.objectContaining({ name: 'todayCompanion', meta: expect.objectContaining({ title: '今日陪伴', expand: true }) }),
    ])
    expect(dailyCompanion?.children).toEqual(expect.arrayContaining([
      expect.objectContaining({ name: 'workdayCompanionMenu' }),
      expect.objectContaining({ name: 'interactionSettingsMenu' }),
    ]))
    expect(companion?.children).not.toEqual(expect.arrayContaining([
      expect.objectContaining({ name: 'interactionSettingsMenu' }),
    ]))
  })

  it('uses the recommended top-level information architecture', () => {
    expect((asyncRoutes as any[]).map(route => route.meta?.title)).toEqual([
      '今日',
      '陪伴',
      '事务与通知',
      '设备与运行',
      '系统与能力',
    ])
  })

  it('groups external notifications under tasks and notifications', () => {
    const reminderManagement = (asyncRoutes as any[]).find(route => route.meta?.title === '事务与通知')
    const taskManagement = reminderManagement?.children?.find((route: any) => route.name === 'taskManagement')

    expect(reminderManagement?.children).toEqual([
      expect.objectContaining({ name: 'taskManagement', meta: expect.objectContaining({ title: '事务管理' }) }),
    ])
    expect(taskManagement?.children).toEqual(expect.arrayContaining([
      expect.objectContaining({ name: 'reminders', path: '/reminders' }),
      expect.objectContaining({ name: 'personalTasks', path: '/personal-tasks' }),
      expect.objectContaining({ name: 'notificationIntegrations', path: '/notifications' }),
    ]))
    expect((asyncRoutes as any[]).filter(route => route.meta?.title === '外部通知')).toHaveLength(0)
  })
})
