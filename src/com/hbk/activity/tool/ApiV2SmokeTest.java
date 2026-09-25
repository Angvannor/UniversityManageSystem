package com.hbk.activity.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * V2.0 阶段 4（接口层）的冒烟测试：直接发真实 HTTP 请求验证接口。
 *
 * <p>【为什么必须单独测接口层】
 * 业务层的测试是直接调 Java 方法，<b>绕过了 HTTP 这一层</b>。
 * 但接口层自己也有会出错的地方，而且这些错误业务层测试完全发现不了：
 * <ul>
 *   <li><b>路由匹配</b>：{@code /api/activities/{id}/registrations/{studentId}/approve}
 *       有两段路径参数，模板转正则如果写错就会匹配不上或匹配错；</li>
 *   <li><b>角色校验</b>：接口层要做一次角色判断，不能只靠前端隐藏菜单。
 *       这一层漏了，学生用 Postman 就能调到管理员接口；</li>
 *   <li><b>返回结构</b>：名单从"一个大列表"改成了"三个分组 + 三个人数"，
 *       字段名和层级必须和前端约定一致；</li>
 *   <li><b>信息泄露</b>：{@code AdminService.listAllUsers()} 返回的实体带 password，
 *       接口层必须转成不含密码的 VO —— 这类问题只有看真实的 HTTP 响应体才能发现。</li>
 * </ul>
 *
 * <p>【运行方式】需要 MySQL 已启动，且<b>接口服务已经在跑</b>：
 * <pre>
 *   终端1： build.bat web
 *   终端2： build.bat run com.hbk.activity.tool.ApiV2SmokeTest
 * </pre>
 *
 * <p>【对演示数据的影响】本测试对演示数据只做<b>只读</b>检查；
 * 所有会改数据的操作（报名、审核、递补、删除）都在一个**临时活动**上完成，
 * 结束时把它删掉。因此可以反复运行，不会污染演示数据。
 *
 * @author HBK组
 */
public class ApiV2SmokeTest {

    private static final String BASE = "http://localhost:8080/api";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static int passed = 0;
    private static int failed = 0;

    /** 演示数据里的 id */
    private static final long TEACHER_ID = 1L;   // teacher01
    private static final long LI = 2L;           // student01 李同学
    private static final long WANG = 3L;         // student02 王同学
    private static final long SUN = 5L;          // student04 孙同学
    private static final long ADMIN_ID = 6L;     // admin01

    public static void main(String[] args) {
        System.out.println("=== V2.0 阶段4 接口层冒烟测试开始 ===");
        System.out.println("目标服务：" + BASE);
        System.out.println();

        // 先确认服务在跑；连不上就直接退出并给出提示，避免后面一大堆莫名其妙的失败
        if (!serverIsUp()) {
            System.out.println("❌ 连不上 " + BASE);
            System.out.println("   请先在另一个终端执行：build.bat web");
            return;
        }
        System.out.println("服务已就绪。");
        System.out.println();

        try {
            testAuthAndDisabledAccount();
            testRegisterRoleWhitelist();

            String teacherToken = login("teacher01", "123456");
            String studentToken = login("student01", "123456");
            String student2Token = login("student02", "123456");
            String adminToken = login("admin01", "123456");

            testRoleGuards(studentToken, teacherToken, adminToken);
            testAdminEndpoints(adminToken, studentToken);
            testActivityFields(teacherToken, studentToken, student2Token);
            testRosterGrouping(teacherToken);
            testReviewAndPromoteFlow(teacherToken, studentToken, student2Token);
        } finally {
            System.out.println();
            System.out.println("=== 结果：通过 " + passed + " 项，失败 " + failed + " 项 ===");
            if (failed > 0) {
                System.out.println("存在失败项，请检查上面的输出。");
            }
        }
    }

