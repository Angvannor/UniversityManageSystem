package com.hbk.activity.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库连接工具类。
 *
 * <p>【这个类是干什么的】
 * 把「怎么连数据库」这件事集中到一个地方。如果不封装，每个 DAO 方法里都要重复写：
 * <pre>
 *     Class.forName("com.mysql.cj.jdbc.Driver");
 *     Connection conn = DriverManager.getConnection(url, user, password);
 *     ... 执行 SQL ...
 *     conn.close();
 * </pre>
 * 连接信息散落在十几个方法里，改一次数据库密码就要改十几处，也容易漏关连接。
 * 有了本类之后，任何地方只需要两行：
 * <pre>
 *     Connection conn = DBUtil.getConnection();
 *     ...
 *     DBUtil.close(conn, stmt, rs);
 * </pre>
 *
 * <p>【对应报告】
 * 实验报告 4.1 总体设计中的「数据访问层通过 JDBC 访问 MySQL」，
 * 本类就是 JDBC 访问的入口。
 *
 * <p>【设计要点】
 * <ol>
 *   <li>类声明为 final、构造方法私有：工具类只提供静态方法，不应该被 new 出来；</li>
 *   <li>用 static 代码块加载驱动，整个程序运行期间只会执行一次；</li>
 *   <li>getConnection() 把异常抛给调用方，而不是自己吞掉：
 *       连不上数据库是严重问题，必须让上层知道并给出提示。</li>
 * </ol>
 *
 * @author HBK组
 */
public final class DBUtil {

    // ------------------------------------------------------------------
    // 1. 数据库连接参数
    //
    //    安全说明（对应任务说明书「不得提交密码等敏感信息」的要求）：
    //    数据库密码不再写死在代码里，而是从 config/db.properties 读取。
    //    真实配置文件已加入 .gitignore，不会进入 Git 仓库；
    //    仓库里只保留 config/db.properties.example 模板。
    //    首次使用请先复制模板并填写密码：
    //        copy config\db.properties.example config\db.properties
    // ------------------------------------------------------------------

    /** MySQL 驱动类名（MySQL 8 的驱动类是 com.mysql.cj.jdbc.Driver） */
    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    /** 外部配置文件的候选路径（按顺序查找，支持不同工作目录下运行） */
    private static final String[] CONFIG_CANDIDATES = {
            "config/db.properties",
            "../config/db.properties",
            "db.properties"
    };

    /** 连接地址默认值：未提供配置文件时使用 */
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/campus_activity"
                    // useUnicode + characterEncoding：保证中文正常读写，不出现乱码
                    + "?useUnicode=true&characterEncoding=utf8"
                    // serverTimezone：MySQL 8 必须指定时区，否则可能报时区错误
                    + "&serverTimezone=Asia/Shanghai"
                    // useSSL=false：本地开发不启用 SSL，避免连接时的握手警告
                    + "&useSSL=false"
                    // allowPublicKeyRetrieval：允许获取公钥，配合 MySQL 8 的加密认证方式
                    + "&allowPublicKeyRetrieval=true";

    /** 数据库用户名默认值 */
    private static final String DEFAULT_USERNAME = "root";

    // 以下三个变量会被 loadConfig() 用配置文件中的内容覆盖，
    // 因此不能声明为 final
    private static String url = DEFAULT_URL;
    private static String username = DEFAULT_USERNAME;
    /** 数据库密码：默认为空，必须由 config/db.properties 提供 */
    private static String password = "";

    // ------------------------------------------------------------------
    // 2. 加载配置与驱动
    //    静态代码块在类第一次被使用时执行，且只执行一次。
    // ------------------------------------------------------------------
    static {
        loadConfig();
        try {
            // Class.forName 会把驱动类加载到内存并注册到 DriverManager，
            // 之后 DriverManager.getConnection 才知道该用哪个驱动连 MySQL。
            Class.forName(DRIVER);
            System.out.println("[DBUtil] 数据库驱动加载成功：" + DRIVER);
        } catch (ClassNotFoundException e) {
            // 走到这里几乎都是因为 classpath 上没有 mysql-connector-j-8.2.0.jar
            throw new ExceptionInInitializerError(
                    "数据库驱动加载失败，请确认 lib/mysql-connector-j-8.2.0.jar 已加入项目的 Libraries。原因：" + e.getMessage());
        }
    }

