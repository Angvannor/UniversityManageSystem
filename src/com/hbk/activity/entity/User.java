package com.hbk.activity.entity;

/**
 * 用户实体类，对应数据库表 {@code user}。
 *
 * <p>【这个类是干什么的】
 * 用 Java 对象表示数据库里的一行 user 记录：
 * 表的一行数据 = 一个 User 对象，表的一个字段 = 对象的一个属性。
 * 这样上层代码传递的就是「一个学生」这种有意义的对象，而不是一堆零散的字符串。
 *
 * <p>【与数据库的对应关系】（见 db/schema.sql 第 2 节）
 * <pre>
 *   user.id       ->  User.id
 *   user.username ->  User.username
 *   user.password ->  User.password   （加密后的密文，不是明文）
 *   user.name     ->  User.name
 *   user.role     ->  User.role       （STUDENT 学生 / TEACHER 教师 / ADMIN 系统管理员）
 *   user.status   ->  User.status     （ACTIVE 可用 / DISABLED 已停用）
 * </pre>
 *
 * <p>【设计说明】
 * <ul>
 *   <li>属性全部 private，外部只能通过 getter / setter 访问，避免被随意改坏；</li>
 *   <li>无参构造方法供 DAO 使用（先 new 出来再逐个 set）；</li>
 *   <li>全参构造方法供测试或手工创建对象时使用；</li>
 *   <li>toString() 刻意不输出 password，防止密码出现在日志或调试输出里。</li>
 * </ul>
 *
 * @author HBK组
 */
public class User {

    /** 用户id，对应 user.id，主键自增 */
    private Long id;

    /** 登录账号（学号 / 工号），对应 user.username，数据库中有唯一约束 */
    private String username;

    /** 密码，对应 user.password，保存的是加密后的密文 */
    private String password;

    /** 姓名，对应 user.name */
    private String name;

    /** 角色，对应 user.role：STUDENT 学生 / TEACHER 活动组织教师 / ADMIN 系统管理员 */
    private String role;

    /**
     * 账号状态，对应 user.status：ACTIVE 可用 / DISABLED 已停用（V2.0 新增）。
     *
     * <p>【停用不是删除】停用只是把状态改成 DISABLED，账号和它的历史数据都保留，
     * 效果是<b>不允许再登录</b>。系统管理员发现账号异常时可以停用，
     * 问题处理完再恢复为 ACTIVE（对应 US-13）。
     */
    private String status;

    // ------------------------------------------------------------------
    // 角色常量：取值必须与 db/schema.sql 中 user.role 的注释完全一致。
    // 注意只有 STUDENT 和 TEACHER 允许自助注册，ADMIN 只能由数据库脚本预置
    // （否则任何人都能注册一个管理员账号，属于权限提升漏洞）。
    // ------------------------------------------------------------------

    /** 角色：学生 */
    public static final String ROLE_STUDENT = "STUDENT";

    /** 角色：活动组织教师 */
    public static final String ROLE_TEACHER = "TEACHER";

    /** 角色：系统管理员（只能预置，不开放注册） */
    public static final String ROLE_ADMIN = "ADMIN";

    // ------------------------------------------------------------------
    // 账号状态常量：取值必须与 db/schema.sql 中 user.status 的注释完全一致。
    // 抽成常量而不是在各处直接写字符串，可以避免拼错字母造成的隐蔽 bug
    // （例如把 DISABLED 写成 DISBALED，编译能过但判断永远不成立）。
    // ------------------------------------------------------------------

    /** 账号状态：可用 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 账号状态：已停用，不能登录 */
    public static final String STATUS_DISABLED = "DISABLED";

    /**
     * 无参构造方法。
     * DAO 从数据库读出一行记录时，会先 new User() 再逐个 set 属性。
     */
    public User() {
    }

    /**
     * 全参构造方法，方便在测试代码里直接创建对象。
     *
     * @param id       用户id
     * @param username 登录账号
     * @param password 密码（密文）
     * @param name     姓名
     * @param role     角色：STUDENT / TEACHER / ADMIN
     * @param status   账号状态：ACTIVE / DISABLED
     */
    public User(Long id, String username, String password, String name, String role, String status) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.name = name;
        this.role = role;
        this.status = status;
    }

    // ------------------------------------------------------------------
    // getter / setter：每个属性一对，供外部安全读写
    // ------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 判断账号是否已被停用。
     *
     * <p>登录时必须检查它：密码正确但账号已停用，仍然不允许登录
     * （对应 US-13 验收标准 3，接口层返回错误码 2003）。
     *
     * <p>写法说明：用常量在前、变量在后的 {@code equals}，
     * 即使 status 为 null 也不会抛空指针异常（写成 {@code status.equals(...)} 就会）。
     *
     * @return true 表示账号已停用
     */
    public boolean isDisabled() {
        return STATUS_DISABLED.equals(status);
    }

    /**
     * 输出对象内容，便于调试时直接 System.out.println(user) 查看数据。
     * 注意：这里不包含 password，避免密码泄漏到控制台或日志中。
     *
     * @return 形如 User{id=1, username='teacher01', name='张老师', role='TEACHER', status='ACTIVE'} 的字符串
     */
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', name='" + name
                + "', role='" + role + "', status='" + status + "'}";
    }
}
