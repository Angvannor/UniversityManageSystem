package com.hbk.activity.tool;

import com.hbk.activity.dao.UserDAO;
import com.hbk.activity.entity.User;
import com.hbk.activity.service.AuthService;
import com.hbk.activity.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * AuthService 手工测试类。
 *
 * <p>【本类的教学重点：测试怎么写】
 * 一个测试用例固定由三部分组成：
 * <pre>
 *   1. 准备（Arrange）：构造输入数据、记录期望结果
 *   2. 执行（Act）    ：调用被测方法，拿到实际结果
 *   3. 断言（Assert） ：比较「实际结果」与「期望结果」，不一致就是 bug
 * </pre>
 * 本类用一个 {@link #check(String, boolean)} 方法充当最简单的断言工具，
 * 它会把每个用例的结果打成「[通过] / [失败]」，最后汇总统计。
 * 这样测试输出一眼就能看出哪一条错了，不用逐个肉眼比对。
 *
 * <p>【运行方式】在项目根目录执行：
 * <pre>
 *   build.bat run com.hbk.activity.tool.AuthServiceTest
 * </pre>
 *
 * <p>【注意】本类属于开发期验证工具，不是系统正式功能。
 * 测试结束后会删除自己创建的测试账号，不给数据库留垃圾数据。
 *
 * @author HBK组
 */
public class AuthServiceTest {

    /** 测试账号名：故意用不常见的名字，避免和真实数据冲突 */
    private static final String TEST_USERNAME = "test_auth_user";

    /** 测试账号的明文密码 */
    private static final String TEST_PASSWORD = "abc123456";

    /** 通过 / 失败的用例计数，用于最后汇总 */
    private static int passed = 0;
    private static int failed = 0;

    /**
     * 最简单的断言工具：判断条件是否成立并累计结果。
     *
     * <p>真实项目会使用 JUnit 的 assertEquals / assertTrue，
     * 原理和这里完全一样，只是它还能在失败时给出更详细的差异信息。
     *
     * @param caseName  用例名称（打印出来便于定位）
     * @param condition 断言条件，true 表示通过
     */
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
        AuthService authService = new AuthService();
        UserDAO userDAO = new UserDAO();

        System.out.println("=== AuthService 测试开始 ===");

        // 先清掉上一次可能残留的测试账号，保证测试可以重复运行
        deleteTestUser();

        // ==============================================================
        // 一、登录相关（使用初始账号 teacher01 / student01，密码 123456）
        // ==============================================================
        System.out.println("\n[登录测试]");

        // 用例1：正确账号 + 正确密码 → 应登录成功
        User teacher = authService.login("teacher01", "123456");
        check("正确账号密码登录成功", teacher != null);
        check("登录返回的角色是 TEACHER", teacher != null && "TEACHER".equals(teacher.getRole()));
        check("登录返回的姓名正确", teacher != null && "张老师".equals(teacher.getName()));
        System.out.println("       登录结果: " + teacher);

        // 用例2：密码错误 → 应返回 null
        check("密码错误时登录失败", authService.login("teacher01", "wrong-password") == null);

        // 用例3：账号不存在 → 应返回 null（且提示与密码错误一致，不暴露账号是否存在）
        check("账号不存在时登录失败", authService.login("no_such_user", "123456") == null);

        // 用例4：空参数 → 应返回 null
        check("账号为空时登录失败", authService.login("", "123456") == null);
        check("密码为空时登录失败", authService.login("teacher01", "") == null);

        // 用例5：密码里有空格的情况（验证 trim 是否会影响密码）
        check("密码多打空格登录失败", authService.login("teacher01", " 123456 ") == null);

        // ==============================================================
        // 二、注册相关
        // ==============================================================
        System.out.println("\n[注册测试]");

        // 用例6：密码太短 → 应被拒绝
        String shortPwdResult = authService.register("test_short_pwd", "123", "短密码", "STUDENT");
        check("密码少于6位被拒绝", "密码长度不能少于 6 位".equals(shortPwdResult));
        System.out.println("       提示信息: " + shortPwdResult);

        // 用例7：账号为空 → 应被拒绝
        check("账号为空被拒绝", authService.register("", TEST_PASSWORD, "空账号", "STUDENT") != null);

        // 用例8：姓名过长/为空 → 应被拒绝
        check("姓名为空被拒绝", authService.register("test_no_name", TEST_PASSWORD, "", "STUDENT") != null);

        // 用例9：正常注册 → 应返回 null（null 表示成功）
        String registerResult = authService.register(TEST_USERNAME, TEST_PASSWORD, "测试学生", "STUDENT");
        check("新账号注册成功（返回 null）", registerResult == null);
        System.out.println("       注册返回: " + registerResult);

        // 用例10：重复注册同一账号 → 应被拒绝并给出明确提示
        String duplicateResult = authService.register(TEST_USERNAME, TEST_PASSWORD, "重复注册", "STUDENT");
        check("重复注册被拒绝", "该账号已被注册".equals(duplicateResult));
        System.out.println("       提示信息: " + duplicateResult);

        // 用例11：验证密码确实以密文保存（这是报告"密码不以明文保存"约束的验证点）
        User saved = userDAO.findByUsername(TEST_USERNAME);
        check("注册后能查到该用户", saved != null);
        check("密码不是明文", saved != null && !TEST_PASSWORD.equals(saved.getPassword()));
        check("密码是64位SHA-256密文", saved != null && saved.getPassword().length() == 64);
        System.out.println("       库中密码: " + (saved == null ? "null" : saved.getPassword()));
        System.out.println("       明文密码: " + TEST_PASSWORD + "（两者应完全不同）");

        // 用例12：新注册的账号能正常登录
        User newUser = authService.login(TEST_USERNAME, TEST_PASSWORD);
        check("新注册账号可以登录", newUser != null);
        check("新账号角色为 STUDENT", newUser != null && "STUDENT".equals(newUser.getRole()));
        System.out.println("       登录结果: " + newUser);

        // ==============================================================
        // 三、清理测试数据
        // ==============================================================
        deleteTestUser();
        System.out.println("\n[清理] 已删除测试账号 " + TEST_USERNAME);
        check("清理后测试账号查不到", userDAO.findByUsername(TEST_USERNAME) == null);

        // ==============================================================
        // 四、测试汇总
        // ==============================================================
        System.out.println("\n=== 测试结束 ===");
        System.out.println("用例总数: " + (passed + failed) + "，通过: " + passed + "，失败: " + failed);
        if (failed > 0) {
            System.out.println("结论：存在未通过的用例，需要检查上面的 [失败] 项。");
        } else {
            System.out.println("结论：AuthService 注册与登录逻辑全部符合预期。");
        }
    }

    /**
     * 删除测试账号（仅测试清理用）。
     *
     * <p>UserDAO 没有提供 delete 方法（正式功能里不需要删除用户），
     * 所以测试类直接用 JDBC 执行一条 DELETE 把数据清干净。
     */
    private static void deleteTestUser() {
        String sql = "DELETE FROM `user` WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TEST_USERNAME);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
