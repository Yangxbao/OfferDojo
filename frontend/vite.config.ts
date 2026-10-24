/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 开发与预览模式都把 /api 代理到本地后端，避免跨域配置分散在两处
export default defineConfig({
  plugins: [vue()],
  // 静态演示构建（npm run build:static-demo）：注入全局标志，
  // 前端请求全部走本地内嵌数据（见 src/static/demoApi.ts）
  define: process.env.BUILD_MODE === 'static-demo' ? { __STATIC_DEMO__: 'true' } : {},
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: true,
      },
    },
  },
})
