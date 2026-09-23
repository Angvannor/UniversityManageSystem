<!--
  ============================================================================
  文件：frontend/src/App.vue
  用途：根组件，整个界面的「外壳」。
    - 登录 / 注册页：直接渲染 router-view（全屏布局）；
    - 已登录：顶部栏 + 侧边菜单 + 内容区，
      菜单项按角色（学生 / 教师）动态显示。
  ============================================================================
-->
<template>
  <!-- 登录、注册页使用独立的全屏布局 -->
  <router-view v-if="isBlankLayout" />

  <!-- 业务页面：统一的后台布局 -->
  <el-container v-else class="layout">
    <el-header class="layout-header">
      <div class="logo">
        <el-icon :size="20"><School /></el-icon>
        <span>校园活动管理系统</span>
        <el-tag size="small" effect="plain" class="version-tag">V1.5</el-tag>
      </div>

      <div class="header-right">
        <el-tag :type="authStore.isTeacher ? 'warning' : 'success'" effect="dark" round>
          {{ authStore.isTeacher ? '教师' : '学生' }}
        </el-tag>
        <span class="user-name">{{ authStore.user?.name }}</span>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <el-container>
      <!-- 侧边菜单：按角色显示不同项 -->
      <el-aside width="200px" class="layout-aside">
        <el-menu :default-active="route.path" router>
          <!-- 学生菜单 -->
          <template v-if="!authStore.isTeacher">
            <el-menu-item index="/activities">
              <el-icon><Search /></el-icon>
              <span>浏览活动</span>
            </el-menu-item>
            <el-menu-item index="/my-registrations">
              <el-icon><Tickets /></el-icon>
              <span>我的报名</span>
            </el-menu-item>
          </template>

          <!-- 教师菜单 -->
          <template v-else>
            <el-menu-item index="/teacher/activities">
              <el-icon><Management /></el-icon>
              <span>活动管理</span>
            </el-menu-item>
            <el-menu-item index="/teacher/registrations">
              <el-icon><List /></el-icon>
              <span>报名名单</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

/** 登录页与注册页使用空白布局（不显示导航栏） */
const isBlankLayout = computed(() => ['/login', '/register'].includes(route.path))

/** 退出登录：清空状态后回到登录页 */
async function handleLogout() {
  await authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  /* 用 CSS 渐变做出品牌色头部 */
  background: linear-gradient(90deg, #1f4e8c, #2d6cdf);
  color: #fff;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}

.version-tag {
  margin-left: 4px;
  border-color: rgba(255, 255, 255, 0.6);
  color: #fff;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  font-size: 14px;
}

.layout-aside {
  background: #fff;
  border-right: 1px solid #ebeef5;
}

.layout-main {
  background: #f5f7fa;
  padding: 16px;
}
</style>
