// ============================================================================
// 文件：frontend/src/stores/auth.js
// 用途：登录状态管理（Pinia store）。集中保存：
//   - token：登录令牌，持久化到 localStorage，刷新页面不丢失；
//   - user ：当前登录用户（id / 账号 / 姓名 / 角色）。
// 并通过 getter 提供 isLogin / isTeacher / isStudent，
// 供路由守卫与 App.vue 的菜单显示使用。
//
// 对应报告「设计决策二」：前端按角色展示不同菜单，后端再做真正的权限校验。
// ============================================================================
import { defineStore } from 'pinia'
import { getCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth'
import { TOKEN_KEY, USER_KEY } from '@/api/request'

/** 从 localStorage 读取用户信息，解析失败时返回 null */
function readStoredUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    /** 登录令牌 */
    token: localStorage.getItem(TOKEN_KEY) || '',
    /** 当前登录用户信息 */
    user: readStoredUser()
  }),

  getters: {
    /** 是否已登录 */
    isLogin: (state) => Boolean(state.token),
    /** 是否教师 */
    isTeacher: (state) => state.user?.role === 'TEACHER',
    /** 是否学生 */
    isStudent: (state) => state.user?.role === 'STUDENT'
  },

  actions: {
    /**
     * 登录：调用接口，成功后保存令牌与用户信息。
     * @param {{username: string, password: string}} form
     */
    async login(form) {
      const data = await loginApi(form)
      this.setAuth(data.token, data.user)
      return data.user
    },

    /**
     * 拉取当前用户信息：页面刷新后若本地只有 token，用它补全并顺便校验令牌是否有效。
     */
    async fetchCurrentUser() {
      const user = await getCurrentUser()
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
      return user
    },

    /** 保存登录状态到内存与 localStorage */
    setAuth(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },

    /** 退出登录：通知后端（失败也不影响本地退出），然后清空状态 */
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 令牌可能已失效，忽略错误
      } finally {
        this.clear()
      }
    },

    /** 清空登录状态 */
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
