package com.hbk.activity.web;

/**
 * 接口业务异常。
 *
 * <p>【为什么需要它】
 * 接口层经常遇到"该拒绝的请求"：未登录、角色不对、参数缺失……
 * 如果在每个处理器里都写 {@code return ApiResult.fail(...)}，代码会被大量分支淹没。
 *
 * <p>有了本异常，处理器里可以直接：
 * <pre>
 *   throw new ApiException(ApiResult.CODE_FORBIDDEN, "当前角色无权执行该操作");
 * </pre>
 * 由 {@link ApiRouter} 统一捕获并转换成 JSON 响应，业务代码保持清晰。
 *
 * <p>注意：它与 Service 层的约定不同 ——
 * Service 用"返回提示文字"表示失败，接口层用"抛异常"表示失败，
 * 这是两层的不同风格，接口层负责把前者翻译成后者。
 */
public class ApiException extends RuntimeException {

    /** 错误码，取值见 {@link ApiResult} */
    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
