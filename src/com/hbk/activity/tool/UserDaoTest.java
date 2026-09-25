package com.hbk.activity.tool;

import com.hbk.activity.dao.UserDAO;
import com.hbk.activity.entity.User;
import com.hbk.activity.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * UserDAO 的自动化测试（V2.0 阶段 6 改造）。
 *
 * <p>【V1.5 版本的两个问题，本类一并修掉】
 * <ol>
 *   <li><b>不可重复运行</b>（技术债 T7）：旧的测试插入了 {@code test_user_01}
 *       却从不清理，第二次运行时会因为唯一约束冲突抛异常、
 *       断言结果全部失真。本类改为<b>先清理、测完再清理</b>，可以反复运行。</li>
 *   <li><b>只打印不断言</b>（技术债 T6）：旧版全靠人眼看控制台输出，
 *       出问题不会自动报错。本类改成断言式，末尾直接给出通过/失败计数。</li>
 * </ol>
 *
 * <p>【运行方式】需要 MySQL 已启动且已执行过 db/schema.sql：
 * <pre>
 *   build.bat run com.hbk.activity.tool.UserDaoTest
 * </pre>
 *
 * @author HBK组
 */
public class UserDaoTest {

    private static int passed = 0;
    private static int failed = 0;

    /** 测试用的临时账号名 */
    private static final String TEMP_USERNAME = "test_user_01";

    public static void main(String[] args) {
        UserDAO dao = new UserDAO();

        System.out.println("=== UserDAO 测试开始 ===");

        // 先清理可能残留的测试数据（上一次运行留下的），保证本测试可以反复运行
        deleteTempUser();
        check("测试开始前库中没有残留的 " + TEMP_USERNAME,
                !dao.existsByUsername(TEMP_USERNAME));

        try {
            // ---------- 1. 按账号判断是否存在 ----------
            check("existsByUsername(\"teacher01\") 返回 true",
                    dao.existsByUsername("teacher01"));
            check("existsByUsername(\"nobody\") 返回 false",
                    !dao.existsByUsername("nobody"));

            // ---------- 2. 按账号查 ----------
            User student = dao.findByUsername("student01");
            check("findByUsername(\"student01\") 能查到", student != null);
            if (student != null) {
                check("查到的姓名是李同学（实际 " + student.getName() + "）",
                        "李同学".equals(student.getName()));
                check("查到的角色是 STUDENT（实际 " + student.getRole() + "）",
                        User.ROLE_STUDENT.equals(student.getRole()));
                check("★ V2.0 新增的 status 字段也被读出来了（实际 "
                                + student.getStatus() + "）",
                        User.STATUS_ACTIVE.equals(student.getStatus()));
            }
            check("findByUsername 查不存在的账号返回 null",
                    dao.findByUsername("nobody") == null);

            // ---------- 3. 按 id 查 ----------
            User byId = dao.findById(student == null ? 2L : student.getId());
            check("findById 能查到同一个用户",
                    byId != null && student != null && byId.getId().equals(student.getId()));
            check("findById 查不存在的 id 返回 null", dao.findById(999999L) == null);

            // ---------- 4. 新增 ----------
            User newUser = new User();
            newUser.setUsername(TEMP_USERNAME);
            // 注意存的应该是密文；这里测试 DAO 的写入能力，随便给一个不像明文的串即可
            newUser.setPassword("test-hash-not-plaintext");
            newUser.setName("测试用户");
            newUser.setRole(User.ROLE_STUDENT);

            int rows = dao.insert(newUser);
            check("insert() 影响行数为 1（实际 " + rows + "）", rows == 1);

            User inserted = dao.findByUsername(TEMP_USERNAME);
            check("新增后能查到该账号", inserted != null);
            check("★ status 为 null 时被兜底写成 ACTIVE（实际 "
                            + (inserted == null ? "?" : inserted.getStatus()) + "）",
                    inserted != null && User.STATUS_ACTIVE.equals(inserted.getStatus()));

            // ---------- 5. 唯一约束生效 ----------
            // 再插一次同名账号，故意触发数据库唯一约束 uk_user_username。
            // ⚠️ 下面控制台里会打印一段 SQLIntegrityConstraintViolationException 堆栈，
            //    这是【预期内】的：UserDAO.insert() 捕获异常后会打印堆栈并返回 0，
            //    测试要验证的正是"返回 0"这个结果，不是异常本身。
            System.out.println("    （下面会打印一段重复键异常堆栈，属于预期内，用于验证唯一约束）");
            int duplicateRows = dao.insert(newUser);
            check("★ 重复插入同名账号返回 0（唯一约束生效）", duplicateRows == 0);

            // ---------- 6. V2.0 新增：findAll 与 updateStatus ----------
            check("findAll() 至少能查到 6 个演示账号（实际 "
                            + dao.findAll().size() + "）",
                    dao.findAll().size() >= 6);

            int disableRows = dao.updateStatus(inserted.getId(), User.STATUS_DISABLED);
            check("updateStatus 停用影响 1 行", disableRows == 1);
            check("停用后读回来是 DISABLED",
                    dao.findById(inserted.getId()).isDisabled());

            int enableRows = dao.updateStatus(inserted.getId(), User.STATUS_ACTIVE);
            check("updateStatus 恢复影响 1 行", enableRows == 1);
            check("恢复后读回来是 ACTIVE",
                    User.STATUS_ACTIVE.equals(dao.findById(inserted.getId()).getStatus()));

        } finally {
            // ---------- 清理 ----------
            // 这一步是"可以反复运行"的关键：不清理的话第二次跑就会在 insert 处失败
            int deleted = deleteTempUser();
            System.out.println("[清理] 删除测试账号 " + TEMP_USERNAME + "（" + deleted + " 行）");
            check("清理后库中不再有该测试账号", !dao.existsByUsername(TEMP_USERNAME));
        }

        System.out.println();
        System.out.println("=== 测试结束 ===");
        System.out.println("总计: " + (passed + failed) + "，通过: " + passed + "，失败: " + failed);
        if (failed > 0) {
            System.out.println("结论：存在未通过的检查，请查看上面的输出。");
        } else {
            System.out.println("结论：UserDAO 的增删改查与状态读写全部符合预期。");
        }
    }

    /**
     * 直接删除测试账号（仅测试清理用）。
     *
     * <p>【为什么用原生 JDBC 而不是 UserDAO】
     * 正式功能里没有"删除用户"这个操作 —— V2.0 的账号停用是改状态而不是删除
     * （见 US-13），所以 UserDAO 刻意不提供 delete 方法。
     * 测试需要把数据清干净，只能绕开 DAO 直接用 JDBC，这与 RegistrationDaoTest
     * 清理报名记录的做法是一致的。
     *
     * @return 被删除的行数
     */
    private static int deleteTempUser() {
        String sql = "DELETE FROM `user` WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TEMP_USERNAME);
            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /** 断言并打印结果 */
    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [OK]   " + description);
        } else {
            failed++;
            System.out.println("  [FAIL] " + description);
        }
    }
}
