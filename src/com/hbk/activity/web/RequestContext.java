package com.hbk.activity.web;

import com.google.gson.JsonObject;
import com.hbk.activity.entity.User;
import com.sun.net.httpserver.HttpExchange;

import java.util.Collections;
import java.util.Map;

/**
 * 一次请求的上下文。
 *
 * <p>【作用】
 * 把 {@link HttpExchange}（JDK 原生的请求/响应对象）和已经解析好的数据包在一起，
 * 处理器方法只需要关心"取哪个参数"，不用再碰底层 API：
 * <pre>
 *   public ApiResult detail(RequestContext ctx) {
 *       Long id = ctx.longPath("id");            // 取路径参数 /api/activities/{id}
 *       String kw = ctx.query("keyword");        // 取查询参数 ?keyword=xx
 *       String title = ctx.str("title");         // 取请求体里的字段
 *       User me = ctx.user();                    // 当前登录用户
 *   }
 * </pre>
 *
 * <p>路径参数、查询参数、请求体都由 {@link ApiRouter} 在调用处理器之前解析好。
 */
public class RequestContext {

    /** JDK 原生的请求对象，少数场景（如写响应头）才需要直接使用 */
    private final HttpExchange exchange;

    /** 路径参数，例如 /api/activities/{id} 匹配后 id -> "3" */
    private final Map<String, String> pathParams;

    /** 查询参数，例如 ?keyword=大赛&status=OPEN */
    private final Map<String, String> queryParams;

    /** 请求体（JSON），GET 请求为空对象 */
    private final JsonObject body;

    /** 当前登录用户；未登录时为 null */
    private User currentUser;

    public RequestContext(HttpExchange exchange,
                          Map<String, String> pathParams,
                          Map<String, String> queryParams,
                          JsonObject body) {
        this.exchange = exchange;
        this.pathParams = pathParams == null ? Collections.emptyMap() : pathParams;
        this.queryParams = queryParams == null ? Collections.emptyMap() : queryParams;
        this.body = body == null ? new JsonObject() : body;
    }

    // ------------------------------------------------------------------
    // 路径参数 / 查询参数
    // ------------------------------------------------------------------

    /**
     * 取路径参数。
     *
     * @param name 参数名（路径模板 {} 中的名字）
     * @return 参数值；不存在时返回 null
     */
    public String path(String name) {
        return pathParams.get(name);
    }

    /**
     * 取路径参数并转成 Long（活动 id、用户 id 都用这个）。
     *
     * @param name 参数名
     * @return 数值；缺失或格式错误时抛 ApiException(1000)
     */
    public Long longPath(String name) {
        String value = path(name);
        if (value == null) {
            throw new ApiException(ApiResult.CODE_PARAM, "缺少路径参数：" + name);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ApiException(ApiResult.CODE_PARAM, "路径参数不是合法数字：" + value);
        }
    }

    /**
     * 取查询参数。
     *
     * @param name 参数名
     * @return 参数值；不存在时返回 null
     */
    public String query(String name) {
        String value = queryParams.get(name);
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * 取布尔型查询参数。
     *
     * @param name 参数名
     * @return true 表示参数值为 "true"（忽略大小写）；否则 false
     */
    public boolean boolQuery(String name) {
        return "true".equalsIgnoreCase(query(name));
    }

    // ------------------------------------------------------------------
    // 请求体字段
    // ------------------------------------------------------------------

    /**
     * 取请求体中的字符串字段。
     *
     * @param field 字段名
     * @return 字段值；不存在或为 null 时返回 null
     */
    public String str(String field) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            return null;
        }
        return body.get(field).getAsString();
    }

    /**
     * 取请求体中的整数字段。
     *
     * @param field 字段名
     * @return 数值；不存在时返回 null
     */
    public Integer intValue(String field) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            return null;
        }
        return body.get(field).getAsInt();
    }

    /**
     * 判断请求体里是否包含某个字段且不为 null。
     *
     * @param field 字段名
     * @return true 表示存在且非空
     */
    public boolean has(String field) {
        return body.has(field) && !body.get(field).isJsonNull();
    }

    /** @return 请求体 JSON 对象（需要一次性转成实体类时使用） */
    public JsonObject body() {
        return body;
    }

    // ------------------------------------------------------------------
    // 当前用户
    // ------------------------------------------------------------------

    /** @return 当前登录用户；未登录为 null */
    public User user() {
        return currentUser;
    }

    /** 由路由在校验令牌后写入当前用户 */
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    /**
     * 取当前登录用户 id。
     *
     * @return 用户 id；未登录时抛 ApiException(401)
     */
    public Long userId() {
        if (currentUser == null) {
            throw new ApiException(ApiResult.CODE_UNAUTHORIZED, "未登录或登录状态已失效");
        }
        return currentUser.getId();
    }

    /**
     * 校验当前用户的角色。
     *
     * <p>对应报告「设计决策二」：接口层再做一次角色判断，
     * 不能只靠前端隐藏菜单。
     *
     * <p>取值用 {@link User} 里的 ROLE_xxx 常量，别直接写字符串。
     *
     * @param role 要求的角色：STUDENT / TEACHER / ADMIN
     * @throws ApiException 角色不符时抛 403
     */
    public void requireRole(String role) {
        if (currentUser == null) {
            throw new ApiException(ApiResult.CODE_UNAUTHORIZED, "未登录或登录状态已失效");
        }
        if (!role.equals(currentUser.getRole())) {
            throw new ApiException(ApiResult.CODE_FORBIDDEN, "当前角色无权执行该操作");
        }
    }

    /**
     * 要求当前用户是系统管理员（V2.0 新增）。
     *
     * <p>等价于 {@code requireRole(User.ROLE_ADMIN)}，单独包一层是为了
     * 调用处读起来更清楚，也避免把 {@code "ADMIN"} 这个字符串抄错。
     *
     * <p>【为什么管理员接口必须单独校验】
     * 管理员能看到全平台的账号列表 —— 这是三类角色里权限最大的视图。
     * 如果只靠前端隐藏菜单，学生只要用 Postman 直接请求就能拿到全部账号信息
     * （对应 US-12 验收标准 3）。
     *
     * <p>【另一面：管理员不能调报名接口】
     * REQ-V2-17 要求管理员不介入报名（US-14）。这一条不需要额外写方法 ——
     * 报名相关接口都声明为 {@code requireRole(TEACHER)} 或
     * {@code requireRole(STUDENT)}，管理员角色不在允许列表里，会被自动拒绝 403。
     *
     * @throws ApiException 未登录抛 401；不是管理员抛 403
     */
    public void requireAdmin() {
        requireRole(User.ROLE_ADMIN);
    }

    /** @return JDK 原生请求对象 */
    public HttpExchange exchange() {
        return exchange;
    }
}
