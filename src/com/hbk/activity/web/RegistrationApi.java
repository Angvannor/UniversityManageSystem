package com.hbk.activity.web;

import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.entity.User;
import com.hbk.activity.service.ActivityService;
import com.hbk.activity.service.AuthService;
import com.hbk.activity.service.RegistrationService;

import java.util.ArrayList;
import java.util.List;

/**
 * 报名接口（对应需求 REQ-03 报名、REQ-04 我的报名与取消、REQ-06 教师查看报名名单）。
 *
 * <p>提供的接口：
 * <pre>
 *   POST   /api/activities/{id}/registrations   学生报名      （仅学生）
 *   DELETE /api/activities/{id}/registrations   学生取消报名  （仅学生）
 *   GET    /api/registrations/mine              我的报名      （仅学生）
 *   GET    /api/activities/{id}/registrations   活动报名名单  （仅教师）
 * </pre>
 *
 * <p>【接口层在这里做的工作】
 * 报名表里只存 activityId / studentId，而界面上要显示活动标题、学生姓名，
 * 因此接口层查完报名记录后，需要"补全"关联信息再返回：
 * <ul>
 *   <li>"我的报名"：按 activityId 补活动标题、地点、时间（对应 {@link ApiVo.MyRegistrationVo}）；</li>
 *   <li>"报名名单"：按 studentId 补学号、姓名（对应 {@link ApiVo.RegistrationRecordVo}）。</li>
 * </ul>
 *
 * <p>报名的三条业务规则（活动存在、状态可报名、不能重复报名）都在 Service 层，
 * 接口层不重复实现。
 */
public class RegistrationApi {

    /** 报名业务对象 */
    private final RegistrationService registrationService = new RegistrationService();

    /** 活动业务对象：查活动信息用于补全显示字段 */
    private final ActivityService activityService = new ActivityService();

    /** 认证业务对象：按 id 查学生学号与姓名 */
    private final AuthService authService = new AuthService();

    /** 角色常量 */
    private static final String ROLE_TEACHER = "TEACHER";
    private static final String ROLE_STUDENT = "STUDENT";

    // ==================================================================
    // 一、学生报名与取消
    // ==================================================================

    /**
     * 学生报名活动。
     *
     * @param ctx 请求上下文（路径参数 id = 活动 id）
     * @return 报名记录 id
     */
    public ApiResult register(RequestContext ctx) {
        ctx.requireRole(ROLE_STUDENT);

        Long activityId = ctx.longPath("id");
        String error = registrationService.register(activityId, ctx.userId());
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(true);
    }

    /**
     * 学生取消报名。
     *
     * @param ctx 请求上下文（路径参数 id = 活动 id）
     * @return 是否成功
     */
    public ApiResult cancel(RequestContext ctx) {
        ctx.requireRole(ROLE_STUDENT);

        Long activityId = ctx.longPath("id");
        String error = registrationService.cancel(activityId, ctx.userId());
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(true);
    }

    // ==================================================================
    // 二、查询
    // ==================================================================

    /**
     * 学生查看"我的报名"（含已取消的记录）。
     *
     * @param ctx 请求上下文
     * @return 报名记录列表（每条附带活动标题、地点与时间）
     */
    public ApiResult mine(RequestContext ctx) {
        ctx.requireRole(ROLE_STUDENT);

        List<ActivityRegistration> records = registrationService.myRegistrations(ctx.userId());
        List<ApiVo.MyRegistrationVo> result = new ArrayList<>(records.size());
        for (ActivityRegistration r : records) {
            ApiVo.MyRegistrationVo vo = new ApiVo.MyRegistrationVo();
            vo.registrationId = r.getId();
            vo.activityId = r.getActivityId();
            vo.registerTime = r.getRegisterTime();
            vo.status = r.getStatus();

            // 补全活动信息（活动可能已被教师删除，所以要判空）
            Activity activity = activityService.detail(r.getActivityId());
            if (activity != null) {
                vo.title = activity.getTitle();
                vo.location = activity.getLocation();
                vo.startTime = activity.getStartTime();
                vo.endTime = activity.getEndTime();
            } else {
                vo.title = "（活动已删除）";
                vo.location = "-";
            }
            result.add(vo);
        }
        return ApiResult.ok(result);
    }

    /**
     * 教师查看某个活动的报名名单。
     *
     * <p>归属校验在 Service 层完成（不是自己的活动会返回空列表）。
     *
     * @param ctx 请求上下文（路径参数 id = 活动 id）
     * @return 报名名单（每条附带学生学号与姓名）
     */
    public ApiResult roster(RequestContext ctx) {
        ctx.requireRole(ROLE_TEACHER);

        Long activityId = ctx.longPath("id");
        List<ActivityRegistration> records =
                registrationService.activityRoster(activityId, ctx.userId());

        List<ApiVo.RegistrationRecordVo> result = new ArrayList<>(records.size());
        for (ActivityRegistration r : records) {
            ApiVo.RegistrationRecordVo vo = new ApiVo.RegistrationRecordVo();
            vo.registrationId = r.getId();
            vo.studentId = r.getStudentId();
            vo.registerTime = r.getRegisterTime();
            vo.status = r.getStatus();

            // 补全学生信息
            User student = authService.getById(r.getStudentId());
            vo.username = student == null ? "-" : student.getUsername();
            vo.studentName = student == null ? "-" : student.getName();
            result.add(vo);
        }
        return ApiResult.ok(result);
    }

    // ==================================================================
    // 三、内部辅助方法
    // ==================================================================

    /**
     * 把 Service 返回的中文提示映射成错误码。
     *
     * @param message Service 返回的失败原因
     * @return 错误码（前端对 3001 / 3003 / 3004 会做针对性提示）
     */
    private int codeOf(String message) {
        if (message.contains("不能重复报名")) {
            return 3001; // 重复报名
        }
        if (message.contains("已关闭报名") || message.contains("不可报名")) {
            return 3003; // 活动当前不可报名
        }
        if (message.contains("尚未报名")) {
            return 3004; // 无可取消的报名
        }
        if (message.contains("活动不存在")) {
            return ApiResult.CODE_NOT_FOUND;
        }
        if (message.contains("只能")) {
            return ApiResult.CODE_FORBIDDEN;
        }
        return ApiResult.CODE_BUSINESS;
    }
}
