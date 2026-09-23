import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'
import { proactiveRoute } from './today'

function Layout() {
  return import('@/layouts/index.vue')
}

const companionRoute: RouteRecordRaw = {
  path: '/companion',
  component: Layout,
  name: 'companion',
  meta: {
    title: '我的伙伴',
    icon: 'i-ri:heart-3-line',
  },
  children: [
    { path: proactiveRoute.path, name: proactiveRoute.name, meta: proactiveRoute.meta, children: proactiveRoute.children ?? [] },
    {
      path: 'persona',
      name: 'companionPersona',
      component: () => import('@/views/companion/persona/index.vue'),
      meta: {
        title: '伙伴管理',
        icon: 'i-ri:user-heart-line',
      },
    },
    {
      path: 'roles/detail/:id?',
      name: 'companionRoleDetail',
      component: () => import('@/views/companion/roles/detail.vue'),
      meta: { title: '伙伴详情', menu: false, activeMenu: '/companion/persona', keepAlive: true, noKeepAlive: 'companionPersona' },
    },
    {
      path: 'memories',
      name: 'companionMemoryList',
      component: () => import('@/views/companion/memories/list.vue'),
      meta: {
        title: '记忆',
        icon: 'i-ri:brain-line',
        keepAlive: 'companionMemoryDetail',
      },
    },
    {
      path: 'memories/detail/:id?',
      name: 'companionMemoryDetail',
      component: () => import('@/views/companion/memories/detail.vue'),
      meta: {
        title: '记忆详情',
        menu: false,
        activeMenu: '/companion/memories',
        keepAlive: true,
        noKeepAlive: 'companionMemoryList',
      },
    },
    {
      path: 'expressions',
      name: 'companionExpressionPacks',
      component: () => import('@/views/companion/expressions/index.vue'),
      meta: {
        title: '表情与形象',
        icon: 'i-ri:emotion-happy-line',
      },
    },

  ],
}

const routes: RouteRecordMainRaw = {
  meta: {
    title: '陪伴',
    icon: 'i-ri:heart-3-line',
  },
  children: [companionRoute],
}

export default routes

export const personalDataRoute: RouteRecordRaw = {
  path: '/companion/personal-data',
  name: 'companionPersonalData',
  component: () => import('@/views/companion/personal-data/index.vue'),
  meta: {
    title: '对话与个人数据',
    icon: 'i-ri:shield-user-line',
  },
}
