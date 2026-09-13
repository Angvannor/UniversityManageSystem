package com.hbk.activity.tool;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.dao.RegistrationDAO;
import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.service.ActivityService;
import com.hbk.activity.service.RegistrationService;
import com.hbk.activity.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RegistrationService 手工测试类。
 *
 * <p>【测试套路】准备 → 执行 → 断言（check），与另外两个测试类一致。
 *
 * <p>【覆盖的业务规则】
 * <ul>
 *   <li>REQ-03 报名：活动不存在 / 活动已关闭 / 正常报名 / 重复报名被拒</li>
 *   <li>REQ-04 取消报名：未报名不能取消 / 正常取消 / 取消后记录保留</li>
 *   <li>REQ-03 重新报名：复用原记录，不新增行</li>
 *   <li>REQ-04 我的报名列表</li>
 *   <li>REQ-06 教师查看报名名单：只能看自己发布的活动</li>
 * </ul>
 *
 * <p>【运行方式】在项目根目录执行：
 * <pre>
 *   build.bat run com.hbk.activity.tool.RegistrationServiceTest
 * </pre>
 *
 * @author HBK组
 */
public class RegistrationServiceTest {

    /** 测试活动标题前缀，用于识别和清理测试数据 */
    private static final String TITLE_PREFIX = "测试活动-报名-";

    /** 教师id：teacher01 的 id 是 1 */
    private static final Long TEACHER_ID = 1L;

    /** 其他教师id：数据库中不存在，用于验证归属校验 */
    private static final Long OTHER_TEACHER_ID = 999L;

    /** 学生id：student01 的 id 是 2 */
    private static final Long STUDENT_ID = 2L;

    /** 一个从未报名的学生id，用于验证"未报名不能取消" */
    private static final Long OTHER_STUDENT_ID = 999L;

    private static int passed = 0;
    private static int failed = 0;

