import { createPinia, setActivePinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick } from 'vue'
import { useAppSettingsStore } from '@/store/modules/app/settings'
import FloatingSidebarMenuButton from './index.vue'

vi.mock('@fantastic-admin/components', () => ({
  FaButton: defineComponent({
    setup: (_, { attrs, slots }) => () => h('button', attrs, slots.default?.()),
  }),
  FaIcon: defineComponent({ render: () => h('span') }),
}))

const cleanups: Array<() => void> = []

afterEach(() => {
  cleanups.splice(0).forEach(cleanup => cleanup())
})

describe('mobile sidebar navigation', () => {
  it('keeps a menu opener available after navigation when the toolbar is enabled', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const settings = useAppSettingsStore()
    settings.setMode(625)
    settings.settings.topbar.toolbar = true
    settings.settings.menu.subMenuCollapse = true

    const container = document.createElement('div')
    const app = createApp(FloatingSidebarMenuButton).use(pinia)
    app.mount(container)
    cleanups.push(() => app.unmount())
    await nextTick()

    const opener = () => container.querySelector<HTMLButtonElement>('button[aria-label="打开侧边栏导航"]')
    expect(opener()).not.toBeNull()

    opener()?.click()
    await nextTick()
    expect(settings.settings.menu.subMenuCollapse).toBe(false)
    expect(opener()).toBeNull()

    // 路由切换收起移动端侧栏后，重新打开入口仍然可见。
    settings.settings.menu.subMenuCollapse = true
    await nextTick()
    expect(opener()).not.toBeNull()
  })
})
