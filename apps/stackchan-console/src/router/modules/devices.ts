import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'

function Layout() {
  return import('@/layouts/index.vue')
}

const deviceRoute: RouteRecordRaw = {
  path: '/devices',
  component: Layout,
  name: 'devices',
  meta: {
    title: '机器人设备',
    icon: 'i-ri:robot-2-line',
  },
  children: [
    {
      path: 'overview',
      name: 'deviceOverview',
      component: () => import('@/views/devices/overview/index.vue'),
      meta: {
        title: '设备总览',
        icon: 'i-ri:dashboard-2-line',
      },
    },
    {
      path: 'pairing',
      name: 'devicePairing',
      component: () => import('@/views/devices/pairing/index.vue'),
      meta: {
        title: '设备配网',
        icon: 'i-ri:wifi-line',
      },
    },
  ],
}

const routes: RouteRecordMainRaw = {
  meta: {
    title: '机器人设备',
    icon: 'i-ri:robot-2-line',
  },
  children: [deviceRoute],
}

export default routes
