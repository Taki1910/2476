import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': loadEnv(mode, '.', 'DEMO_').DEMO_API_TARGET ?? 'http://localhost:8080',
    },
  },
}))
