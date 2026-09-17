// ============================================================================
// 文件：frontend/vite.config.js
// 用途：前端构建与开发服务器配置。
//
// 关于「假接口」开关（USE_MOCK）：
//   true  —— 使用 mock/mock-api.js 提供的本地假接口。
//            后端接口层还没写出来之前，前端可以独立开发和调试。
//   false —— 关闭假接口，把 /api 请求代理到 http://localhost:8080
//            的真实后端（V2.0 用 JDK 内置 HttpServer 写的那一层）。
//
//   两种模式下前端源码完全一样，切换只需改这一个常量。
// ============================================================================
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import mockApi from './mock/mock-api.js'

/** 是否使用本地假接口；后端接口层完成后改成 false 即可切到真接口 */
const USE_MOCK = true

export default defineConfig({
  plugins: [
    vue(),
    // 只有开启假接口时才加载 mock 插件
    ...(USE_MOCK ? [mockApi()] : [])
  ],

  resolve: {
    alias: {
      // 用 @ 代表 src 目录，避免出现 ../../.. 这类脆弱相对路径
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },

  server: {
    port: 5173,
    open: true,
    // 关闭假接口时才需要代理；开着假接口时请求会被 mock 插件拦下
    proxy: USE_MOCK
      ? undefined
      : {
          '/api': {
            target: 'http://localhost:8080',
            changeOrigin: true
          }
        }
  }
})
