// ============================================================================
// 文件：frontend/src/api/admin.js
// 用途：系统管理员接口封装（V2.0 新增，对应 US-11 ~ US-13）。
//       对应后端 AdminApi。所有接口都只允许 ADMIN 角色调用，
//       其他角色请求会被后端返回 403。
// ============================================================================
import request from '@/api/request'

/**
 * 查看平台上的全部活动（只读监督，US-11）。
 *
 * 与「活动列表」的区别只在于范围：不受"只看自己发布的"限制，能看到全部教师的活动。
 * @returns {Promise<Array>} 活动列表（含发布教师姓名与人数）
 */
export function allActivities() {
  return request.get('/admin/activities')
}

/**
 * 查看全部用户账号及其状态（US-12）。
 *
 * 返回的每一项包含：id / username / name / role / status / statusText，
 * **不含密码**（后端已过滤）。
 * @returns {Promise<Array>}
 */
export function allUsers() {
  return request.get('/admin/users')
}

/**
 * 停用账号（US-13）。
 *
 * 停用是改状态而不是删除：账号与历史数据都保留，效果是不允许再登录。
 * 管理员不能停用自己的账号（后端会拒绝）。
 * @param {number|string} userId 目标用户 id
 * @returns {Promise<boolean>}
 */
export function disableUser(userId) {
  return request.put(`/admin/users/${userId}/disable`)
}

/**
 * 恢复账号为可用（US-13）。
 * @param {number|string} userId 目标用户 id
 * @returns {Promise<boolean>}
 */
export function enableUser(userId) {
  return request.put(`/admin/users/${userId}/enable`)
}