    /** 断言工具 */
    private static void check(String caseName, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [通过] " + caseName);
        } else {
            failed++;
            System.out.println("  [失败] " + caseName + "   <<< 需要检查");
        }
    }

    public static void main(String[] args) {
        RegistrationService service = new RegistrationService();
        ActivityService activityService = new ActivityService();
        RegistrationDAO registrationDAO = new RegistrationDAO();

        System.out.println("=== RegistrationService 测试开始 ===");
        cleanup();

        // ==============================================================
        // 准备数据：一个开放报名的活动 + 一个已关闭的活动
        // ==============================================================
        Activity open = buildActivity(TITLE_PREFIX + "开放活动", "OPEN");
        activityService.publish(open);
        Long openId = lastActivityId(activityService);

        Activity closed = buildActivity(TITLE_PREFIX + "已关闭活动", "OPEN");
        activityService.publish(closed);
        Long closedId = lastActivityId(activityService);
        activityService.close(closedId, TEACHER_ID);      // 通过 Service 关闭它

        System.out.println("\n[准备] 开放活动 id=" + openId + "，已关闭活动 id=" + closedId);

        // ==============================================================
        // 一、报名校验（REQ-03）
        // ==============================================================
        System.out.println("\n[报名校验]");

        check("活动不存在时报名被拒",
                "活动不存在".equals(service.register(999999L, STUDENT_ID)));

        String closedResult = service.register(closedId, STUDENT_ID);
        check("活动已关闭时报名被拒", closedResult != null && closedResult.contains("已关闭"));
        System.out.println("       提示信息: " + closedResult);

        // 正常报名
        String registerResult = service.register(openId, STUDENT_ID);
        check("正常报名成功（返回 null）", registerResult == null);
        check("报名后人数为 1", registrationDAO.countRegistered(openId) == 1);
        System.out.println("       报名返回: " + registerResult);

        // 重复报名
        String duplicate = service.register(openId, STUDENT_ID);
        check("重复报名被拒", duplicate != null && duplicate.contains("不能重复报名"));
        System.out.println("       提示信息: " + duplicate);

        // ==============================================================
        // 二、我的报名（REQ-04）
        // ==============================================================
        System.out.println("\n[我的报名]");

        List<ActivityRegistration> mine = service.myRegistrations(STUDENT_ID);
        check("我的报名列表不为空", !mine.isEmpty());
        check("列表包含刚报名的活动", containsActivity(mine, openId));
        System.out.println("       我的报名条数: " + mine.size());

        // ==============================================================
        // 三、教师查看报名名单（REQ-06）
        // ==============================================================
        System.out.println("\n[教师查看名单]");

        List<ActivityRegistration> roster = service.activityRoster(openId, TEACHER_ID);
        check("发布教师能查看名单", !roster.isEmpty());
        check("名单包含报名学生", containsStudent(roster, STUDENT_ID));

        List<ActivityRegistration> otherRoster = service.activityRoster(openId, OTHER_TEACHER_ID);
        check("其他教师查看名单为空", otherRoster.isEmpty());
        check("不存在的活动名单为空", service.activityRoster(999999L, TEACHER_ID).isEmpty());

        // ==============================================================
        // 四、取消报名（REQ-04）
        // ==============================================================
        System.out.println("\n[取消报名]");

        String cancelNotRegistered = service.cancel(openId, OTHER_STUDENT_ID);
        check("未报名不能取消", cancelNotRegistered != null && cancelNotRegistered.contains("尚未报名"));
        System.out.println("       提示信息: " + cancelNotRegistered);

        String cancelResult = service.cancel(openId, STUDENT_ID);
        check("取消报名成功（返回 null）", cancelResult == null);
        check("取消后有效人数为 0", registrationDAO.countRegistered(openId) == 0);
        System.out.println("       取消返回: " + cancelResult);

        // 取消后记录仍保留（状态为 CANCELLED），这一点很重要
        ActivityRegistration afterCancel =
                registrationDAO.findByActivityAndStudent(openId, STUDENT_ID);
        check("取消后记录仍存在", afterCancel != null);
        check("取消后状态为 CANCELLED", afterCancel != null && "CANCELLED".equals(afterCancel.getStatus()));
        System.out.println("       取消后的记录: " + afterCancel);

        // 重复取消应被拒绝
        String cancelAgain = service.cancel(openId, STUDENT_ID);
        check("重复取消被拒", cancelAgain != null && cancelAgain.contains("尚未报名"));

        // ==============================================================
        // 五、取消后重新报名（复用原记录）
        // ==============================================================
        System.out.println("\n[重新报名]");

        String reRegister = service.register(openId, STUDENT_ID);
        check("取消后可以重新报名", reRegister == null);
        check("重新报名后人数为 1", registrationDAO.countRegistered(openId) == 1);

        // 重新报名应当复用原来的行，而不是新增一行
        List<ActivityRegistration> rosterAfterReRegister = service.activityRoster(openId, TEACHER_ID);
        check("重新报名没有产生重复记录", rosterAfterReRegister.size() == 1);
        System.out.println("       名单条数: " + rosterAfterReRegister.size() + "（期望 1）");

        // ==============================================================
        // 六、清理与汇总
        // ==============================================================
        cleanup();
        System.out.println("\n[清理] 已删除全部测试数据");
        check("清理后无测试活动残留", byTitlePrefix(activityService.listAll()) == null);

        System.out.println("\n=== 测试结束 ===");
        System.out.println("用例总数: " + (passed + failed) + "，通过: " + passed + "，失败: " + failed);
        if (failed > 0) {
            System.out.println("结论：存在未通过的用例，需要检查上面的 [失败] 项。");
        } else {
            System.out.println("结论：RegistrationService 的报名、取消、名单查询逻辑全部符合预期。");
        }
    }

    /**
     * 构造测试活动对象。
     *
     * @param title  标题
     * @param status 状态：OPEN / CLOSED
     * @return 活动对象
     */
    private static Activity buildActivity(String title, String status) {
        Activity activity = new Activity();
        activity.setTitle(title);
        activity.setDescription("由 RegistrationServiceTest 创建");
        activity.setLocation("测试地点");
        activity.setStartTime(LocalDateTime.of(2026, 12, 20, 9, 0));
        activity.setEndTime(LocalDateTime.of(2026, 12, 20, 11, 0));
        activity.setStatus(status);
        activity.setTeacherId(TEACHER_ID);
        return activity;
    }

    /**
     * 取当前教师最新发布的活动id（列表按开始时间排序，取最后一条）。
     *
     * @param activityService 活动业务对象
     * @return 最新活动的id
     */
    private static Long lastActivityId(ActivityService activityService) {
        List<Activity> list = activityService.listByTeacher(TEACHER_ID);
        return list.get(list.size() - 1).getId();
    }

    /** 判断报名列表中是否包含指定活动的记录 */
    private static boolean containsActivity(List<ActivityRegistration> list, Long activityId) {
        for (ActivityRegistration r : list) {
            if (r.getActivityId().equals(activityId)) {
                return true;
            }
        }
        return false;
    }

    /** 判断报名列表中是否包含指定学生的记录 */
    private static boolean containsStudent(List<ActivityRegistration> list, Long studentId) {
        for (ActivityRegistration r : list) {
            if (r.getStudentId().equals(studentId)) {
                return true;
            }
        }
        return false;
    }

    /** 查找标题以测试前缀开头的活动 */
    private static Activity byTitlePrefix(List<Activity> list) {
        for (Activity a : list) {
            if (a.getTitle() != null && a.getTitle().startsWith(TITLE_PREFIX)) {
                return a;
            }
        }
        return null;
    }

    /** 清理测试数据：先删报名记录，再删活动（外键顺序不能反） */
    private static void cleanup() {
        String deleteRegistrations =
                "DELETE FROM activity_registration WHERE activity_id IN "
                        + "(SELECT id FROM activity WHERE title LIKE ?)";
        String deleteActivities = "DELETE FROM activity WHERE title LIKE ?";
        try (Connection conn = DBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(deleteRegistrations)) {
                ps.setString(1, TITLE_PREFIX + "%");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(deleteActivities)) {
                ps.setString(1, TITLE_PREFIX + "%");
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
