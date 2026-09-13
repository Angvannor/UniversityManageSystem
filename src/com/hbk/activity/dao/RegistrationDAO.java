package com.hbk.activity.dao;

import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动报名表数据访问类：负责 {@code activity_registration} 表的所有 SQL 操作。
 *
 * <p>【这个类是干什么的】
 * 把「报名」这件事的 SQL 全部关在这一层。它同时服务于两个角色：
 * 学生端（报名、取消报名、查看我的报名）和教师端（查看活动的报名名单）。
 *
 * <p>【覆盖的需求】
 * <pre>
 *   REQ-03 学生报名活动          -> findByActivityAndStudent() 判断 + insert() 写入
 *   REQ-04 学生取消报名          -> updateStatus(..., CANCELLED)
 *   REQ-04 学生查看我的报名      -> findByStudentId()
 *   REQ-06 教师查看报名名单      -> findByActivityId()
 *   人数上限约束                -> countRegistered()
 * </pre>
 *
 * <p>【本表的设计要点（对应报告"设计决策三、四"）】
 * <ul>
 *   <li>(activity_id, student_id) 在数据库上有唯一约束，一个学生对一个活动
 *       只能有一行记录，所以取消报名是 {@code updateStatus} 改状态，
 *       而不是删除记录；重新报名时再把状态改回 REGISTERED 即可。</li>
 *   <li>{@code register_time} 建表时写了 {@code DEFAULT CURRENT_TIMESTAMP}，
 *       插入时不用传该字段，MySQL 会自动填当前时间。</li>
 * </ul>
 *
 * <p>【写 DAO 的固定套路回顾】
 * <ul>
 *   <li>查多条（SQL 无参数）：{@code try (conn; ps; rs) { while (rs.next()) ... }}</li>
 *   <li>查单条 / 查数量（SQL 有 ?）：先 {@code try (conn; ps)} → setXxx →
 *       再 {@code try (ResultSet rs = ps.executeQuery()) { if (rs.next()) ... }}</li>
 *   <li>增删改：用 {@code ps.executeUpdate()} 返回影响行数</li>
 *   <li>每个 try-with-resources 外面都要有 catch(SQLException)，否则编译不过</li>
 * </ul>
 *
 * @author HBK组
 */
public class RegistrationDAO {

    /**
     * 查询时统一使用的字段列表，避免各方法重复书写。
     * 注意表名 activity_registration 不是保留字，不需要反引号。
     */
    private static final String COLUMNS = "id, activity_id, student_id, register_time, status";

    // ==================================================================
    // 一、查询
    // ==================================================================

