package com.hbk.activity.tool;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.dao.RegistrationDAO;
import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * RegistrationDAO 的自动化测试（V2.0 阶段 6 改造）。
 *
 * <p>【用途】
 * 实际调用 RegistrationDAO 的方法读写数据库，验证 SQL、参数绑定和
 * 结果集转换是否正确。
 *
 * <p>【V2.0 相对 V1.5 的三处改造】
 * <ol>
 *   <li><b>只打印 → 断言</b>（技术债 T6）：旧版全靠人眼看控制台输出，
 *       出问题不会自动报错；现在末尾直接给出通过/失败计数。</li>
 *   <li><b>修掉一个会误删演示数据的缺陷</b>：
 *       旧版用「取列表最后一条」定位刚插入的活动，但
 *       {@code findByTeacherId()} 是<b>按 start_time 排序</b>的，
 *       "最后一条"可能是别的活动 —— 曾经因此把报名记录写到了演示活动
 *       「校园歌手大赛」上，并在清理时误删了李同学在该活动的正式参加记录。
 *       现在改用 {@code insert()} 回填的自增主键定位。</li>
 *   <li><b>状态取值跟上 V2.0</b>：初始状态由 {@code REGISTERED} 改为
 *       {@code PENDING_REVIEW}；新增 {@code review_time} 的读写验证。</li>
 * </ol>
 *
 * <p>【为什么要先建一个活动】
 * activity_registration 表的 activity_id 和 student_id 都是外键，
 * 指向 activity.id 和 user.id。直接插入不存在的活动 id 会被外键拒绝，
 * 所以测试先创建一个活动，测完再删掉。
 *
 * <p>【运行方式】需要 MySQL 已启动且已执行过 db/schema.sql：
 * <pre>
 *   build.bat run com.hbk.activity.tool.RegistrationDaoTest
 * </pre>
 * 测试自带数据清理，可以反复运行。
 *
 * @author HBK组
 */
public class RegistrationDaoTest {

    private static int passed = 0;
    private static int failed = 0;

    /** 测试使用的学生 id：student01 的 id 是 2 */
    private static final Long STUDENT_ID = 2L;

    /** 测试使用的教师 id：teacher01 的 id 是 1 */
    private static final Long TEACHER_ID = 1L;

