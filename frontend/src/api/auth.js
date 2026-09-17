// ============================================================================
// 文件：frontend/src/api/auth.js
// 用途：认证相关接口封装（对应需求 REQ-01 注册与登录）。
//       页面只调用这里的函数，不直接写 URL，接口调整时只需改这一处。
//       对应后端 AuthController：/api/auth/**
// ============================================================================
import request from '@/api/request'

/**
 * 注册账号。
 * @param {{username: string, password: string, name: string, role: string}} data
 * @returns {Promise<object>} 注册成功的用户信息（不含密码）
 */
export function register(data) {
  return request.post('/auth/register', data)
}

/**
 * 登录：成功后返回 { token, user }。
 * @param {{username: string, password: string}} data
 * @returns {Promise<{token: string, user: object}>}
 */
export function login(data) {
  return request.post('/auth/login', data)
}

/**
 * 退出登录。
 * @returns {Promise<void>}
 */
export function logout() {
  return request.post('/auth/logout')
}

/**
 * 获取当前登录用户信息（用于刷新页面后恢复登录状态）。
 * @returns {Promise<object>}
 */
export function getCurrentUser() {
  return request.get('/auth/me')
}
