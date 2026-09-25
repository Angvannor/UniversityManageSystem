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

        // 1.1 findAll() 至少能查出演示的 6 个账号。
        //     这里用 >= 而不是 ==，因为其他测试可能会留下测试账号
        //     （例如 UserDaoTest 的 test_user_01、本测试的 test_v2_student），
        //     写成 == 会在批量连跑时误报失败 —— 判断"能不能查出来"才是本项的目的。
        int total = userDAO.findAll().size();
        check("findAll() 至少返回 6 个用户（实际 " + total + "）", total >= 6);

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

        // 2.1 读得出：演示活动「程序设计大赛」设了上限 30
        //     这里按标题查而不是写死 id：写死 id 一旦演示数据被改动就会误报，
        //     而标题是 db/schema.sql 明确定义的演示数据标识。
        Activity a1 = findActivityByTitle("程序设计大赛");
        check("演示活动「程序设计大赛」存在", a1 != null);
        if (a1 != null) {
            check("它设了人数上限", a1.hasCapacityLimit());
            check("上限是 30（实际 " + a1.getCapacity() + "）",
                    Integer.valueOf(30).equals(a1.getCapacity()));
            check("它的参加条件不为空", a1.getEligibility() != null);
        }

        // 2.2 ★ 关键：capacity 为 NULL 时必须读成 null，不能读成 0
        //     演示活动「书法体验课」的 capacity 与 eligibility 都是 NULL
        Activity noCap = findActivityByTitle("书法体验课");
        check("演示活动「书法体验课」存在", noCap != null);
        if (noCap != null) {
            check("★ 它的 capacity 读成 null 而不是 0（实际 " + noCap.getCapacity() + "）",
                    noCap.getCapacity() == null);
            check("★ 它的 hasCapacityLimit() 返回 false", !noCap.hasCapacityLimit());
            check("它的 eligibility 是 null（无特殊条件）", noCap.getEligibility() == null);
        }

        //     演示活动「篮球友谊赛」设了上限 12，但没有参加条件
        Activity a3 = findActivityByTitle("篮球友谊赛");
        check("演示活动「篮球友谊赛」存在", a3 != null);
        if (a3 != null) {
            check("它的上限是 12（实际 " + a3.getCapacity() + "）",
                    Integer.valueOf(12).equals(a3.getCapacity()));
            check("它的 eligibility 是 null", a3.getEligibility() == null);
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

        // 【为什么自己造数据而不是用演示数据】
        // 本测试要验证"候补按进入候补的时间排序"，这需要构造一个
        // "报名时间顺序与审核时间顺序相反"的场景。用演示数据也能看到这个现象，
        // 但那样测试就依赖了「校园歌手大赛」的具体记录 ——
        // 一旦演示数据被别的测试改动，这里会误报失败。
        // 自己建活动、自己插报名记录，测试才能独立、可反复运行。
        Activity temp = new Activity();
        temp.setTitle("【测试】RegistrationDAO 状态与排序验证");
        temp.setDescription("DaoV2SmokeTest 临时数据，跑完会删掉");
        temp.setLocation("测试地点");
        temp.setStartTime(LocalDateTime.now().plusDays(5));
        temp.setEndTime(LocalDateTime.now().plusDays(5).plusHours(2));
        temp.setCapacity(1);
        temp.setStatus("OPEN");
        temp.setTeacherId(1L);
        activityDAO.insert(temp);

        Long id = temp.getId();
        check("创建测试活动成功（id=" + id + "）", id != null);
        if (id == null) {
            System.out.println();
            return;
        }

        try {
            // ---------- 3.1 三种状态各插一条 ----------
            //   学生 2 -> CONFIRMED（已审核）
            //   学生 3 -> WAITLISTED，报名时间【较早】、进入候补时间【较晚】-> 候补第 2
            //   学生 5 -> WAITLISTED，报名时间【较晚】、进入候补时间【较早】-> 候补第 1
            //   学生 4 未参与（student03 是停用账号，不拿来造报名数据）
            registrationDAO.insert(newRegistration(id, 2L));
            registrationDAO.insert(newRegistration(id, 3L));
            registrationDAO.insert(newRegistration(id, 5L));

            // 先写成待审核，再用"审核"的方式写入状态与时间（模拟真实流程）
            registrationDAO.updateStatusAndReviewTime(id, 2L,
                    ActivityRegistration.STATUS_CONFIRMED, LocalDateTime.of(2026, 10, 1, 9, 0));
            registrationDAO.updateStatusAndReviewTime(id, 3L,
                    ActivityRegistration.STATUS_WAITLISTED, LocalDateTime.of(2026, 10, 1, 9, 10));
            registrationDAO.updateStatusAndReviewTime(id, 5L,
                    ActivityRegistration.STATUS_WAITLISTED, LocalDateTime.of(2026, 10, 1, 9, 5));

            // 把学生 3 的报名时间改早、学生 5 的报名时间改晚，
            // 制造出"报名早的人反而排在候补后面"的局面
            updateRegisterTime(id, 3L, LocalDateTime.of(2026, 10, 1, 7, 30));
            updateRegisterTime(id, 5L, LocalDateTime.of(2026, 10, 1, 8, 30));

            // ---------- 3.2 ★ review_time 为 NULL 的情形 ----------
            //   新插一条待审核的记录（学生 4 是停用账号，这里改用学生 2 之外的方式：
            //   直接再建一个活动来验证，避免与上面的数据混在一起）
            Activity temp2 = new Activity();
            temp2.setTitle("【测试】review_time 为空的情形");
            temp2.setLocation("测试地点");
            temp2.setStartTime(LocalDateTime.now().plusDays(6));
            temp2.setEndTime(LocalDateTime.now().plusDays(6).plusHours(2));
            temp2.setStatus("OPEN");
            temp2.setTeacherId(1L);
            activityDAO.insert(temp2);
            Long id2 = temp2.getId();
            registrationDAO.insert(newRegistration(id2, 2L));

            ActivityRegistration notReviewed = registrationDAO.findByActivityAndStudent(id2, 2L);
            check("★ 未审核记录的 review_time 是 null（实际 " + notReviewed.getReviewTime() + "）",
                    notReviewed.getReviewTime() == null);
            check("★ 读取 null 的 review_time 没有抛空指针异常", true);
            check("待审核记录的 occupiesSlot() 为 true（也算报过名）",
                    notReviewed.isPendingReview() && notReviewed.occupiesSlot());

            // ---------- 3.3 countByStatus：三种状态各数一遍 ----------
            check("已正式参加 1 人（实际 "
                            + registrationDAO.countByStatus(id, ActivityRegistration.STATUS_CONFIRMED) + "）",
                    registrationDAO.countByStatus(id, ActivityRegistration.STATUS_CONFIRMED) == 1);
            check("候补 2 人（实际 "
                            + registrationDAO.countByStatus(id, ActivityRegistration.STATUS_WAITLISTED) + "）",
                    registrationDAO.countByStatus(id, ActivityRegistration.STATUS_WAITLISTED) == 2);
            check("待审核 0 人（实际 "
                            + registrationDAO.countByStatus(id, ActivityRegistration.STATUS_PENDING_REVIEW) + "）",
                    registrationDAO.countByStatus(id, ActivityRegistration.STATUS_PENDING_REVIEW) == 0);

            // ---------- 3.4 countRegistered 只统计"占位"的三种状态 ----------
            check("已报名人数 = 3（1 正式参加 + 2 候补，实际 "
                            + registrationDAO.countRegistered(id) + "）",
                    registrationDAO.countRegistered(id) == 3);

            // 把其中一条改成 REJECTED 后，它就不再占位
            registrationDAO.updateStatusAndReviewTime(id, 5L,
                    ActivityRegistration.STATUS_REJECTED, LocalDateTime.now());
            check("★ 改成 REJECTED 后不再计入已报名人数（应为 2，实际 "
                            + registrationDAO.countRegistered(id) + "）",
                    registrationDAO.countRegistered(id) == 2);
            // 还原成候补，继续验证排序
            registrationDAO.updateStatusAndReviewTime(id, 5L,
                    ActivityRegistration.STATUS_WAITLISTED, LocalDateTime.of(2026, 10, 1, 9, 5));

            // ---------- 3.5 ★ 候补队列按 review_time 排序（本测试的重点） ----------
            var waitlist = registrationDAO.findWaitlistOrderByReviewTime(id);
            check("候补队列有 2 人（实际 " + waitlist.size() + "）", waitlist.size() == 2);
            if (waitlist.size() == 2) {
                ActivityRegistration first = waitlist.get(0);
                ActivityRegistration second = waitlist.get(1);

                check("★ 候补第 1 位是学生 5（进入候补时间 09:05，实际 "
                                + first.getStudentId() + "，review_time=" + first.getReviewTime() + "）",
                        Long.valueOf(5L).equals(first.getStudentId()));
                check("★ 候补第 2 位是学生 3（进入候补时间 09:10，实际 "
                                + second.getStudentId() + "，review_time=" + second.getReviewTime() + "）",
                        Long.valueOf(3L).equals(second.getStudentId()));
                check("★ 学生 3 报名更早（07:30 < 08:30）却排在候补后面 —— "
                                + "证明排序用的是 review_time 而不是 register_time",
                        second.getRegisterTime().isBefore(first.getRegisterTime()));
            }

            // ---------- 3.6 按状态查名单 与 查全部 ----------
            check("按状态查 CONFIRMED 得 1 条",
                    registrationDAO.findByActivityIdAndStatus(id,
                            ActivityRegistration.STATUS_CONFIRMED).size() == 1);
            check("按状态查 WAITLISTED 得 2 条",
                    registrationDAO.findByActivityIdAndStatus(id,
                            ActivityRegistration.STATUS_WAITLISTED).size() == 2);
            check("findByActivityId 返回全部 3 条（含各状态）",
                    registrationDAO.findByActivityId(id).size() == 3);

            // ---------- 3.7 updateStatusAndReviewTime 一次写入两列 ----------
            LocalDateTime reviewAt = LocalDateTime.of(2026, 10, 2, 10, 0, 0);
            int rows = registrationDAO.updateStatusAndReviewTime(
                    id, 2L, ActivityRegistration.STATUS_REJECTED, reviewAt);
            check("审核写入影响 1 行", rows == 1);
            ActivityRegistration after = registrationDAO.findByActivityAndStudent(id, 2L);
            check("状态已改为 REJECTED", after.isRejected());
            check("review_time 已写入（实际 " + after.getReviewTime() + "）",
                    reviewAt.equals(after.getReviewTime()));

            // 清理第二个临时活动
            registrationDAO.deleteByActivityId(id2);
            activityDAO.deleteById(id2);

        } finally {
            // ---------- 清理：先删报名记录（外键），再删活动 ----------
            int regDeleted = registrationDAO.deleteByActivityId(id);
            int actDeleted = activityDAO.deleteById(id);
            check("清理测试活动（删除报名记录 " + regDeleted + " 条、活动 " + actDeleted + " 个）",
                    actDeleted == 1);
        }

        System.out.println();
    }

    // ==================================================================
    // 辅助方法
    // ==================================================================

    /**
     * 组装一条待审核的报名记录。
     *
     * @param activityId 活动id
     * @param studentId  学生id
     * @return 报名记录对象
     */
    private static ActivityRegistration newRegistration(Long activityId, Long studentId) {
        ActivityRegistration registration = new ActivityRegistration();
        registration.setActivityId(activityId);
        registration.setStudentId(studentId);
        registration.setStatus(ActivityRegistration.STATUS_PENDING_REVIEW);
        return registration;
    }

    /**
     * 直接修改报名时间（仅测试用）。
     *
     * <p>RegistrationDAO 没有提供修改 register_time 的方法 ——
     * 正式功能里报名时间一旦写入就不该再改，所以不能为测试去污染 DAO。
     * 这里直接用 JDBC 执行一条 UPDATE。
     *
     * @param activityId   活动id
     * @param studentId    学生id
     * @param registerTime 目标报名时间
     */
    private static void updateRegisterTime(Long activityId, Long studentId,
                                           LocalDateTime registerTime) {
        String sql = "UPDATE activity_registration SET register_time = ? "
                + "WHERE activity_id = ? AND student_id = ?";
        try (java.sql.Connection conn = com.hbk.activity.util.DBUtil.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, registerTime);
            ps.setLong(2, activityId);
            ps.setLong(3, studentId);
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 按标题查找演示活动。
     *
     * <p>测试里不应该写死活动 id —— id 会随数据变动，写死之后一旦演示数据
     * 被改动就会误报失败。按标题查找更稳定，标题是 db/schema.sql 明确定义的。
     *
     * @param title 活动标题
     * @return 活动对象；找不到返回 null
     */
    private static Activity findActivityByTitle(String title) {
        for (Activity activity : activityDAO.findAll()) {
            if (title.equals(activity.getTitle())) {
                return activity;
            }
        }
        return null;
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
