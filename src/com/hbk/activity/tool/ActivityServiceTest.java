package com.hbk.activity.tool;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.dao.RegistrationDAO;
import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.service.ActivityService;
import com.hbk.activity.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ActivityService 手工测试类。
 *
 * <p>【测试套路回顾】（与 AuthServiceTest 一致）
 * <pre>
 *   准备（构造输入） -> 执行（调用被测方法） -> 断言（check 比较实际与期望）
 * </pre>
 *
 * <p>【覆盖的业务规则】
 * <ul>
 *   <li>REQ-05 发布活动：必填校验、结束时间必须晚于开始时间</li>
 *   <li>REQ-05 修改 / 关闭 / 删除：只能操作自己发布的活动（归属校验）</li>
 *   <li>REQ-05 删除限制：已有报名记录的活动不能删除</li>
 *   <li>REQ-02 浏览活动：列表查询与详情查询</li>
 * </ul>
 *
 * <p>【运行方式】在项目根目录执行：
 * <pre>
 *   build.bat run com.hbk.activity.tool.ActivityServiceTest
 * </pre>
 *
 * <p>测试开始前和结束后都会清理标题以 "测试活动-" 开头的活动及其报名记录，
 * 因此可以反复运行，也不会影响正常数据。
 *
 * @author HBK组
 */
public class ActivityServiceTest {

    /** 测试活动标题前缀，用于识别和清理测试数据 */
    private static final String TITLE_PREFIX = "测试活动-";

    /** 测试使用的教师id：teacher01 的 id 是 1 */
    private static final Long TEACHER_ID = 1L;

    /** 另一个教师id：数据库里不存在，用来验证"不能管理别人的活动" */
    private static final Long OTHER_TEACHER_ID = 999L;

    /** 学生id：student01 的 id 是 2 */
    private static final Long STUDENT_ID = 2L;

    private static int passed = 0;
    private static int failed = 0;

    /** 断言工具：累计通过 / 失败数量 */
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
        ActivityService service = new ActivityService();
        ActivityDAO activityDAO = new ActivityDAO();
        RegistrationDAO registrationDAO = new RegistrationDAO();

        System.out.println("=== ActivityService 测试开始 ===");
        cleanup();

        // ==============================================================
        // 一、发布活动的校验（REQ-05）
        // ==============================================================
        System.out.println("\n[发布活动校验]");

        // 用例1：标题为空
        Activity noTitle = buildActivity(null, "测试地点", 0);
        check("标题为空被拒绝", "活动标题不能为空".equals(service.publish(noTitle)));

        // 用例2：地点为空
        Activity noLocation = buildActivity(TITLE_PREFIX + "无地点", null, 0);
        check("地点为空被拒绝", "活动地点不能为空".equals(service.publish(noLocation)));

        // 用例3：结束时间早于开始时间
        Activity badTime = buildActivity(TITLE_PREFIX + "时间颠倒", "测试地点", -2);
        String badTimeResult = service.publish(badTime);
        check("结束时间早于开始时间被拒绝", "活动结束时间必须晚于开始时间".equals(badTimeResult));
        System.out.println("       提示信息: " + badTimeResult);

        // 用例4：正常发布
        Activity normal = buildActivity(TITLE_PREFIX + "程序设计大赛", "计算机学院 A301", 2);
        String publishResult = service.publish(normal);
        check("正常发布成功（返回 null）", publishResult == null);
        System.out.println("       发布返回: " + publishResult);

        // 取得刚发布活动的 id
        List<Activity> teacherActivities = service.listByTeacher(TEACHER_ID);
        Long activityId = teacherActivities.get(teacherActivities.size() - 1).getId();
        System.out.println("       新活动 id = " + activityId);

        // ==============================================================
        // 二、查询（REQ-02）
        // ==============================================================
        System.out.println("\n[查询测试]");

        check("listAll() 能查到刚发布的活动", findById(service.listAll(), activityId) != null);
        check("listByTeacher(1) 能查到", findById(service.listByTeacher(TEACHER_ID), activityId) != null);
        check("listByTeacher(999) 查不到", findById(service.listByTeacher(OTHER_TEACHER_ID), activityId) == null);

        check("detail() 查到活动详情", service.detail(activityId) != null);
        check("detail() 标题正确", service.detail(activityId) != null
                && (TITLE_PREFIX + "程序设计大赛").equals(service.detail(activityId).getTitle()));
        check("detail() 不存在的活动返回 null", service.detail(999999L) == null);
        System.out.println("       详情: " + service.detail(activityId));

        // ==============================================================
        // 三、归属校验：教师只能管理自己的活动
        // ==============================================================
        System.out.println("\n[归属校验]");

        Activity editByOther = service.detail(activityId);
        editByOther.setTitle(TITLE_PREFIX + "被外人改了");
        String otherUpdate = service.update(editByOther, OTHER_TEACHER_ID);
        check("其他教师修改活动被拒绝", "只能管理自己发布的活动".equals(otherUpdate));
        System.out.println("       提示信息: " + otherUpdate);

        check("其他教师关闭活动被拒绝",
                "只能管理自己发布的活动".equals(service.close(activityId, OTHER_TEACHER_ID)));
        check("其他教师删除活动被拒绝",
                "只能管理自己发布的活动".equals(service.delete(activityId, OTHER_TEACHER_ID)));

