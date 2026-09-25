package com.hbk.activity.web;

import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.entity.User;
import com.hbk.activity.service.ActivityService;
import com.hbk.activity.service.AdminService;
import com.hbk.activity.service.AuthService;
import com.hbk.activity.service.RegistrationService;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统管理员接口（V2.0 新增，对应 US-11 ~ US-14）。
 *
 * <p>提供的接口：
 * <pre>
 *   GET /api/admin/activities              查看平台全部活动（只读监督）
 *   GET /api/admin/users                   查看全部用户账号及状态
 *   PUT /api/admin/users/{id}/disable      停用账号
 *   PUT /api/admin/users/{id}/enable       恢复账号
 * </pre>
 *
 * <p>【这些接口都要 {@code requireAdmin()}】
 * 管理员能看到全平台的账号列表 —— 这是三类角色里权限最大的视图。
 * 只靠前端隐藏菜单是不够的：学生只要用 Postman 直接请求就能拿到全部账号信息。
 * 所以每个方法的第一句都必须校验角色（US-12 验收标准 3）。
 *
 * <p>【本类只有"账号"和"活动监督"，没有任何报名相关方法】
 * 这是 REQ-V2-17 的落实 —— 管理员不介入具体报名事务。
 * 依据是三条独立证据（A1 明确不处理报名、A4 权限里没有报名记录、A6 把报名问题推给教师）。
 *
 * <p>【两个必须注意的安全点】
 * <ol>
 *   <li>{@code AdminService.listAllUsers()} 返回的 User 实体<b>带着 password 字段</b>
 *       （DAO 为了登录校验会查出来）。这里必须转成 {@link ApiVo.UserVo} 才能返回，
 *       那个类刻意不含 password（US-12 验收标准 2）；</li>
 *   <li>停用 / 恢复接口的响应里不返回任何用户详情，只返回是否成功，
 *       减少密码密文被带出去的机会。</li>
 * </ol>
 */
public class AdminApi {

    /** 管理员业务对象 */
    private final AdminService adminService = new AdminService();

    /** 活动业务对象：补教师姓名 */
    private final ActivityService activityService = new ActivityService();

    /** 报名业务对象：补活动人数（管理员只读，不参与报名处理） */
    private final RegistrationService registrationService = new RegistrationService();

    /** 认证业务对象：按 id 查教师姓名 */
    private final AuthService authService = new AuthService();

    // ==================================================================
    // 一、活动监督视图（US-11）
    // ==================================================================

    /**
     * 查看平台上的全部活动（只读监督，US-11）。
     *
     * <p>与教师端"我的活动"的区别只在于范围：这里不受 teacherId 限制，能看到全部。
     * 返回的结构与 {@code GET /api/activities} 一致（都带教师姓名与人数），
     * 方便前端复用同一套表格组件。
     *
     * <p>注意本接口<b>只读</b>：没有配套的发布 / 修改 / 删除接口给管理员，
     * 那三项仍然只允许活动的发布教师执行。
     *
     * @param ctx 请求上下文
     * @return 去重后的活动列表（含教师姓名与人数）
     */
    public ApiResult activities(RequestContext ctx) {
        ctx.requireAdmin();

        List<Activity> all = adminService.listAllActivities();
        List<ApiVo.ActivityVo> result = new ArrayList<>(all.size());
        for (Activity a : all) {
            ApiVo.ActivityVo vo = ApiVo.ActivityVo.from(a);
            User teacher = authService.getById(a.getTeacherId());
            vo.teacherName = teacher == null ? "未知" : teacher.getName();
            vo.registeredCount = registrationService.countRegistered(a.getId());
            vo.confirmedCount = registrationService.countByStatus(
                    a.getId(), ActivityRegistration.STATUS_CONFIRMED);
            vo.waitlistedCount = registrationService.countByStatus(
                    a.getId(), ActivityRegistration.STATUS_WAITLISTED);
            // 管理员不是学生，不设置 joined / myStatus（保持 null）
            result.add(vo);
        }
        return ApiResult.ok(result);
    }

    // ==================================================================
    // 二、账号管理（US-12、US-13）
    // ==================================================================

    /**
     * 查看全部用户账号及其状态（US-12）。
     *
     * <p>⚠️ 一定要经过 {@link ApiVo.UserVo} 转换：Service 返回的实体带 password，
     * 直接序列化会把密码密文发给前端。
     *
     * @param ctx 请求上下文
     * @return 用户列表（含账号、姓名、角色、账号状态，<b>不含密码</b>）
     */
    public ApiResult users(RequestContext ctx) {
        ctx.requireAdmin();

        List<User> all = adminService.listAllUsers();
        List<ApiVo.UserVo> result = new ArrayList<>(all.size());
        for (User u : all) {
            result.add(ApiVo.UserVo.from(u));
        }
        return ApiResult.ok(result);
    }

    /**
     * 停用账号（US-13）。
     *
     * <p>"不能停用自己"的校验在 Service 层 —— 一旦把自己停用，
     * 就再也没人能进后台把它改回来。
     *
     * @param ctx 请求上下文（路径参数 id = 目标用户 id）
     * @return 是否成功
     */
    public ApiResult disableUser(RequestContext ctx) {
        ctx.requireAdmin();

        // 目标用户 id 走路径参数，操作者 id 走会话（不能由请求体指定，否则可以伪造）
        Long targetId = ctx.longPath("id");
        String error = adminService.disableUser(targetId, ctx.userId());
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(true);
    }

    /**
     * 恢复账号为可用（US-13）。
     *
     * @param ctx 请求上下文（路径参数 id = 目标用户 id）
     * @return 是否成功
     */
    public ApiResult enableUser(RequestContext ctx) {
        ctx.requireAdmin();

        String error = adminService.enableUser(ctx.longPath("id"));
        if (error != null) {
            return ApiResult.fail(codeOf(error), error);
        }
        return ApiResult.ok(true);
    }

    // ==================================================================
    // 三、内部辅助方法
    // ==================================================================

    /**
     * 把 Service 返回的中文提示映射成错误码。
     *
     * <p>注意这里<b>没有</b>"含'只能' → 403"这条通用规则：
     * 服务里那句"不能停用自己的账号"也含"不能"，如果照抄报名接口的写法会误判成 403。
     * 所以这里逐条精确匹配。
     *
     * @param message Service 返回的失败原因
     * @return 错误码
     */
    private int codeOf(String message) {
        if (message.contains("用户不存在")) {
            return ApiResult.CODE_NOT_FOUND;
        }
        if (message.contains("不能停用自己")) {
            return ApiResult.CODE_FORBIDDEN;
        }
        if (message.contains("已经处于停用状态") || message.contains("无需恢复")) {
            return ApiResult.CODE_BUSINESS;
        }
        return ApiResult.CODE_BUSINESS;
    }
}
