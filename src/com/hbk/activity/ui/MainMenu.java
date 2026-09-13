package com.hbk.activity.ui;

import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.entity.User;
import com.hbk.activity.service.ActivityService;
import com.hbk.activity.service.AuthService;
import com.hbk.activity.service.RegistrationService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/**
 * 控制台主菜单：校园活动管理系统 V1.0 的界面层。
 *
 * <p>【本类的职责】
 * 只做两件事：<b>显示信息</b> 和 <b>读取用户输入</b>，
 * 然后把请求交给 Service 层处理，最后把结果显示出来。
 * 本类<b>不写任何业务规则</b>（那是 Service 的事），也<b>不写 SQL</b>（那是 DAO 的事）。
 *
 * <p>【程序结构】
 * <pre>
 *   main() 主循环
 *     ├─ 注册
 *     └─ 登录成功
 *          ├─ 学生菜单 studentMenu()
 *          │    浏览活动 / 活动详情 / 报名 / 我的报名 / 取消报名
 *          └─ 教师菜单 teacherMenu()
 *               查看我的活动 / 发布 / 修改 / 关闭报名 / 删除 / 查看报名名单
 * </pre>
 * 菜单用 {@code while(true)} 循环显示，选 0 时 {@code return} 回到上一层，
 * 因此学生/教师退出登录后会回到主菜单，主菜单选 0 才真正结束程序。
 *
 * <p>【运行方式】在项目根目录执行 {@code build.bat}，
 * 或直接运行本类的 main 方法。
 *
 * @author HBK组
 */
public class MainMenu {

    /**
     * 全局唯一的输入扫描器。
     * 整个程序共用一个实例：如果每个方法都 new Scanner(System.in)，
     * 会出现输入被"吞掉"的怪异问题。
     */
    private static final Scanner SCANNER = new Scanner(System.in);

    /** 日期时间输入格式：用户按 yyyy-MM-dd HH:mm 输入，例如 2026-12-20 09:00 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 角色常量，与数据库 user.role 的取值一致 */
    private static final String ROLE_TEACHER = "TEACHER";
    private static final String ROLE_STUDENT = "STUDENT";

    // 三个业务对象：菜单层需要的全部功能都在这里
    private static final AuthService AUTH_SERVICE = new AuthService();
    private static final ActivityService ACTIVITY_SERVICE = new ActivityService();
    private static final RegistrationService REGISTRATION_SERVICE = new RegistrationService();

    // ==================================================================
    // 程序入口：主菜单
    // ==================================================================

    /**
     * 程序入口。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("      校园活动管理系统 V1.0");
        System.out.println("========================================");

        // 主循环：登录成功进入角色菜单，角色菜单退出后回到这里
        while (true) {
            System.out.println("\n--- 主菜单 ---");
            System.out.println("1. 注册");
            System.out.println("2. 登录");
            System.out.println("0. 退出系统");
            int choice = readInt("请选择：");

            switch (choice) {
                case 1:
                    doRegister();
                    break;
                case 2: {
                    User user = doLogin();
                    if (user != null) {
                        // 按角色进入不同菜单：这是"学生与教师权限不同"这条约束在界面上的体现
                        if (ROLE_TEACHER.equals(user.getRole())) {
                            teacherMenu(user);
                        } else {
                            studentMenu(user);
                        }
                    }
                    break;
                }
                case 0:
                    System.out.println("感谢使用，再见！");
                    SCANNER.close();
                    return;                     // 结束 main，程序退出
                default:
                    System.out.println("没有这个选项，请重新选择。");
            }
        }
    }

    // ==================================================================
    // 注册与登录
    // ==================================================================

    /**
     * 注册流程（REQ-01）。
     */
    private static void doRegister() {
        System.out.println("\n--- 注册 ---");
        String username = readLine("请输入账号：");
        String password = readLine("请输入密码（至少6位）：");
        String name = readLine("请输入姓名：");
        System.out.println("请选择身份：1. 学生  2. 教师");
        int roleChoice = readInt("请选择：");
        String role = (roleChoice == 2) ? ROLE_TEACHER : ROLE_STUDENT;

        // Service 返回 null 表示成功，非 null 是失败原因
        String error = AUTH_SERVICE.register(username, password, name, role);
        if (error == null) {
            System.out.println("注册成功！请使用新账号登录。");
        } else {
            System.out.println("注册失败：" + error);
        }
    }

