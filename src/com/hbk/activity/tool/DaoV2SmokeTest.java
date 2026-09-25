package com.hbk.activity.tool;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.dao.RegistrationDAO;
import com.hbk.activity.dao.UserDAO;
import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.entity.User;

import java.time.LocalDateTime;

/**
 * V2.0 阶段 2（实体与 DAO）的冒烟测试：验证新增字段与新增方法是否真的能用。
 *
 * <p>【为什么单独写这个类】
 * 阶段 2 改了实体字段和 DAO 的读写语句，其中最容易出错的是<b>可空字段</b>：
 * <ul>
 *   <li>{@code activity.capacity} 允许为 NULL（表示不限制人数），
 *       而 {@code ResultSet.getInt()} 遇到 NULL 返回的是 0 而不是报错 ——
 *       如果忘了用 {@code wasNull()} 判断，"不限制"就变成了"一个人都不能报"；</li>
 *   <li>{@code activity_registration.review_time} 允许为 NULL（还没审核），
 *       对 null 直接调 {@code toLocalDateTime()} 会抛空指针异常。</li>
 * </ul>
 * 这两类错误都是"编译能过、运行时才炸或静默算错"，靠肉眼看代码很难发现，
 * 必须真的连上数据库跑一遍。正式的业务测试在第 6 阶段扩展，
 * 本类只负责确认 DAO 层"读得出、写得进、NULL 没读错"。
 *
 * <p>【运行方式】需要 MySQL 已启动且已执行过 db/schema.sql：
 * <pre>
 *   build.bat run com.hbk.activity.tool.DaoV2SmokeTest
 * </pre>
 *
 * <p>本类自带数据清理，可以反复运行。测试期间会临时插入一个活动，
 * 结束时删除，不会污染演示数据。
 *
 * @author HBK组
 */
