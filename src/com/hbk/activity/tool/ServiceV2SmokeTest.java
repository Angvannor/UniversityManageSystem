package com.hbk.activity.tool;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.dao.RegistrationDAO;
import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.entity.User;
import com.hbk.activity.service.ActivityService;
import com.hbk.activity.service.AdminService;
import com.hbk.activity.service.AuthService;
import com.hbk.activity.service.RegistrationService;

import java.time.LocalDateTime;

/**
 * V2.0 阶段 3（业务层）的冒烟测试：验证报名状态机、审核、候补递补与账号停用。
 *
 * <p>【为什么单独写这个类】
 * 阶段 3 把「报名」从一个动作改成了一条状态链，其中<b>审核</b>这一步要把两个规则叠在一起：
 * <ol>
 *   <li>审核通过 ≠ 正式参加（教师访谈 T6）；</li>
 *   <li>有没有名额要看"已正式参加人数"够不够人数上限。</li>
 * </ol>
 * 这两条组合起来有四种分支（驳回 / 通过且有名额 / 通过但已满 / 活动不设上限），
 * 靠读代码很容易漏掉其中一支，必须真的建一个"上限为 1"的活动把三条路上都走一遍。
 *
 * <p>【运行方式】需要 MySQL 已启动且已执行过 db/schema.sql：
 * <pre>
 *   build.bat run com.hbk.activity.tool.ServiceV2SmokeTest
 * </pre>
 * 本类自带数据清理（临时活动与它产生的报名记录都会删掉），可反复运行。
 *
 * @author HBK组
 */