    /**
     * 登录流程（REQ-01）。
     *
     * @return 登录成功返回用户对象（含 id 与 role），失败返回 null
     */
    private static User doLogin() {
        System.out.println("\n--- 登录 ---");
        String username = readLine("请输入账号：");
        String password = readLine("请输入密码：");

        User user = AUTH_SERVICE.login(username, password);
        if (user == null) {
            // 统一提示：不区分"账号不存在"和"密码错误"，避免暴露账号是否存在
            System.out.println("登录失败：账号或密码错误");
            return null;
        }
        System.out.println("登录成功，欢迎 " + user.getName()
                + "（" + roleText(user.getRole()) + "）");
        return user;
    }

    // ==================================================================
    // 学生菜单
    // ==================================================================

    /**
     * 学生菜单（REQ-02 浏览、REQ-03 报名、REQ-04 我的报名与取消）。
     *
     * @param user 当前登录的学生
     */
    private static void studentMenu(User user) {
        while (true) {
            System.out.println("\n--- 学生菜单 ---");
            System.out.println("1. 浏览活动列表");
            System.out.println("2. 查询活动详情");
            System.out.println("3. 报名活动");
            System.out.println("4. 我的报名");
            System.out.println("5. 取消报名");
            System.out.println("0. 退出登录");
            int choice = readInt("请选择：");

            switch (choice) {
                case 1:
                    listActivities();
                    break;
                case 2:
                    showActivityDetail();
                    break;
                case 3:
                    registerActivity(user);
                    break;
                case 4:
                    myRegistrations(user);
                    break;
                case 5:
                    cancelRegistration(user);
                    break;
                case 0:
                    System.out.println("已退出登录。");
                    return;                     // 回到主菜单
                default:
                    System.out.println("没有这个选项，请重新选择。");
            }
        }
    }

    // ==================================================================
    // 教师菜单
    // ==================================================================

    /**
     * 教师菜单（REQ-05 发布与管理、REQ-06 查看报名名单）。
     *
     * @param user 当前登录的教师
     */
    private static void teacherMenu(User user) {
        while (true) {
            System.out.println("\n--- 教师菜单 ---");
            System.out.println("1. 查看我发布的活动");
            System.out.println("2. 发布活动");
            System.out.println("3. 修改活动");
            System.out.println("4. 关闭报名");
            System.out.println("5. 删除活动");
            System.out.println("6. 查看活动报名名单");
            System.out.println("0. 退出登录");
            int choice = readInt("请选择：");

            switch (choice) {
                case 1:
                    listMyActivities(user);
                    break;
                case 2:
                    publishActivity(user);
                    break;
                case 3:
                    updateActivity(user);
                    break;
                case 4:
                    closeActivity(user);
                    break;
                case 5:
                    deleteActivity(user);
                    break;
                case 6:
                    showRoster(user);
                    break;
                case 0:
                    System.out.println("已退出登录。");
                    return;                     // 回到主菜单
                default:
                    System.out.println("没有这个选项，请重新选择。");
            }
        }
    }

    // ==================================================================
    // 功能：查询类
    // ==================================================================

    /**
     * 浏览全部活动（学生，REQ-02）。
     */
    private static void listActivities() {
        printActivities(ACTIVITY_SERVICE.listAll());
    }

    /**
     * 查看某个活动的详情（学生，REQ-02）。
     *
     * <p>先列出活动列表，用户根据编号输入活动 id。
     */
    private static void showActivityDetail() {
        List<Activity> list = ACTIVITY_SERVICE.listAll();
        if (list.isEmpty()) {
            System.out.println("暂无活动。");
            return;
        }
        printActivities(list);

        Long activityId = (long) readInt("\n请输入要查看的活动编号（0 表示取消）：");
        if (activityId == 0) {
            return;
        }

        Activity activity = ACTIVITY_SERVICE.detail(activityId);
        if (activity == null) {
            System.out.println("活动不存在。");
            return;
        }

        System.out.println("\n========== 活动详情 ==========");
        System.out.println("标题　　：" + activity.getTitle());
        System.out.println("地点　　：" + activity.getLocation());
        System.out.println("开始时间：" + formatDateTime(activity.getStartTime()));
        System.out.println("结束时间：" + formatDateTime(activity.getEndTime()));
        System.out.println("状态　　：" + statusText(activity.getStatus()));
        System.out.println("内容说明：" + (activity.getDescription() == null ? "无" : activity.getDescription()));
        System.out.println("==============================");
    }

