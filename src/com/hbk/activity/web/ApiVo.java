package com.hbk.activity.web;

import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.User;

import java.time.LocalDateTime;

/**
 * 接口响应视图对象（VO = View Object）。
 *
 * <p>【为什么要单独定义 VO，不直接把实体类返回给前端】
 * <ol>
 *   <li><b>安全</b>：{@code User} 实体里有 password 字段，
 *       直接序列化会把密码密文发给前端，必须过滤掉；</li>
 *   <li><b>补充计算字段</b>：活动列表要显示"发布教师姓名""已报名人数""我是否已报名"，
 *       这些字段不在 activity 表里，是查询后拼出来的；</li>
 *   <li><b>解耦</b>：数据库字段调整时，只要改 VO 的组装代码，前端接口契约不变。</li>
 * </ol>
 *
 * <p>【为什么把 5 个 VO 写在同一个文件里】
 * 它们都是"接口返回结构"这一类东西，每个只有几个字段。
 * 合成一个文件既减少文件数量，又能一眼看到接口的完整返回契约。
 * 写法是 Java 的"静态内部类"：使用时要写 {@code ApiVo.ActivityVo}。
 *
 * <p>字段全部是 public —— Gson 通过字段反射序列化，
 * 这些类只用于"输出"，不需要封装，写 public 更简洁。
 */
public final class ApiVo {

    private ApiVo() {
    }

    // ==================================================================
    // 1. 用户信息（不含密码）
    // ==================================================================

    /**
     * 用户信息（对外暴露的版本，刻意不包含 password 字段）。
     */
    public static class UserVo {
        /** 用户 id */
        public Long id;
        /** 登录账号 */
        public String username;
        /** 姓名 */
        public String name;
        /** 角色：STUDENT / TEACHER */
        public String role;

        /**
         * 由用户实体转换而来。
         *
         * @param user 用户实体
         * @return 不含密码的用户视图对象；入参为 null 时返回 null
         */
        public static UserVo from(User user) {
            if (user == null) {
                return null;
            }
            UserVo vo = new UserVo();
            vo.id = user.getId();
            vo.username = user.getUsername();
            vo.name = user.getName();
            vo.role = user.getRole();
            return vo;
        }
    }

    // ==================================================================
    // 2. 登录结果
    // ==================================================================

    /**
     * 登录成功后的返回：令牌 + 用户信息。
     * 前端把 token 存入 localStorage，之后每次请求都带上它。
     */
    public static class LoginVo {
        /** 登录令牌 */
        public String token;
        /** 当前用户信息 */
        public UserVo user;

        public LoginVo(String token, UserVo user) {
            this.token = token;
            this.user = user;
        }
    }

    // ==================================================================
    // 3. 活动（列表与详情共用）
    // ==================================================================

    /**
     * 活动视图对象。
     *
     * <p>{@code teacherName}、{@code registeredCount}、{@code joined} 三个字段
     * 是接口层查询后补充的：
     * <ul>
     *   <li>teacherName：用 teacherId 反查用户表得到；</li>
     *   <li>registeredCount：统计报名表得到（活动表不保存人数，避免冗余不一致）；</li>
     *   <li>joined：当前登录学生是否已报名，前端据此决定按钮显示"已报名"还是"立即报名"。</li>
     * </ul>
     */
    public static class ActivityVo {
        public Long id;
        public String title;
        public String description;
        public String location;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public String status;
        public Long teacherId;
        /** 发布教师姓名（关联查询得到） */
        public String teacherName;
        /** 当前有效报名人数（统计得到） */
        public int registeredCount;
        /** 当前学生是否已报名；教师身份查看时为 null */
        public Boolean joined;

        /**
         * 由活动实体转换而来（附加字段由接口层另行设置）。
         *
         * @param activity 活动实体
         * @return 活动视图对象
         */
        public static ActivityVo from(Activity activity) {
            ActivityVo vo = new ActivityVo();
            vo.id = activity.getId();
            vo.title = activity.getTitle();
            vo.description = activity.getDescription();
            vo.location = activity.getLocation();
            vo.startTime = activity.getStartTime();
            vo.endTime = activity.getEndTime();
            vo.status = activity.getStatus();
            vo.teacherId = activity.getTeacherId();
            return vo;
        }
    }

    // ==================================================================
    // 4. 我的报名
    // ==================================================================

    /**
     * 学生端"我的报名"列表项：报名记录 + 对应活动的信息。
     *
     * <p>报名表里只有 activityId，界面上要显示活动标题和地点，
     * 所以接口层会按 activityId 查出活动信息填进来。
     */
    public static class MyRegistrationVo {
        /** 报名记录 id */
        public Long registrationId;
        /** 活动 id */
        public Long activityId;
        /** 活动标题 */
        public String title;
        /** 活动地点 */
        public String location;
        /** 活动开始时间 */
        public LocalDateTime startTime;
        /** 活动结束时间 */
        public LocalDateTime endTime;
        /** 报名时间 */
        public LocalDateTime registerTime;
        /** 报名状态：REGISTERED / CANCELLED */
        public String status;
    }

    // ==================================================================
    // 5. 教师看到的报名名单
    // ==================================================================

    /**
     * 教师端报名名单项：报名记录 + 学生信息。
     *
     * <p>报名表里只有 studentId，名单上要显示学号和姓名，
     * 所以接口层会按 studentId 查出学生信息填进来。
     */
    public static class RegistrationRecordVo {
        /** 报名记录 id */
        public Long registrationId;
        /** 学生 id */
        public Long studentId;
        /** 学生账号（学号） */
        public String username;
        /** 学生姓名 */
        public String studentName;
        /** 报名时间 */
        public LocalDateTime registerTime;
        /** 报名状态：REGISTERED / CANCELLED */
        public String status;
    }
}
