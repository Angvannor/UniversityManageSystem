package com.hbk.activity.web;

/**
 * 接口统一返回结构。
 *
 * <p>所有接口都返回同一种 JSON 形状，前端只要判断 {@code code == 0} 就知道成功与否：
 * <pre>
 *   成功：{ "code": 0,    "message": "success", "data": { ... } }
 *   失败：{ "code": 3001, "message": "你已经报名过该活动", "data": null }
 * </pre>
 *
 * <p>字段用 private final 即可 —— Gson 通过反射读取字段来序列化，
 * 不需要为每个字段写 getter。
 *
 * <p>错误码与前端 mock/mock-api.js 中的取值保持一致，
 * 这样从假接口切换到真接口时前端的判断逻辑不用改。
 */
public class ApiResult {

    // -------- 通用错误码（与前端约定一致） --------
    /** 成功 */
    public static final int CODE_OK = 0;
    /** 参数不合法 */
    public static final int CODE_PARAM = 1000;
    /** 业务处理失败（具体原因看 message） */
    public static final int CODE_BUSINESS = 1001;
    /** 未登录或登录状态失效 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 角色无权执行该操作 */
    public static final int CODE_FORBIDDEN = 403;
    /** 数据不存在 */
    public static final int CODE_NOT_FOUND = 404;
    /** 账号或密码错误 */
    public static final int CODE_LOGIN_FAILED = 2001;
    /** 账号已被注册 */
    public static final int CODE_USERNAME_EXISTS = 2002;
    /** V2.0 新增：账号已被管理员停用（密码是对的，但不允许登录） */
    public static final int CODE_ACCOUNT_DISABLED = 2003;
    /** V2.0 新增：报名记录当前状态不允许该操作（例如审核一条已取消的报名） */
    public static final int CODE_REG_STATUS = 3005;
    /** V2.0 新增：没有空余名额，无法递补 */
    public static final int CODE_NO_VACANCY = 3006;

    /** 状态码：0 表示成功 */
    private final int code;

    /** 提示信息 */
    private final String message;

    /** 业务数据：失败时为 null */
    private final Object data;

    private ApiResult(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功并携带数据。
     *
     * @param data 业务数据
     * @return 成功响应
     */
    public static ApiResult ok(Object data) {
        return new ApiResult(CODE_OK, "success", data);
    }

    /**
     * 成功但无数据。
     *
     * @return 成功响应
     */
    public static ApiResult ok() {
        return new ApiResult(CODE_OK, "success", null);
    }

    /**
     * 失败响应。
     *
     * @param code    错误码
     * @param message 失败原因（会直接展示给用户）
     * @return 失败响应
     */
    public static ApiResult fail(int code, String message) {
        return new ApiResult(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