    /**
     * 教师查看自己发布的活动（REQ-05）。
     *
     * @param user 当前登录教师
     */
    private static void listMyActivities(User user) {
        printActivities(ACTIVITY_SERVICE.listByTeacher(user.getId()));
    }

    /**
     * 学生查看我的报名记录（REQ-04）。
     *
     * @param user 当前登录学生
     */
    private static void myRegistrations(User user) {
        List<ActivityRegistration> list = REGISTRATION_SERVICE.myRegistrations(user.getId());
        if (list.isEmpty()) {
            System.out.println("你还没有报名任何活动。");
            return;
        }

        System.out.println("\n共 " + list.size() + " 条报名记录：");
        System.out.println("--------------------------------------------------------------");
        for (ActivityRegistration r : list) {
            // 报名记录里只有活动id，界面要显示活动标题，所以再查一次活动
            Activity activity = ACTIVITY_SERVICE.detail(r.getActivityId());
            String title = (activity == null) ? "（活动已不存在）" : activity.getTitle();
            String location = (activity == null) ? "-" : activity.getLocation();

            System.out.println("  活动编号：" + r.getActivityId()
                    + " | " + title
                    + " | 地点：" + location
                    + " | 报名时间：" + formatDateTime(r.getRegisterTime())
                    + " | 状态：" + statusText(r.getStatus()));
        }
        System.out.println("--------------------------------------------------------------");
    }

    /**
     * 教师查看某个活动的报名名单（REQ-06）。
     *
     * @param user 当前登录教师
     */
    private static void showRoster(User user) {
        List<Activity> mine = ACTIVITY_SERVICE.listByTeacher(user.getId());
        if (mine.isEmpty()) {
            System.out.println("你还没有发布任何活动。");
            return;
        }
        printActivities(mine);

        Long activityId = (long) readInt("\n请输入要查看名单的活动编号（0 表示取消）：");
        if (activityId == 0) {
            return;
        }

        // activityRoster 内部会校验该活动是否属于当前教师
        List<ActivityRegistration> roster = REGISTRATION_SERVICE.activityRoster(activityId, user.getId());
        if (roster.isEmpty()) {
            System.out.println("该活动暂无报名记录（或该活动不属于你）。");
            return;
        }

        System.out.println("\n========== 报名名单 ==========");
        int index = 1;
        for (ActivityRegistration r : roster) {
            System.out.println("  " + (index++) + ". 学生id：" + r.getStudentId()
                    + " | 报名时间：" + formatDateTime(r.getRegisterTime())
                    + " | 状态：" + statusText(r.getStatus()));
        }
        System.out.println("有效报名人数：" + countRegistered(roster));
        System.out.println("==============================");
    }

    // ==================================================================
    // 功能：报名与取消（学生）
    // ==================================================================

    /**
     * 学生报名活动（REQ-03）。
     *
     * @param user 当前登录学生
     */
    private static void registerActivity(User user) {
        List<Activity> list = ACTIVITY_SERVICE.listAll();
        if (list.isEmpty()) {
            System.out.println("暂无活动可报名。");
            return;
        }
        printActivities(list);

        Long activityId = (long) readInt("\n请输入要报名的活动编号（0 表示取消）：");
        if (activityId == 0) {
            return;
        }

        // 业务规则（活动是否存在、是否可报名、是否重复报名）全部由 Service 判断
        String error = REGISTRATION_SERVICE.register(activityId, user.getId());
        if (error == null) {
            System.out.println("报名成功！可在【我的报名】中查看。");
        } else {
            System.out.println("报名失败：" + error);
        }
    }

    /**
     * 学生取消报名（REQ-04）。
     *
     * @param user 当前登录学生
     */
    private static void cancelRegistration(User user) {
        List<ActivityRegistration> mine = REGISTRATION_SERVICE.myRegistrations(user.getId());
        if (mine.isEmpty()) {
            System.out.println("你还没有报名任何活动。");
            return;
        }
        myRegistrations(user);

        Long activityId = (long) readInt("\n请输入要取消报名的活动编号（0 表示取消）：");
        if (activityId == 0) {
            return;
        }

        String error = REGISTRATION_SERVICE.cancel(activityId, user.getId());
        if (error == null) {
            System.out.println("已取消报名。");
        } else {
            System.out.println("取消失败：" + error);
        }
    }

    // ==================================================================
    // 功能：教师发布与管理活动（REQ-05）
    // ==================================================================

