// ============================================================================
// 文件：frontend/src/api/request.js
// 用途：Axios 实例与统一拦截器。所有接口模块都基于它发起请求：
//   1. 统一 baseURL（/api）与超时时间；
//   2. 请求拦截：自动附加 "Authorization: Bearer <token>"；
//   3. 响应拦截：后端统一返回 {code, message, data}
//      —— code === 0   时把 data 交给调用方；
//      —— code === 401 时清除登录状态并跳转登录页；
//      —— 其他错误码统一弹出 message 提示。
//
// 说明：开发期的假接口（mock/mock-api.js）返回的结构与真接口完全一致，
//       因此本文件在「假接口 / 真接口」两种模式下都不需要修改。
// ============================================================================
import axios from 'axios'
import { ElMessage } from 'element-plus'

/** 后端约定的成功状态码 */
const SUCCESS_CODE = 0

/** 未登录 / 令牌失效状态码 */
const UNAUTHORIZED_CODE = 401

/** localStorage 中保存令牌的键名 */
export const TOKEN_KEY = 'ums_token'

/** localStorage 中保存用户信息的键名 */
export const USER_KEY = 'ums_user'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// ------------------------------ 请求拦截器 ------------------------------
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/**
 * 处理未登录：清除本地登录信息并跳转登录页。
 * 放在这里统一处理，避免每个页面都写一遍。
 */
function handleUnauthorized() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  if (!window.location.pathname.startsWith('/login')) {
    ElMessage.warning('登录状态已失效，请重新登录')
    window.location.href = '/login'
  }
}

// ------------------------------ 响应拦截器 ------------------------------
request.interceptors.response.use(
  (response) => {
    const body = response.data

    // 后端未按统一结构返回时（例如静态资源），直接返回原始数据
    if (body === null || typeof body !== 'object' || !('code' in body)) {
      return body
    }

    if (body.code === SUCCESS_CODE) {
      return body.data
    }

    if (body.code === UNAUTHORIZED_CODE) {
      handleUnauthorized()
      return Promise.reject(new Error(body.message || '未登录'))
    }

    // 业务失败：弹出后端返回的原因，并抛出错误让页面自行决定是否额外处理
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    // HTTP 层面的错误（网络异常、真接口返回 401 等）
    if (error.response && error.response.status === UNAUTHORIZED_CODE) {
      handleUnauthorized()
      return Promise.reject(error)
    }
    ElMessage.error(error.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default request