    // ==================================================================
    // 一、登录与停用账号
    // ==================================================================
    private static void testAuthAndDisabledAccount() {
        System.out.println("[1] 登录与停用账号（US-13）");

        // 正常账号
        JsonObject ok = post("/auth/login", "{\"username\":\"student01\",\"password\":\"123456\"}", null);
        check("student01 能正常登录", ok.get("code").getAsInt() == 0);

        // ★ 停用账号：密码是对的，但必须返回 2003 而不是 2001
        JsonObject disabled = post("/auth/login", "{\"username\":\"student03\",\"password\":\"123456\"}", null);
        check("★ 被停用账号登录返回错误码 2003（实际 " + disabled.get("code").getAsInt() + "）",
                disabled.get("code").getAsInt() == 2003);
        check("提示信息说明了原因（实际：" + disabled.get("message").getAsString() + "）",
                disabled.get("message").getAsString().contains("停用"));

        // 密码错误的账号仍返回 2001（与停用区分开）
        JsonObject wrongPwd = post("/auth/login", "{\"username\":\"student01\",\"password\":\"wrong\"}", null);
        check("密码错误返回 2001（与停用区分开，实际 " + wrongPwd.get("code").getAsInt() + "）",
                wrongPwd.get("code").getAsInt() == 2001);

        System.out.println();
    }

    // ==================================================================
    // 二、注册角色白名单（防权限提升）
    // ==================================================================
    private static void testRegisterRoleWhitelist() {
        System.out.println("[2] 注册角色白名单（防自我提权）");

        // ★ 尝试直接注册成管理员
        JsonObject asAdmin = post("/auth/register",
                "{\"username\":\"hacker_admin_api\",\"password\":\"123456\","
                        + "\"name\":\"想提权的人\",\"role\":\"ADMIN\"}", null);
        check("★ 用 ADMIN 角色注册被拒绝（实际 code=" + asAdmin.get("code").getAsInt() + "）",
                asAdmin.get("code").getAsInt() == 403);
        check("提示信息说明了只能注册学生或教师",
                asAdmin.get("message").getAsString().contains("只能注册学生或教师"));

        // 确认这个账号真的没被建出来
        JsonObject login = post("/auth/login",
                "{\"username\":\"hacker_admin_api\",\"password\":\"123456\"}", null);
        check("★ 该账号确实不存在，无法登录（实际 code=" + login.get("code").getAsInt() + "）",
                login.get("code").getAsInt() == 2001);

        System.out.println();
    }

    // ==================================================================
    // 三、角色越权矩阵
    // ==================================================================
    private static void testRoleGuards(String studentToken, String teacherToken, String adminToken) {
        System.out.println("[3] 角色越权矩阵（US-12 / US-14）");

        // 未登录
        check("未带令牌访问需登录接口返回 401",
                get("/activities", null).get("code").getAsInt() == 401);

        // 学生不能碰管理员接口
        check("学生访问 /admin/users 被拒绝 403",
                get("/admin/users", studentToken).get("code").getAsInt() == 403);
        check("学生访问 /admin/activities 被拒绝 403",
                get("/admin/activities", studentToken).get("code").getAsInt() == 403);
        check("学生停用账号被拒绝 403",
                put("/admin/users/" + SUN + "/disable", "{}", studentToken).get("code").getAsInt() == 403);

        // 教师不能碰管理员接口
        check("教师访问 /admin/users 被拒绝 403",
                get("/admin/users", teacherToken).get("code").getAsInt() == 403);

        // ★ 管理员不能碰报名接口（US-14，REQ-V2-17）
        check("★ 管理员查看报名名单被拒绝 403",
                get("/activities/2/registrations", adminToken).get("code").getAsInt() == 403);
        check("★ 管理员审核报名被拒绝 403",
                put("/activities/2/registrations/" + WANG + "/approve", "{}", adminToken)
                        .get("code").getAsInt() == 403);
        check("★ 管理员递补被拒绝 403",
                put("/activities/2/registrations/" + WANG + "/promote", "{}", adminToken)
                        .get("code").getAsInt() == 403);
        check("★ 管理员报名（学生接口）被拒绝 403",
                post("/activities/1/registrations", "{}", adminToken).get("code").getAsInt() == 403);
        check("★ 管理员查看【我的报名】被拒绝 403",
                get("/registrations/mine", adminToken).get("code").getAsInt() == 403);

        // 教师不能调学生接口
        check("教师查看【我的报名】被拒绝 403",
                get("/registrations/mine", teacherToken).get("code").getAsInt() == 403);

        System.out.println();
    }

