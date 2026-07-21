import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const environment = { ...process.env, ...loadEnv(mode, process.cwd(), '') }
  const apiProxyTarget = environment.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'
  const llmApiProxyTarget = environment.VITE_LLM_API_PROXY_TARGET ?? apiProxyTarget

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api/v1/settings/llm': {
          target: llmApiProxyTarget,
          changeOrigin: true,
          secure: !llmApiProxyTarget.startsWith('https://'),
        },
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
          secure: !apiProxyTarget.startsWith('https://'),
        },
      },
    },
  }
})
