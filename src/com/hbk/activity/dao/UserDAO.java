package com.hbk.activity.dao;

import com.hbk.activity.entity.User;
import com.hbk.activity.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用户表数据访问类：负责 user 表的所有 SQL 操作。
 */
public class UserDAO {

    /**
     * 判断账号是否已被注册。
     *
     * @param username 待检查的登录账号
     * @return true 表示该账号已存在
     */
    public boolean existsByUsername(String username) {
        // 只查数量，不查整行；表名 user 是保留字，用反引号包起来
        String sql = "SELECT COUNT(*) FROM `user` WHERE username = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // ? 从 1 开始编号
            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {                 // 先把游标移到第一行
                    return rs.getInt(1) > 0;     // 取第1列（COUNT 结果）
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;                            // 查询异常时按"不存在"处理
    }

    /**
     * 新增一个用户。
     *
     * @param user 待新增的用户对象（id 不需要设置，由数据库自增生成）
     * @return 影响的行数：1 表示新增成功，0 表示失败
     */
    public int insert(User user) {
        // INSERT 语句：字段列表里不写 id，因为它由数据库自增生成
        // VALUES 里 4 个 ? 分别对应 username、password、name、role
        String sql = "INSERT INTO `user` (username, password, name, role) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // ? 从 1 开始编号，顺序必须和上面字段列表一致
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getName());
            ps.setString(4, user.getRole());

            // 增删改都用 executeUpdate()，返回受影响的行数
            return ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;               // 出异常按失败处理
        }
    }

    public User findByUsername(String username) {
        String sql = "SELECT id, username, password, name, role FROM `user` WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);          // 第1个?填username

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {                // 有数据
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setPassword(rs.getString("password"));
                    user.setName(rs.getString("name"));
                    user.setRole(rs.getString("role"));
                    return user;                // 查到了
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;                            // 没查到
    }

    /**
     * 根据用户id查询用户。
     *
     * @param id 用户id
     * @return 查到返回 User 对象，查不到返回 null
     */
    public User findById(Long id) {
        // 注意条件是 id，不是 username
        String sql = "SELECT id, username, password, name, role FROM `user` WHERE id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // id 是 Long 类型，用 setLong（不是 setString）
            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setPassword(rs.getString("password"));
                    user.setName(rs.getString("name"));
                    user.setRole(rs.getString("role"));
                    return user;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}