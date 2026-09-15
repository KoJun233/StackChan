import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { asyncRoutes } from '@/router/routes'
import { useAppMenuStore } from './menu'
import { useAppRouteStore } from './route'

describe('app menu store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('keeps the single sidebar organized into visible secondary groups', () => {
    useAppRouteStore().generateRoutesAtFront(asyncRoutes)

    const menu = useAppMenuStore()

    expect(menu.sidebarMenus.map(item => item.meta?.title)).toEqual([
      '今日陪伴',
      '角色与内容',
      '事务管理',
      '机器人设备',
      '能力配置',
      '高级扩展',
    ])

    const daily = menu.sidebarMenus.find(item => item.meta?.title === '今日陪伴')
    expect(daily?.meta?.expand).toBe(true)
    expect(daily?.children?.map(item => item.meta?.title)).toEqual([
      '今日概览',
      '陪伴聊天',
      '工作陪伴',
      '主动陪伴',
    ])

    const tasks = menu.sidebarMenus.find(item => item.meta?.title === '事务管理')
    expect(tasks?.children?.map(item => item.meta?.title)).toEqual([
      '提醒',
      '个人待办',
    ])
    const advanced = menu.sidebarMenus.find(item => item.meta?.title === '高级扩展')
    expect(advanced?.meta?.expand).toBe(false)
    expect(advanced?.children?.map(item => item.meta?.title)).toEqual([
      '可用帮助与扩展',
      '外部通知',
    ])
  })
})