    /**
     * 查询某个学生对某个活动的报名记录（报名前的重复报名校验，REQ-03）。
     *
     * <p>为什么返回记录而不是 boolean：
     * 调用方不仅要知道"报没报过"，还需要知道"当前状态是什么"
     * （已报名 → 拒绝重复报名；已取消 → 允许重新报名，把状态改回去）。
     *
     * <p>SQL 里有两个 {@code ?}，编号 1 是活动id、编号 2 是学生id。
     *
     * @param activityId 活动id
     * @param studentId  学生id
     * @return 查到返回报名记录，查不到返回 null
     */
    public ActivityRegistration findByActivityAndStudent(Long activityId, Long studentId) {
        String sql = "SELECT " + COLUMNS + " FROM activity_registration "
                + "WHERE activity_id = ? AND student_id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 两个 ? 按出现顺序编号：1 对应 activity_id，2 对应 student_id
            ps.setLong(1, activityId);
            ps.setLong(2, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            // catch 必须挂在最外层 try 上，内层 try(rs) 抛出的异常也会被这里接住
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 查询某个学生的全部报名记录（学生端"我的报名"，REQ-04）。
     *
     * <p>返回的是报名记录本身（活动id、报名时间、状态）。
     * 界面上要显示活动标题和地点时，由业务层拿着 activityId
     * 再调用 {@code ActivityDAO.findById()} 获取，这样本类不需要写联表查询。
     *
     * @param studentId 学生id
     * @return 报名记录列表，按报名时间倒序（最近报名的排前面）；无数据时返回空列表
     */
    public List<ActivityRegistration> findByStudentId(Long studentId) {
        String sql = "SELECT " + COLUMNS + " FROM activity_registration "
                + "WHERE student_id = ? ORDER BY register_time DESC";

        List<ActivityRegistration> list = new ArrayList<>();

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, studentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 查询某个活动的全部报名记录（教师端"报名名单"，REQ-06）。
     *
     * <p>按报名时间正序排列，教师看到的顺序就是学生报名的先后顺序。
     *
     * @param activityId 活动id
     * @return 报名记录列表；无数据时返回空列表
     */
    public List<ActivityRegistration> findByActivityId(Long activityId) {
        String sql = "SELECT " + COLUMNS + " FROM activity_registration "
                + "WHERE activity_id = ? ORDER BY register_time ASC";

        List<ActivityRegistration> list = new ArrayList<>();

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, activityId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 统计某个活动当前有效的报名人数（用于人数上限校验，REQ-03 约束）。
     *
     * <p>为什么条件里写死 {@code status = 'REGISTERED'}：
     * 已取消的记录不计入人数，否则学生一取消一报名就把名额占满了。
     * 这里 {@code 'REGISTERED'} 是固定的查询条件，不是用户输入，
     * 因此直接写在 SQL 里（用单引号），而 activity_id 是变量所以用 {@code ?}。
     *
     * @param activityId 活动id
     * @return 有效报名人数；查询失败时返回 0
     */
    public int countRegistered(Long activityId) {
        String sql = "SELECT COUNT(*) FROM activity_registration "
                + "WHERE activity_id = ? AND status = 'REGISTERED'";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // activity_id 是 Long 类型，用 setLong
            ps.setLong(1, activityId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // COUNT(*) 的结果就是人数，直接返回；不要写成 rs.getInt(1) > 0
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // ==================================================================
    // 二、新增 / 修改状态
    // ==================================================================

    /**
     * 新增一条报名记录（学生报名活动，REQ-03）。
     *
     * <p>只传 3 个参数：activity_id、student_id、status。
     * 主键 id 由数据库自增，register_time 由数据库默认值 CURRENT_TIMESTAMP 生成，
     * 都不需要在这里设置。
     *
     * <p>注意：如果该学生对同一活动已经有一行记录（包括已取消的），
     * 数据库唯一约束 uk_reg_student_activity 会让本方法抛异常，
     * 因此业务层必须先调 {@link #findByActivityAndStudent} 判断。
     *
     * @param registration 待新增的报名记录（至少设置 activityId、studentId、status）
     * @return 影响行数：1 表示报名成功，0 表示失败
     */
    public int insert(ActivityRegistration registration) {
        String sql = "INSERT INTO activity_registration (activity_id, student_id, status) "
                + "VALUES (?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // getter 后面一定要有括号，否则取到的是"方法本身"
            ps.setLong(1, registration.getActivityId());
            ps.setLong(2, registration.getStudentId());
            ps.setString(3, registration.getStatus());

            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 修改报名状态（取消报名 / 重新报名，REQ-03、REQ-04）。
     *
     * <p>这个方法实现两个业务动作：
     * <ul>
     *   <li>取消报名：传入 {@code "CANCELLED"}</li>
     *   <li>重新报名：传入 {@code "REGISTERED"}（复用原来那一行，不新增记录）</li>
     * </ul>
     *
     * <p>参数绑定顺序按 SQL 里的 {@code ?} 依次来：
     * {@code SET status = ? WHERE activity_id = ? AND student_id = ?}
     * → 1=状态、2=活动id、3=学生id。
     * 这里直接用方法参数即可，不需要去调用 getter。
     *
     * @param activityId 活动id
     * @param studentId  学生id
     * @param status     目标状态：REGISTERED / CANCELLED
     * @return 影响行数：1 表示修改成功，0 表示没有匹配的记录
     */
    public int updateStatus(Long activityId, Long studentId, String status) {
        String sql = "UPDATE activity_registration SET status = ? "
                + "WHERE activity_id = ? AND student_id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setLong(2, activityId);
            ps.setLong(3, studentId);

            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 删除某个活动的全部报名记录（包括已取消的）。
     *
     * <p>【为什么需要这个方法】
     * 删除活动时会遇到外键约束问题：
     * activity_registration.activity_id 有外键指向 activity.id，
     * 只要表里还存在<b>任何一行</b>该活动的报名记录（哪怕状态是 CANCELLED），
     * MySQL 就会拒绝删除活动，报：
     * <pre>
     *   Cannot delete or update a parent row: a foreign key constraint fails
     * </pre>
     * 因此业务层在删除活动前，要先调用本方法把该活动的报名记录（含已取消的）清干净。
     * 这也是"Service 层需要编排多个 DAO"的一个典型场景。
     *
     * <p>注意与 {@link #updateStatus} 的区别：
     * 学生取消报名用 updateStatus 改状态（保留记录）；
     * 本方法只在"整个活动都要删掉"时使用。
     *
     * @param activityId 活动id
     * @return 被删除的记录行数
     */
    public int deleteByActivityId(Long activityId) {
        String sql = "DELETE FROM activity_registration WHERE activity_id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, activityId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // ==================================================================
    // 三、内部辅助方法
    // ==================================================================

    /**
     * 把结果集当前行转换成一个 ActivityRegistration 对象。
     *
     * <p>三个查询方法都要做同样的转换，因此抽成私有方法复用。
     * 括号里写数据库列名（下划线），赋值给 Java 属性（驼峰）。
     *
     * <p>register_time 用 {@code getTimestamp(...).toLocalDateTime()} 转换；
     * 该字段建表时是 NOT NULL 且有默认值，所以不必判空。
     *
     * @param rs 已经指向某一行的结果集
     * @return 转换后的报名记录对象
     * @throws SQLException 取值失败时抛出，由调用方的 try-catch 统一处理
     */
    private ActivityRegistration mapRow(ResultSet rs) throws SQLException {
        ActivityRegistration registration = new ActivityRegistration();
        registration.setId(rs.getLong("id"));
        registration.setActivityId(rs.getLong("activity_id"));
        registration.setStudentId(rs.getLong("student_id"));
        registration.setRegisterTime(rs.getTimestamp("register_time").toLocalDateTime());
        registration.setStatus(rs.getString("status"));
        return registration;
    }
}
