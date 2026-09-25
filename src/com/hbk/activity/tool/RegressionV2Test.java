package com.hbk.activity.tool;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.dao.RegistrationDAO;
import com.hbk.activity.dao.UserDAO;
import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.entity.User;
import com.hbk.activity.service.ActivityService;
import com.hbk.activity.service.AuthService;
import com.hbk.activity.service.RegistrationService;
import com.hbk.activity.util.PasswordUtil;

import java.time.LocalDateTime;

/**
 * V2.0 阶段 6（测试）的回归与边界测试。
 *
 * <p>【为什么要单独写这个类，而不是散在其它测试里】
 * 它回答两个问题，这两个问题都需要"集中逐条对照"才看得清楚：
 * <ol>
 *   <li><b>V1.0 已确认的六项业务规则有没有因为 V2.0 的改造而失效？</b>
 *       这六条写在报告里，评审时会逐条看。散在各个测试里不好对照，
 *       集中成一个 §1，每条一行，一眼就能看出是否回归。</li>
 *   <li><b>哪些边界情况还没被测过？</b>
 *       把"传不存在的 id""对别人的活动做操作""参数越界"这类
 *       非正常路径集中在一个 §2，避免它们夹在正常流程里被忽略。</li>
 * </ol>
 *
 * <p>【运行方式】需要 MySQL 已启动且已执行过 db/schema.sql：
 * <pre>
 *   build.bat run com.hbk.activity.tool.RegressionV2Test
 * </pre>
 * 本类自带数据清理，可反复运行。
 *
 * @author HBK组
 */
public class RegressionV2Test {

    private static int passed = 0;
    private static int failed = 0;

    private static final AuthService authService = new AuthService();
    private static final ActivityService activityService = new ActivityService();
    private static final RegistrationService registrationService = new RegistrationService();

    private static final ActivityDAO activityDAO = new ActivityDAO();
    private static final RegistrationDAO registrationDAO = new RegistrationDAO();
    private static final UserDAO userDAO = new UserDAO();

    private static final Long TEACHER_ID = 1L;        // teacher01，演示活动的发布者
    private static final Long OTHER_TEACHER_ID = 999L; // 不存在的教师，用来验证归属校验
    private static final Long LI = 2L;                // student01 李同学
    private static final Long WANG = 3L;              // student02 王同学

    public static void main(String[] args) {
        System.out.println("=== V2.0 阶段6 回归与边界测试开始 ===");
        System.out.println();

        testV1RulesRegression();
        testBoundaryCases();

        System.out.println();
        System.out.println("=== 结果：通过 " + passed + " 项，失败 " + failed + " 项 ===");
        if (failed > 0) {
            System.out.println("存在失败项，请检查上面的输出。");
        }
    }

