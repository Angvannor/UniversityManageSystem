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
 * 活动接口（对应需求 REQ-02 浏览活动、REQ-05 教师发布与管理活动）。
 *
 * <p>提供的接口：
 * <pre>
 *   GET    /api/activities           活动列表（支持 keyword / status / onlyMine）
 *   GET    /api/activities/{id}      活动详情
 *   POST   /api/activities           发布活动      （仅教师）
 *   PUT    /api/activities/{id}      修改活动      （仅教师）
 *   PUT    /api/activities/{id}/close 关闭报名     （仅教师）
 *   DELETE /api/activities/{id}      删除活动      （仅教师）
 * </pre>
 *
 * <p>【本类做的三件"接口层该做"的事】
 * <ol>
 *   <li>把实体 {@code Activity} 组装成带计算字段的 {@link ApiVo.ActivityVo}
 *       （教师姓名、报名人数、我是否已报名）；</li>
 *   <li>把请求体里的字段装配成 {@link Activity} 实体，并把当前登录教师设为归属人；</li>
 *   <li>角色校验：发布 / 修改 / 关闭 / 删除只允许教师。</li>
 * </ol>
 * 业务规则（时间校验、归属校验、有报名不能删）仍然全部在 Service 层，
 * 接口层不重复实现。
 */
public class ActivityApi {

    /** 活动业务对象 */
    private final ActivityService activityService = new ActivityService();

    /** 报名业务对象：统计报名人数、判断本人是否已报名 */
    private final RegistrationService registrationService = new RegistrationService();

    /** 认证业务对象：按 id 查教师姓名 */
    private final AuthService authService = new AuthService();

    // 角色取值统一用 User 里的常量，避免各处手写字符串抄错
    private static final String ROLE_TEACHER = User.ROLE_TEACHER;
    private static final String ROLE_STUDENT = User.ROLE_STUDENT;

    // ==================================================================
    // 一、查询
    // ==================================================================

    /**
     * 活动列表。
     *
     * <p>查询参数：
     * <ul>
     *   <li>{@code keyword}：按标题模糊搜索；</li>
     *   <li>{@code status}：按状态筛选（OPEN / CLOSED / FINISHED）；</li>
     *   <li>{@code onlyMine}：true 时只返回当前教师发布的活动。</li>
     * </ul>
     *
     * @param ctx 请求上下文
     * @return 活动列表（含教师姓名、报名人数、当前学生是否已报名）
     */
    public ApiResult list(RequestContext ctx) {
        User me = ctx.user();
        boolean onlyMine = ctx.boolQuery("onlyMine");

        List<Activity> activities;
        if (onlyMine) {
            // 只看自己的活动：必须是教师，Service 内部还会按 teacherId 过滤
            ctx.requireRole(ROLE_TEACHER);
            activities = activityService.listByTeacher(me.getId());
        } else {
            activities = activityService.listAll();
        }

        // 关键字与状态筛选
        // 说明：当前数据量很小，直接在内存里过滤，避免为 V1.0 的 Service 增加查询条件；
        //      数据量变大后应当把过滤条件下推到 SQL（属于 V3.0 的改进点）。
        String keyword = ctx.query("keyword");
        String status = ctx.query("status");
        List<Activity> filtered = new ArrayList<>();
        for (Activity a : activities) {
            if (keyword != null && (a.getTitle() == null || !a.getTitle().contains(keyword))) {
                continue;
            }
            if (status != null && !status.equals(a.getStatus())) {
                continue;
            }
            filtered.add(a);
        }

        List<ApiVo.ActivityVo> result = new ArrayList<>(filtered.size());
        for (Activity a : filtered) {
            result.add(toVo(a, me));
        }
        return ApiResult.ok(result);
    }

    /**
     * 活动详情。
     *
     * @param ctx 请求上下文（路径参数 id）
     * @return 活动详情
     */
    public ApiResult detail(RequestContext ctx) {
        Long id = ctx.longPath("id");
        Activity activity = activityService.detail(id);
        if (activity == null) {
            return ApiResult.fail(ApiResult.CODE_NOT_FOUND, "活动不存在或已被删除");
        }
        return ApiResult.ok(toVo(activity, ctx.user()));
    }

    // ==================================================================
    // 二、教师发布与管理
    // ==================================================================

    /**
     * 发布活动（仅教师）。
     *
     * <p>请求体除 V1.5 的字段外，V2.0 新增两个可选字段：
     * <ul>
     *   <li>{@code capacity}：人数上限，<b>省略或传 null 表示不限制人数</b>；
     *       注意不要传空字符串 ""，Gson 把 "" 转成 Integer 会失败；</li>
     *   <li>{@code eligibility}：参加条件，自由文本，可省略。</li>
     * </ul>
     * 两个字段的合法性校验在 Service 层（`capacity <= 0` 拒绝、
     * 纯空白的参加条件会被规范成 null）。
     *
     * @param ctx 请求上下文（请求体含 title / location / description /
     *            startTime / endTime / capacity / eligibility）
     * @return 新活动 id
     */
    public ApiResult create(RequestContext ctx) {
        ctx.requireRole(ROLE_TEACHER);

        // 用 Gson 直接把请求体转成 Activity 对象
        // （时间字段由 JsonUtil 里的适配器处理 "yyyy-MM-dd HH:mm:ss" 字符串）
        Activity activity = JsonUtil.fromJson(ctx.body().toString(), Activity.class);
        // 归属人由服务端决定，不接受前端传入，防止伪造
        activity.setTeacherId(ctx.userId());

        String error = activityService.publish(activity);
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(activity.getId());
    }

