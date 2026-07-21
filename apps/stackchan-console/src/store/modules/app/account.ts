import * as authApi from '@/api/modules/auth'
import router from '@/router'

export const useAppAccountStore = defineStore('appAccount', () => {
  const appSettingsStore = useAppSettingsStore()
  const appTabbarStore = useAppTabbarStore()
  const appRouteStore = useAppRouteStore()
  const appMenuStore = useAppMenuStore()

  // 账号信息
  const token = ref(sessionStorage.getItem('authenticated') ?? '')
  const account = ref(sessionStorage.getItem('account') ?? '')
  const avatar = ref('')

  // 权限信息
  const permissions = ref<string[]>([])

  // 登录状态
  const isLogin = computed(() => {
    if (token.value) {
      return true
    }
    return false
  })

  // 登录
  async function login(data: {
    account: string
    password: string
  }) {
    await authApi.login({ username: data.account, password: data.password })
    sessionStorage.setItem('authenticated', 'true')
    sessionStorage.setItem('account', data.account)
    account.value = data.account
    token.value = 'session'
  }

  // 手动登出
  async function logout(redirect = router.currentRoute.value.fullPath) {
    let logoutError: unknown
    try {
      await authApi.logout()
    }
    catch (error) {
      logoutError = error
    }
    clearSession()
    try {
      await router.push({
        name: 'login',
        query: {
          ...(redirect !== appSettingsStore.settings.app.home.fullPath && router.currentRoute.value.name !== 'login' && { redirect }),
        },
      })
    }
    finally {
      logoutCleanStatus()
    }
    if (logoutError !== undefined) {
      throw logoutError
    }
  }

  // 请求登出
  async function requestLogout() {
    try {
      clearSession()
      await router.push({
        name: 'login',
        query: {
          ...(
            router.currentRoute.value.fullPath !== appSettingsStore.settings.app.home.fullPath
            && router.currentRoute.value.name !== 'login'
            && {
              redirect: router.currentRoute.value.fullPath,
            }
          ),
        },
      })
    }
    finally {
      logoutCleanStatus()
    }
  }

  // 登出后清除状态
  function logoutCleanStatus() {
    sessionStorage.removeItem('account')
    sessionStorage.removeItem('authenticated')
    token.value = ''
    account.value = ''
    avatar.value = ''
    permissions.value = []
    appSettingsStore.updateSettings({}, true)
    appTabbarStore.clean()
    appRouteStore.removeRoutes()
    appMenuStore.setActived(0)
  }

  function clearSession() {
    token.value = ''
    sessionStorage.removeItem('authenticated')
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('stackchan:session-expired', () => {
      if (token.value) {
        void requestLogout().catch(() => {})
      }
    })
  }

  // 获取权限
  async function getPermissions() {
    permissions.value = ['admin']
  }

  // 修改密码
  async function editPassword(data: {
    password: string
    newPassword: string
  }) {
    await authApi.changePassword({
      currentPassword: data.password,
      newPassword: data.newPassword,
    })
    await requestLogout()
  }

  return {
    token,
    account,
    avatar,
    permissions,
    isLogin,
    login,
    logout,
    requestLogout,
    getPermissions,
    editPassword,
  }
})
