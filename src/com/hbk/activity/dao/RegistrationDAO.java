package com.hbk.activity.dao;

import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
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
 *   REQ-06 教师查看报名名单      -> findByActivityId() / findByActivityIdAndStatus()
 *   人数上限与候补               -> countRegistered() / countByStatus()
 *   US-07 教师审核报名           -> updateStatusAndReviewTime()
 *   US-09 候补按顺序递补         -> findWaitlistOrderByReviewTime()
 * </pre>
 *
 * <p>【本表的设计要点（对应报告"设计决策三、四"）】
 * <ul>
 *   <li>(activity_id, student_id) 在数据库上有唯一约束，一个学生对一个活动
 *       只能有一行记录，所以取消报名是 {@code updateStatus} 改状态，
 *       而不是删除记录；重新报名时再把状态改回 PENDING_REVIEW 即可。</li>
 *   <li>{@code register_time} 建表时写了 {@code DEFAULT CURRENT_TIMESTAMP}，
 *       插入时不用传该字段，MySQL 会自动填当前时间。</li>
 *   <li>{@code review_time}（V2.0 新增）由教师审核时写入，
 *       未审核的记录为 NULL。它同时是候补队列的排序依据。</li>
 * </ul>
 *
 * <p>【V2.0 状态取值】见 {@link com.hbk.activity.entity.ActivityRegistration} 的 STATUS_xxx 常量。
 * 本类里凡是要写死状态字符串的 SQL，都用单引号写成固定条件；
 * 而会变化的值一律用 {@code ?} 占位。
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
     * V2.0 新增 review_time 字段。
     */
    private static final String COLUMNS =
            "id, activity_id, student_id, register_time, review_time, status";

    /**
     * 「占用名额」的状态集合，用于拼在 SQL 的 IN 条件里。
     *
     * <p>待审核、候补、正式参加都算"这个学生报了名"，只是结果不同；
     * CANCELLED（学生取消）和 REJECTED（教师驳回）不算。
     *
     * <p>写成常量而不是在 SQL 里手写三遍字符串，是为了与实体类的状态常量保持一致，
     * 改的时候只需改这一处。
     */
    private static final String OCCUPYING_STATUSES =
            "'" + ActivityRegistration.STATUS_PENDING_REVIEW + "', "
                    + "'" + ActivityRegistration.STATUS_WAITLISTED + "', "
                    + "'" + ActivityRegistration.STATUS_CONFIRMED + "'";

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
     * 按状态查询某个活动的报名记录（教师端报名名单分组展示，US-07 / US-08）。
     *
     * <p>教师端名单要分成「待审核」「候补」「正式参加」三块，
     * 与其查出全部再在 Java 里过滤，不如让数据库直接按状态过滤
     * （表上已经建了 {@code idx_reg_activity_status (activity_id, status)} 索引）。
     *
     * <p>注意本方法<b>不按 review_time 排序</b>：候补队列有专门的
     * {@link #findWaitlistOrderByReviewTime(Long)}，因为只有候补才需要
     * 按"进入候补的时间"排队。
     *
     * @param activityId 活动id
     * @param status     报名状态，取值见 {@link ActivityRegistration} 的 STATUS_xxx 常量
     * @return 报名记录列表，按报名时间正序；无数据时返回空列表
     */
    public List<ActivityRegistration> findByActivityIdAndStatus(Long activityId, String status) {
        String sql = "SELECT " + COLUMNS + " FROM activity_registration "
                + "WHERE activity_id = ? AND status = ? ORDER BY register_time ASC";

        List<ActivityRegistration> list = new ArrayList<>();

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, activityId);
            ps.setString(2, status);

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
     * 查询某个活动的候补队列，按「进入候补的时间」升序（US-09 的排序依据）。
     *
     * <p>【为什么这里必须用 review_time 而不是 register_time】
     * 教师访谈 T2 的原话是「候补要有明确先后，简单一点就是按<b>进入候补的时间</b>处理，
     * 先进入的人排在前面」。而"进入候补"发生在<b>教师审核通过的那一刻</b>，
     * 不是学生报名的时刻 —— 学生可能很早就报名，教师过两天才审核。
     *
     * <p>举例（也是 db/schema.sql 里演示数据构造的情况）：
     * <pre>
     *   孙同学 07:30 报名、09:10 审核  -> 候补第 2
     *   王同学 07:50 报名、09:05 审核  -> 候补第 1
     * </pre>
     * 孙同学报名更早却排在后面。如果按 register_time 排序，顺序就反了。
     *
     * <p>ORDER BY 默认就是升序 ASC，这里显式写出来是为了让意图更清楚。
     *
     * @param activityId 活动id
     * @return 候补记录列表，先进入候补的排前面；无数据时返回空列表
     */
    public List<ActivityRegistration> findWaitlistOrderByReviewTime(Long activityId) {
        String sql = "SELECT " + COLUMNS + " FROM activity_registration "
                + "WHERE activity_id = ? AND status = ? ORDER BY review_time ASC";

        List<ActivityRegistration> list = new ArrayList<>();

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, activityId);
            ps.setString(2, ActivityRegistration.STATUS_WAITLISTED);

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
     * 统计某个活动当前「已报名」的人数（界面显示"已报名 N 人"，REQ-02）。
     *
     * <p>【V2.0 语义说明】统计的是<b>占用名额</b>的三种状态之和：
     * 待审核 + 候补 + 正式参加。已取消（学生自己退的）和未通过（教师驳回的）不计入，
     * 否则学生一取消一报名就把人数刷上去了。
     *
     * <p>注意它<b>不是</b>"已正式参加人数"。判断名额是否已满要用
     * {@code countByStatus(activityId, STATUS_CONFIRMED)}，
     * 因为候补的人和待审核的人都还没有占住正式名额。
     *
     * <p>状态集合来自 {@link #OCCUPYING_STATUSES} 常量；activity_id 是变量，
     * 所以仍然用 {@code ?} 占位绑定。
     *
     * @param activityId 活动id
     * @return 已报名人数；查询失败时返回 0
     */
    public int countRegistered(Long activityId) {
        String sql = "SELECT COUNT(*) FROM activity_registration "
                + "WHERE activity_id = ? AND status IN (" + OCCUPYING_STATUSES + ")";

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

    /**
     * 按状态统计某个活动的人数（V2.0 新增）。
     *
     * <p>教师端报名名单顶部要同时显示三个数字，都靠这一个方法：
     * <pre>
     *   countByStatus(id, STATUS_CONFIRMED)      -> 已确定参加 N 人（与人数上限比较）
     *   countByStatus(id, STATUS_WAITLISTED)     -> 候补 K 人
     *   countByStatus(id, STATUS_PENDING_REVIEW) -> 待审核 J 人
     * </pre>
     * 与其写三个几乎一样的方法，不如写一个通用的，用参数区分状态。
     *
     * @param activityId 活动id
     * @param status     报名状态，取值见 {@link ActivityRegistration} 的 STATUS_xxx 常量
     * @return 该状态下的记录数；查询失败时返回 0
     */
    public int countByStatus(Long activityId, String status) {
        String sql = "SELECT COUNT(*) FROM activity_registration "
                + "WHERE activity_id = ? AND status = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, activityId);
            ps.setString(2, status);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
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
     *   <li>取消报名：传入 {@link ActivityRegistration#STATUS_CANCELLED}</li>
     *   <li>重新报名：传入 {@link ActivityRegistration#STATUS_PENDING_REVIEW}
     *       （复用原来那一行，不新增记录）</li>
     * </ul>
     *
     * <p>注意重新报名时应该用 {@link #updateStatusAndReviewTime} 把 review_time
     * 一并清空成 null，否则上一轮的审核时间会残留，学生在新一轮里的排队位置就错了。
     *
     * <p>参数绑定顺序按 SQL 里的 {@code ?} 依次来：
     * {@code SET status = ? WHERE activity_id = ? AND student_id = ?}
     * → 1=状态、2=活动id、3=学生id。
     * 这里直接用方法参数即可，不需要去调用 getter。
     *
     * @param activityId 活动id
     * @param studentId  学生id
     * @param status     目标状态，取值见 {@link ActivityRegistration} 的 STATUS_xxx 常量
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
     * 审核报名：同时写入新状态和审核时间（教师审核，US-07）。
     *
     * <p>【为什么状态和 review_time 必须在一条 SQL 里一起写】
     * 候选人的排队位置由 review_time 决定。如果分两条 SQL 更新
     * （先改状态、再写时间），中间一旦失败就会出现"状态是候补但时间是空"的记录，
     * 排序时它的位置就不确定了。两条信息本来就是一个业务动作的结果，应当一起写。
     *
     * <p>参数顺序按 SQL 里的 {@code ?} 依次来：
     * {@code SET status = ?, review_time = ? WHERE activity_id = ? AND student_id = ?}
     * → 1=状态、2=审核时间、3=活动id、4=学生id。
     *
     * @param activityId 活动id
     * @param studentId  学生id
     * @param status     目标状态：{@link ActivityRegistration#STATUS_CONFIRMED}、
     *                   {@link ActivityRegistration#STATUS_WAITLISTED} 或
     *                   {@link ActivityRegistration#STATUS_REJECTED}
     * @param reviewTime 审核时间；传 null 表示清空（用于学生重新报名时复位）
     * @return 影响行数：1 表示修改成功，0 表示没有匹配的记录
     */
    public int updateStatusAndReviewTime(Long activityId, Long studentId,
                                         String status, LocalDateTime reviewTime) {
        String sql = "UPDATE activity_registration SET status = ?, review_time = ? "
                + "WHERE activity_id = ? AND student_id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            // LocalDateTime 用 setObject 绑定；传 null 会写成 SQL NULL
            ps.setObject(2, reviewTime);
            ps.setLong(3, activityId);
            ps.setLong(4, studentId);

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
     * <p>各查询方法都要做同样的转换，因此抽成私有方法复用。
     * 括号里写数据库列名（下划线），赋值给 Java 属性（驼峰）。
     *
     * <p>【两个时间字段的差别：一个可空、一个不可空】
     * <ul>
     *   <li>{@code register_time}：建表时是 NOT NULL 且有默认值，
     *       所以直接 {@code getTimestamp(...).toLocalDateTime()} 就行；</li>
     *   <li>{@code review_time}（V2.0 新增）：允许为 NULL（还没被教师审核），
     *       必须先判断 {@code getTimestamp} 是不是返回了 null，
     *       否则对 null 调 toLocalDateTime() 会立刻抛空指针异常。</li>
     * </ul>
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

        // review_time 可为 NULL：先取出 Timestamp 判空，再转 LocalDateTime
        java.sql.Timestamp reviewTimestamp = rs.getTimestamp("review_time");
        registration.setReviewTime(reviewTimestamp == null ? null : reviewTimestamp.toLocalDateTime());

        registration.setStatus(rs.getString("status"));
        return registration;
    }
}
