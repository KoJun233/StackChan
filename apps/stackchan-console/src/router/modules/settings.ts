import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'

function Layout() {
  return import('@/layouts/index.vue')
}

const settingsRoute: RouteRecordRaw = {
  path: '/settings',
  component: Layout,
  name: 'settings',
  meta: {
    title: '系统设置',
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
    {
      path: 'interaction',
      name: 'interactionSettings',
      component: () => import('@/views/settings/interaction/index.vue'),
      meta: {
        title: '交互与主动陪伴',
        icon: 'i-ri:chat-smile-3-line',
      },
    },
    {
      path: 'agent',
      name: 'agentCapabilities',
      component: () => import('@/views/settings/agent/index.vue'),
      meta: {
        title: 'Agent 能力',
        icon: 'i-ri:robot-2-line',
      },
    },
  ],
}

const routes: RouteRecordMainRaw = {
  meta: {
    title: '系统设置',
    icon: 'i-ri:settings-3-line',
  },
  children: [settingsRoute],
}

export default routes