    // ==================================================================
    // 四、管理员接口（US-11、US-12）
    // ==================================================================
    private static void testAdminEndpoints(String adminToken, String studentToken) {
        System.out.println("[4] 管理员接口（US-11 / US-12）");

        JsonObject users = get("/admin/users", adminToken);
        check("管理员能查看全部账号", users.get("code").getAsInt() == 0);
        int userCount = users.getAsJsonArray("data").size();
        check("账号数 >= 6（实际 " + userCount + "）", userCount >= 6);

        // ★ 响应体里绝不能出现 password 字段
        String rawUsers = users.toString();
        check("★ 账号列表响应里不含 password 字段", !rawUsers.contains("password"));

        // 找到 admin01 与 student03，确认状态字段已返回
        JsonObject adminVo = findUser(users.getAsJsonArray("data"), "admin01");
        check("能查到 admin01", adminVo != null);
        if (adminVo != null) {
            check("admin01 角色是 ADMIN",
                    "ADMIN".equals(adminVo.get("role").getAsString()));
            check("admin01 状态是 ACTIVE 且有中文说明",
                    "ACTIVE".equals(adminVo.get("status").getAsString())
                            && "可用".equals(adminVo.get("statusText").getAsString()));
        }
        JsonObject disabledVo = findUser(users.getAsJsonArray("data"), "student03");
        check("student03 显示为已停用",
                disabledVo != null
                        && "已停用".equals(disabledVo.get("statusText").getAsString()));

        JsonObject activities = get("/admin/activities", adminToken);
        check("管理员能查看全部活动", activities.get("code").getAsInt() == 0);
        check("活动数 >= 3（实际 " + activities.getAsJsonArray("data").size() + "）",
                activities.getAsJsonArray("data").size() >= 3);

        // ★ 管理员不能停用自己
        JsonObject self = put("/admin/users/" + ADMIN_ID + "/disable", "{}", adminToken);
        check("★ 管理员不能停用自己（实际 code=" + self.get("code").getAsInt() + "）",
                self.get("code").getAsInt() == 403);

        // 停用 -> 恢复一个学生，确认能走通且状态真的变了
        check("停用孙同学成功",
                put("/admin/users/" + SUN + "/disable", "{}", adminToken).get("code").getAsInt() == 0);
        JsonObject afterDisable = findUser(
                get("/admin/users", adminToken).getAsJsonArray("data"), "student04");
        check("停用后状态是 DISABLED",
                afterDisable != null && "DISABLED".equals(afterDisable.get("status").getAsString()));

        // 被停用的账号不能登录
        check("★ 被停用后立即无法登录（错误码 2003）",
                post("/auth/login", "{\"username\":\"student04\",\"password\":\"123456\"}", null)
                        .get("code").getAsInt() == 2003);

        check("恢复孙同学成功",
                put("/admin/users/" + SUN + "/enable", "{}", adminToken).get("code").getAsInt() == 0);
        check("恢复后可以登录",
                post("/auth/login", "{\"username\":\"student04\",\"password\":\"123456\"}", null)
                        .get("code").getAsInt() == 0);

        // 不存在的用户
        check("停用不存在的用户返回 404",
                put("/admin/users/99999/disable", "{}", adminToken).get("code").getAsInt() == 404);

        System.out.println();
    }

