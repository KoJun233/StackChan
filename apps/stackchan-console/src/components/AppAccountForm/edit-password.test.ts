import { createApp, defineComponent, h, onMounted } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

const accountStore = vi.hoisted(() => ({
  editPassword: vi.fn(),
}))

const toast = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
}))

vi.mock('@/store/modules/app/account', () => ({
  useAppAccountStore: () => accountStore,
}))

vi.mock('@fantastic-admin/components', async () => {
  const passthrough = defineComponent({
    setup(_, { slots }) {
      return () => h('div', slots.default?.())
    },
  })
  return {
    FaButton: passthrough,
    FaForm: defineComponent({
      emits: ['submit'],
      setup(_, { emit }) {
        onMounted(() => emit('submit', {
          password: 'old-password',
          newPassword: 'new-password-123',
          checkPassword: 'new-password-123',
        }))
        return () => h('form')
      },
    }),
    FaFormItem: passthrough,
    FaIcon: passthrough,
    FaInput: passthrough,
    useFaToast: () => toast,
  }
})

import EditPassword from './edit-password.vue'

describe('edit password form', () => {
  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('shows an actionable error when password rotation fails', async () => {
    accountStore.editPassword.mockRejectedValue(new Error('原密码不正确'))
    const container = document.createElement('div')
    document.body.append(container)

    createApp(EditPassword).mount(container)

    await vi.waitFor(() => expect(toast.error).toHaveBeenCalledWith('修改失败', {
      description: '原密码不正确',
    }))
    expect(toast.success).not.toHaveBeenCalled()
  })
})
