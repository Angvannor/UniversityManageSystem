// ============================================================================
// 文件：frontend/src/api/registration.js
// 用途：报名相关接口封装（REQ-03 报名、REQ-04 我的报名与取消、REQ-06 报名名单）。
//       对应后端 RegistrationController。
// ============================================================================
import request from '@/api/request'

/**
 * 学生报名活动。
 * @param {number|string} activityId 活动 id
 * @returns {Promise<number>} 报名记录 id
 */
export function registerActivity(activityId) {
  return request.post(`/activities/${activityId}/registrations`)
}

/**
 * 学生取消报名。
 * @param {number|string} activityId 活动 id
 * @returns {Promise<boolean>}
 */
export function cancelRegistration(activityId) {
  return request.delete(`/activities/${activityId}/registrations`)
}

/**
 * 学生查看我的报名（含已取消记录）。
 * @returns {Promise<Array>}
 */
export function myRegistrations() {
  return request.get('/registrations/mine')
}

/**
 * 教师查看某个活动的报名名单。
 * @param {number|string} activityId 活动 id
 * @returns {Promise<Array>}
 */
export function registrationsOfActivity(activityId) {
  return request.get(`/activities/${activityId}/registrations`)
}
