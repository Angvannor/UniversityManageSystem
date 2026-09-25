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
 * 报名接口（对应需求 REQ-03 报名、REQ-04 我的报名与取消、REQ-06 教师查看报名名单，
 * 以及 V2.0 新增的 US-07 审核、US-09 候补递补）。
 *
 * <p>提供的接口：
 * <pre>
 *   POST   /api/activities/{id}/registrations                      学生报名      （仅学生）
 *   DELETE /api/activities/{id}/registrations                      学生取消报名  （仅学生）
 *   GET    /api/registrations/mine                                 我的报名      （仅学生）
 *   GET    /api/activities/{id}/registrations                      活动报名名单  （仅教师）
 *   PUT    /api/activities/{id}/registrations/{studentId}/approve  审核通过      （仅教师）
 *   PUT    /api/activities/{id}/registrations/{studentId}/reject   审核驳回      （仅教师）
 *   PUT    /api/activities/{id}/registrations/{studentId}/promote  候补递补      （仅教师）
 * </pre>
 *
 * <p>【接口层在这里做的工作】
 * 报名表里只存 activityId / studentId，而界面上要显示活动标题、学生姓名，
 * 因此接口层查完报名记录后需要"补全"关联信息再返回：
 * <ul>
 *   <li>"我的报名"：按 activityId 补活动标题、地点、时间；</li>
 *   <li>"报名名单"：按 studentId 补学号、姓名。</li>
 * </ul>
 *
 * <p>【管理员为什么进不来】
 * 上面每个方法的第一句都是 {@code requireRole(...)}，只允许 STUDENT 或 TEACHER。
 * 管理员角色不在允许列表里，会被自动拒绝 403 ——
 * 这正是 US-14「管理员不介入具体报名事务」在代码里的落实方式
 * （依据 A1、A4、A6 三条独立证据，是本轮证据最充分的结论）。
 *
 * <p>报名的业务规则（活动存在、状态可报名、不能重复报名、审核分支、候补排队）
 * 全部在 Service 层，接口层不重复实现，只负责取参数、补显示字段、映射错误码。
 */
public class RegistrationApi {

    /** 报名业务对象 */
    private final RegistrationService registrationService = new RegistrationService();

    /** 活动业务对象：查活动信息用于补全显示字段 */
    private final ActivityService activityService = new ActivityService();

    /** 认证业务对象：按 id 查学生学号与姓名 */
    private final AuthService authService = new AuthService();

    // ==================================================================
    // 一、学生报名与取消
    // ==================================================================