public class ServiceV2SmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final AuthService authService = new AuthService();
    private static final ActivityService activityService = new ActivityService();
    private static final RegistrationService registrationService = new RegistrationService();
    private static final AdminService adminService = new AdminService();

    private static final ActivityDAO activityDAO = new ActivityDAO();
    private static final RegistrationDAO registrationDAO = new RegistrationDAO();

    /** 演示数据里的教师（teacher01，同时也是全部演示活动的发布者） */
    private static final Long TEACHER_ID = 1L;
    /** 一个不存在的教师id，用来验证归属校验 */
    private static final Long OTHER_TEACHER_ID = 999L;

    /** 演示数据里的三个学生 */
    private static final Long LI = 2L;     // student01 李同学
    private static final Long WANG = 3L;   // student02 王同学
    private static final Long SUN = 5L;    // student04 孙同学

    /** 演示数据里 admin01 的 id，用于验证"管理员不能停用自己" */
    private static final Long ADMIN_ID = 6L;

    /** 本测试临时注册的账号名（可重复运行，存在就跳过创建） */
    private static final String TEST_STUDENT_USERNAME = "test_v2_student";

    public static void main(String[] args) {
        System.out.println("=== V2.0 阶段3 业务层冒烟测试开始 ===");
        System.out.println();

        testAuthService();
        testAdminService();
        testActivityCapacityValidation();
        testRegistrationStateMachine();

        System.out.println();
        System.out.println("=== 结果：通过 " + passed + " 项，失败 " + failed + " 项 ===");
        if (failed > 0) {
            System.out.println("存在失败项，请检查上面的输出。");
        }
    }

    // ==================================================================
    // 一、AuthService：注册角色白名单 + 停用账号
    // ==================================================================
    private static void testAuthService() {
        System.out.println("[1] AuthService（注册角色白名单 / 停用账号）");

        // 1.1 ★ 不允许自助注册管理员（否则任何人都能把自己提权成 ADMIN）
        String adminRegister = authService.register(
                "test_hacker_admin", "123456", "想提权的人", User.ROLE_ADMIN);
        check("★ 用 ADMIN 角色注册被拒绝（实际返回：" + adminRegister + "）",
                adminRegister != null && adminRegister.contains("只能注册学生或教师"));
        check("★ 该账号没有被真的建出来",
                authService.getByUsername("test_hacker_admin") == null);

        // 1.2 正常角色仍可注册。
        //     账号已存在时跳过创建 —— 这样测试可以反复运行而不会因为
        //     "该账号已被注册"而失败（UserDAO 没有删除方法，只能这样处理）。
        User created = authService.getByUsername(TEST_STUDENT_USERNAME);
        if (created == null) {
            String okRegister = authService.register(
                    TEST_STUDENT_USERNAME, "123456", "测试学生", User.ROLE_STUDENT);
            check("用 STUDENT 角色注册成功", okRegister == null);
            created = authService.getByUsername(TEST_STUDENT_USERNAME);
        } else {
            System.out.println("    （测试账号已存在，跳过创建）");
        }
        check("新账号状态是 ACTIVE",
                created != null && User.STATUS_ACTIVE.equals(created.getStatus()));

        // 1.3 ★ 停用账号：login() 会返回对象（密码是对的），但状态是 DISABLED
        //     这是有意设计：好让调用方区分"密码错"(2001) 和"账号停用"(2003)
        User disabled = authService.login("student03", "123456");
        check("★ 停用账号密码正确时 login() 返回非 null（供调用方区分错误码）", disabled != null);
        check("★ 但 user.isDisabled() 为 true，调用方必须据此拒绝登录",
                disabled != null && disabled.isDisabled());

        // 1.4 正常账号登录后 isDisabled() 为 false
        User active = authService.login("student01", "123456");
        check("正常账号可以登录且未被停用", active != null && !active.isDisabled());

        System.out.println();
    }

    // ==================================================================
    // 二、AdminService：管理员权限与账号停用恢复
    // ==================================================================
    private static void testAdminService() {
        System.out.println("[2] AdminService（监督视图 / 停用恢复）");

        // 2.1 监督视图。
        //     用 >= 而不是写死条数：其他测试可能临时增删活动，
        //     这里只验证"管理员确实能查到全部活动"这件事本身。
        check("listAllActivities() 能查出活动（>= 3 条，实际 "
                        + adminService.listAllActivities().size() + "）",
                adminService.listAllActivities().size() >= 3);
        check("listAllUsers() 能查出全部用户（>= 6 个，实际 "
                        + adminService.listAllUsers().size() + "）",
                adminService.listAllUsers().size() >= 6);

        // 2.2 ★ 管理员不能停用自己（否则把自己锁死，谁都进不去后台了）
        String selfDisable = adminService.disableUser(ADMIN_ID, ADMIN_ID);
        check("★ 管理员不能停用自己的账号（实际返回：" + selfDisable + "）",
                selfDisable != null && selfDisable.contains("不能停用自己"));

        // 2.3 停用 -> 恢复，走一遍完整流程
        String disableResult = adminService.disableUser(SUN, ADMIN_ID);
        check("停用孙同学成功", disableResult == null);
        User sun = authService.getById(SUN);
        check("停用后状态是 DISABLED", sun != null && sun.isDisabled());

        // 2.4 重复停用要被拒绝
        String again = adminService.disableUser(SUN, ADMIN_ID);
        check("重复停用被拒绝", again != null && again.contains("已经处于停用状态"));

        String enableResult = adminService.enableUser(SUN);
        check("恢复孙同学成功", enableResult == null);
        User sunBack = authService.getById(SUN);
        check("恢复后状态是 ACTIVE",
                sunBack != null && User.STATUS_ACTIVE.equals(sunBack.getStatus()));

        // 2.5 不存在的用户
        String notFound = adminService.disableUser(99999L, ADMIN_ID);
        check("停用不存在的用户被拒绝", notFound != null && notFound.contains("用户不存在"));

        System.out.println();
    }

    // ==================================================================
    // 三、ActivityService：人数上限校验
    // ==================================================================
    private static void testActivityCapacityValidation() {
        System.out.println("[3] ActivityService（人数上限 / 参加条件校验）");

        // 3.1 上限为 0 或负数要被拒绝
        Activity bad = buildTempActivity("【测试】上限非法", 0);
        String error0 = activityService.publish(bad);
        check("人数上限为 0 被拒绝（实际返回：" + error0 + "）",
                error0 != null && error0.contains("人数上限必须是大于 0"));

        Activity negative = buildTempActivity("【测试】上限负数", -5);
        String errorNegative = activityService.publish(negative);
        check("人数上限为负数被拒绝",
                errorNegative != null && errorNegative.contains("人数上限必须是大于 0"));

        // 3.2 ★ 上限留空（null）是合法的，表示不限制人数
        Activity noLimit = buildTempActivity("【测试】不限制人数", null);
        String errorNull = activityService.publish(noLimit);
        check("★ 人数上限留空可以发布（实际返回：" + errorNull + "）", errorNull == null);
        if (noLimit.getId() != null) {
            Activity readBack = activityDAO.findById(noLimit.getId());
            check("★ 留空读回来仍然是 null 而不是 0",
                    readBack.getCapacity() == null);
            activityDAO.deleteById(noLimit.getId());
        }

        // 3.3 参加条件只输空格 -> 规范成 null（避免界面上"有条件"却显示空白）
        Activity blankEligibility = buildTempActivity("【测试】空白参加条件", 10);
        blankEligibility.setEligibility("     ");
        String errorBlank = activityService.publish(blankEligibility);
        check("只输空格的参加条件可以发布", errorBlank == null);
        if (blankEligibility.getId() != null) {
            Activity readBack = activityDAO.findById(blankEligibility.getId());
            check("★ 纯空白的参加条件被规范成 null",
                    readBack.getEligibility() == null);
            activityDAO.deleteById(blankEligibility.getId());
        }

        // 3.4 参加条件超长要被拒绝
        Activity tooLong = buildTempActivity("【测试】条件超长", 10);
        tooLong.setEligibility("条".repeat(501));
        String errorLong = activityService.publish(tooLong);
        check("参加条件超过 500 字被拒绝",
                errorLong != null && errorLong.contains("不能超过 500"));

        System.out.println();
    }

    // ==================================================================
    // 四、RegistrationService：报名状态机的完整走查（本测试的重点）
    // ==================================================================
    private static void testRegistrationStateMachine() {
        System.out.println("[4] RegistrationService（状态机 / 审核 / 候补递补）");

        // 建一个"上限为 1"的活动：这样只要一个人通过，第二个人就必然进候补
        Activity activity = buildTempActivity("【测试】上限为1的活动", 1);
        String publishError = activityService.publish(activity);
        if (publishError != null || activity.getId() == null) {
            check("创建测试活动成功（失败原因：" + publishError + "）", false);
            return;
        }
        Long id = activity.getId();
        System.out.println("    已创建测试活动 id=" + id + "（人数上限 1）");

        try {
            // ---------- 4.1 报名后是「待审核」 ----------
            check("李同学报名成功", registrationService.register(id, LI) == null);
            ActivityRegistration li = registrationService.myRegistration(id, LI);
            check("★ 报名后的初始状态是 PENDING_REVIEW（实际 " + li.getStatus() + "）",
                    li.isPendingReview());
            check("★ 未审核时 review_time 为 null", li.getReviewTime() == null);
            check("此时【已报名人数】为 1", registrationService.countRegistered(id) == 1);
            check("此时【已正式参加】为 0",
                    registrationService.countByStatus(id, ActivityRegistration.STATUS_CONFIRMED) == 0);
            check("hasRegistered() 对未审核的报名返回 true（也算报过名）",
                    registrationService.hasRegistered(id, LI));

            // ---------- 4.2 不能重复报名 ----------
            String duplicate = registrationService.register(id, LI);
            check("★ 重复报名被拒绝（实际返回：" + duplicate + "）",
                    duplicate != null && duplicate.contains("不能重复报名"));

            // ---------- 4.3 审核：通过 + 有名额 -> 正式参加 ----------
            String reviewLi = registrationService.review(id, LI, TEACHER_ID, true);
            check("审核通过成功", reviewLi == null);
            ActivityRegistration liAfter = registrationService.myRegistration(id, LI);
            check("★ 通过且有名额 -> CONFIRMED（实际 " + liAfter.getStatus() + "）",
                    liAfter.isConfirmed());
            check("★ 审核写了 review_time", liAfter.getReviewTime() != null);

            // ---------- 4.4 第二个学生：通过 + 已满 -> 候补 ----------
            check("王同学报名成功", registrationService.register(id, WANG) == null);

            // 先验证归属校验：别的老师不能审
            String wrongTeacher = registrationService.review(id, WANG, OTHER_TEACHER_ID, true);
            check("★ 其他教师审核被拒绝（实际返回：" + wrongTeacher + "）",
                    wrongTeacher != null && wrongTeacher.contains("只能审核自己"));

            String reviewWang = registrationService.review(id, WANG, TEACHER_ID, true);
            check("审核通过成功（第二名）", reviewWang == null);
            ActivityRegistration wang = registrationService.myRegistration(id, WANG);
            check("★ 通过但已满 -> WAITLISTED（实际 " + wang.getStatus() + "）",
                    wang.isWaitlisted());
            check("★ 候补也记录了进入候补的时间", wang.getReviewTime() != null);

            // ---------- 4.5 第三个学生：驳回 -> 未通过 ----------
            check("孙同学报名成功", registrationService.register(id, SUN) == null);
            String rejectSun = registrationService.review(id, SUN, TEACHER_ID, false);
            check("驳回成功", rejectSun == null);
            ActivityRegistration sun = registrationService.myRegistration(id, SUN);
            check("★ 驳回 -> REJECTED（实际 " + sun.getStatus() + "）", sun.isRejected());
            check("★ 未通过的记录不占报名资格（hasRegistered 返回 false）",
                    !registrationService.hasRegistered(id, SUN));
            check("未通过不计入【已报名人数】", registrationService.countRegistered(id) == 2);

            // ---------- 4.6 已处理的记录不能再审 ----------
            String reviewAgain = registrationService.review(id, WANG, TEACHER_ID, true);
            check("★ 对候补中的记录再审被拒绝（实际返回：" + reviewAgain + "）",
                    reviewAgain != null && reviewAgain.contains("不是待审核"));

            // ---------- 4.7 名额已满时不能递补 ----------
            String promoteFull = registrationService.promote(id, WANG, TEACHER_ID);
            check("★ 名额已满时递补被拒绝（实际返回：" + promoteFull + "）",
                    promoteFull != null && promoteFull.contains("名额已满"));

            // ---------- 4.8 有人取消 -> 空出名额 -> 可以递补 ----------
            check("李同学取消报名成功", registrationService.cancel(id, LI) == null);
            ActivityRegistration liCancelled = registrationService.myRegistration(id, LI);
            check("取消后状态是 CANCELLED", liCancelled.isCancelled());
            check("★ 取消后空出一个名额（已正式参加归 0）",
                    registrationService.countByStatus(id, ActivityRegistration.STATUS_CONFIRMED) == 0);

            String promoteWang = registrationService.promote(id, WANG, TEACHER_ID);
            check("★ 空出名额后递补成功（实际返回：" + promoteWang + "）", promoteWang == null);
            ActivityRegistration wangAfter = registrationService.myRegistration(id, WANG);
            check("★ 递补后变成 CONFIRMED（实际 " + wangAfter.getStatus() + "）",
                    wangAfter.isConfirmed());

            // ---------- 4.9 递补也要校验归属 ----------
            check("孙同学重新报名成功", registrationService.register(id, SUN) == null);
            // 孙同学此前被驳回，重新报名应当复用原行并回到待审核
            ActivityRegistration sunAgain = registrationService.myRegistration(id, SUN);
            check("★ 被驳回后重新报名复用原行、回到 PENDING_REVIEW（实际 " + sunAgain.getStatus() + "）",
                    sunAgain.isPendingReview());
            check("★ 重新报名清空了上一轮的 review_time",
                    sunAgain.getReviewTime() == null);

            String promoteWrongTeacher = registrationService.promote(id, SUN, OTHER_TEACHER_ID);
            check("其他教师递补被拒绝",
                    promoteWrongTeacher != null && promoteWrongTeacher.contains("只能处理自己"));

            // ---------- 4.10 三种占位状态都能取消 ----------
            check("孙同学取消（待审核状态）成功", registrationService.cancel(id, SUN) == null);
            check("孙同学取消后状态是 CANCELLED",
                    registrationService.myRegistration(id, SUN).isCancelled());

            check("王同学取消（正式参加状态）成功", registrationService.cancel(id, WANG) == null);
            check("重复取消被拒绝",
                    registrationService.cancel(id, WANG) != null);

            // ---------- 4.11 人数统计口径 ----------
            check("全部取消后，已报名人数为 0", registrationService.countRegistered(id) == 0);
            check("已正式参加为 0",
                    registrationService.countByStatus(id, ActivityRegistration.STATUS_CONFIRMED) == 0);
            check("候补为 0",
                    registrationService.countByStatus(id, ActivityRegistration.STATUS_WAITLISTED) == 0);

        } finally {
            // ---------- 清理：先删报名记录（外键），再删活动 ----------
            int regDeleted = registrationDAO.deleteByActivityId(id);
            int actDeleted = activityDAO.deleteById(id);
            System.out.println("    （已清理测试活动 id=" + id
                    + "，删除报名记录 " + regDeleted + " 条、活动 " + actDeleted + " 个）");
        }

        System.out.println();
    }

    // ==================================================================
    // 辅助方法
    // ==================================================================

    /**
     * 组装一个用于测试的临时活动。
     *
     * @param title    标题
     * @param capacity 人数上限，null 表示不限制
     * @return 活动对象（teacherId 固定为演示教师）
     */
    private static Activity buildTempActivity(String title, Integer capacity) {
        Activity activity = new Activity();
        activity.setTitle(title);
        activity.setDescription("ServiceV2SmokeTest 创建的临时数据，测试结束会删除");
        activity.setLocation("测试地点");
        activity.setStartTime(LocalDateTime.now().plusDays(3));
        activity.setEndTime(LocalDateTime.now().plusDays(3).plusHours(2));
        activity.setCapacity(capacity);
        activity.setEligibility(null);
        activity.setStatus("OPEN");
        activity.setTeacherId(TEACHER_ID);
        return activity;
    }

    /**
     * 断言并打印结果。
     *
     * @param description 这一项在验证什么
     * @param condition   成立表示通过
     */
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
