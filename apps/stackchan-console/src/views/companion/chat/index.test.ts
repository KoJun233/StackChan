import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, ref } from 'vue'
import Chat from './index.vue'

const api = vi.hoisted(() => ({ create: vi.fn(), list: vi.fn(), messages: vi.fn(), stream: vi.fn() }))
const partnerId = '550e8400-e29b-41d4-a716-446655440000'
vi.mock('@/api/modules/roles', () => ({ listRoles: async () => [{ id: '550e8400-e29b-41d4-a716-446655440000', name: '小伙伴', defaultRole: true }] }))
vi.mock('@/api/modules/companion', () => ({
  createConversation: api.create,
  listConversations: api.list,
  getConversationMessages: api.messages,
  streamMessage: api.stream,
  StreamMessageServerError: class extends Error {},
}))
vi.mock('vue-router', async original => ({ ...await original<typeof import('vue-router')>(), useRoute: () => ({ name: 'companionChat', query: {} }) }))
vi.mock('@vueuse/core', async original => ({ ...await original<typeof import('@vueuse/core')>(), useMediaQuery: () => ref(true) }))
vi.mock('@tdesign-vue-next/chat/es/chat-actionbar', () => ({ default: defineComponent({ render: () => h('div') }) }))
vi.mock('@tdesign-vue-next/chat/es/chat-content', () => ({ default: defineComponent({ render: () => h('div') }) }))
vi.mock('@tdesign-vue-next/chat/es/chat-list', () => ({ default: defineComponent({ render: () => h('div') }) }))
vi.mock('@fantastic-admin/components', () => {
  const box = defineComponent({
    props: { title: String, description: String },
    setup: (props, { slots }) => () => h('div', [props.title, props.description, slots.header?.(), slots.default?.(), slots.action?.()]),
  })
  return {
    FaCard: box,
    FaPageHeader: box,
    FaPageMain: box,
    FaAlert: box,
    FaIcon: box,
    FaScrollArea: box,
    FaDrawer: box,
    FaSelect: box,
    FaButton: defineComponent({
      props: { disabled: Boolean, loading: Boolean },
      emits: ['click'],
      setup: (props, { slots, emit }) => () => h('button', {
        disabled: props.disabled || props.loading,
        onClick: (event: MouseEvent) => emit('click', event),
      }, slots.default?.()),
    }),
    FaTextarea: defineComponent({
      props: { modelValue: String, disabled: Boolean },
      emits: ['update:modelValue', 'keydown'],
      setup: (props, { emit }) => () => h('textarea', {
        value: props.modelValue,
        disabled: props.disabled,
        onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
        onKeydown: (event: KeyboardEvent) => emit('keydown', event),
      }),
    }),
    useFaToast: () => ({ success: vi.fn(), error: vi.fn() }),
  }
})

const cleanups: Array<() => void> = []
async function mountChat() {
  const container = document.createElement('div')
  const app = createApp(Chat).use(createPinia())
  app.mount(container)
  cleanups.push(() => app.unmount())
  await vi.waitFor(() => expect(api.list).toHaveBeenCalledWith(partnerId))
  await nextTick()
  return container
}
function click(container: HTMLElement, label: string) {
  Array.from(container.querySelectorAll('button')).find(button => button.textContent?.trim() === label)?.click()
}
async function type(container: HTMLElement, content: string) {
  const textarea = container.querySelector('textarea')!
  textarea.value = content
  textarea.dispatchEvent(new Event('input'))
  await nextTick()
  return textarea
}

describe('chat page recovery and native events', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.list.mockResolvedValue([])
    api.messages.mockResolvedValue([])
    api.create.mockResolvedValue({ id: 'conversation', roleId: partnerId, title: '新对话' })
    api.stream.mockResolvedValue(undefined)
  })
  afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()))

  it('passes the current partner UUID, not the native click event, to creation', async () => {
    const container = await mountChat()
    click(container, '新对话')
    await vi.waitFor(() => expect(api.create).toHaveBeenCalledWith(partnerId))
    expect(api.create).toHaveBeenCalledOnce()
  })

  it('restores an editable draft after initial creation fails', async () => {
    api.create.mockRejectedValueOnce(new Error('断网了'))
    const container = await mountChat()
    await type(container, '今天想聊聊')
    click(container, '发送')
    await vi.waitFor(() => expect(container.textContent).toContain('断网了'))
    await vi.waitFor(() => expect(container.querySelector('textarea')?.value).toBe('今天想聊聊'))
    expect(container.textContent).toContain('断网了')
    expect(container.querySelector('textarea')?.disabled).toBe(false)
    expect(api.stream).not.toHaveBeenCalled()
    click(container, '发送')
    await vi.waitFor(() => expect(api.stream).toHaveBeenCalledOnce())
    expect(container.querySelector('textarea')?.value).toBe('')
  })

  it('does not send while the Chinese input method is composing', async () => {
    const container = await mountChat()
    const textarea = await type(container, '你好')
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', isComposing: true }))
    await nextTick()
    expect(api.create).not.toHaveBeenCalled()
    expect(textarea.value).toBe('你好')
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true }))
    await nextTick()
    expect(api.create).not.toHaveBeenCalled()
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    await vi.waitFor(() => expect(api.stream).toHaveBeenCalledOnce())
  })
})
