/// <reference types="vite/client" />
interface ImportMetaEnv {
  // Auto generate by env-parse
  /**
   * 网络请求地址，应用于 axios 的 baseURL
   * Network request address, applied to axios's baseURL
   */
  readonly VITE_APP_API_BASEURL: string
  /**
   * 调试工具，可设置 eruda 或 vconsole
   * Debugging tool, can set eruda or vconsole
   */
  readonly VITE_APP_DEBUG_TOOL: string
  /**
   * 应用配置面板
   * Application configuration panel
   */
  readonly VITE_APP_SETTING: boolean
  /**
   * localStorage/sessionStorage 前缀
   * localStorage/sessionStorage prefix
   */
  readonly VITE_APP_STORAGE_PREFIX: string
  /**
   * 网站标题
   * Website title
   */
  readonly VITE_APP_TITLE: string
  /**
   * 构建后生成存档，支持 zip 和 tar
   * Generate archive after build, supports zip and tar
   */
  readonly VITE_BUILD_ARCHIVE: string
  /**
   * 压缩方式，支持 gzip 和 brotli
   * Build compression method, supports gzip and brotli
   */
  readonly VITE_BUILD_COMPRESS: string
  /**
   * 启用假数据
   * Enable build fake data
   */
  readonly VITE_BUILD_FAKE: boolean
  /**
   * 启用 sourcemap
   * Enable build sourcemap
   */
  readonly VITE_BUILD_SOURCEMAP: boolean
}