    // ==================================================================
    // 五、活动新字段与学生状态（US-01、US-05、US-06）
    // ==================================================================
    private static void testActivityFields(String teacherToken, String studentToken,
                                           String student2Token) {
        System.out.println("[5] 活动人数上限 / 参加条件 / 学生自己的状态（US-01 / 05 / 06）");

        // 学生视角看活动 1（李同学在该活动是 CONFIRMED）
        JsonObject a1 = get("/activities/1", studentToken).getAsJsonObject("data");
        check("详情返回 capacity（实际 " + a1.get("capacity") + "）",
                a1.has("capacity") && a1.get("capacity").getAsInt() == 30);
        check("详情返回 eligibility",
                a1.has("eligibility") && !a1.get("eligibility").isJsonNull());
        check("详情返回 confirmedCount", a1.has("confirmedCount"));
        check("详情返回 waitlistedCount", a1.has("waitlistedCount"));
        check("★ 李同学看到自己的状态是 CONFIRMED（实际 " + a1.get("myStatus").getAsString() + "）",
                "CONFIRMED".equals(a1.get("myStatus").getAsString()));
        check("★ 状态中文说明是【已确认参加】",
                "已确认参加".equals(a1.get("myStatusText").getAsString()));
        check("joined 为 true", a1.get("joined").getAsBoolean());

        // 王同学在活动 1 是 PENDING_REVIEW
        JsonObject a1ByWang = get("/activities/1", student2Token).getAsJsonObject("data");
        check("★ 王同学看到自己的状态是 PENDING_REVIEW（待老师审核）",
                "PENDING_REVIEW".equals(a1ByWang.get("myStatus").getAsString())
                        && "待老师审核".equals(a1ByWang.get("myStatusText").getAsString()));

        // 活动 4「书法体验课」没设上限，capacity 应为 JSON null
        JsonObject a4 = get("/activities/4", studentToken).getAsJsonObject("data");
        check("★ 未设上限的活动 capacity 返回 null（而不是 0）",
                a4.has("capacity") && a4.get("capacity").isJsonNull());
        check("未填参加条件的活动 eligibility 返回 null",
                a4.has("eligibility") && a4.get("eligibility").isJsonNull());

        // 教师视角不返回 myStatus（他不是学生）
        JsonObject a1ByTeacher = get("/activities/1", teacherToken).getAsJsonObject("data");
        check("教师查看时 myStatus 为 null",
                !a1ByTeacher.has("myStatus") || a1ByTeacher.get("myStatus").isJsonNull());

        // 人数上限校验：0 应被拒绝且错误码是参数错误
        String badActivity = "{\"title\":\"【测试】上限非法\",\"location\":\"测试地点\","
                + "\"startTime\":\"2026-12-01 09:00:00\",\"endTime\":\"2026-12-01 11:00:00\","
                + "\"capacity\":0}";
        JsonObject bad = post("/activities", badActivity, teacherToken);
        check("★ 人数上限为 0 被拒绝，错误码 1000（实际 " + bad.get("code").getAsInt() + "）",
                bad.get("code").getAsInt() == 1000);

        System.out.println();
    }

    // ==================================================================
    // 六、教师名单分组返回（US-08）
    // ==================================================================
    private static void testRosterGrouping(String teacherToken) {
        System.out.println("[6] 教师报名名单分组（US-08）");

        JsonObject resp = get("/activities/2/registrations", teacherToken);
        check("教师能查看自己活动的名单", resp.get("code").getAsInt() == 0);

        JsonObject data = resp.getAsJsonObject("data");
        check("返回 activityTitle", data.has("activityTitle"));
        check("上限是 1（实际 " + data.get("capacity").getAsInt() + "）",
                data.get("capacity").getAsInt() == 1);
        check("已正式参加 1 人（实际 " + data.get("confirmedCount").getAsInt() + "）",
                data.get("confirmedCount").getAsInt() == 1);
        check("候补 2 人（实际 " + data.get("waitlistedCount").getAsInt() + "）",
                data.get("waitlistedCount").getAsInt() == 2);
        check("待审核 0 人（实际 " + data.get("pendingReviewCount").getAsInt() + "）",
                data.get("pendingReviewCount").getAsInt() == 0);
        check("★ 已满员 full=true（1 个名额已被占）", data.get("full").getAsBoolean());

        JsonArray waitlisted = data.getAsJsonArray("waitlisted");
        check("候补分组有 2 条", waitlisted.size() == 2);
        if (waitlisted.size() == 2) {
            long first = waitlisted.get(0).getAsJsonObject().get("studentId").getAsLong();
            long second = waitlisted.get(1).getAsJsonObject().get("studentId").getAsLong();
            check("★ 候补第 1 位是王同学（id=3），实际 " + first, first == WANG);
            check("★ 候补第 2 位是孙同学（id=5），实际 " + second, second == SUN);
            check("候补项带 reviewTime 与中文状态",
                    waitlisted.get(0).getAsJsonObject().has("reviewTime")
                            && "候补中".equals(waitlisted.get(0).getAsJsonObject()
                                    .get("statusText").getAsString()));
        }
        check("三个分组字段都存在",
                data.has("pendingReview") && data.has("waitlisted") && data.has("confirmed"));

        System.out.println();
    }

