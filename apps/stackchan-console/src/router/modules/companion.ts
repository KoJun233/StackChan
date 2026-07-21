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