    /**
     * 从外部配置文件读取数据库连接信息。
     *
     * <p>为什么把密码放在代码外面：任务说明书要求不得提交密码等敏感信息。
     * 一旦密码进入代码并被提交，就会永久留在 Git 历史中。
     *
     * <p>找不到配置文件时给出明确提示，并继续使用默认的地址与用户名
     * （此时密码为空，连接会失败并提示原因，便于使用者定位问题）。
     */
    private static void loadConfig() {
        java.io.File configFile = null;
        for (String path : CONFIG_CANDIDATES) {
            java.io.File f = new java.io.File(path);
            if (f.isFile()) {
                configFile = f;
                break;
            }
        }

        if (configFile == null) {
            System.out.println("[DBUtil] 未找到数据库配置文件 config/db.properties");
            System.out.println("        请先复制模板并填写密码："
                    + "copy config\\db.properties.example config\\db.properties");
            return;
        }

        java.util.Properties props = new java.util.Properties();
        try (java.io.Reader reader = new java.io.InputStreamReader(
                new java.io.FileInputStream(configFile), java.nio.charset.StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (java.io.IOException e) {
            System.out.println("[DBUtil] 读取配置文件失败：" + e.getMessage());
            return;
        }

        url = props.getProperty("db.url", DEFAULT_URL);
        username = props.getProperty("db.username", DEFAULT_USERNAME);
        password = props.getProperty("db.password", "");

        // 只打印来源与用户名，绝不打印密码
        System.out.println("[DBUtil] 已读取数据库配置：" + configFile.getPath()
                + "（用户：" + username + "，密码：已隐藏）");
    }

    /**
     * 私有构造方法：防止外部 new DBUtil()。
     * 工具类不需要实例，所有方法都是静态的。
     */
    private DBUtil() {
    }

    // ------------------------------------------------------------------
    // 3. 对外提供的方法
    // ------------------------------------------------------------------

    /**
     * 获取一个数据库连接。
     *
     * <p>用法（推荐 try-with-resources，用完自动关闭）：
     * <pre>
     *     try (Connection conn = DBUtil.getConnection()) {
     *         // 在这里执行 SQL
     *     } catch (SQLException e) {
     *         e.printStackTrace();
     *     }
     * </pre>
     *
     * @return 数据库连接对象
     * @throws SQLException 连接失败时抛出（账号密码错误、MySQL 服务未启动、库不存在等）
     */
    public static Connection getConnection() throws SQLException {
        try {
            // 使用 loadConfig() 从配置文件读到的连接信息
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            // 换成更容易看懂的提示，再抛出异常让上层处理
            throw new SQLException("数据库连接失败，请确认 MySQL80 服务已启动、"
                    + "且 config/db.properties 中的账号密码正确。原始错误：" + e.getMessage(), e);
        }
    }

    /**
     * 统一关闭数据库资源。
     *
     * <p>关闭顺序必须是 ResultSet → Statement → Connection（与打开顺序相反），
     * 否则可能出现「连接已关闭但结果集还在用」的错误。
     *
     * <p>三个参数都允许传 null（比如只执行了 INSERT，没有 ResultSet），
     * 方法内部会先判空再关闭。
     *
     * @param conn 数据库连接，可为 null
     * @param stmt 语句对象（Statement 或 PreparedStatement），可为 null
     * @param rs   结果集，可为 null
     */
    public static void close(Connection conn, Statement stmt, ResultSet rs) {
        // 结果集
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                System.err.println("[DBUtil] 关闭 ResultSet 失败：" + e.getMessage());
            }
        }
        // 语句对象
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                System.err.println("[DBUtil] 关闭 Statement 失败：" + e.getMessage());
            }
        }
        // 连接
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("[DBUtil] 关闭 Connection 失败：" + e.getMessage());
            }
        }
    }

    /**
     * 关闭连接与语句对象的重载方法（用于没有 ResultSet 的场景，如增删改）。
     *
     * @param conn 数据库连接，可为 null
     * @param stmt 语句对象，可为 null
     */
    public static void close(Connection conn, Statement stmt) {
        close(conn, stmt, null);
    }

    // ------------------------------------------------------------------
    // 4. 自测入口
    //    写完本类后可以直接运行这个 main 方法，验证能否连上数据库。
    //    验证通过后，这个方法可以保留（不影响正式功能）。
    // ------------------------------------------------------------------

    /**
     * 连接自测：读取数据库名、MySQL 版本与三张表的记录数。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        System.out.println("=== DBUtil 连接自测 ===");

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            stmt = conn.createStatement();

            // 1) 当前连的是哪个库
            System.out.println("当前数据库：" + conn.getCatalog());

            // 2) MySQL 版本
            rs = stmt.executeQuery("SELECT VERSION()");
            if (rs.next()) {
                System.out.println("MySQL 版本：" + rs.getString(1));
            }

            // 3) 三张表各有多少条数据
            for (String table : new String[]{"user", "activity", "activity_registration"}) {
                // 表名要用反引号包起来：user 是 MySQL 保留字
                rs = stmt.executeQuery("SELECT COUNT(*) FROM `" + table + "`");
                if (rs.next()) {
                    System.out.println("表 " + table + " 共 " + rs.getInt(1) + " 条数据");
                }
            }

            System.out.println("\n结论：数据库连接正常，可以开始写实体类和 DAO 了。");
        } catch (SQLException e) {
            System.out.println("\n连接失败：" + e.getMessage());
            System.out.println("排查顺序：");
            System.out.println("  1. MySQL80 服务是否启动（net start MySQL80）");
            System.out.println("  2. 数据库 campus_activity 是否已创建（执行 db/schema.sql）");
            System.out.println("  3. DBUtil 里的账号密码是否正确");
        } finally {
            // 无论成功失败都要关闭资源
            close(conn, stmt, rs);
        }
    }
}
