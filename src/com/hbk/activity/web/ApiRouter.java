package com.hbk.activity.web;

import com.google.gson.JsonObject;
import com.hbk.activity.entity.User;
import com.hbk.activity.service.AuthService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接口路由器：负责"把 HTTP 请求交给正确的处理器，并把结果写回响应"。
 *
 * <p>【它在整个请求链路中的位置】
 * <pre>
 *   浏览器/Vue ──HTTP──> ApiRouter ──> AuthApi / ActivityApi / RegistrationApi
 *                            │                        │
 *                            │                        └──> Service ──> DAO ──> MySQL
 *                            └── 统一处理：跨域、令牌校验、异常转换、JSON 序列化
 * </pre>
 *
 * <p>【一次请求经过的步骤】
 * <ol>
 *   <li>写入跨域响应头（开发期前端端口不同）；（OPTIONS 预检直接返回）</li>
 *   <li>把 URL 路径与注册的路由模板比对，取出路径参数；</li>
 *   <li>解析查询参数；对 POST/PUT 读取并解析 JSON 请求体；</li>
 *   <li>如果该接口需要登录，从请求头取令牌并还原当前用户，取不到就返回 401；</li>
 *   <li>调用处理器方法拿 {@link ApiResult}；</li>
 *   <li>把结果序列化成 JSON 写回，并打印一行访问日志。</li>
 * </ol>
 *
 * <p>【路径模板】
 * 注册时写 {@code /api/activities/{id}}，内部会转成正则并在匹配后
 * 把 {@code id} 放进路径参数 Map，处理器用 {@code ctx.longPath("id")} 取用。
 */
public class ApiRouter {

    /** 访问日志的时间格式 */
    private static final DateTimeFormatter LOG_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 用于把登录用户从令牌还原出来（令牌里只有 userId） */
    private final AuthService authService;

    /** 已注册的路由表，按注册顺序匹配 */
    private final List<Route> routes = new ArrayList<>();

    public ApiRouter(AuthService authService) {
        this.authService = authService;
    }

    // ==================================================================
    // 一、路由注册（链式调用，让 WebServerMain 里读起来像一张接口清单）
    // ==================================================================

    /**
     * 注册一个需要登录的 GET 接口。
     *
     * @param template 路径模板，如 /api/activities/{id}
     * @param handler  处理器
     * @return 路由器本身，便于链式注册
     */
    public ApiRouter get(String template, ApiHandler handler) {
        return add("GET", template, handler, true);
    }

    /** 注册一个需要登录的 POST 接口 */
    public ApiRouter post(String template, ApiHandler handler) {
        return add("POST", template, handler, true);
    }

    /** 注册一个需要登录的 PUT 接口 */
    public ApiRouter put(String template, ApiHandler handler) {
        return add("PUT", template, handler, true);
    }

    /** 注册一个需要登录的 DELETE 接口 */
    public ApiRouter delete(String template, ApiHandler handler) {
        return add("DELETE", template, handler, true);
    }

    /**
     * 注册一个<b>免登录</b>的 POST 接口（注册、登录用）。
     *
     * @param template 路径模板
     * @param handler  处理器
     * @return 路由器本身
     */
    public ApiRouter publicPost(String template, ApiHandler handler) {
        return add("POST", template, handler, false);
    }

    /**
     * 把一条路由加入路由表，并把路径模板编译成正则。
     *
     * @param method    HTTP 方法
     * @param template  路径模板，{} 中为参数名
     * @param handler   处理器
     * @param needAuth  是否需要登录
     * @return 路由器本身
     */
    private ApiRouter add(String method, String template, ApiHandler handler, boolean needAuth) {
        List<String> paramNames = new ArrayList<>();
        Pattern pattern = compileTemplate(template, paramNames);
        routes.add(new Route(method, template, pattern, paramNames, handler, needAuth));
        return this;
    }

