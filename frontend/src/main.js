// ============================================================================
// 文件：frontend/src/main.js
// 用途：前端应用入口。依次：
//   1. 创建 Vue 应用实例；
//   2. 安装 Pinia（登录状态）与 Vue Router（页面路由）；
//   3. 安装 Element Plus 组件库与图标；
//   4. 引入全局样式，挂载到 index.html 的 #app 节点。
// ============================================================================
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import 'element-plus/dist/index.css'
import '@/styles/global.css'

import App from '@/App.vue'
import router from '@/router'

const app = createApp(App)

// 全局注册 Element Plus 图标，模板里可直接写 <el-icon><Plus /></el-icon>
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(createPinia())                 // 状态管理
app.use(router)                        // 路由与守卫
app.use(ElementPlus, { locale: zhCn }) // 组件库（中文）

app.mount('#app')
