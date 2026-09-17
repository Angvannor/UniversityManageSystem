// ============================================================================
// 文件：frontend/src/api/activity.js
// 用途：活动相关接口封装（REQ-02 浏览活动、REQ-05 教师发布与管理活动）。
//       对应后端 ActivityController：/api/activities/**
// ============================================================================
import request from '@/api/request'

/**
 * 查询活动列表。
 * @param {{keyword?: string, status?: string, onlyMine?: boolean}} params 查询条件
 * @returns {Promise<Array>} 活动列表（含教师姓名、报名人数、当前学生是否已报名）
 */
export function listActivities(params) {
  return request.get('/activities', { params })
}

/**
 * 查询活动详情。
 * @param {number|string} id 活动 id
 * @returns {Promise<object>}
 */
export function getActivity(id) {
  return request.get(`/activities/${id}`)
}

/**
 * 教师发布活动。
 * @param {object} data 活动参数
 * @returns {Promise<number>} 新活动 id
 */
export function createActivity(data) {
  return request.post('/activities', data)
}

/**
 * 教师修改活动。
 * @param {number|string} id 活动 id
 * @param {object} data 活动参数
 * @returns {Promise<boolean>}
 */
export function updateActivity(id, data) {
  return request.put(`/activities/${id}`, data)
}

/**
 * 教师关闭活动报名（状态改为 CLOSED）。
 * @param {number|string} id 活动 id
 * @returns {Promise<boolean>}
 */
export function closeActivity(id) {
  return request.put(`/activities/${id}/close`)
}

/**
 * 教师删除活动（已有有效报名时后端会拒绝并给出原因）。
 * @param {number|string} id 活动 id
 * @returns {Promise<boolean>}
 */
export function deleteActivity(id) {
  return request.delete(`/activities/${id}`)
}
