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
    title: '机器人',
    icon: 'i-ri:robot-2-line',
  },
  children: [
    {
      path: 'overview',
      name: 'deviceOverview',
      component: () => import('@/views/devices/overview/index.vue'),
      meta: {
        title: '设备与日常控制',
        icon: 'i-ri:dashboard-2-line',
      },
    },
    {
      path: 'pairing',
      name: 'devicePairing',
      component: () => import('@/views/devices/pairing/index.vue'),
      meta: {
        title: '配网与配对',
        icon: 'i-ri:wifi-line',
      },
    },
    {
      path: 'health',
      name: 'deviceHealth',
      component: () => import('@/views/devices/health/index.vue'),
      meta: {
        title: '运行健康',
        icon: 'i-ri:heart-pulse-line',
      },
    },
  ],
}

const routes: RouteRecordMainRaw = {
  meta: {
    title: '设备与运行',
    icon: 'i-ri:robot-2-line',
  },
  children: [deviceRoute],
}

export default routes
