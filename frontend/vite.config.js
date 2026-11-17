// Builds the Vue app and keeps API requests same-origin during local development.
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const proxy = { '/api': env.VITE_API_PROXY || 'http://localhost:8080' }

  return {
    plugins: [vue()],
    server: { proxy },
    preview: { proxy },
  }
})