    /**
     * 把 /api/activities/{id} 这样的模板编译成正则，并收集参数名。
     *
     * @param template   路径模板
     * @param paramNames 输出参数：模板里出现的参数名，顺序与捕获组一致
     * @return 编译后的正则
     */
    private Pattern compileTemplate(String template, List<String> paramNames) {
        StringBuilder regex = new StringBuilder("^");
        Matcher matcher = Pattern.compile("\\{([^/}]+)\\}").matcher(template);
        int last = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(template.substring(last, matcher.start())));
            paramNames.add(matcher.group(1));
            regex.append("([^/]+)"); // 一段非斜杠内容作为参数值
            last = matcher.end();
        }
        regex.append(Pattern.quote(template.substring(last))).append("$");
        return Pattern.compile(regex.toString());
    }

    // ==================================================================
    // 二、请求分发
    // ==================================================================

    /**
     * 处理一次 HTTP 请求（由 WebServerMain 注册为 /api 的处理器）。
     *
     * @param exchange JDK 提供的请求/响应对象
     */
    public void dispatch(HttpExchange exchange) throws IOException {
        // 1. 跨域响应头；OPTIONS 预检请求直接结束
        addCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath();
        long startAt = System.currentTimeMillis();

        ApiResult result;
        try {
            // 2. 匹配路由，取出路径参数
            Matcher matched = null;
            Route route = null;
            Map<String, String> pathParams = new LinkedHashMap<>();
            for (Route candidate : routes) {
                if (!candidate.method.equals(method)) {
                    continue;
                }
                Matcher m = candidate.pattern.matcher(path);
                if (m.matches()) {
                    route = candidate;
                    matched = m;
                    for (int i = 0; i < candidate.paramNames.size(); i++) {
                        pathParams.put(candidate.paramNames.get(i), m.group(i + 1));
                    }
                    break;
                }
            }
            if (route == null) {
                result = ApiResult.fail(ApiResult.CODE_NOT_FOUND, "接口不存在：" + method + " " + path);
            } else {
                // 3. 解析查询参数与请求体
                Map<String, String> queryParams = parseQuery(exchange.getRequestURI().getRawQuery());
                JsonObject body = readBody(exchange);
                RequestContext ctx = new RequestContext(exchange, pathParams, queryParams, body);

                // 4. 需要登录的接口：从令牌还原当前用户
                if (route.needAuth) {
                    User user = resolveUser(exchange);
                    if (user == null) {
                        result = ApiResult.fail(ApiResult.CODE_UNAUTHORIZED, "未登录或登录状态已失效");
                        log(exchange, result, startAt);
                        send(exchange, result);
                        return;
                    }
                    ctx.setCurrentUser(user);
                }

                // 5. 交给处理器
                result = route.handler.handle(ctx);
            }
        } catch (ApiException e) {
            // 业务异常：按它自带的错误码返回
            result = ApiResult.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            // 未预期异常：记录堆栈，只给前端一句通用提示
            System.err.println("[" + LocalDateTime.now().format(LOG_TIME) + "] 接口异常 "
                    + method + " " + path + " -> " + e);
            e.printStackTrace();
            result = ApiResult.fail(5000, "服务器内部错误：" + e.getMessage());
        }

        log(exchange, result, startAt);
        send(exchange, result);
    }

    /**
     * 从请求头解析令牌并查出当前用户。
     *
     * <p>请求头格式：{@code Authorization: Bearer <token>}；
     * 为了调试方便，也允许不写 Bearer 前缀直接放令牌。
     *
     * @param exchange 请求对象
     * @return 当前用户；未带令牌或令牌无效时返回 null
     */
    private User resolveUser(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        Long userId = SessionManager.getUserId(token);
        return authService.getById(userId);
    }

    /**
     * 解析查询字符串。
     *
     * @param rawQuery URL 中 ? 之后的部分，可为 null
     * @return 参数名到值的映射
     */
    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            // URL 里的中文是百分号编码的，必须解码，否则"按标题搜索中文"会失败
            String name = idx < 0 ? pair : pair.substring(0, idx);
            String value = idx < 0 ? "" : pair.substring(idx + 1);
            params.put(decode(name), decode(value));
        }
        return params;
    }

    /** URL 解码（UTF-8） */
    private String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    /**
     * 读取并解析 JSON 请求体。
     *
     * @param exchange 请求对象
     * @return 请求体 JSON；GET/DELETE 或空请求体返回空对象
     */
    private JsonObject readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            return new JsonObject();
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        try {
            return JsonUtil.parseObject(text);
        } catch (Exception e) {
            throw new ApiException(ApiResult.CODE_PARAM, "请求体不是合法的 JSON");
        }
    }

    /**
     * 写入响应。
     *
     * <p>响应一律是 UTF-8 编码的 JSON。
     * HTTP 状态码：未登录返回 401，其余一律 200 ——
     * 真正的业务结果由响应体里的 code 表示，前端按 code 判断。
     *
     * @param exchange 请求对象
     * @param result   要返回的结果
     */
    private void send(HttpExchange exchange, ApiResult result) throws IOException {
        byte[] bytes = JsonUtil.toJson(result).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(result.getCode() == ApiResult.CODE_UNAUTHORIZED ? 401 : 200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * 写入允许跨域的响应头。
     *
     * <p>开发期前端跑在 5173 端口、后端在 8080，属于跨域请求；
     * 虽然前端 Vite 里配了代理可以规避，但直接打开浏览器或用 Postman
     * 调试时仍然需要这些响应头。
     */
    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "3600");
    }

    /**
     * 打印一行访问日志，便于在控制台观察前端到底调了哪个接口。
     *
     * @param exchange 请求对象
     * @param result   处理结果
     * @param startAt  开始处理的时间戳（毫秒）
     */
    private void log(HttpExchange exchange, ApiResult result, long startAt) {
        String flag = result.getCode() == ApiResult.CODE_OK ? "OK  " : "FAIL";
        System.out.printf("[%s] %s %-6s %s %s -> code=%d %s (%dms)%n",
                LocalDateTime.now().format(LOG_TIME),
                flag,
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery(),
                result.getCode(),
                result.getMessage(),
                System.currentTimeMillis() - startAt);
    }

    // ==================================================================
    // 三、内部类：一条路由
    // ==================================================================

    /** 一条路由记录：方法、原始模板、编译后的正则、参数名、处理器、是否需要登录 */
    private static class Route {
        private final String method;
        private final String template;
        private final Pattern pattern;
        private final List<String> paramNames;
        private final ApiHandler handler;
        private final boolean needAuth;

        private Route(String method, String template, Pattern pattern,
                      List<String> paramNames, ApiHandler handler, boolean needAuth) {
            this.method = method;
            this.template = template;
            this.pattern = pattern;
            this.paramNames = paramNames;
            this.handler = handler;
            this.needAuth = needAuth;
        }
    }
}
