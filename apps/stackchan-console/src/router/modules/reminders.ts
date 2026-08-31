import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'
import NotificationRoutes from './notifications'

function Layout() {
  return import('@/layouts/index.vue')
}

const reminderRoute: RouteRecordRaw = {
  path: '/reminders',
  component: Layout,
  name: 'reminders',
  meta: {
    title: '提醒管理',
    icon: 'i-ri:notification-3-line',
  },
  children: [
    {
      path: '',
      name: 'reminderList',
      component: () => import('@/views/reminders/list.vue'),
      meta: {
        title: '提醒列表',
        menu: false,
        breadcrumb: false,
        keepAlive: 'reminderDetail',
      },
    },
    {
      path: 'detail/:id?',
      name: 'reminderDetail',
      component: () => import('@/views/reminders/detail.vue'),
      meta: {
        title: '提醒详情',
        menu: false,
        activeMenu: '/reminders',
        keepAlive: true,
        noKeepAlive: 'reminderList',
      },
    },
  ],
}

const personalTaskRoute: RouteRecordRaw = {
  path: '/personal-tasks',
  component: Layout,
  name: 'personalTasks',
  meta: {
    title: '个人待办',
    icon: 'i-ri:task-line',
  },
  children: [
    {
      path: '',
      name: 'personalTaskList',
      component: () => import('@/views/personal_tasks/list.vue'),
      meta: {
        title: '待办列表',
        menu: false,
        breadcrumb: false,
        keepAlive: 'personalTaskDetail',
      },
    },
    {
      path: 'detail/:id?',
      name: 'personalTaskDetail',
      component: () => import('@/views/personal_tasks/detail.vue'),
      meta: {
        title: '待办详情',
        menu: false,
        activeMenu: '/personal-tasks',
        keepAlive: true,
        noKeepAlive: 'personalTaskList',
      },
    },
  ],
}

const routes: RouteRecordMainRaw = {
  meta: {
    title: '提醒管理',
    icon: 'i-ri:notification-3-line',
  },
  children: [reminderRoute, personalTaskRoute, ...(NotificationRoutes.children ?? [])],
}

export default routes
