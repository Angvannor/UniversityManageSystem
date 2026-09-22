package com.hbk.activity.web;

import com.hbk.activity.entity.Activity;
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

    /** 角色常量 */
    private static final String ROLE_TEACHER = "TEACHER";
    private static final String ROLE_STUDENT = "STUDENT";

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
     * @param ctx 请求上下文（请求体含 title / location / description / startTime / endTime / status）
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
     * <p>补充三个计算字段：
     * <ul>
     *   <li>teacherName：用 teacherId 反查用户表；</li>
     *   <li>registeredCount：统计报名表中状态为 REGISTERED 的记录数；</li>
     *   <li>joined：仅当查看者是学生时才有意义（教师看到的是 null）。</li>
     * </ul>
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

        if (viewer != null && ROLE_STUDENT.equals(viewer.getRole())) {
            vo.joined = registrationService.hasRegistered(activity.getId(), viewer.getId());
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
        if (message.contains("不能为空") || message.contains("必须晚于")) {
            return ApiResult.CODE_PARAM;
        }
        return ApiResult.CODE_BUSINESS;
    }
}