        // 修改一个不存在的活动：id 用数据库里肯定没有的值
        Activity ghost = new Activity();
        ghost.setId(999999L);
        ghost.setTitle(TITLE_PREFIX + "不存在的活动");
        ghost.setStartTime(LocalDateTime.of(2026, 12, 1, 9, 0));
        ghost.setEndTime(LocalDateTime.of(2026, 12, 1, 11, 0));
        check("修改不存在的活动被拒绝", "活动不存在".equals(service.update(ghost, TEACHER_ID)));

        // ==============================================================
        // 四、修改与关闭
        // ==============================================================
        System.out.println("\n[修改与关闭]");

        // 用例：本人修改活动
        Activity toUpdate = service.detail(activityId);
        toUpdate.setTitle(TITLE_PREFIX + "已改名的活动");
        toUpdate.setLocation("新地点 B202");
        String updateResult = service.update(toUpdate, TEACHER_ID);
        check("本人修改活动成功", updateResult == null);
        check("修改后标题已更新", (TITLE_PREFIX + "已改名的活动").equals(service.detail(activityId).getTitle()));
        check("修改后地点已更新", "新地点 B202".equals(service.detail(activityId).getLocation()));
        System.out.println("       修改后: " + service.detail(activityId));

        // 用例：本人关闭报名
        String closeResult = service.close(activityId, TEACHER_ID);
        check("本人关闭报名成功", closeResult == null);
        check("关闭后状态为 CLOSED", "CLOSED".equals(service.detail(activityId).getStatus()));
        System.out.println("       关闭后状态: " + service.detail(activityId).getStatus());

        // 把状态改回 OPEN，便于后面的删除测试
        Activity reopen = service.detail(activityId);
        reopen.setStatus("OPEN");
        service.update(reopen, TEACHER_ID);

        // ==============================================================
        // 五、删除限制：有报名的活动不能删除
        // ==============================================================
        System.out.println("\n[删除限制]");

        // 先让一名学生报名
        // 注意状态要用 V2.0 的有效取值：countRegistered() 统计的是"占位"的三种状态
        // （待审核 / 候补 / 正式参加），原来写的 "REGISTERED" 已经不存在了，
        // 用它插进去的记录不算占位，删除保护就会失效。
        ActivityRegistration registration = new ActivityRegistration();
        registration.setActivityId(activityId);
        registration.setStudentId(STUDENT_ID);
        registration.setStatus(ActivityRegistration.STATUS_CONFIRMED);
        registrationDAO.insert(registration);

        String deleteWithRegistration = service.delete(activityId, TEACHER_ID);
        check("有人报名时删除被拒绝", deleteWithRegistration != null && deleteWithRegistration.contains("人报名"));
        System.out.println("       提示信息: " + deleteWithRegistration);

        // 学生取消报名后，人数归零，此时可以删除
        registrationDAO.updateStatus(activityId, STUDENT_ID, ActivityRegistration.STATUS_CANCELLED);
        check("取消报名后人数归零", registrationDAO.countRegistered(activityId) == 0);

        String deleteResult = service.delete(activityId, TEACHER_ID);
        check("无人报名时删除成功", deleteResult == null);
        check("删除后查不到该活动", service.detail(activityId) == null);
        System.out.println("       删除返回: " + deleteResult);

        // ==============================================================
        // 六、清理与汇总
        // ==============================================================
        cleanup();
        System.out.println("\n[清理] 已删除全部测试数据");
        check("清理后无测试活动残留", findByTitlePrefix(service.listAll()) == null);

        System.out.println("\n=== 测试结束 ===");
        System.out.println("用例总数: " + (passed + failed) + "，通过: " + passed + "，失败: " + failed);
        if (failed > 0) {
            System.out.println("结论：存在未通过的用例，需要检查上面的 [失败] 项。");
        } else {
            System.out.println("结论：ActivityService 的发布、查询、管理逻辑全部符合预期。");
        }
    }

    /**
     * 构造一个测试用活动对象。
     *
     * @param title        标题（null 表示故意不设置）
     * @param location     地点（null 表示故意不设置）
     * @param durationHour 活动时长（小时）；传负数可构造"结束时间早于开始时间"的非法数据
     * @return 活动对象
     */
    private static Activity buildActivity(String title, String location, int durationHour) {
        Activity activity = new Activity();
        activity.setTitle(title);
        activity.setLocation(location);
        activity.setDescription("由 ActivityServiceTest 创建的测试数据");
        LocalDateTime start = LocalDateTime.of(2026, 12, 1, 9, 0);
        activity.setStartTime(start);
        activity.setEndTime(start.plusHours(durationHour));
        activity.setStatus("OPEN");
        activity.setTeacherId(TEACHER_ID);
        return activity;
    }

    /**
     * 在列表中按 id 查找活动。
     *
     * @param list 活动列表
     * @param id   目标id
     * @return 找到返回该活动，否则返回 null
     */
    private static Activity findById(List<Activity> list, Long id) {
        for (Activity activity : list) {
            if (activity.getId().equals(id)) {
                return activity;
            }
        }
        return null;
    }

    /**
     * 在列表中查找标题以测试前缀开头的活动。
     *
     * @param list 活动列表
     * @return 找到返回第一个匹配项，否则返回 null
     */
    private static Activity findByTitlePrefix(List<Activity> list) {
        for (Activity activity : list) {
            if (activity.getTitle() != null && activity.getTitle().startsWith(TITLE_PREFIX)) {
                return activity;
            }
        }
        return null;
    }

    /**
     * 清理测试数据：先删报名记录，再删活动（外键顺序不能反）。
     */
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
