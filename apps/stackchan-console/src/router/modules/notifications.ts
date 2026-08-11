import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'

function Layout() {
  return import('@/layouts/index.vue')
}

const notificationRoute: RouteRecordRaw = {
  path: '/notifications',
  component: Layout,
  name: 'notificationIntegrations',
  meta: {
    title: '外部通知',
    icon: 'i-ri:send-plane-line',
  },
  children: [
    {
      path: '',
      name: 'notificationIntegrationList',
      component: () => import('@/views/notifications/list.vue'),
      meta: {
        title: '外部通知',
        menu: false,
        breadcrumb: false,
        keepAlive: 'notificationIntegrationDetail',
      },
    },
    {
      path: 'detail/:id?',
      name: 'notificationIntegrationDetail',
      component: () => import('@/views/notifications/detail.vue'),
      meta: {
        title: '通知集成详情',
        menu: false,
        activeMenu: '/notifications',
        keepAlive: true,
        noKeepAlive: 'notificationIntegrationList',
      },
    },
  ],
}

const routes: RouteRecordMainRaw = {
  meta: { title: '外部通知', icon: 'i-ri:send-plane-line' },
  children: [notificationRoute],
}

export default routes