    public static void main(String[] args) {
        ActivityDAO activityDAO = new ActivityDAO();
        RegistrationDAO dao = new RegistrationDAO();

        System.out.println("=== RegistrationDAO 测试开始 ===");

        // ---------- 0. 准备数据：先创建一个测试活动（外键需要） ----------
        Activity activity = new Activity();
        activity.setTitle("测试活动-报名功能验证");
        activity.setDescription("由 RegistrationDaoTest 创建");
        activity.setLocation("测试地点");
        activity.setStartTime(LocalDateTime.of(2026, 11, 1, 9, 0));
        activity.setEndTime(LocalDateTime.of(2026, 11, 1, 11, 0));
        activity.setStatus("OPEN");
        activity.setTeacherId(TEACHER_ID);
        activityDAO.insert(activity);

        // ★ 用 insert() 回填的自增主键，不要用「取列表最后一条」的办法：
        //   findByTeacherId() 是按 start_time 排序的，"最后一条"很可能是别的活动。
        Long activityId = activity.getId();
        System.out.println("    已创建测试活动 id = " + activityId);
        check("测试活动创建成功（id 已回填）", activityId != null);
        if (activityId == null) {
            printSummary();
            return;
        }

        try {
            // ---------- 1. 报名前：查不到记录，人数为 0 ----------
            check("报名前查不到该学生的记录",
                    dao.findByActivityAndStudent(activityId, STUDENT_ID) == null);
            check("报名前人数为 0", dao.countRegistered(activityId) == 0);

            // ---------- 2. 报名：新增记录 ----------
            // V2.0 起报名后的初始状态是「待审核」（PENDING_REVIEW），
            // 它和候补、正式参加一样都属于"占位"，会被 countRegistered() 计入。
            ActivityRegistration registration = new ActivityRegistration();
            registration.setActivityId(activityId);
            registration.setStudentId(STUDENT_ID);
            registration.setStatus(ActivityRegistration.STATUS_PENDING_REVIEW);
            int insertRows = dao.insert(registration);
            check("insert() 影响行数为 1（实际 " + insertRows + "）", insertRows == 1);

            // ---------- 3. 报名后：能查到记录，人数变 1 ----------
            ActivityRegistration found = dao.findByActivityAndStudent(activityId, STUDENT_ID);
            check("报名后能查到记录", found != null);
            if (found != null) {
                check("状态是 PENDING_REVIEW（实际 " + found.getStatus() + "）",
                        found.isPendingReview());
                check("★ 未审核记录的 review_time 是 null（实际 "
                                + found.getReviewTime() + "）",
                        found.getReviewTime() == null);
            }
            check("报名后人数为 1", dao.countRegistered(activityId) == 1);
            check("★ countByStatus(PENDING_REVIEW) 也是 1",
                    dao.countByStatus(activityId,
                            ActivityRegistration.STATUS_PENDING_REVIEW) == 1);

            // ---------- 4. 取消报名：状态改为 CANCELLED，人数回到 0 ----------
            int cancelRows = dao.updateStatus(activityId, STUDENT_ID,
                    ActivityRegistration.STATUS_CANCELLED);
            check("updateStatus(CANCELLED) 影响行数为 1（实际 " + cancelRows + "）",
                    cancelRows == 1);
            check("取消后状态是 CANCELLED",
                    dao.findByActivityAndStudent(activityId, STUDENT_ID).isCancelled());
            check("★ 取消后不再计入人数（应回到 0，实际 "
                            + dao.countRegistered(activityId) + "）",
                    dao.countRegistered(activityId) == 0);

            // ---------- 5. 重新报名：复用同一行，状态改回待审核 ----------
            // 用 updateStatusAndReviewTime 而不是 updateStatus，
            // 因为重新报名必须把上一轮的 review_time 清空（传 null）。
            int reRows = dao.updateStatusAndReviewTime(activityId, STUDENT_ID,
                    ActivityRegistration.STATUS_PENDING_REVIEW, null);
            check("重新报名（复用原行）影响行数为 1（实际 " + reRows + "）", reRows == 1);
            check("重新报名后人数回到 1", dao.countRegistered(activityId) == 1);

            // ---------- 6. 审核：状态与时间一次写入 ----------
            LocalDateTime reviewAt = LocalDateTime.of(2026, 11, 1, 10, 0, 0);
            int reviewRows = dao.updateStatusAndReviewTime(activityId, STUDENT_ID,
                    ActivityRegistration.STATUS_CONFIRMED, reviewAt);
            check("审核写入影响行数为 1（实际 " + reviewRows + "）", reviewRows == 1);

            ActivityRegistration reviewed =
                    dao.findByActivityAndStudent(activityId, STUDENT_ID);
            check("状态已改为 CONFIRMED", reviewed.isConfirmed());
            check("★ review_time 已写入（实际 " + reviewed.getReviewTime() + "）",
                    reviewAt.equals(reviewed.getReviewTime()));
            check("★ 按状态查 CONFIRMED 得 1 条",
                    dao.findByActivityIdAndStatus(activityId,
                            ActivityRegistration.STATUS_CONFIRMED).size() == 1);
            check("★ countByStatus(CONFIRMED) 为 1",
                    dao.countByStatus(activityId,
                            ActivityRegistration.STATUS_CONFIRMED) == 1);

            // ---------- 7. 我的报名 / 教师查看名单 ----------
            check("findByStudentId 能查到该学生的记录（>= 1 条）",
                    dao.findByStudentId(STUDENT_ID).size() >= 1);
            check("findByActivityId 能查到该活动的 1 条记录",
                    dao.findByActivityId(activityId).size() == 1);

            // ---------- 8. 候补队列查询（V2.0 新增方法） ----------
            check("★ 没有候补时 findWaitlistOrderByReviewTime 返回空列表",
                    dao.findWaitlistOrderByReviewTime(activityId).isEmpty());

            // ---------- 9. 重复插入同一学生对同一活动：应触发唯一约束 ----------
            // DAO 内部捕获异常后返回 0，所以这里不会让测试崩掉
            ActivityRegistration duplicate = new ActivityRegistration();
            duplicate.setActivityId(activityId);
            duplicate.setStudentId(STUDENT_ID);
            duplicate.setStatus(ActivityRegistration.STATUS_PENDING_REVIEW);
            check("★ 重复插入返回 0（唯一约束 uk_reg_student_activity 生效）",
                    dao.insert(duplicate) == 0);

        } finally {
            // ---------- 清理：先删报名记录，再删活动（外键顺序不能反） ----------
            int regDeleted = dao.deleteByActivityId(activityId);
            int actDeleted = activityDAO.deleteById(activityId);
            System.out.println("[清理] 删除测试报名记录 " + regDeleted
                    + " 条、测试活动 " + actDeleted + " 个");
            check("清理成功（活动已删除）", activityDAO.findById(activityId) == null);
        }

        System.out.println();
        System.out.println("=== 测试结束 ===");
        printSummary();
    }

    /** 打印结论 */
    private static void printSummary() {
        System.out.println("总计: " + (passed + failed) + "，通过: " + passed + "，失败: " + failed);
        if (failed > 0) {
            System.out.println("结论：存在未通过的检查，请查看上面的输出。");
        } else {
            System.out.println("结论：RegistrationDAO 的状态流转、"
                    + "审核时间读写与唯一约束全部符合预期。");
        }
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

    /**
     * 直接删除一条报名记录（保留给将来可能的用例使用）。
     *
     * <p>正式功能里取消报名是"改状态"而不是删除，所以 RegistrationDAO
     * 没有提供单条 delete 方法；测试需要把数据清干净时可以直接用 JDBC。
     * 当前清理走的是 {@code deleteByActivityId}，本方法暂未使用。
     *
     * @param activityId 活动id
     * @param studentId  学生id
     */
    @SuppressWarnings("unused")
    private static void deleteRegistration(Long activityId, Long studentId) {
        String sql = "DELETE FROM activity_registration WHERE activity_id = ? AND student_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, activityId);
            ps.setLong(2, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