    /**
     * 教师发布活动（REQ-05）。
     *
     * @param user 当前登录教师
     */
    private static void publishActivity(User user) {
        System.out.println("\n--- 发布活动 ---");
        String title = readLine("活动标题：");
        String location = readLine("活动地点：");
        String description = readLine("活动内容说明：");

        LocalDateTime startTime = readDateTime("活动开始时间");
        if (startTime == null) {
            return;                             // 时间格式错误，放弃本次发布
        }
        LocalDateTime endTime = readDateTime("活动结束时间");
        if (endTime == null) {
            return;
        }

        Activity activity = new Activity();
        activity.setTitle(title);
        activity.setLocation(location);
        activity.setDescription(description);
        activity.setStartTime(startTime);
        activity.setEndTime(endTime);
        activity.setStatus("OPEN");             // 新发布的活动默认开放报名
        activity.setTeacherId(user.getId());    // 归属当前登录教师

        String error = ACTIVITY_SERVICE.publish(activity);
        if (error == null) {
            System.out.println("活动发布成功！");
        } else {
            System.out.println("发布失败：" + error);
        }
    }

    /**
     * 教师修改活动（REQ-05）。
     *
     * <p>交互方式：先列出自己发布的活动，用户选一个，
     * 然后逐个字段询问新值 —— <b>直接回车表示保持原值不变</b>。
     *
     * @param user 当前登录教师
     */
    private static void updateActivity(User user) {
        List<Activity> mine = ACTIVITY_SERVICE.listByTeacher(user.getId());
        if (mine.isEmpty()) {
            System.out.println("你还没有发布任何活动。");
            return;
        }
        printActivities(mine);

        Long activityId = (long) readInt("\n请输入要修改的活动编号（0 表示取消）：");
        if (activityId == 0) {
            return;
        }

        Activity activity = ACTIVITY_SERVICE.detail(activityId);
        if (activity == null) {
            System.out.println("活动不存在。");
            return;
        }

        System.out.println("（直接回车表示保持原值不变）");

        String title = readLine("标题 [" + activity.getTitle() + "]：");
        if (!title.isEmpty()) {
            activity.setTitle(title);
        }

        String location = readLine("地点 [" + activity.getLocation() + "]：");
        if (!location.isEmpty()) {
            activity.setLocation(location);
        }

        String description = readLine("内容说明 [" + activity.getDescription() + "]：");
        if (!description.isEmpty()) {
            activity.setDescription(description);
        }

        String startText = readLine("开始时间 [" + formatDateTime(activity.getStartTime()) + "]：");
        if (!startText.isEmpty()) {
            LocalDateTime startTime = parseDateTime(startText);
            if (startTime == null) {
                System.out.println("时间格式不正确，本次修改已取消。");
                return;
            }
            activity.setStartTime(startTime);
        }

        String endText = readLine("结束时间 [" + formatDateTime(activity.getEndTime()) + "]：");
        if (!endText.isEmpty()) {
            LocalDateTime endTime = parseDateTime(endText);
            if (endTime == null) {
                System.out.println("时间格式不正确，本次修改已取消。");
                return;
            }
            activity.setEndTime(endTime);
        }

        // 修改同样要经过 Service 校验（含"只能管理自己发布的活动"）
        String error = ACTIVITY_SERVICE.update(activity, user.getId());
        if (error == null) {
            System.out.println("活动修改成功！");
        } else {
            System.out.println("修改失败：" + error);
        }
    }

    /**
     * 教师关闭活动报名（REQ-05）。
     *
     * @param user 当前登录教师
     */
    private static void closeActivity(User user) {
        List<Activity> mine = ACTIVITY_SERVICE.listByTeacher(user.getId());
        if (mine.isEmpty()) {
            System.out.println("你还没有发布任何活动。");
            return;
        }
        printActivities(mine);

        Long activityId = (long) readInt("\n请输入要关闭报名的活动编号（0 表示取消）：");
        if (activityId == 0) {
            return;
        }

        String error = ACTIVITY_SERVICE.close(activityId, user.getId());
        if (error == null) {
            System.out.println("已关闭该活动的报名，学生将无法再报名。");
        } else {
            System.out.println("关闭失败：" + error);
        }
    }

