package com.hbk.activity.service;

import com.hbk.activity.dao.UserDAO;
import com.hbk.activity.entity.User;
import com.hbk.activity.util.PasswordUtil;

/**
 * 用户认证业务类：负责注册与登录的业务规则（REQ-01）。
 */
public class AuthService {

    private final UserDAO userDAO = new UserDAO();

    /**
     * 注册新用户。
     *
     * @return 成功返回 null；失败返回失败原因（可直接打印给用户）
     */
    public String register(String username, String password, String name, String role) {
        // 1. 非空校验：菜单层可能传进来空字符串
        if (username == null || username.trim().isEmpty()) {
            return "账号不能为空";
        }
        if (password == null || password.length() < 6) {
            return "密码长度不能少于 6 位";
        }
        if (name == null || name.trim().isEmpty()) {
            return "姓名不能为空";
        }

        // 2. 账号查重（业务规则）：已存在就直接拒绝，给用户友好提示
        if (userDAO.existsByUsername(username.trim())) {
            return "该账号已被注册";
        }

        // 3. 组装对象：注意密码必须加密后再存，不能存明文
        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(PasswordUtil.encrypt(password));   // ← 关键
        user.setName(name.trim());
        user.setRole(role);

        // 4. 写库
        int rows = userDAO.insert(user);
        return rows > 0 ? null : "注册失败，请稍后重试";
    }

    /**
     * 登录校验（REQ-01）。
     *
     * <p>【为什么不区分"账号不存在"和"密码错误"】
     * 两种情况都返回 null，由菜单统一提示"账号或密码错误"。
     * 如果分别提示，攻击者就能通过不断尝试来判断哪些账号真实存在（账号枚举）。
     *
     * @param username 登录账号
     * @param password 明文密码
     * @return 登录成功返回 User 对象（含 role，菜单据此决定进入学生还是教师菜单）；
     *         登录失败返回 null
     */
    public User login(String username, String password) {
        // 第1步：非空校验。本方法返回 User，所以失败一律 return null
        if (username == null || password == null
                || username.trim().isEmpty() || password.trim().isEmpty()) {
            return null;
        }

        // 第2步：按账号查询用户。
        //   findByUsername 是方法，必须加括号并传参数才会返回结果；
        //   只查一次并存进变量，避免后面重复查库。
        User user = userDAO.findByUsername(username.trim());
        if (user == null) {
            return null;                  // 账号不存在
        }

        // 第3步：校验密码。
        //   库里存的是 SHA-256 密文，不能直接用 equals 比对，
        //   要用 PasswordUtil.matches 把用户输入的明文重新哈希后再比。
        if (!PasswordUtil.matches(password, user.getPassword())) {
            return null;                  // 密码错误
        }

        // 第4步：账号与密码都通过，返回用户对象（其中包含角色）
        return user;
    }
}