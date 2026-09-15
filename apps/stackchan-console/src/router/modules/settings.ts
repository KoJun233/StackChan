import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'
import NotificationRoutes from './notifications'

function Layout() {
  return import('@/layouts/index.vue')
}

const settingsRoute: RouteRecordRaw = {
  path: '/settings',
  component: Layout,
  name: 'settings',
  meta: {
    title: '能力配置',
    icon: 'i-ri:settings-3-line',
  },
  children: [
    {
      path: 'llm',
      name: 'llmSettings',
      component: () => import('@/views/settings/llm/index.vue'),
      meta: {
        title: 'AI 配置',
        icon: 'i-ri:ai-generate-2',
      },
    },
    {
      path: 'speech',
      name: 'speechSettings',
      component: () => import('@/views/settings/speech/index.vue'),
      meta: {
        title: '语音配置',
        icon: 'i-ri:mic-line',
      },
    },
  ],
}

const advancedRoute: RouteRecordRaw = {
  path: '/advanced',
  name: 'advancedExtensions',
  meta: { title: '高级扩展', icon: 'i-ri:tools-line', expand: false },
  children: [
    {
      path: '/settings/agent',
      name: 'agentCapabilitiesMenu',
      component: Layout,
      meta: { title: '可用帮助与扩展', icon: 'i-ri:robot-2-line' },
      children: [{
        path: '',
        name: 'agentCapabilities',
        component: () => import('@/views/settings/agent/index.vue'),
        meta: { title: '可用帮助与扩展', menu: false, breadcrumb: false },
      }],
    },
    ...(NotificationRoutes.children ?? []),
  ],
}

const routes: RouteRecordMainRaw = {
  meta: {
    title: '系统与能力',
    icon: 'i-ri:settings-3-line',
  },
  children: [settingsRoute, advancedRoute],
}

export default routes