    // ==================================================================
    // 七、审核与递补的完整流程（US-07、US-09）
    //   全部在一个临时活动上做，测完删掉，不碰演示数据
    // ==================================================================
    private static void testReviewAndPromoteFlow(String teacherToken, String studentToken,
                                                 String student2Token) {
        System.out.println("[7] 审核与递补完整流程（US-07 / US-09）");

        // 建一个上限为 1 的临时活动
        String body = "{\"title\":\"【测试】接口层审核流程验证\",\"location\":\"测试地点\","
                + "\"startTime\":\"2026-12-20 09:00:00\",\"endTime\":\"2026-12-20 11:00:00\","
                + "\"capacity\":1}";
        JsonObject created = post("/activities", body, teacherToken);
        check("教师发布临时活动成功", created.get("code").getAsInt() == 0);
        if (created.get("code").getAsInt() != 0) {
            return;
        }
        long id = created.get("data").getAsLong();
        System.out.println("    临时活动 id = " + id + "（人数上限 1）");

        try {
            // 7.1 学生报名 -> 待审核
            check("李同学报名成功",
                    post("/activities/" + id + "/registrations", "{}", studentToken)
                            .get("code").getAsInt() == 0);
            JsonObject detail = get("/activities/" + id, studentToken).getAsJsonObject("data");
            check("★ 报名后状态是 PENDING_REVIEW（实际 " + detail.get("myStatus").getAsString() + "）",
                    "PENDING_REVIEW".equals(detail.get("myStatus").getAsString()));

            // 7.2 重复报名被拒（3001）
            check("重复报名返回 3001",
                    post("/activities/" + id + "/registrations", "{}", studentToken)
                            .get("code").getAsInt() == 3001);

            // 7.3 学生不能审核
            check("学生调用审核接口被拒 403",
                    put("/activities/" + id + "/registrations/" + LI + "/approve", "{}", studentToken)
                            .get("code").getAsInt() == 403);

            // 7.4 审核通过 -> 有名额 -> 正式参加
            check("教师审核通过成功",
                    put("/activities/" + id + "/registrations/" + LI + "/approve", "{}", teacherToken)
                            .get("code").getAsInt() == 0);
            JsonObject afterApprove = get("/activities/" + id, studentToken).getAsJsonObject("data");
            check("★ 通过且有名额 -> CONFIRMED（实际 " + afterApprove.get("myStatus").getAsString() + "）",
                    "CONFIRMED".equals(afterApprove.get("myStatus").getAsString()));

            // 7.5 重复审核同一条 -> 3005
            JsonObject again = put("/activities/" + id + "/registrations/" + LI + "/approve",
                    "{}", teacherToken);
            check("★ 对已确认的记录再审返回 3005（实际 " + again.get("code").getAsInt() + "）",
                    again.get("code").getAsInt() == 3005);

            // 7.6 第二个人报名并通过 -> 已满 -> 候补
            check("王同学报名成功",
                    post("/activities/" + id + "/registrations", "{}", student2Token)
                            .get("code").getAsInt() == 0);
            check("教师审核通过（第二名）",
                    put("/activities/" + id + "/registrations/" + WANG + "/approve", "{}", teacherToken)
                            .get("code").getAsInt() == 0);
            JsonObject wangDetail = get("/activities/" + id, student2Token).getAsJsonObject("data");
            check("★ 通过但已满 -> WAITLISTED（实际 " + wangDetail.get("myStatus").getAsString() + "）",
                    "WAITLISTED".equals(wangDetail.get("myStatus").getAsString()));
            check("★ 候补的中文说明是【候补中】",
                    "候补中".equals(wangDetail.get("myStatusText").getAsString()));

            // 7.7 名额已满时递补 -> 3006
            JsonObject promoteFull = put("/activities/" + id + "/registrations/" + WANG + "/promote",
                    "{}", teacherToken);
            check("★ 名额已满时递补返回 3006（实际 " + promoteFull.get("code").getAsInt() + "）",
                    promoteFull.get("code").getAsInt() == 3006);

            // 7.8 李同学取消 -> 空出名额
            check("李同学取消报名成功",
                    delete("/activities/" + id + "/registrations", studentToken)
                            .get("code").getAsInt() == 0);

            // 7.9 空出名额后递补成功
            check("★ 空出名额后递补成功",
                    put("/activities/" + id + "/registrations/" + WANG + "/promote", "{}", teacherToken)
                            .get("code").getAsInt() == 0);
            JsonObject wangAfter = get("/activities/" + id, student2Token).getAsJsonObject("data");
            check("★ 递补后变成 CONFIRMED（实际 " + wangAfter.get("myStatus").getAsString() + "）",
                    "CONFIRMED".equals(wangAfter.get("myStatus").getAsString()));

            // 7.10 驳回分支
            check("孙同学报名成功",
                    post("/activities/" + id + "/registrations", "{}",
                            login("student04", "123456")).get("code").getAsInt() == 0);
            check("教师驳回成功",
                    put("/activities/" + id + "/registrations/" + SUN + "/reject", "{}", teacherToken)
                            .get("code").getAsInt() == 0);
            JsonObject sunDetail = get("/activities/" + id, login("student04", "123456"))
                    .getAsJsonObject("data");
            check("★ 驳回 -> REJECTED 且中文为【未通过】",
                    "REJECTED".equals(sunDetail.get("myStatus").getAsString())
                            && "未通过".equals(sunDetail.get("myStatusText").getAsString()));

        } finally {
            // 清理：先让报名的人取消，再删活动
            // （删除活动时若有报名记录会被 Service 拒绝，这与 V1.5 的规则一致）
            cancelQuietly(id, studentToken);
            cancelQuietly(id, student2Token);
            cancelQuietly(id, login("student04", "123456"));
            JsonObject deleted = delete("/activities/" + id, teacherToken);
            check("清理临时活动（实际 code=" + deleted.get("code").getAsInt() + "）",
                    deleted.get("code").getAsInt() == 0);
        }

        System.out.println();
    }

