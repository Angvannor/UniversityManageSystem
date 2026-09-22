package com.hbk.activity.web;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录会话管理：保存"令牌 → 用户 id"的对应关系。
 *
 * <p>【为什么需要它】
 * HTTP 是"无状态"的：服务器处理完一个请求就忘了是谁发的。
 * 所以登录成功后要发给前端一个令牌（token），前端之后每次请求都在
 * 请求头里带上它，服务端凭令牌认出用户。
 *
 * <p>【本项目的实现方式：内存 Map】
 * <pre>
 *   登录成功 → UUID 生成 token → sessions.put(token, userId) → 返回给前端
 *   后续请求 → 从请求头取 token → sessions.get(token) → 得到 userId
 * </pre>
 *
 * <p>【已知局限（V3.0 会改进）】
 * <ul>
 *   <li>数据在内存里，<b>服务重启后所有人需要重新登录</b>；</li>
 *   <li>无法多实例部署（换台机器就不认识这个 token 了）。</li>
 * </ul>
 * V3.0 换成 JWT 后，令牌自带签名与用户信息，就能解决这两个问题。
 *
 * <p>用 ConcurrentHashMap 是因为 HttpServer 用线程池处理请求，
 * 多个请求会并发读写这个 Map。
 */
public final class SessionManager {

    /** 令牌 → 用户 id */
    private static final Map<String, Long> SESSIONS = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    /**
     * 为用户创建一个新令牌。
     *
     * @param userId 用户 id
     * @return 新生成的令牌字符串
     */
    public static String createToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        SESSIONS.put(token, userId);
        return token;
    }

    /**
     * 根据令牌查询用户 id。
     *
     * @param token 令牌；可为 null
     * @return 用户 id；令牌无效时返回 null
     */
    public static Long getUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return SESSIONS.get(token);
    }

    /**
     * 注销令牌（退出登录时调用）。
     *
     * @param token 令牌
     */
    public static void remove(String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
    }

    /**
     * 当前在线会话数（仅用于日志观察，没有业务含义）。
     *
     * @return 会话数量
     */
    public static int size() {
        return SESSIONS.size();
    }
}
