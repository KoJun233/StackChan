import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'
import { personalDataRoute } from './companion'
import NotificationRoutes from './notifications'

function Layout() {
  return import('@/layouts/index.vue')
}

const settingsRoute: RouteRecordRaw = {
  path: '/settings',
  name: 'settings',
  meta: {
    title: '设置与数据',
    expand: false,
    icon: 'i-ri:settings-3-line',
  },
  children: [
    {
      path: 'llm',
      name: 'llmSettingsMenu',
      component: Layout,
      meta: { title: 'AI 配置', icon: 'i-ri:ai-generate-2' },
      children: [{ path: '', name: 'llmSettings', component: () => import('@/views/settings/llm/index.vue'), meta: { title: 'AI 配置', menu: false, breadcrumb: false } }],
    },
    {
      path: 'speech',
      name: 'speechSettingsMenu',
      component: Layout,
      meta: { title: '语音配置', icon: 'i-ri:mic-line' },
      children: [{ path: '', name: 'speechSettings', component: () => import('@/views/settings/speech/index.vue'), meta: { title: '语音配置', menu: false, breadcrumb: false } }],
    },
  ],
}

settingsRoute.children!.push(
  {
    path: '/companion/personal-data',
    name: 'personalDataMenu',
    component: Layout,
    meta: { title: '对话与个人数据', icon: 'i-ri:shield-user-line' },
    children: [{ ...personalDataRoute, path: '', meta: { ...personalDataRoute.meta, menu: false, breadcrumb: false } }],
  },

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
)

const advancedRoute: RouteRecordRaw = {
  path: '/advanced',
  name: 'advancedExtensions',
  redirect: '/settings/agent',
  meta: { title: '高级扩展', menu: false, expand: false },
}

const routes: RouteRecordMainRaw = {
  meta: {
    title: '系统与能力',
    icon: 'i-ri:settings-3-line',
  },
  children: [settingsRoute, advancedRoute],
}

export default routes
