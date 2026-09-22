package com.hbk.activity.web;

/**
 * 接口处理器接口。
 *
 * <p>每个 HTTP 接口实现一个方法：从 {@link RequestContext} 取参数，
 * 调用 Service 层完成业务，返回 {@link ApiResult}。
 *
 * <p>因为是"一个入参一个返回值"的单一方法，可以用 Lambda 或方法引用注册：
 * <pre>
 *   router.get("/api/activities", activityApi::list);
 *   router.post("/api/auth/login", authApi::login).publicAccess();
 * </pre>
 *
 * <p>方法声明抛出 Exception 是为了让处理器内部可以自由调用
 * Service / DAO（它们可能抛 SQLException 等受检异常），
 * 由 {@link ApiRouter} 统一兜底处理。
 */
@FunctionalInterface
public interface ApiHandler {

    /**
     * 处理一次请求。
     *
     * @param ctx 请求上下文
     * @return 要返回给前端的响应
     * @throws Exception 处理过程中的任何异常，由路由统一转换为 JSON
     */
    ApiResult handle(RequestContext ctx) throws Exception;
}
