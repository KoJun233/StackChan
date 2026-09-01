import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'

function Layout() {
  return import('@/layouts/index.vue')
}

const dashboardRoute: RouteRecordRaw = {
  path: '/dashboard',
  component: Layout,
  name: 'dashboard',
  meta: { title: '今日概览', icon: 'i-ri:home-heart-line' },
  children: [
    {
      path: '',
      name: 'dashboardHome',
      component: () => import('@/views/dashboard/index.vue'),
      meta: { title: '今日概览', menu: false, breadcrumb: false },
    },
  ],
}

const chatRoute: RouteRecordRaw = {
  path: '/companion/chat',
  component: Layout,
  name: 'companionChatMenu',
  meta: { title: '陪伴聊天', icon: 'i-ri:chat-smile-3-line' },
  children: [
    {
      path: '',
      name: 'companionChat',
      component: () => import('@/views/companion/chat/index.vue'),
      meta: { title: '陪伴聊天', menu: false, breadcrumb: false, keepAlive: true },
    },
  ],
}

const workdayRoute: RouteRecordRaw = {
  path: '/companion/workday',
  component: Layout,
  name: 'workdayCompanionMenu',
  meta: { title: '工作陪伴', icon: 'i-ri:focus-2-line' },
  children: [
    {
      path: '',
      name: 'workdayCompanion',
      component: () => import('@/views/settings/interaction/index.vue'),
      meta: { title: '工作陪伴', menu: false, breadcrumb: false },
    },
  ],
}

const proactiveRoute: RouteRecordRaw = {
  path: '/settings/interaction',
  component: Layout,
  name: 'interactionSettingsMenu',
  meta: {
    title: '主动关心',
    icon: 'i-ri:heart-add-2-line',
  },
  children: [
    {
      path: '',
      name: 'interactionSettings',
      component: () => import('@/views/settings/interaction/index.vue'),
      meta: { title: '主动关心', menu: false, breadcrumb: false },
    },
  ],
}

const todayCompanionRoute: RouteRecordRaw = {
  path: '/today',
  name: 'todayCompanion',
  meta: {
    title: '今日陪伴',
    icon: 'i-ri:sun-line',
    expand: true,
  },
  children: [dashboardRoute, chatRoute, workdayRoute, proactiveRoute],
}

const routes: RouteRecordMainRaw = {
  meta: { title: '今日', icon: 'i-ri:sun-line', sort: 100 },
  children: [todayCompanionRoute],
}

export default routes
