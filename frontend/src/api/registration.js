// ============================================================================
// 文件：frontend/src/api/registration.js
// 用途：报名相关接口封装（REQ-03 报名、REQ-04 我的报名与取消、REQ-06 报名名单，
//       以及 V2.0 新增的 US-07 审核、US-09 候补递补）。
//       对应后端 RegistrationApi。
// ============================================================================
import request from '@/api/request'

/**
 * 学生报名活动。
 * @param {number|string} activityId 活动 id
 * @returns {Promise<boolean>} 是否成功（成功后该报名处于「待审核」）
 */
export function registerActivity(activityId) {
  return request.post(`/activities/${activityId}/registrations`)
}

/**
 * 学生取消报名。
 * 待审核 / 候补 / 正式参加三种状态都可以取消。
 * @param {number|string} activityId 活动 id
 * @returns {Promise<boolean>}
 */
export function cancelRegistration(activityId) {
  return request.delete(`/activities/${activityId}/registrations`)
}

/**
 * 学生查看我的报名（含已取消、未通过的记录）。
 * 每条记录带 status 与 statusText（中文），前端直接显示 statusText。
 * @returns {Promise<Array>}
 */
export function myRegistrations() {
  return request.get('/registrations/mine')
}

/**
 * 教师查看某个活动的报名名单（V2.0：按状态分组返回）。
 * @param {number|string} activityId 活动 id
 * @returns {Promise<Object>} 形如
 *   { activityId, activityTitle, capacity, confirmedCount, waitlistedCount,
 *     pendingReviewCount, full, pendingReview: [], waitlisted: [], confirmed: [] }
 */
export function registrationsOfActivity(activityId) {
  return request.get(`/activities/${activityId}/registrations`)
}

// ---------------------------------------------------------------------------
// 以下三个是 V2.0 新增的教师操作
// ---------------------------------------------------------------------------

/**
 * 教师审核通过一条报名。
 *
 * 通过之后是「正式参加」还是「候补」，由后端根据名额自动判断 ——
 * 前端不需要自己算，也不应该自己算（算错了教师会看到与实际不符的状态）。
 *
 * @param {number|string} activityId 活动 id
 * @param {number|string} studentId  被审核的学生 id
 * @returns {Promise<boolean>}
 */
export function approveRegistration(activityId, studentId) {
  return request.put(`/activities/${activityId}/registrations/${studentId}/approve`)
}

/**
 * 教师驳回一条报名（判定为不符合参加条件）。
 * @param {number|string} activityId 活动 id
 * @param {number|string} studentId  被驳回的学生 id
 * @returns {Promise<boolean>}
 */
export function rejectRegistration(activityId, studentId) {
  return request.put(`/activities/${activityId}/registrations/${studentId}/reject`)
}

/**
 * 教师把空出的名额递给某个候补学生（V2.0 新增）。
 *
 * 递补是教师手动触发的：系统只负责把候补名单按进入候补的时间排好序，
 * 具体给谁由教师决定，不强制按队列顺序（前面的人可能联系不上）。
 *
 * @param {number|string} activityId 活动 id
 * @param {number|string} studentId  被递补的学生 id
 * @returns {Promise<boolean>}
 */
export function promoteRegistration(activityId, studentId) {
  return request.put(`/activities/${activityId}/registrations/${studentId}/promote`)
}
