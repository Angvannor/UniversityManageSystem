/**
 * 本文件用途：验证 IDEA 数据源使用的 JDBC 连接串能否真正连上 MySQL。
 *
 * 用法（在项目根目录执行）：
 *   javac -encoding UTF-8 -d build/verify src/tools/TestJdbcConnection.java
 *   java -cp "build/verify;lib/mysql-connector-j-8.3.0.jar" TestJdbcConnection
 *
 * 验证内容：
 *   1. 驱动类 com.mysql.cj.jdbc.Driver 能否加载；
 *   2. .idea/dataSources.xml 中配置的连接串能否连上数据库；
 *   3. 打印 MySQL 版本、当前用户、现有数据库列表与字符集。
 *
 * 说明：这只是环境自检的小工具，不属于系统功能代码，正式开发时不会用到它。
 */
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestJdbcConnection {

    /** 与 .idea/dataSources.xml 中 jdbc-url 完全一致的连接串 */
    private static final String URL =
            "jdbc:mysql://localhost:3306/campus_activity"
            + "?useUnicode=true&characterEncoding=utf8"
            + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    private static final String USER = "root";
    private static final String PASSWORD = "shuaizixia070711";

    public static void main(String[] args) {
        System.out.println("=== JDBC 连接自检 ===");
        System.out.println("URL: " + URL);

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("[1/3] 驱动加载成功: com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("[1/3] 驱动加载失败，请确认 lib/mysql-connector-j-8.3.0.jar 在 classpath 上");
            return;
        }

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            System.out.println("[2/3] 数据库连接成功");

            try (ResultSet rs = stmt.executeQuery("SELECT VERSION(), CURRENT_USER(), DATABASE()")) {
                if (rs.next()) {
                    System.out.println("      MySQL 版本 : " + rs.getString(1));
                    System.out.println("      当前用户   : " + rs.getString(2));
                    System.out.println("      当前数据库 : " + rs.getString(3));
                }
            }

            System.out.println("[3/3] 字符集与现有数据库：");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT @@character_set_database, @@collation_database")) {
                if (rs.next()) {
                    System.out.println("      字符集 : " + rs.getString(1)
                            + " / " + rs.getString(2));
                }
            }
            try (ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
                while (rs.next()) {
                    System.out.println("      - " + rs.getString(1));
                }
            }
            System.out.println("\n结论：IDEA 数据源的连接参数可用。");
        } catch (Exception e) {
            System.out.println("[2/3] 连接失败: " + e.getMessage());
            System.out.println("\n排查建议：");
            System.out.println("  1. 确认 MySQL80 服务已启动（net start MySQL80）");
            System.out.println("  2. 确认数据库 campus_activity 已创建（执行 db/schema.sql）");
            System.out.println("  3. 确认密码正确，且 MySQL 允许 root 从 localhost 登录");
        }
    }
}
