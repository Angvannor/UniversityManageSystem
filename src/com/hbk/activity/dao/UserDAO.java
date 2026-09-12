package com.hbk.activity.dao;

import com.hbk.activity.entity.User;
import com.hbk.activity.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用户表数据访问类：负责 {@code user} 表的所有 SQL 操作。
 *
 * <p>【这个类是干什么的】
 * 把「用户」相关的 SQL 全部关在这一层，对上只暴露 Java 方法。
 * 业务层（AuthService）调用 {@code existsByUsername()}、{@code insert()} 时
 * 完全看不到 SQL，也不需要知道数据存在表里还是别的地方。
 *
 * <p>【覆盖的需求】
 * <pre>
 *   REQ-01 注册：existsByUsername() 查重 + insert() 写入
 *   REQ-01 登录：findByUsername() 取出用户（含密码密文）后由业务层比对
 *   REQ-01 恢复登录状态：findById()
 * </pre>
 *
 * <p>【本类用到的三种 JDBC 写法】
 * <ol>
 *   <li><b>查单条</b>（SQL 带 ?）：先 {@code try (conn; ps)} → setXxx 填参数 →
 *       再 {@code try (ResultSet rs = ps.executeQuery()) { if (rs.next()) ... }}</li>
 *   <li><b>查数量</b>：用 {@code SELECT COUNT(*)}，把结果当单条读出来</li>
 *   <li><b>新增</b>：用 {@code ps.executeUpdate()}，返回影响行数，不需要 ResultSet</li>
 * </ol>
 *
 * <p>【两个必须记住的细节】
 * <ul>
 *   <li>表名要写成 {@code `user`}（加反引号）—— user 是 MySQL 保留字，
 *       直接写 user 会导致语法错误；</li>
 *   <li>参数一律用 {@code ?} 占位 + {@code setXxx} 绑定，绝不拼接字符串，
 *       否则用户可以输入 {@code ' OR '1'='1} 之类的字符改变 SQL 语义（SQL 注入）。</li>
 * </ul>
 *
 * @author HBK组
 */
public class UserDAO {

    /**
     * 查询时统一使用的字段列表，避免各方法重复书写。
     * 这里包含 password，因为登录校验必须用到密码密文。
     */
    private static final String COLUMNS = "id, username, password, name, role";

    // ==================================================================
    // 一、查询
    // ==================================================================

    /**
     * 判断账号是否已被注册（注册时查重，REQ-01）。
     *
     * <p>只需要判断"有没有"，所以用 COUNT(*) 让数据库返回一个数字，
     * 不必把整行（包括密码）都查出来。
     *
     * @param username 待检查的登录账号
     * @return true 表示该账号已存在
     */
    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM `user` WHERE username = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // ? 的序号从 1 开始
            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {                  // 先移动游标到第一行
                    return rs.getInt(1) > 0;      // 取第1列（COUNT 的结果）
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;                             // 查询异常时按"不存在"处理
    }

    /**
     * 根据账号查询用户（登录时使用，REQ-01）。
     *
     * <p>返回的对象里包含密码密文，由业务层负责比对：
     * 一致才允许登录（本类不做密码判断，那属于业务规则）。
     *
     * @param username 登录账号
     * @return 查到返回 User 对象，查不到返回 null
     */
    public User findByUsername(String username) {
        String sql = "SELECT " + COLUMNS + " FROM `user` WHERE username = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;                              // 没查到
    }

    /**
     * 根据用户id查询用户（登录状态恢复时使用）。
     *
     * <p>注意条件字段是 id（Long 类型），所以用 setLong，
     * 不要照抄 findByUsername 里的 username 和 setString。
     *
     * @param id 用户id
     * @return 查到返回 User 对象，查不到返回 null
     */
    public User findById(Long id) {
        String sql = "SELECT " + COLUMNS + " FROM `user` WHERE id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

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

    // ==================================================================
    // 二、新增
    // ==================================================================

    /**
     * 新增一个用户（注册，REQ-01）。
     *
     * <p>要点：
     * <ul>
     *   <li>字段列表里不写 id，因为它由数据库 AUTO_INCREMENT 自动生成；</li>
     *   <li>4 个 {@code ?} 的编号顺序必须与字段列表一致：username、password、name、role；</li>
     *   <li>新增属于"增删改"，用 {@code executeUpdate()}，返回影响行数。</li>
     * </ul>
     *
     * <p>注意：如果账号已存在，数据库的唯一约束 uk_user_username 会让本方法抛出
     * SQLIntegrityConstraintViolationException。业务层应当先调用
     * {@link #existsByUsername(String)} 给出友好提示，
     * 数据库约束只作为并发情况下的最后一道保障。
     *
     * @param user 待新增的用户对象（id 不需要设置，密码应为密文）
     * @return 影响行数：1 表示新增成功，0 表示失败
     */
    public int insert(User user) {
        String sql = "INSERT INTO `user` (username, password, name, role) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 顺序必须和上面括号里的字段列表一一对应
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getName());
            ps.setString(4, user.getRole());

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
     * 把结果集当前行转换成一个 User 对象。
     *
     * <p>抽成私有方法，避免 findByUsername 和 findById 里重复写 5 行 set。
     *
     * <p>括号里是<b>数据库列名</b>，赋值给的是<b>Java 属性</b>。
     * 本表两者同名，遇到 start_time 这类字段就需要转换，见 ActivityDAO.mapRow。
     *
     * @param rs 已经指向某一行的结果集
     * @return 转换后的用户对象
     * @throws SQLException 取值失败时抛出，由调用方的 try-catch 统一处理
     */
    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setName(rs.getString("name"));
        user.setRole(rs.getString("role"));
        return user;
    }
}
