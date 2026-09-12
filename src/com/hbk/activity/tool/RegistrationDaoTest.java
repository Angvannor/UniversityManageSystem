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
import java.util.List;

/**
 * RegistrationDAO 手工测试类。
 *
 * <p>【用途】
 * 实际调用 RegistrationDAO 的 6 个方法读写数据库，验证 SQL、参数绑定和
 * 结果集转换是否正确。属于开发期验证工具，不是系统正式功能。
 *
 * <p>【为什么要先建一个活动】
 * activity_registration 表的 activity_id 和 student_id 都是外键，
 * 指向 activity.id 和 user.id。如果直接插入不存在的活动id，
 * MySQL 会拒绝（外键约束错误），所以测试先创建一个活动，测完再删掉。
 *
 * <p>【运行方式】在项目根目录执行：
 * <pre>
 *   build.bat run com.hbk.activity.tool.RegistrationDaoTest
 * </pre>
 *
 * @author HBK组
 */
public class RegistrationDaoTest {

    /** 测试使用的学生id：student01 的 id 是 2 */
    private static final Long STUDENT_ID = 2L;

    /** 测试使用的教师id：teacher01 的 id 是 1 */
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

        // insert 拿不到自增主键，从列表里取刚插入的那条
        List<Activity> activities = activityDAO.findByTeacherId(TEACHER_ID);
        Long activityId = activities.get(activities.size() - 1).getId();
        System.out.println("[0] 已创建测试活动 id = " + activityId);

        try {
            // ---------- 1. 报名前：查不到记录，人数为 0 ----------
            ActivityRegistration none = dao.findByActivityAndStudent(activityId, STUDENT_ID);
            System.out.println("[1] 报名前查询记录: " + none + "（期望 null）");
            System.out.println("    当前报名人数: " + dao.countRegistered(activityId) + "（期望 0）");

            // ---------- 2. 报名：新增记录 ----------
            ActivityRegistration registration = new ActivityRegistration();
            registration.setActivityId(activityId);
            registration.setStudentId(STUDENT_ID);
            registration.setStatus("REGISTERED");
            int insertRows = dao.insert(registration);
            System.out.println("[2] insert() 影响行数: " + insertRows + "（期望 1）");

            // ---------- 3. 报名后：能查到记录，人数变 1 ----------
            ActivityRegistration found = dao.findByActivityAndStudent(activityId, STUDENT_ID);
            System.out.println("[3] 报名后查询记录: " + found);
            System.out.println("    当前报名人数: " + dao.countRegistered(activityId) + "（期望 1）");

            // ---------- 4. 取消报名：状态改为 CANCELLED，人数回到 0 ----------
            int cancelRows = dao.updateStatus(activityId, STUDENT_ID, "CANCELLED");
            System.out.println("[4] updateStatus(CANCELLED) 影响行数: " + cancelRows + "（期望 1）");
            System.out.println("    取消后记录状态: " + dao.findByActivityAndStudent(activityId, STUDENT_ID).getStatus());
            System.out.println("    取消后报名人数: " + dao.countRegistered(activityId) + "（期望 0）");

            // ---------- 5. 重新报名：复用同一行，状态改回 REGISTERED ----------
            int reRows = dao.updateStatus(activityId, STUDENT_ID, "REGISTERED");
            System.out.println("[5] updateStatus(REGISTERED) 影响行数: " + reRows + "（期望 1）");
            System.out.println("    重新报名后人数: " + dao.countRegistered(activityId) + "（期望 1）");

            // ---------- 6. 我的报名 / 教师查看名单 ----------
            List<ActivityRegistration> mine = dao.findByStudentId(STUDENT_ID);
            System.out.println("[6] findByStudentId(" + STUDENT_ID + ") 条数: " + mine.size());
            List<ActivityRegistration> roster = dao.findByActivityId(activityId);
            System.out.println("    findByActivityId(" + activityId + ") 条数: " + roster.size());

            // ---------- 7. 重复插入同一学生对同一活动：应触发唯一约束 ----------
            System.out.println("[7] 尝试重复插入同一报名（期望触发唯一约束异常）...");
            dao.insert(registration);
        } finally {
            // ---------- 清理测试数据 ----------
            // 先删报名记录，再删活动（外键顺序不能反）
            deleteRegistration(activityId, STUDENT_ID);
            activityDAO.deleteById(activityId);
            System.out.println("\n[清理] 已删除测试报名记录与测试活动");
            System.out.println("    剩余报名记录数: " + dao.findByActivityId(activityId).size());
        }

        System.out.println("=== 测试结束 ===");
    }

    /**
     * 直接删除一条报名记录（仅测试清理用）。
     *
     * <p>正式功能里取消报名是"改状态"而不是删除，所以 RegistrationDAO 没有提供
     * delete 方法；测试需要把数据清干净，这里直接用 JDBC 执行一条 DELETE。
     *
     * @param activityId 活动id
     * @param studentId  学生id
     */
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
