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

        // 3. 角色白名单（V2.0 新增的必需校验）
        //    role 是前端传上来的，V2.0 又新增了 ADMIN 角色，如果不在这里拦住，
        //    任何人都能构造请求把角色写成 ADMIN 注册成系统管理员 —— 这是权限提升漏洞。
        //    因此只允许自助注册 STUDENT 和 TEACHER，管理员账号只能由数据库脚本预置
        //    （对应最小假设 A2：不提供管理员建号功能）。
        if (!User.ROLE_STUDENT.equals(role) && !User.ROLE_TEACHER.equals(role)) {
            return "只能注册学生或教师账号";
        }

        // 4. 组装对象：注意密码必须加密后再存，不能存明文
        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(PasswordUtil.encrypt(password));   // ← 关键
        user.setName(name.trim());
        user.setRole(role);
        // 新注册的账号一定是可用的（停用是管理员事后对异常账号的操作）
        user.setStatus(User.STATUS_ACTIVE);

        // 5. 写库
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
     * <p>⚠️【V2.0 重要：调用方必须检查账号是否被停用】
     * 本方法<b>不负责</b>拦截已停用的账号，因为调用方需要区分两种失败：
     * <pre>
     *   返回 null            -> 账号不存在或密码错误    -> 提示"账号或密码错误"，错误码 2001
     *   返回 user 且已停用   -> 密码是对的，但账号被停用 -> 提示"账号已被停用"，错误码 2003
     * </pre>
     * 如果把两种情况都变成 null，调用方就没法给出准确提示了。
     * 因此<b>每一个调用本方法的地方</b>，拿到非 null 结果后都必须过一下
     * {@code user.isDisabled()}，否则被停用的账号仍能进入系统
     * （对应 US-13 验收标准 3）。
     *
     * @param username 登录账号
     * @param password 明文密码
     * @return 密码校验通过返回 User 对象（含 role 与 status，调用方需检查 status）；
     *         账号不存在或密码错误返回 null
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

        // 第4步：账号与密码都通过，返回用户对象（含角色与账号状态）。
        //   注意这里【故意不判断 status】，好让调用方能区分"密码错"和"账号被停用"，
        //   由调用方负责用 user.isDisabled() 拦截。
        return user;
    }

    /**
     * 根据用户 id 查询用户。
     *
     * <p>【接口层需要它的两个原因】
     * <ol>
     *   <li>Web 接口每次请求只带令牌，服务端要从令牌还原"当前用户是谁"，
     *       拿到 id 后用它查出完整的用户对象（含角色）；</li>
     *   <li>活动列表要显示"发布教师"的姓名，需要用教师 id 反查。</li>
     * </ol>
     *
     * @param userId 用户 id
     * @return 用户对象；id 为 null 或查不到时返回 null
     */
    public User getById(Long userId) {
        if (userId == null) {
            return null;
        }
        return userDAO.findById(userId);
    }

    /**
     * 根据账号查询用户。
     *
     * <p>接口层在注册成功后需要把新用户信息返回给前端（用于提示"注册成功，张三"），
     * 而 register() 只返回"是否成功"，因此这里补一个按账号查询的方法。
     *
     * @param username 登录账号
     * @return 用户对象；查不到时返回 null
     */
    public User getByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userDAO.findByUsername(username.trim());
    }
}