    /**
     * 修改活动（仅教师，且只能改自己发布的）。
     *
     * @param ctx 请求上下文（路径参数 id + 请求体字段）
     * @return 是否成功
     */
    public ApiResult update(RequestContext ctx) {
        ctx.requireRole(ROLE_TEACHER);

        Activity activity = JsonUtil.fromJson(ctx.body().toString(), Activity.class);
        // id 以 URL 为准，避免前端漏传或传错
        activity.setId(ctx.longPath("id"));

        String error = activityService.update(activity, ctx.userId());
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(true);
    }

    /**
     * 关闭活动报名（仅教师）。
     *
     * @param ctx 请求上下文（路径参数 id）
     * @return 是否成功
     */
    public ApiResult close(RequestContext ctx) {
        ctx.requireRole(ROLE_TEACHER);
        String error = activityService.close(ctx.longPath("id"), ctx.userId());
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(true);
    }

    /**
     * 删除活动（仅教师，且活动不能有有效报名）。
     *
     * @param ctx 请求上下文（路径参数 id）
     * @return 是否成功
     */
    public ApiResult delete(RequestContext ctx) {
        ctx.requireRole(ROLE_TEACHER);
        String error = activityService.delete(ctx.longPath("id"), ctx.userId());
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(true);
    }

    // ==================================================================
    // 三、内部辅助方法
    // ==================================================================

    /**
     * 把活动实体组装成前端需要的视图对象。
     *
     * <p>补充这些计算字段：
     * <ul>
     *   <li>teacherName：用 teacherId 反查用户表；</li>
     *   <li>registeredCount：已报名人数（占位三态之和）；</li>
     *   <li>confirmedCount：<b>已正式参加</b>人数（V2.0 新增）——
     *       学生端要显示"限 N 人 · 已确定 M 人"，判断满没满也是比这个数；</li>
     *   <li>waitlistedCount：候补人数（V2.0 新增）；</li>
     *   <li>joined / myStatus / myStatusText：仅当查看者是学生时才有意义
     *       （教师与管理员看到的是 null）。学生端据此显示
     *       "待老师审核 / 候补中 / 已确认参加 / 未通过"，并决定报名按钮能不能点。</li>
     * </ul>
     *
     * <p>注意 {@code capacity} / {@code eligibility} 两个字段由
     * {@code ActivityVo.from()} 直接带出来，这里不需要额外处理。
     *
     * @param activity 活动实体
     * @param viewer   当前登录用户，可为 null
     * @return 活动视图对象
     */
    private ApiVo.ActivityVo toVo(Activity activity, User viewer) {
        ApiVo.ActivityVo vo = ApiVo.ActivityVo.from(activity);

        User teacher = authService.getById(activity.getTeacherId());
        vo.teacherName = teacher == null ? "未知" : teacher.getName();

        vo.registeredCount = registrationService.countRegistered(activity.getId());
        vo.confirmedCount = registrationService.countByStatus(
                activity.getId(), ActivityRegistration.STATUS_CONFIRMED);
        vo.waitlistedCount = registrationService.countByStatus(
                activity.getId(), ActivityRegistration.STATUS_WAITLISTED);

        if (viewer != null && ROLE_STUDENT.equals(viewer.getRole())) {
            // 当前学生自己的报名状态：可能是 null（从未报名）、待审核、候补、已确认、未通过、已取消
            ActivityRegistration mine =
                    registrationService.myRegistration(activity.getId(), viewer.getId());
            vo.joined = mine != null && mine.occupiesSlot();
            if (mine != null) {
                vo.myStatus = mine.getStatus();
                vo.myStatusText = ApiVo.statusText(mine.getStatus());
            }
        }
        return vo;
    }

    /**
     * 把 Service 返回的中文提示映射成错误码。
     *
     * @param message Service 返回的失败原因
     * @return 错误码：权限问题 → 403，找不到 → 404，参数问题 → 1000，其余 → 1001
     */
    private int codeOf(String message) {
        if (message.contains("只能管理自己发布的活动")) {
            return ApiResult.CODE_FORBIDDEN;
        }
        if (message.contains("活动不存在")) {
            return ApiResult.CODE_NOT_FOUND;
        }
        // V2.0 新增：人数上限与参加条件的校验失败也属于"参数不合法"
        if (message.contains("不能为空") || message.contains("必须晚于")
                || message.contains("人数上限") || message.contains("参加条件不能超过")) {
            return ApiResult.CODE_PARAM;
        }
        return ApiResult.CODE_BUSINESS;
    }
}
