// ============================================================================
// 文件：frontend/src/router/index.js
// 用途：前端路由表与导航守卫。
//   1. 定义页面路径与组件的映射（学生页面 / 教师页面）；
//   2. 守卫：未登录访问业务页面 → 跳登录页；
//            已登录访问登录页  → 跳回各自首页；
//            角色不匹配        → 跳回各自首页并提示。
//
// 说明：前端的角色守卫只是「界面友好性」处理，
//       真正的权限校验在后端（业务层校验活动归属 + 接口层校验角色）。
// ============================================================================
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const routes = [
  // 默认首页：按角色重定向
  { path: '/', redirect: '/activities' },

  // ---------------------------- 公开页面 ----------------------------
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', public: true }
  },

  // ---------------------------- 学生页面 ----------------------------
  {
    path: '/activities',
    name: 'ActivityList',
    component: () => import('@/views/student/ActivityList.vue'),
    meta: { title: '浏览活动', role: 'STUDENT' }
  },
  {
    path: '/activities/:id',
    name: 'ActivityDetail',
    component: () => import('@/views/student/ActivityDetail.vue'),
    meta: { title: '活动详情', role: 'STUDENT' }
  },
  {
    path: '/my-registrations',
    name: 'MyRegistrations',
    component: () => import('@/views/student/MyRegistrations.vue'),
    meta: { title: '我的报名', role: 'STUDENT' }
  },

  // ---------------------------- 教师页面 ----------------------------
  {
    path: '/teacher/activities',
    name: 'TeacherActivities',
    component: () => import('@/views/teacher/ActivityManage.vue'),
    meta: { title: '活动管理', role: 'TEACHER' }
  },
  {
    path: '/teacher/registrations',
    name: 'TeacherRegistrations',
    component: () => import('@/views/teacher/ActivityRoster.vue'),
    meta: { title: '报名名单', role: 'TEACHER' }
  },

  // 未匹配路径统一回首页
  { path: '/:pathMatch(.*)*', redirect: '/activities' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置守卫：登录校验与角色跳转。
 */
router.beforeEach((to) => {
  const authStore = useAuthStore()

  // 1. 公开页面（登录、注册）
  if (to.meta.public) {
    // 已登录用户访问登录页 → 跳回自己的首页
    if (authStore.isLogin) {
      return authStore.isTeacher ? '/teacher/activities' : '/activities'
    }
    return true
  }

  // 2. 未登录 → 跳登录页
  if (!authStore.isLogin) {
    ElMessage.warning('请先登录')
    return '/login'
  }

  // 3. 角色不匹配 → 跳回该角色自己的首页
  if (to.meta.role && to.meta.role !== authStore.user?.role) {
    ElMessage.warning('当前角色无权访问该页面')
    return authStore.isTeacher ? '/teacher/activities' : '/activities'
  }

  return true
})

/** 后置钩子：把页面标题写入浏览器标签页 */
router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} - 校园活动管理系统 V1.5`
    : '校园活动管理系统 V1.5'
})

export default router
