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

    expect(menu.sidebarMenus.filter(item => item.meta?.menu !== false).map(item => item.meta?.title)).toEqual([
      '伙伴首页',
      '聊天',
      '我的伙伴',
      '我的事务',
      '机器人',
      '设置与数据',
    ])

    const tasks = menu.sidebarMenus.find(item => item.meta?.title === '我的事务')
    expect(tasks?.children?.map(item => item.meta?.title)).toEqual([
      '提醒',
      '个人待办',
      '工作陪伴',
    ])
    const advanced = menu.sidebarMenus.find(item => item.meta?.title === '设置与数据')
    expect(advanced?.meta?.expand).toBe(false)
    expect(advanced?.children?.map(item => item.meta?.title)).toEqual([
      'AI 配置',
      '语音配置',
      '对话与个人数据',
      '可用帮助与扩展',
      '外部通知',
    ])
    expect(menu.sidebarMenus.find(item => item.meta?.title === '高级扩展')?.meta?.menu).toBe(false)
  })
})
