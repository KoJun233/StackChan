import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'

function Layout() {
  return import('@/layouts/index.vue')
}

const dashboardRoute: RouteRecordRaw = {
  path: '/dashboard',
  component: Layout,
  name: 'dashboard',
  meta: { title: '伙伴首页', icon: 'i-ri:home-heart-line' },
  children: [
    {
      path: '',
      name: 'dashboardHome',
      component: () => import('@/views/dashboard/index.vue'),
      meta: { title: '伙伴首页', menu: false, breadcrumb: false },
    },
  ],
}

const chatRoute: RouteRecordRaw = {
  path: '/companion/chat',
  component: Layout,
  name: 'companionChatMenu',
  meta: { title: '聊天', icon: 'i-ri:chat-smile-3-line' },
  children: [
    {
      path: '',
      name: 'companionChat',
      component: () => import('@/views/companion/chat/index.vue'),
      meta: { title: '聊天', menu: false, breadcrumb: false, keepAlive: true },
    },
  ],
}

export const workdayRoute: RouteRecordRaw = {
  path: '/companion/workday',
  component: Layout,
  name: 'workdayCompanionMenu',
  meta: { title: '工作陪伴', icon: 'i-ri:focus-2-line' },
  children: [
    {
      path: '',
      name: 'workdayCompanion',
      component: () => import('@/views/companion/workday/index.vue'),
      meta: { title: '工作陪伴', menu: false, breadcrumb: false },
    },
  ],
}

export const proactiveRoute: RouteRecordRaw = {
  path: '/settings/interaction',
  component: Layout,
  name: 'interactionSettingsMenu',
  meta: {
    title: '陪伴方式',
    icon: 'i-ri:heart-add-2-line',
  },
  children: [
    {
      path: '',
      name: 'interactionSettings',
      component: () => import('@/views/settings/interaction/index.vue'),
      meta: { title: '陪伴方式', menu: false, breadcrumb: false },
    },
  ],
}

const routes: RouteRecordMainRaw = {
  meta: { title: '日常', icon: 'i-ri:home-heart-line', sort: 100 },
  children: [dashboardRoute, chatRoute],
}

export default routes
