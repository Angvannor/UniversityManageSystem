package com.hbk.activity.web;

import com.hbk.activity.entity.User;
import com.hbk.activity.service.AuthService;

/**
 * 认证接口（对应需求 REQ-01）。
 *
 * <p>提供的接口：
 * <pre>
 *   POST /api/auth/register  注册        （免登录）
 *   POST /api/auth/login     登录        （免登录）
 *   POST /api/auth/logout    退出登录    （需登录）
 *   GET  /api/auth/me        当前用户信息 （需登录）
 * </pre>
 *
 * <p>【本类的写法特点】
 * 每个方法只做三件事：<b>取参数 → 调用 Service → 包装成 ApiResult</b>。
 * 业务规则全在 Service 层，这里不写任何 if 判断业务的地方
 * （只有"Service 返回了错误信息"这种翻译工作）。
 */
public class AuthApi {

    /** 认证业务对象 */
    private final AuthService authService = new AuthService();

    /**
     * 注册。
     *
     * <p>Service 的约定是"返回 null 表示成功，返回文字表示失败原因"，
     * 这里把文字翻译成前端需要的错误码。
     *
     * @param ctx 请求上下文（请求体含 username / password / name / role）
     * @return 新用户信息
     */
    public ApiResult register(RequestContext ctx) {
        String username = ctx.str("username");
        String password = ctx.str("password");
        String name = ctx.str("name");
        String role = ctx.str("role");

        String error = authService.register(username, password, name, role);
        if (error != null) {
            return ApiResult.fail(codeOfRegisterError(error), error);
        }

        // 注册成功后把用户信息返回给前端（前端用它提示"注册成功，某某"）
        User created = authService.getByUsername(username);
        return ApiResult.ok(ApiVo.UserVo.from(created));
    }

    /**
     * 登录：校验账号密码并签发令牌。
     *
     * @param ctx 请求上下文（请求体含 username / password）
     * @return 令牌与用户信息
     */
    public ApiResult login(RequestContext ctx) {
        User user = authService.login(ctx.str("username"), ctx.str("password"));
        if (user == null) {
            // 账号不存在与密码错误返回同一提示，避免暴露账号是否存在
            return ApiResult.fail(ApiResult.CODE_LOGIN_FAILED, "账号或密码错误");
        }

        String token = SessionManager.createToken(user.getId());
        System.out.println("      登录成功：" + user.getName() + "（" + user.getRole()
                + "），当前在线会话数 " + SessionManager.size());
        return ApiResult.ok(new ApiVo.LoginVo(token, ApiVo.UserVo.from(user)));
    }

    /**
     * 退出登录：把令牌从会话中移除。
     *
     * @param ctx 请求上下文
     * @return 恒定成功（即使令牌已失效）
     */
    public ApiResult logout(RequestContext ctx) {
        String header = ctx.exchange().getRequestHeaders().getFirst("Authorization");
        if (header != null) {
            SessionManager.remove(header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim());
        }
        return ApiResult.ok();
    }

    /**
     * 获取当前登录用户信息（前端刷新页面后用它恢复登录状态）。
     *
     * @param ctx 请求上下文（路由已保证此时已登录）
     * @return 当前用户信息
     */
    public ApiResult me(RequestContext ctx) {
        return ApiResult.ok(ApiVo.UserVo.from(ctx.user()));
    }

    /**
     * 把 Service 返回的中文提示映射成前端使用的错误码。
     *
     * <p>说明：V1.0 的 Service 用"返回提示文字"表示失败，没有携带错误码。
     * 接口层为了让前端能区分错误类型，做了一层轻量映射。
     * 更彻底的做法是让 Service 直接返回带错误码的结果对象（V3.0 可改进）。
     *
     * @param message Service 返回的失败原因
     * @return 错误码
     */
    private int codeOfRegisterError(String message) {
        if (message.contains("已被注册")) {
            return ApiResult.CODE_USERNAME_EXISTS;
        }
        if (message.contains("不能为空") || message.contains("长度")) {
            return ApiResult.CODE_PARAM;
        }
        return ApiResult.CODE_BUSINESS;
    }
}