    // ==================================================================
    // 一、V1.0 六项业务规则回归（对应 US-15）
    // ==================================================================
    private static void testV1RulesRegression() {
        System.out.println("[1] V1.0 六项业务规则回归（US-15：不得因本轮改造而失效）");

        Long id = createTempActivity("【测试】六项规则回归", null);
        if (id == null) {
            check("创建测试活动成功", false);
            return;
        }

        try {
            // ---------- 规则① 同一学生不能重复报名同一活动 ----------
            check("规则① 李同学首次报名成功", registrationService.register(id, LI) == null);
            String duplicate = registrationService.register(id, LI);
            check("规则① ★ 重复报名被拒绝（实际：" + duplicate + "）",
                    duplicate != null && duplicate.contains("不能重复报名"));

            // ---------- 规则② 只有 OPEN 的活动才能报名 ----------
            Long closedId = createTempActivity("【测试】已关闭的活动", null);
            activityDAO.updateStatus(closedId, "CLOSED");
            String closedRegister = registrationService.register(closedId, LI);
            check("规则② ★ 已关闭的活动不能报名（实际：" + closedRegister + "）",
                    closedRegister != null && closedRegister.contains("已关闭报名"));
            cleanupActivity(closedId);

            // ---------- 规则③ 取消报名只改状态、记录保留、可重新报名且复用原记录 ----------
            ActivityRegistration before = registrationService.myRegistration(id, LI);
            Long recordIdBefore = before.getId();
            check("规则③ 取消报名成功", registrationService.cancel(id, LI) == null);
            ActivityRegistration afterCancel = registrationService.myRegistration(id, LI);
            check("规则③ ★ 取消后记录仍然存在（只是状态变了）", afterCancel != null);
            check("规则③ ★ 记录主键没有变（说明是改状态而不是删了重建）",
                    recordIdBefore.equals(afterCancel.getId()));

            check("规则③ 取消后可以重新报名", registrationService.register(id, LI) == null);
            ActivityRegistration afterRe = registrationService.myRegistration(id, LI);
            check("规则③ ★ 重新报名复用同一行（主键仍不变）",
                    recordIdBefore.equals(afterRe.getId()));
            check("规则③ 重新报名后回到待审核", afterRe.isPendingReview());
            check("规则③ 重新报名清空了上一轮的审核时间",
                    afterRe.getReviewTime() == null);

            // ---------- 规则④ 教师只能管理、查看自己发布的活动 ----------
            Activity mine = new Activity();
            mine.setId(id);
            mine.setTitle("想改别人的活动");
            check("规则④ ★ 修改他人活动被拒绝",
                    containsOnly(activityService.update(mine, OTHER_TEACHER_ID)));
            check("规则④ ★ 关闭他人活动被拒绝",
                    containsOnly(activityService.close(id, OTHER_TEACHER_ID)));
            check("规则④ ★ 删除他人活动被拒绝",
                    containsOnly(activityService.delete(id, OTHER_TEACHER_ID)));
            check("规则④ ★ 查看他人活动的报名名单返回空列表",
                    registrationService.activityRoster(id, OTHER_TEACHER_ID).isEmpty());

            // ---------- 规则⑥ 密码不以明文保存 ----------
            //   （规则⑤"有报名不能删"单独放在 §1 末尾，因为它要构造三种状态）
            User student = userDAO.findByUsername("student01");
            check("规则⑥ ★ 数据库里存的不是明文密码（实际长度 "
                            + (student == null ? "?" : student.getPassword().length()) + "）",
                    student != null && !"123456".equals(student.getPassword()));
            check("规则⑥ ★ 但用正确密码能校验通过（说明存的是它的哈希）",
                    student != null && PasswordUtil.matches("123456", student.getPassword()));

            // ---------- 规则⑤ 已有有效报名的活动不能删除 ----------
            //   分三种占位状态各验证一次：待审核 / 正式参加 / 候补
            Activity forDelete = activityDAO.findById(id);
            check("规则⑤ ★ 只有【待审核】记录时，删除被拒绝",
                    forDelete != null && deleteRefused(id));

            registrationService.review(id, LI, TEACHER_ID, true);   // 通过 -> 有名额 -> 正式参加
            check("准备数据：李同学转为正式参加",
                    registrationService.myRegistration(id, LI).isConfirmed());
            check("规则⑤ ★ 有【正式参加】记录时，删除仍被拒绝", deleteRefused(id));

            // 第二个学生：活动未设上限，所以通过后也是正式参加；
            // 这里换个思路 —— 先把上限调成 1 再让王同学报名，才能造出候补
            Activity limited = activityDAO.findById(id);
            limited.setCapacity(1);
            activityDAO.update(limited);

            check("准备数据：王同学报名成功", registrationService.register(id, WANG) == null);
            registrationService.review(id, WANG, TEACHER_ID, true);  // 上限 1 已满 -> 候补
            check("准备数据：王同学转为候补",
                    registrationService.myRegistration(id, WANG).isWaitlisted());

            // 让李同学退出，此时活动上只剩"候补"状态的记录
            check("准备数据：李同学取消报名", registrationService.cancel(id, LI) == null);
            check("规则⑤ ★ 只剩【候补】记录时，删除依然被拒绝", deleteRefused(id));

            // 全部取消之后才能删除
            check("准备数据：王同学也取消", registrationService.cancel(id, WANG) == null);
            check("规则⑤ ★ 全部取消后可以删除", activityService.delete(id, TEACHER_ID) == null);
            check("规则⑤ 删除后确实查不到了", activityService.detail(id) == null);

        } finally {
            // 如果中间的断言失败导致没走到删除那一步，这里兜底清理
            if (activityDAO.findById(id) != null) {
                cleanupActivity(id);
            }
        }

        System.out.println();
    }