    /**
     * 教师删除活动（REQ-05）。
     *
     * <p>已有学生报名的活动不允许删除，Service 会返回原因。
     *
     * @param user 当前登录教师
     */
    private static void deleteActivity(User user) {
        List<Activity> mine = ACTIVITY_SERVICE.listByTeacher(user.getId());
        if (mine.isEmpty()) {
            System.out.println("你还没有发布任何活动。");
            return;
        }
        printActivities(mine);

        Long activityId = (long) readInt("\n请输入要删除的活动编号（0 表示取消）：");
        if (activityId == 0) {
            return;
        }

        // 二次确认，避免误删
        String confirm = readLine("确认删除该活动吗？输入 y 确认，其他任意键取消：");
        if (!"y".equalsIgnoreCase(confirm)) {
            System.out.println("已取消删除操作。");
            return;
        }

        String error = ACTIVITY_SERVICE.delete(activityId, user.getId());
        if (error == null) {
            System.out.println("活动已删除。");
        } else {
            System.out.println("删除失败：" + error);
        }
    }

    // ==================================================================
    // 界面辅助方法
    // ==================================================================

    /**
     * 打印活动列表。
     *
     * @param list 活动列表
     */
    private static void printActivities(List<Activity> list) {
        if (list.isEmpty()) {
            System.out.println("暂无活动。");
            return;
        }
        System.out.println("\n共 " + list.size() + " 个活动：");
        System.out.println("--------------------------------------------------------------------------");
        for (Activity a : list) {
            System.out.println("  [" + a.getId() + "] " + a.getTitle()
                    + " | 地点：" + a.getLocation()
                    + " | " + formatDateTime(a.getStartTime()) + " ~ " + formatDateTime(a.getEndTime())
                    + " | " + statusText(a.getStatus()));
            if (a.getDescription() != null && !a.getDescription().isEmpty()) {
                System.out.println("        说明：" + a.getDescription());
            }
        }
        System.out.println("--------------------------------------------------------------------------");
    }

    /**
     * 统计名单里状态为"已报名"的记录数。
     *
     * @param roster 报名名单
     * @return 有效报名人数
     */
    private static int countRegistered(List<ActivityRegistration> roster) {
        int count = 0;
        for (ActivityRegistration r : roster) {
            if ("REGISTERED".equals(r.getStatus())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 把数据库里的英文状态转换成界面上的中文。
     *
     * <p>数据库存英文（便于程序判断），界面显示中文（便于用户理解）。
     *
     * @param status 状态英文值
     * @return 中文说明
     */
    private static String statusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "OPEN" -> "可报名";
            case "CLOSED" -> "已关闭";
            case "FINISHED" -> "已结束";
            case "REGISTERED" -> "已报名";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    /**
     * 把用户角色转换成中文。
     *
     * @param role 角色英文值
     * @return 中文说明
     */
    private static String roleText(String role) {
        return ROLE_TEACHER.equals(role) ? "教师" : "学生";
    }

    /**
     * 把时间格式化成 yyyy-MM-dd HH:mm 显示。
     *
     * @param dateTime 时间，可为 null
     * @return 格式化后的字符串
     */
    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * 提示用户输入时间并解析。
     *
     * @param label 提示文字，例如"活动开始时间"
     * @return 解析成功返回 LocalDateTime；格式错误返回 null
     */
    private static LocalDateTime readDateTime(String label) {
        String text = readLine(label + "（格式 2026-12-20 09:00）：");
        LocalDateTime result = parseDateTime(text);
        if (result == null) {
            System.out.println("时间格式不正确，请按 yyyy-MM-dd HH:mm 输入。");
        }
        return result;
    }

    /**
     * 解析 yyyy-MM-dd HH:mm 格式的时间字符串。
     *
     * @param text 时间字符串
     * @return 解析成功返回 LocalDateTime；失败返回 null
     */
    private static LocalDateTime parseDateTime(String text) {
        try {
            return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 读取一个整数输入。
     *
     * <p>为什么全部用 nextLine() 读取再自己转数字：
     * {@code nextInt()} 只读走数字，会把回车换行符留在缓冲区，
     * 导致下一次 {@code nextLine()} 立刻读到空字符串（控制台程序经典陷阱）。
     *
     * @param prompt 提示文字
     * @return 用户输入的整数；输入不是数字时提示后重新输入
     */
    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = SCANNER.nextLine().trim();
            if (line.isEmpty()) {
                System.out.println("输入不能为空，请重新输入。");
                continue;
            }
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("输入不合法，请输入数字。");
            }
        }
    }

    /**
     * 读取一行文本输入（自动去掉首尾空格）。
     *
     * @param prompt 提示文字
     * @return 用户输入的内容
     */
    private static String readLine(String prompt) {
        System.out.print(prompt);
        return SCANNER.nextLine().trim();
    }
}
