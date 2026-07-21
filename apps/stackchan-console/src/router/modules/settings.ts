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