public class DaoV2SmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final UserDAO userDAO = new UserDAO();
    private static final ActivityDAO activityDAO = new ActivityDAO();
    private static final RegistrationDAO registrationDAO = new RegistrationDAO();

    public static void main(String[] args) {
        System.out.println("=== V2.0 阶段2 DAO 冒烟测试开始 ===");
        System.out.println("（依赖 db/schema.sql 的演示数据，若数据被改过请先重跑脚本）");
        System.out.println();

        testUserDao();
        testActivityDao();
        testRegistrationDao();

        System.out.println();
        System.out.println("=== 结果：通过 " + passed + " 项，失败 " + failed + " 项 ===");
        if (failed > 0) {
            System.out.println("存在失败项，请检查上面的输出。");
        }
    }

    // ==================================================================
    // 一、UserDAO：新增 status 字段 + findAll() + updateStatus()
    // ==================================================================
    private static void testUserDao() {
        System.out.println("[1] UserDAO");

        // 1.1 findAll() 能查出全部 6 个演示账号
        int total = userDAO.findAll().size();
        check("findAll() 返回 6 个用户（实际 " + total + "）", total == 6);

        // 1.2 status 字段被正确读出：admin01 是 ADMIN/ACTIVE
        User admin = userDAO.findByUsername("admin01");
        check("admin01 存在", admin != null);
        if (admin != null) {
            check("admin01 的角色是 ADMIN（实际 " + admin.getRole() + "）",
                    "ADMIN".equals(admin.getRole()));
            check("admin01 状态是 ACTIVE（实际 " + admin.getStatus() + "）",
                    User.STATUS_ACTIVE.equals(admin.getStatus()));
            check("admin01 未被停用", !admin.isDisabled());
        }

        // 1.3 status 为 DISABLED 的账号能被正确识别
        User disabled = userDAO.findByUsername("student03");
        check("student03 存在", disabled != null);
        if (disabled != null) {
            check("student03 状态是 DISABLED（实际 " + disabled.getStatus() + "）",
                    User.STATUS_DISABLED.equals(disabled.getStatus()));
            check("student03.isDisabled() 返回 true", disabled.isDisabled());
        }

        // 1.4 updateStatus()：停用再恢复，改完要能读回来
        User teacher = userDAO.findByUsername("teacher01");
        if (teacher != null) {
            int rows = userDAO.updateStatus(teacher.getId(), User.STATUS_DISABLED);
            check("停用 teacher01 影响 1 行", rows == 1);
            check("停用后读回是 DISABLED",
                    userDAO.findById(teacher.getId()).isDisabled());

            rows = userDAO.updateStatus(teacher.getId(), User.STATUS_ACTIVE);
            check("恢复 teacher01 影响 1 行", rows == 1);
            check("恢复后读回是 ACTIVE",
                    User.STATUS_ACTIVE.equals(userDAO.findById(teacher.getId()).getStatus()));
            System.out.println("    （teacher01 已恢复为 ACTIVE，演示数据未被破坏）");
        }

        System.out.println();
    }

    // ==================================================================
    // 二、ActivityDAO：新增 capacity / eligibility，重点验证 NULL
    // ==================================================================
    private static void testActivityDao() {
        System.out.println("[2] ActivityDAO");

        // 2.1 读得出：活动1 设了上限 30
        Activity a1 = activityDAO.findById(1L);
        check("活动1 存在", a1 != null);
        if (a1 != null) {
            check("活动1 有上限", a1.hasCapacityLimit());
            check("活动1 上限是 30（实际 " + a1.getCapacity() + "）",
                    Integer.valueOf(30).equals(a1.getCapacity()));
            check("活动1 参加了条件不为空", a1.getEligibility() != null);
        }

        // 2.2 ★ 关键：capacity 为 NULL 时必须读成 null，不能读成 0
        //     活动4「书法体验课」的 capacity 与 eligibility 都是 NULL
        Activity a4 = activityDAO.findById(4L);
        check("活动4 存在", a4 != null);
        if (a4 != null) {
            check("★ 活动4 的 capacity 读成 null 而不是 0（实际 " + a4.getCapacity() + "）",
                    a4.getCapacity() == null);
            check("★ 活动4.hasCapacityLimit() 返回 false", !a4.hasCapacityLimit());
            check("活动4 的 eligibility 是 null（无特殊条件）", a4.getEligibility() == null);
        }

        //     活动3「篮球友谊赛」设了上限 12，但没有参加条件
        Activity a3 = activityDAO.findById(3L);
        check("活动3 存在", a3 != null);
        if (a3 != null) {
            check("活动3 上限是 12（实际 " + a3.getCapacity() + "）",
                    Integer.valueOf(12).equals(a3.getCapacity()));
            check("活动3 有上限", a3.hasCapacityLimit());
            check("活动3 的 eligibility 是 null", a3.getEligibility() == null);
        }

        // 2.3 ★ 写进去再读回来，确认 NULL 双向都不丢
        Activity temp = new Activity();
        temp.setTitle("【测试】capacity 为 null 的活动");
        temp.setDescription("DaoV2SmokeTest 临时数据，跑完会删掉");
        temp.setLocation("测试地点");
        temp.setStartTime(LocalDateTime.now().plusDays(1));
        temp.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        temp.setCapacity(null);                       // 不限制人数
        temp.setEligibility(null);                    // 无特殊条件
        temp.setStatus("OPEN");
        temp.setTeacherId(1L);

        int rows = activityDAO.insert(temp);
        check("插入 capacity=null 的活动成功", rows == 1);
        check("插入后主键被回填（id=" + temp.getId() + "）", temp.getId() != null);

        if (temp.getId() != null) {
            Activity readBack = activityDAO.findById(temp.getId());
            check("★ 读回来 capacity 仍是 null（实际 " + readBack.getCapacity() + "）",
                    readBack.getCapacity() == null);
            check("★ 读回来 eligibility 仍是 null", readBack.getEligibility() == null);

            // 2.4 update() 也要能处理 null 与有值两种
            readBack.setCapacity(5);
            readBack.setEligibility("测试条件");
            check("update() 改成有值成功", activityDAO.update(readBack) == 1);
            Activity after = activityDAO.findById(temp.getId());
            check("改后 capacity=5（实际 " + after.getCapacity() + "）",
                    Integer.valueOf(5).equals(after.getCapacity()));
            check("改后 eligibility='测试条件'",
                    "测试条件".equals(after.getEligibility()));

            // 2.5 清理临时活动
            int deleted = activityDAO.deleteById(temp.getId());
            check("清理临时活动（删除 " + deleted + " 行）", deleted == 1);
        }

        System.out.println();
    }

    // ==================================================================
    // 三、RegistrationDAO：新增 review_time + 按状态查询与统计
    // ==================================================================
    private static void testRegistrationDao() {
        System.out.println("[3] RegistrationDAO");

        // 3.1 ★ review_time 为 NULL 时要读成 null，不能抛空指针
        ActivityRegistration pending = registrationDAO.findByActivityAndStudent(1L, 3L);
        check("活动1+王同学的记录存在", pending != null);
        if (pending != null) {
            check("状态是 PENDING_REVIEW（实际 " + pending.getStatus() + "）",
                    pending.isPendingReview());
            check("★ 未审核记录的 review_time 是 null（实际 " + pending.getReviewTime() + "）",
                    pending.getReviewTime() == null);
            check("occupiesSlot() 返回 true（待审核也占报名资格）", pending.occupiesSlot());
        }

        // 3.2 已审核记录的 review_time 有值
        ActivityRegistration confirmed = registrationDAO.findByActivityAndStudent(1L, 2L);
        if (confirmed != null) {
            check("活动1+李同学的记录是 CONFIRMED", confirmed.isConfirmed());
            check("已审核记录的 review_time 不为 null", confirmed.getReviewTime() != null);
        }

        // 3.3 countByStatus：活动2 的三种人数
        int confirmedCount = registrationDAO.countByStatus(2L, ActivityRegistration.STATUS_CONFIRMED);
        int waitlistedCount = registrationDAO.countByStatus(2L, ActivityRegistration.STATUS_WAITLISTED);
        int pendingCount = registrationDAO.countByStatus(2L, ActivityRegistration.STATUS_PENDING_REVIEW);
        check("活动2 已确定参加 1 人（实际 " + confirmedCount + "）", confirmedCount == 1);
        check("活动2 候补 2 人（实际 " + waitlistedCount + "）", waitlistedCount == 2);
        check("活动2 待审核 0 人（实际 " + pendingCount + "）", pendingCount == 0);

        // 3.4 countRegistered 统计的是"占位"的三种状态之和
        //     活动2：1 已确认 + 2 候补 = 3（活动的 REJECTED 记录在活动3，不影响）
        int occupied = registrationDAO.countRegistered(2L);
        check("活动2 已报名人数 = 3（1 已确认 + 2 候补，实际 " + occupied + "）", occupied == 3);

        //     活动3 只有一条 REJECTED，不属于占位，应算 0
        check("活动3 已报名人数 = 0（只有被驳回的记录）",
                registrationDAO.countRegistered(3L) == 0);

        // 3.5 ★ 候补队列必须按 review_time 排：王同学(3) 在前，孙同学(5) 在后
        var waitlist = registrationDAO.findWaitlistOrderByReviewTime(2L);
        check("活动2 候补队列有 2 人（实际 " + waitlist.size() + "）", waitlist.size() == 2);
        if (waitlist.size() == 2) {
            Long first = waitlist.get(0).getStudentId();
            Long second = waitlist.get(1).getStudentId();
            check("★ 候补第1位是王同学(id=3)，实际 id=" + first, Long.valueOf(3L).equals(first));
            check("★ 候补第2位是孙同学(id=5)，实际 id=" + second, Long.valueOf(5L).equals(second));
            check("★ 孙同学报名更早却排在后面（证明用的是 review_time 而不是 register_time）",
                    waitlist.get(0).getRegisterTime().isAfter(waitlist.get(1).getRegisterTime()));
        }

        // 3.6 findByActivityIdAndStatus 与 findByActivityId 的分工
        check("按状态查 CONFIRMED 得 1 条",
                registrationDAO.findByActivityIdAndStatus(2L,
                        ActivityRegistration.STATUS_CONFIRMED).size() == 1);
        check("findByActivityId 返回全部 3 条（含各状态）",
                registrationDAO.findByActivityId(2L).size() == 3);

        // 3.7 updateStatusAndReviewTime：一次写入状态 + 审核时间，再读回来
        LocalDateTime reviewAt = LocalDateTime.of(2026, 10, 2, 10, 0, 0);
        int rows = registrationDAO.updateStatusAndReviewTime(
                1L, 3L, ActivityRegistration.STATUS_REJECTED, reviewAt);
        check("审核写入影响 1 行", rows == 1);
        ActivityRegistration after = registrationDAO.findByActivityAndStudent(1L, 3L);
        check("状态已改为 REJECTED", after.isRejected());
        check("review_time 已写入（实际 " + after.getReviewTime() + "）",
                reviewAt.equals(after.getReviewTime()));

        // 3.8 还原演示数据：改回 PENDING_REVIEW，并把 review_time 清成 null
        registrationDAO.updateStatusAndReviewTime(
                1L, 3L, ActivityRegistration.STATUS_PENDING_REVIEW, null);
        ActivityRegistration restored = registrationDAO.findByActivityAndStudent(1L, 3L);
        check("已还原为 PENDING_REVIEW 且 review_time 为 null",
                restored.isPendingReview() && restored.getReviewTime() == null);
        System.out.println("    （活动1 王同学的记录已还原为待审核，演示数据未被破坏）");

        System.out.println();
    }

    // ==================================================================
    // 辅助方法
    // ==================================================================

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