    /**
     * 学生报名活动。
     *
     * <p>V2.0 起报名后进入「待审核」，不是直接算数（教师访谈 T6）。
     *
     * @param ctx 请求上下文（路径参数 id = 活动 id）
     * @return 是否成功
     */
    public ApiResult register(RequestContext ctx) {
        ctx.requireRole(User.ROLE_STUDENT);

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
     * <p>待审核 / 候补 / 正式参加三种状态都可以取消。
     *
     * @param ctx 请求上下文（路径参数 id = 活动 id）
     * @return 是否成功
     */
    public ApiResult cancel(RequestContext ctx) {
        ctx.requireRole(User.ROLE_STUDENT);

        Long activityId = ctx.longPath("id");
        String error = registrationService.cancel(activityId, ctx.userId());
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(true);
    }

    // ==================================================================
    // 二、教师审核与候补递补（V2.0 新增，US-07 / US-09）
    // ==================================================================

    /**
     * 教师审核通过一条报名（US-07）。
     *
     * <p>审核结果由 Service 决定：有名额则正式参加，已满则转入候补。
     * 前端不需要关心这个分支怎么判断。
     *
     * @param ctx 请求上下文（id = 活动 id，studentId = 被审核的学生）
     * @return 是否成功
     */
    public ApiResult approve(RequestContext ctx) {
        return review(ctx, true);
    }

    /**
     * 教师驳回一条报名（US-07）。
     *
     * @param ctx 请求上下文（id = 活动 id，studentId = 被驳回的学生）
     * @return 是否成功
     */
    public ApiResult reject(RequestContext ctx) {
        return review(ctx, false);
    }

    /**
     * 审核的公共实现：通过 / 驳回只差一个布尔值，抽出来避免两份重复代码。
     *
     * @param ctx     请求上下文
     * @param approve true 表示通过
     * @return 是否成功
     */
    private ApiResult review(RequestContext ctx, boolean approve) {
        // 只有活动组织教师能审 —— 管理员会被这一句挡在门外（US-14）
        ctx.requireRole(User.ROLE_TEACHER);

        Long activityId = ctx.longPath("id");
        Long studentId = ctx.longPath("studentId");

        // 归属校验（"只能审核自己发布活动的报名"）在 Service 层完成
        String error = registrationService.review(activityId, studentId, ctx.userId(), approve);
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(true);
    }

    /**
     * 教师把空出的名额递补给某个候补学生（US-09）。
     *
     * <p>递补是教师手动触发的（最小假设 A6）：系统只负责把候补名单按
     * 进入候补的时间排好序，具体给谁由教师决定，不强制按队列顺序。
     *
     * @param ctx 请求上下文（id = 活动 id，studentId = 被递补的学生）
     * @return 是否成功
     */
    public ApiResult promote(RequestContext ctx) {
        ctx.requireRole(User.ROLE_TEACHER);

        Long activityId = ctx.longPath("id");
        Long studentId = ctx.longPath("studentId");

        String error = registrationService.promote(activityId, studentId, ctx.userId());
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(true);
    }

    // ==================================================================
    // 三、查询
    // ==================================================================

    /**
     * 学生查看"我的报名"。
     *
     * <p>返回的记录包含各种状态（待审核 / 候补 / 正式参加 / 未通过 / 已取消），
     * 每条都带 {@code statusText}，前端直接显示即可。
     *
     * @param ctx 请求上下文
     * @return 报名记录列表（每条附带活动标题、地点、时间与人数信息）
     */
    public ApiResult mine(RequestContext ctx) {
        ctx.requireRole(User.ROLE_STUDENT);

        List<ActivityRegistration> records = registrationService.myRegistrations(ctx.userId());
        List<ApiVo.MyRegistrationVo> result = new ArrayList<>(records.size());
        for (ActivityRegistration r : records) {
            ApiVo.MyRegistrationVo vo = ApiVo.MyRegistrationVo.from(r);

            // 补全活动信息（活动可能已被教师删除，所以要判空）
            Activity activity = activityService.detail(r.getActivityId());
            if (activity != null) {
                vo.title = activity.getTitle();
                vo.location = activity.getLocation();
                vo.startTime = activity.getStartTime();
                vo.endTime = activity.getEndTime();
                vo.capacity = activity.getCapacity();
                vo.eligibility = activity.getEligibility();
                vo.confirmedCount = registrationService.countByStatus(
                        r.getActivityId(), ActivityRegistration.STATUS_CONFIRMED);
            } else {
                vo.title = "（活动已删除）";
                vo.location = "-";
            }
            result.add(vo);
        }
        return ApiResult.ok(result);
    }

    /**
     * 教师查看某个活动的报名名单（V2.0：按状态分组返回）。
     *
     * <p>返回结构是 {@link ApiVo.RosterVo}：三个分组 + 三个人数 + 人数上限。
     * 教师最关心"还有几条要审"和"现在满没满"，这两个数字由后端算好，
     * 前端不自己数（数错了教师会看到与实际不符的数字）。
     *
     * <p>归属校验在这里显式做一次，是为了能给出明确的 403 提示；
     * Service 层内部也会再校验一次（拿不到归属就返回空列表），属于双保险。
     *
     * @param ctx 请求上下文（路径参数 id = 活动 id）
     * @return 分组后的报名名单
     */
    public ApiResult roster(RequestContext ctx) {
        ctx.requireRole(User.ROLE_TEACHER);

        Long activityId = ctx.longPath("id");
        Long teacherId = ctx.userId();

        Activity activity = activityService.detail(activityId);
        if (activity == null) {
            return ApiResult.fail(ApiResult.CODE_NOT_FOUND, "活动不存在");
        }
        if (!teacherId.equals(activity.getTeacherId())) {
            return ApiResult.fail(ApiResult.CODE_FORBIDDEN, "只能查看自己发布活动的报名名单");
        }

        ApiVo.RosterVo vo = new ApiVo.RosterVo();
        vo.activityId = activityId;
        vo.activityTitle = activity.getTitle();
        vo.capacity = activity.getCapacity();

        // 三个人数（口径不同，别混用：见 RegistrationService.countRegistered 的说明）
        vo.confirmedCount = registrationService.countByStatus(
                activityId, ActivityRegistration.STATUS_CONFIRMED);
        vo.waitlistedCount = registrationService.countByStatus(
                activityId, ActivityRegistration.STATUS_WAITLISTED);
        vo.pendingReviewCount = registrationService.countByStatus(
                activityId, ActivityRegistration.STATUS_PENDING_REVIEW);

        // 满没满只跟"已正式参加"比；活动没设上限则永远不满
        vo.full = activity.hasCapacityLimit() && vo.confirmedCount >= activity.getCapacity();

        // 三个分组。候补用 waitlist()（按进入候补的时间排序），
        // 另外两组用 rosterByStatus()（按报名时间排序）。
        vo.pendingReview = toRecordList(registrationService.rosterByStatus(
                activityId, teacherId, ActivityRegistration.STATUS_PENDING_REVIEW));
        vo.waitlisted = toRecordList(registrationService.waitlist(activityId, teacherId));
        vo.confirmed = toRecordList(registrationService.rosterByStatus(
                activityId, teacherId, ActivityRegistration.STATUS_CONFIRMED));

        return ApiResult.ok(vo);
    }

    // ==================================================================
    // 四、内部辅助方法
    // ==================================================================

    /**
     * 把报名记录列表转成名单项列表，并按 studentId 补上学号与姓名。
     *
     * <p>三个分组都要做同样的转换，抽出来避免写三遍。
     *
     * @param records 报名记录列表
     * @return 名单项列表
     */
    private List<ApiVo.RegistrationRecordVo> toRecordList(List<ActivityRegistration> records) {
        List<ApiVo.RegistrationRecordVo> result = new ArrayList<>(records.size());
        for (ActivityRegistration r : records) {
            ApiVo.RegistrationRecordVo vo = ApiVo.RegistrationRecordVo.from(r);

            User student = authService.getById(r.getStudentId());
            vo.username = student == null ? "-" : student.getUsername();
            vo.studentName = student == null ? "-" : student.getName();
            result.add(vo);
        }
        return result;
    }

    /**
     * 把 Service 返回的中文提示映射成错误码。
     *
     * ⚠️【这里靠关键词匹配，所以 Service 的提示语措辞是接口契约的一部分】
     * 改动 RegistrationService 里的提示语时，必须回来同步这个映射，
     * 否则前端会收到错误的错误码（例如把"名额已满"当成普通业务失败）。
     *
     * @param message Service 返回的失败原因
     * @return 错误码
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
        // V2.0 新增：报名记录当前状态不允许该操作
        // （例如审核一条已经确认或已取消的报名）
        if (message.contains("不是待审核") || message.contains("不是候补")) {
            return ApiResult.CODE_REG_STATUS;
        }
        // V2.0 新增：没有空余名额，无法递补
        if (message.contains("名额已满")) {
            return ApiResult.CODE_NO_VACANCY;
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
