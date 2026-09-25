package com.hbk.activity.web;

import com.hbk.activity.service.AuthService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Web 接口服务启动类（V2.0 的入口之一，与控制台入口 {@code ui.MainMenu} 并列）。
 *
 * <p>【结构】
 * <pre>
 *   WebServerMain（本类） ──┐
 *                          ├──> Service ──> DAO ──> JDBC ──> MySQL
 *   ui.MainMenu（控制台） ──┘
 * </pre>
 * 两个入口共用同一套业务逻辑，这正是 V1.0 分层设计带来的好处：
 * 新增一种"界面"不需要动业务规则和数据访问。
 *
 * <p>【V2.0 的接口变化】从 14 个增加到 21 个：
 * <ul>
 *   <li>新增 3 个教师审核 / 递补接口（US-07、US-09）；</li>
 *   <li>新增 4 个管理员接口（US-11 ~ US-13），全部要求 ADMIN 角色；</li>
 *   <li>{@code GET /api/activities/{id}/registrations} 的返回结构
 *       从"一个大列表"改为"按状态分组的三个列表 + 三个人数"。</li>
 * </ul>
 *
 * <p>【为什么用 JDK 自带的 HttpServer】
 * {@code com.sun.net.httpserver.HttpServer} 是 JDK 内置的轻量 HTTP 服务器，
 * 不需要引入任何框架或额外 jar。用它先把"HTTP 接口"这件事本身写清楚
 * （路由、请求解析、令牌、JSON），到 V3.0 换成 Spring Boot 时，
 * 就能具体感受到框架帮忙省掉了哪些重复劳动。
 *
 * <p>【启动方式】
 * <pre>
 *   build.bat web            或
 *   java -cp "build/classes;lib/*" com.hbk.activity.web.WebServerMain
 * </pre>
 * 启动后接口前缀为 http://localhost:8080/api ，按 Ctrl+C 停止。
 *
 * <p>【依赖前提】MySQL 已启动，且项目根目录下有 config/db.properties。
 */
public class WebServerMain {

    /** 监听端口；前端 Vite 的 /api 代理指向这个端口 */
    private static final int PORT = 8080;

    /** 处理请求的线程数：同一个时刻最多并行处理 8 个请求 */
    private static final int THREADS = 8;

    public static void main(String[] args) {
        try {
            // ---------- 1. 准备业务对象与路由表 ----------
            AuthService authService = new AuthService();
            AuthApi authApi = new AuthApi();
            ActivityApi activityApi = new ActivityApi();
            RegistrationApi registrationApi = new RegistrationApi();
            AdminApi adminApi = new AdminApi();

            ApiRouter router = new ApiRouter(authService);

            // 下面这段就像一张接口清单，按注册顺序一目了然
            router
                    // 认证（注册与登录免登录即可访问）
                    .publicPost("/api/auth/register", authApi::register)
                    .publicPost("/api/auth/login", authApi::login)
                    .post("/api/auth/logout", authApi::logout)
                    .get("/api/auth/me", authApi::me)
                    // 活动
                    .get("/api/activities", activityApi::list)
                    .get("/api/activities/{id}", activityApi::detail)
                    .post("/api/activities", activityApi::create)
                    .put("/api/activities/{id}", activityApi::update)
                    .put("/api/activities/{id}/close", activityApi::close)
                    .delete("/api/activities/{id}", activityApi::delete)
                    // 报名（学生）
                    .post("/api/activities/{id}/registrations", registrationApi::register)
                    .delete("/api/activities/{id}/registrations", registrationApi::cancel)
                    .get("/api/registrations/mine", registrationApi::mine)
                    // 报名名单与审核（教师）—— V2.0 新增三个审核/递补接口
                    .get("/api/activities/{id}/registrations", registrationApi::roster)
                    .put("/api/activities/{id}/registrations/{studentId}/approve",
                            registrationApi::approve)
                    .put("/api/activities/{id}/registrations/{studentId}/reject",
                            registrationApi::reject)
                    .put("/api/activities/{id}/registrations/{studentId}/promote",
                            registrationApi::promote)
                    // 管理员（V2.0 新增，全部要求 ADMIN 角色）
                    .get("/api/admin/activities", adminApi::activities)
                    .get("/api/admin/users", adminApi::users)
                    .put("/api/admin/users/{id}/disable", adminApi::disableUser)
                    .put("/api/admin/users/{id}/enable", adminApi::enableUser);

            // ---------- 2. 创建并启动 HTTP 服务器 ----------
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            // /api 开头的所有请求都交给路由器处理
            server.createContext("/api", router::dispatch);
            // 用线程池处理请求，避免一个慢请求阻塞其它请求
            server.setExecutor(Executors.newFixedThreadPool(THREADS));
            server.start();

            printBanner();

            // 关闭钩子：按 Ctrl+C 时优雅停止
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n正在停止接口服务 ...");
                server.stop(0);
                System.out.println("接口服务已停止。");
            }));
        } catch (IOException e) {
            System.err.println("接口服务启动失败：" + e.getMessage());
            System.err.println("可能原因：端口 " + PORT + " 已被占用（是不是已经启动了一个？）");
        }
    }

    /** 打印启动信息与接口清单，方便对照测试 */
    private static void printBanner() {
        System.out.println("""
                ==========================================================
                  校园活动管理系统 V2.0 —— 接口服务已启动
                ----------------------------------------------------------
                  接口前缀： http://localhost:8080/api
                  监听端口： 8080（前端 Vite 的 /api 代理指向这里）
                  处理线程： 8
                ----------------------------------------------------------
                  接口清单（共 21 个）：
                    POST   /api/auth/register                注册
                    POST   /api/auth/login                   登录
                    POST   /api/auth/logout                  退出登录
                    GET    /api/auth/me                      当前用户
                    GET    /api/activities                   活动列表
                    GET    /api/activities/{id}              活动详情
                    POST   /api/activities                   发布活动（教师）
                    PUT    /api/activities/{id}              修改活动（教师）
                    PUT    /api/activities/{id}/close        关闭报名（教师）
                    DELETE /api/activities/{id}              删除活动（教师）
                    POST   /api/activities/{id}/registrations 报名（学生）
                    DELETE /api/activities/{id}/registrations 取消报名（学生）
                    GET    /api/activities/{id}/registrations 报名名单（教师）
                    GET    /api/registrations/mine           我的报名（学生）
                    PUT    .../registrations/{stu}/approve   审核通过（教师）
                    PUT    .../registrations/{stu}/reject    审核驳回（教师）
                    PUT    .../registrations/{stu}/promote   候补递补（教师）
                    GET    /api/admin/activities             全部活动（管理员）
                    GET    /api/admin/users                  全部账号（管理员）
                    PUT    /api/admin/users/{id}/disable     停用账号（管理员）
                    PUT    /api/admin/users/{id}/enable      恢复账号（管理员）
                ----------------------------------------------------------
                  演示账号：teacher01 / student01 / student02 / student04
                            admin01              密码均为 123456
                            student03 已停用，用来验证停用账号不能登录
                  按 Ctrl+C 停止服务
                ==========================================================
                """);
    }
}
