import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'

function Layout() {
  return import('@/layouts/index.vue')
}

const companionRoute: RouteRecordRaw = {
  path: '/companion',
  component: Layout,
  name: 'companion',
  meta: {
    title: 'AI 陪伴',
    icon: 'i-ri:heart-3-line',
  },
  children: [
    {
      path: 'chat',
      name: 'companionChat',
      component: () => import('@/views/companion/chat/index.vue'),
      meta: {
        title: '陪伴聊天',
        icon: 'i-ri:chat-smile-3-line',
        keepAlive: true,
      },
    },
    {
      path: 'persona',
      name: 'companionPersona',
      component: () => import('@/views/companion/persona/index.vue'),
      meta: {
        title: '人设设置',
        icon: 'i-ri:user-heart-line',
      },
    },
    {
      path: 'memories',
      name: 'companionMemoryList',
      component: () => import('@/views/companion/memories/list.vue'),
      meta: {
        title: '长期记忆',
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
  ],
}

const routes: RouteRecordMainRaw = {
  meta: {
    title: 'AI 陪伴',
    icon: 'i-ri:heart-3-line',
  },
  children: [companionRoute],
}

export default routes
