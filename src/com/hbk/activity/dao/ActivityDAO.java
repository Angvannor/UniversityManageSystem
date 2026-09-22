package com.hbk.activity.dao;

import com.hbk.activity.entity.Activity;
import com.hbk.activity.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动表数据访问类：负责 {@code activity} 表的所有 SQL 操作。
 *
 * <p>【这个类是干什么的】
 * 把「活动」相关的 SQL 全部关在这一层，对上只暴露 Java 方法。
 * 业务层（ActivityService）和菜单层看到的都是
 * {@code findAll()}、{@code insert(activity)} 这样的方法名，看不到 SQL。
 * 对应报告 4.1 中「数据访问层负责对数据库执行查询与更新操作」。
 *
 * <p>【覆盖的需求】
 * <pre>
 *   REQ-02 学生浏览活动列表 / 查看活动详情  ->  findAll()      / findById()
 *   REQ-05 教师发布、修改、关闭、删除自己的活动 -> insert() / update() / updateStatus() / deleteById()
 *   REQ-05 教师查看自己发布的活动            ->  findByTeacherId()
 * </pre>
 *
 * <p>【写 DAO 方法时反复用到的三种写法，务必分清】
 * <ol>
 *   <li><b>查多条</b>（SQL 无参数）：{@code try (conn; ps; rs) { while (rs.next()) ... }}</li>
 *   <li><b>查单条</b>（SQL 有 ?）：先 {@code try (conn; ps)}，setXxx 填参数，
 *       再 {@code try (ResultSet rs = ps.executeQuery()) { if (rs.next()) ... }}</li>
 *   <li><b>增删改</b>（INSERT/UPDATE/DELETE）：用 {@code ps.executeUpdate()}，
 *       返回影响行数，不需要 ResultSet</li>
 * </ol>
 *
 * <p>【表名说明】activity 不是 MySQL 保留字，因此不像 user 那样需要反引号。
 *
 * @author HBK组
 */
public class ActivityDAO {

    /**
     * 查询时统一使用的字段列表。
     * 抽成常量，避免在多个方法里重复写同一串字段名（改字段时只需改一处）。
     */
    private static final String COLUMNS =
            "id, title, description, location, start_time, end_time, status, teacher_id";

    // ==================================================================
    // 一、查询
    // ==================================================================

    /**
     * 查询全部活动，按开始时间排序（学生浏览活动列表，REQ-02）。
     *
     * <p>SQL 里没有 {@code ?}，所以 ResultSet 可以直接声明在 try 头部。
     *
     * @return 活动列表；没有任何数据时返回空列表，而不是 null
     */
    public List<Activity> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM activity ORDER BY start_time";

