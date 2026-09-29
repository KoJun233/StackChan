import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw, RouterMatcher } from 'vue-router'
import { cloneDeep } from 'es-toolkit'
import { createRouterMatcher } from 'vue-router'
import apiApp from '@/api/modules/app'
import { systemRoutes as systemRoutesRaw } from '@/router/routes'

export const useAppRouteStore = defineStore(
  'appRoute',
  () => {
    const isGenerate = ref(false)
    // 原始路由
    const routesRaw = ref<RouteRecordMainRaw[]>([])
    // 已注册的路由，用于登出时删除路由
    const currentRemoveRoutes = ref<(() => void)[]>([])

    // 实际路由
    const routes = computed(() => {
      const returnRoutes: RouteRecordRaw[] = []
      if (routesRaw.value) {
        routesRaw.value.forEach((item) => {
          const tmpRoutes = cloneDeep(item.children) as RouteRecordRaw[]
          tmpRoutes.map((v) => {
            if (!v.meta) {
              v.meta = {}
            }
            v.meta.auth = item.meta?.auth ?? v.meta?.auth
            return v
          })
          returnRoutes.push(...tmpRoutes)
        })
        returnRoutes.forEach((item) => {
          if (item.children) {
            item.children = deleteMiddleRouteComponent(item.children, !!item.component)
          }
          return item
        })
      }
      return returnRoutes
    })
    // 系统路由
    const systemRoutes = computed(() => {
      const routes = [...systemRoutesRaw]
      routes.forEach((item) => {
        if (item.children) {
          item.children = deleteMiddleRouteComponent(item.children, !!item.component)
        }
      })
      return routes
    })
    // 一条页面路径只保留最外层布局；无布局的菜单分组须保留子路由的布局。
    function deleteMiddleRouteComponent(routes: RouteRecordRaw[], hasLayout: boolean) {
      const res: RouteRecordRaw[] = []
      routes.forEach((route) => {
        if (route.children?.length) {
          if (hasLayout) {
            delete route.component
          }
          route.children = deleteMiddleRouteComponent(route.children, hasLayout || !!route.component)
        }
        else {
          delete route.children
        }
        res.push(route)
      })
      return res
    }

    // 路由匹配器
    const routesMatcher = ref<RouterMatcher>()
    // 根据路径获取匹配的路由
    function getRouteMatchedByPath(path: string) {
      return routesMatcher.value?.resolve({ path }, undefined!)?.matched ?? []
    }

    // 路由排序，sort 越大越靠前
    function sortAsyncRoutes<T extends RouteRecordMainRaw[] | RouteRecordRaw[]>(routes: T): T {
      routes.sort((a, b) => (b.meta?.sort ?? 0) - (a.meta?.sort ?? 0))
      routes.forEach((route) => {
        if (route.children) {
          route.children = sortAsyncRoutes(route.children)
        }
      })
      return routes
    }

    // 生成路由（前端生成）
    function generateRoutesAtFront(asyncRoutes: RouteRecordMainRaw[]) {
      // 设置 routes 数据
      routesRaw.value = sortAsyncRoutes(cloneDeep(asyncRoutes) as any)
      // 创建路由匹配器
      const routes: RouteRecordRaw[] = []
      routesRaw.value.forEach((route) => {
        if (route.children) {
          routes.push(...route.children)
        }
      })
      routesMatcher.value = createRouterMatcher(routes, {})
      isGenerate.value = true
    }
    // 格式化后端路由数据
    function formatBackRoutes(routes: any, views = import.meta.glob('@/views/**/*.vue')): RouteRecordMainRaw[] {
      return routes.map((route: any) => {
        switch (route.component) {
          case 'Layout':
            route.component = () => import('@/layouts/index.vue')
            break
          default:
            if (route.component) {
              route.component = views[`/src/views/${route.component}`]
            }
            else {
              delete route.component
            }
        }
        if (route.children) {
          route.children = formatBackRoutes(route.children, views)
        }
        return route
      })
    }
    // 生成路由（后端获取）
    async function generateRoutesAtBack() {
      await apiApp.routeList().then((res) => {
        // 设置 routes 数据
        routesRaw.value = sortAsyncRoutes(formatBackRoutes(res.data) as any)
        // 创建路由匹配器
        const routes: RouteRecordRaw[] = []
        routesRaw.value.forEach((route) => {
          if (route.children) {
            routes.push(...route.children)
          }
        })
        routesMatcher.value = createRouterMatcher(routes, {})
        isGenerate.value = true
      })
    }
    function setCurrentRemoveRoutes(routes: (() => void)[]) {
      currentRemoveRoutes.value = routes
    }
    // 清空动态路由
    function removeRoutes() {
      isGenerate.value = false
      routesRaw.value = []
      currentRemoveRoutes.value.forEach((removeRoute) => {
        removeRoute()
      })
      currentRemoveRoutes.value = []
    }

    return {
      isGenerate,
      routesRaw,
      currentRemoveRoutes,
      routes,
      systemRoutes,
      getRouteMatchedByPath,
      generateRoutesAtFront,
      generateRoutesAtBack,
      setCurrentRemoveRoutes,
      removeRoutes,
    }
  },
)
