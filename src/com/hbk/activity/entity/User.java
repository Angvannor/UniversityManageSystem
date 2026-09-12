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
 *   user.role     ->  User.role       （STUDENT 学生 / TEACHER 教师）
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

    /** 角色，对应 user.role：STUDENT 学生 / TEACHER 活动组织教师 */
    private String role;

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
     * @param role     角色：STUDENT / TEACHER
     */
    public User(Long id, String username, String password, String name, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.name = name;
        this.role = role;
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

    /**
     * 输出对象内容，便于调试时直接 System.out.println(user) 查看数据。
     * 注意：这里不包含 password，避免密码泄漏到控制台或日志中。
     *
     * @return 形如 User{id=1, username='teacher01', name='张老师', role='TEACHER'} 的字符串
     */
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', name='" + name + "', role='" + role + "'}";
    }
}