    // ==================================================================
    // 二、边界与非正常路径
    // ==================================================================
    private static void testBoundaryCases() {
        System.out.println("[2] 边界与非正常路径");

        Long id = createTempActivity("【测试】边界情况", 5);
        if (id == null) {
            check("创建测试活动成功", false);
            return;
        }

        try {
            // ---------- 2.1 活动不存在 ----------
            long ghost = 999999L;
            check("报名不存在的活动被拒绝",
                    "活动不存在".equals(registrationService.register(ghost, LI)));
            check("取消不存在的活动的报名被拒绝（提示尚未报名）",
                    hasText(registrationService.cancel(ghost, LI), "尚未报名"));
            check("审核不存在的活动被拒绝",
                    "活动不存在".equals(registrationService.review(ghost, LI, TEACHER_ID, true)));
            check("递补不存在的活动被拒绝",
                    "活动不存在".equals(registrationService.promote(ghost, LI, TEACHER_ID)));

            // ---------- 2.2 学生没报名就审核 / 递补 ----------
            check("审核一个没报名的学生被拒绝",
                    hasText(registrationService.review(id, LI, TEACHER_ID, true), "没有报名"));
            check("递补一个没报名的学生被拒绝",
                    hasText(registrationService.promote(id, LI, TEACHER_ID), "没有报名"));

            // ---------- 2.3 归属校验：rosterByStatus 与 waitlist（V2.0 新增方法） ----------
            check("★ rosterByStatus 对他人活动返回空列表",
                    registrationService.rosterByStatus(id, OTHER_TEACHER_ID,
                            ActivityRegistration.STATUS_CONFIRMED).isEmpty());
            check("★ rosterByStatus 对本人的活动正常返回（不抛异常）",
                    registrationService.rosterByStatus(id, TEACHER_ID,
                            ActivityRegistration.STATUS_CONFIRMED) != null);
            check("★ waitlist 对他人活动返回空列表",
                    registrationService.waitlist(id, OTHER_TEACHER_ID).isEmpty());
            check("★ waitlist 对 null 教师 id 返回空列表（不抛空指针）",
                    registrationService.waitlist(id, null).isEmpty());
            check("★ rosterByStatus 对 null 教师 id 返回空列表",
                    registrationService.rosterByStatus(id, null,
                            ActivityRegistration.STATUS_CONFIRMED).isEmpty());

            // ---------- 2.4 myRegistration 的空值处理 ----------
            check("myRegistration(activityId=null) 返回 null 而不是抛异常",
                    registrationService.myRegistration(null, LI) == null);
            check("myRegistration(studentId=null) 返回 null",
                    registrationService.myRegistration(id, null) == null);
            check("从未报名的学生查自己的记录返回 null",
                    registrationService.myRegistration(id, LI) == null);
            check("hasRegistered 对 null 参数返回 false",
                    !registrationService.hasRegistered(null, LI));

            // ---------- 2.5 参数越界 ----------
            Activity badCapacity = buildActivity("【测试】上限为负", -1);
            check("上限为负数被拒绝",
                    hasText(activityService.publish(badCapacity), "人数上限"));

            Activity updated = activityDAO.findById(id);
            updated.setCapacity(0);
            check("★ 修改活动时上限为 0 同样被拒绝（update 也走了校验）",
                    hasText(activityService.update(updated, TEACHER_ID), "人数上限"));

            // ---------- 2.6 注册角色白名单 ----------
            check("★ 非法角色字符串被拒绝（实际：PRINCIPAL）",
                    hasText(authService.register("test_principal", "123456", "校长", "PRINCIPAL"),
                            "只能注册学生或教师"));
            check("角色为 null 时也被拒绝",
                    hasText(authService.register("test_null_role", "123456", "无角色", null),
                            "只能注册学生或教师"));

            // ---------- 2.7 查询的空值处理 ----------
            check("getByUsername 传空字符串返回 null",
                    authService.getByUsername("") == null);
            check("getByUsername 传 null 返回 null",
                    authService.getByUsername(null) == null);
            check("getById 传 null 返回 null",
                    authService.getById(null) == null);

        } finally {
            cleanupActivity(id);
        }

        System.out.println();
    }

    // ==================================================================
    // 辅助方法
    // ==================================================================

    /** 判断 Service 返回的提示是否包含某段文字（含 null 保护） */
    private static boolean hasText(String message, String keyword) {
        return message != null && message.contains(keyword);
    }

    /** 判断 Service 返回的失败原因是不是"权限/归属"类（含"只能"） */
    private static boolean containsOnly(String message) {
        return hasText(message, "只能");
    }

    /** 判断"删除活动"这个动作是否被拒绝了 */
    private static boolean deleteRefused(Long activityId) {
        String error = activityService.delete(activityId, TEACHER_ID);
        return error != null && error.contains("人报名");
    }

    /**
     * 建一个临时活动并返回它的 id。
     *
     * @param title    标题
     * @param capacity 人数上限，null 表示不限制
     * @return 活动 id；创建失败返回 null
     */
    private static Long createTempActivity(String title, Integer capacity) {
        Activity activity = buildActivity(title, capacity);
        String error = activityService.publish(activity);
        return error == null ? activity.getId() : null;
    }

    /** 组装一个临时活动对象（不落库） */
    private static Activity buildActivity(String title, Integer capacity) {
        Activity activity = new Activity();
        activity.setTitle(title);
        activity.setDescription("RegressionV2Test 创建的临时数据，测试结束会删除");
        activity.setLocation("测试地点");
        activity.setStartTime(LocalDateTime.now().plusDays(7));
        activity.setEndTime(LocalDateTime.now().plusDays(7).plusHours(2));
        activity.setCapacity(capacity);
        activity.setEligibility(null);
        activity.setStatus("OPEN");
        activity.setTeacherId(TEACHER_ID);
        return activity;
    }

    /**
     * 清理一个临时活动（先删报名记录，再删活动）。
     *
     * <p>顺序不能反：activity_registration.activity_id 有外键指向 activity.id，
     * 表里只要还剩任何一行该活动的报名记录，删活动就会被外键拦住
     * （哪怕那条记录的状态是"已取消"）。
     */
    private static void cleanupActivity(Long activityId) {
        if (activityId == null) {
            return;
        }
        registrationDAO.deleteByActivityId(activityId);
        activityDAO.deleteById(activityId);
    }

    /** 断言并打印结果 */
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
