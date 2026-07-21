import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(viteConfig({ command: 'serve', mode: 'test' }), defineConfig({
  test: {
    environment: 'happy-dom',
    setupFiles: './src/test/setup.ts',
  },
}))