    // ==================================================================
    // HTTP 与 JSON 辅助方法
    // ==================================================================

    /** 取消报名（清理用，失败不报错） */
    private static void cancelQuietly(long activityId, String token) {
        try {
            delete("/activities/" + activityId + "/registrations", token);
        } catch (Exception ignored) {
            // 清理阶段的失败不影响测试结论
        }
    }

    private static boolean serverIsUp() {
        try {
            get("/activities", null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 登录并返回令牌；失败抛异常（后续检查都会用到它） */
    private static String login(String username, String password) {
        JsonObject resp = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", null);
        if (resp.get("code").getAsInt() != 0) {
            throw new IllegalStateException("登录失败：" + username + " -> " + resp);
        }
        return resp.getAsJsonObject("data").get("token").getAsString();
    }

    private static JsonObject get(String path, String token) {
        return send(request(path, token).GET().build());
    }

    private static JsonObject post(String path, String body, String token) {
        return send(request(path, token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private static JsonObject put(String path, String body, String token) {
        return send(request(path, token)
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private static JsonObject delete(String path, String token) {
        return send(request(path, token).DELETE().build());
    }

    private static HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    /** 发请求并把响应体解析成 JsonObject */
    private static JsonObject send(HttpRequest request) {
        try {
            HttpResponse<String> response =
                    CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("请求失败：" + request.uri() + " -> " + e.getMessage(), e);
        }
    }

    /** 在用户数组里按账号查找 */
    private static JsonObject findUser(JsonArray users, String username) {
        for (JsonElement element : users) {
            JsonObject user = element.getAsJsonObject();
            if (username.equals(user.get("username").getAsString())) {
                return user;
            }
        }
        return null;
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [OK]   " + description);
        } else {
            failed++;
            System.out.println("  [FAIL] " + description);
        }
    }
}