        List<Activity> list = new ArrayList<>();

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            // while 循环：把结果集里的每一行都转换成一个 Activity 对象
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 根据活动id查询单个活动（学生查看活动详情，REQ-02）。
     *
     * <p>SQL 里有 {@code ?}，因此分两步：先绑定参数，再执行查询。
     * 注意 WHERE 必须写在 ORDER BY 之前（查单条也不需要排序）。
     *
     * @param id 活动id
     * @return 查到返回 Activity 对象，查不到返回 null
     */
    public Activity findById(Long id) {
        String sql = "SELECT " + COLUMNS + " FROM activity WHERE id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 先给 ? 填值（第1个?）
            ps.setLong(1, id);

            // 参数绑定完成后再执行查询
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 查询某个教师发布的所有活动（教师端"我发布的活动"，REQ-05）。
     *
     * <p>它是「教师只能管理自己发布的活动」这条约束的数据基础：
     * 教师端只展示这些活动，编辑 / 删除前再校验一次归属。
     *
     * @param teacherId 教师用户id
     * @return 活动列表；该教师没有发布过活动时返回空列表
     */
    public List<Activity> findByTeacherId(Long teacherId) {
        String sql = "SELECT " + COLUMNS + " FROM activity WHERE teacher_id = ? ORDER BY start_time";

        List<Activity> list = new ArrayList<>();

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 参数名是 teacherId（驼峰），不是数据库列名 teacher_id
            ps.setLong(1, teacherId);

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

    // ==================================================================
    // 二、新增 / 修改 / 删除
    // ==================================================================

    /**
     * 新增一个活动（教师发布活动，REQ-05）。
     *
     * <p>要点：
     * <ul>
     *   <li>字段列表里不写 id，由数据库自增生成；</li>
     *   <li>7 个 {@code ?} 的编号顺序必须与字段列表一致；</li>
     *   <li>增删改一律使用 {@code executeUpdate()}，返回影响行数；</li>
     *   <li>新增成功后会把数据库生成的主键回填到入参对象的 id 上
     *       （Web 接口需要把新活动 id 返回给前端）。</li>
     * </ul>
     *
     * @param activity 待新增的活动对象（id 不需要设置，插入后会被回填）
     * @return 影响行数：1 表示成功，0 表示失败
     */
    public int insert(Activity activity) {
        String sql = "INSERT INTO activity (title, description, location, start_time, end_time, status, teacher_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        // 第二个参数 Statement.RETURN_GENERATED_KEYS 告诉驱动：
        // 插入完成后我要取回数据库生成的自增主键
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            // PreparedStatement 只有 setString / setLong / setObject 这类方法，
            // 没有 setTitle 这种按字段名命名的方法；第一个参数是 ? 的序号
            ps.setString(1, activity.getTitle());
            ps.setString(2, activity.getDescription());
            ps.setString(3, activity.getLocation());
            // LocalDateTime 用 setObject 绑定最简单（不用手动转 Timestamp）
            ps.setObject(4, activity.getStartTime());
            ps.setObject(5, activity.getEndTime());
            ps.setString(6, activity.getStatus());
            ps.setLong(7, activity.getTeacherId());

            int rows = ps.executeUpdate();

            // 取回自增主键并回填到实体上，调用方之后就能用 activity.getId()
            if (rows > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        activity.setId(keys.getLong(1));
                    }
                }
            }
            return rows;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 修改活动信息（教师编辑活动，REQ-05）。
     *
     * <p>SQL 里有 7 个 {@code ?}：前 6 个是要修改的字段，
     * 第 7 个是 WHERE 条件里的活动id —— 顺序不能错。
     *
     * @param activity 带 id 的活动对象（id 用来定位要改哪一行）
     * @return 影响行数：1 表示成功，0 表示没有匹配的行
     */
    public int update(Activity activity) {
        String sql = "UPDATE activity SET title = ?, description = ?, location = ?, "
                + "start_time = ?, end_time = ?, status = ? WHERE id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, activity.getTitle());
            ps.setString(2, activity.getDescription());
            ps.setString(3, activity.getLocation());
            ps.setObject(4, activity.getStartTime());
            ps.setObject(5, activity.getEndTime());
            ps.setString(6, activity.getStatus());
            // 第7个 ? 是 WHERE id = ?，填的是 Java 属性 activity.getId()
            ps.setLong(7, activity.getId());

            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 只修改活动状态（教师关闭报名，REQ-05）。
     *
     * <p>单独写一个方法而不是调用 update()，是因为关闭报名只需要改一个字段，
     * 没必要把整行数据重新写一遍。
     *
     * <p>参数顺序容易写错：SQL 是 {@code SET status = ? WHERE id = ?}，
     * 所以第 1 个 {@code ?} 是状态，第 2 个是活动id。
     *
     * @param id     活动id
     * @param status 新状态：OPEN / CLOSED / FINISHED
     * @return 影响行数：1 表示成功
     */
    public int updateStatus(Long id, String status) {
        String sql = "UPDATE activity SET status = ? WHERE id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 第1个?是状态（String），第2个?是活动id（Long），别写反
            ps.setString(1, status);
            ps.setLong(2, id);

            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 根据活动id删除活动（教师删除活动，REQ-05）。
     *
     * <p>注意：业务层在调用本方法前必须先检查该活动是否已有报名记录，
     * 否则会触发外键约束错误（activity_registration 引用了 activity）。
     *
     * @param id 活动id
     * @return 影响行数：1 表示删除成功
     */
    public int deleteById(Long id) {
        String sql = "DELETE FROM activity WHERE id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            // DELETE 属于增删改，用 executeUpdate，不需要 ResultSet
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
     * 把结果集当前行转换成一个 Activity 对象。
     *
     * <p>抽成私有方法的目的：findAll、findById、findByTeacherId 都要做同样的转换，
     * 写一次就够了，以后实体类加字段也只需改这一处。
     *
     * <p>注意括号里写的是<b>数据库列名</b>（下划线），
     * 赋值给的是<b>Java 属性</b>（驼峰），转换就在这一处发生。
     *
     * <p>时间字段用 {@code getTimestamp(...).toLocalDateTime()}：
     * ResultSet 没有 getLocalDateTime 方法，必须先取 Timestamp 再转。
     * （start_time / end_time 在建表时是 NOT NULL，所以不必判空。）
     *
     * @param rs 已经指向某一行的结果集
     * @return 转换后的活动对象
     * @throws SQLException 取值失败时抛出，由调用方的 try-catch 统一处理
     */
    private Activity mapRow(ResultSet rs) throws SQLException {
        Activity activity = new Activity();
        activity.setId(rs.getLong("id"));
        activity.setTitle(rs.getString("title"));
        activity.setDescription(rs.getString("description"));
        activity.setLocation(rs.getString("location"));
        activity.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
        activity.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
        activity.setStatus(rs.getString("status"));
        activity.setTeacherId(rs.getLong("teacher_id"));
        return activity;
    }
}
