package com.hbk.activity.web;

import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;
import com.hbk.activity.entity.User;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 把数据库里的英文状态转换成界面上的中文（V2.0 新增）。
     *
     * <p>【为什么由后端返回中文，而不是让前端自己映射】
     * 三种角色（学生端、教师端、管理员端）以及控制台都要显示同一套状态文案，
     * 分散在四处各写一遍很容易改漏一处，出现"同一个状态在两个页面叫法不同"。
     * 统一在后端算好，前端直接显示。
     *
     * @param status 状态英文值（活动状态或报名状态）
     * @return 中文说明；无法识别时原样返回，便于排查
     */
    public static String statusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            // 活动状态
            case "OPEN" -> "可报名";
            case "CLOSED" -> "已关闭";
            case "FINISHED" -> "已结束";
            // 报名状态（V2.0 的五种）
            case ActivityRegistration.STATUS_PENDING_REVIEW -> "待老师审核";
            case ActivityRegistration.STATUS_CONFIRMED -> "已确认参加";
            case ActivityRegistration.STATUS_WAITLISTED -> "候补中";
            case ActivityRegistration.STATUS_REJECTED -> "未通过";
            case ActivityRegistration.STATUS_CANCELLED -> "已取消";
            // 账号状态
            case User.STATUS_ACTIVE -> "可用";
            case User.STATUS_DISABLED -> "已停用";
            default -> status;
        };
    }

    // ==================================================================
    // 1. 用户信息（不含密码）
    // ==================================================================

    /**
     * 用户信息（对外暴露的版本，刻意不包含 password 字段）。
     *
     * <p>V2.0 起同时用于「当前登录用户」和「管理员的用户列表」两个场景 ——
     * 它本来就不含 password，直接复用即可，没必要再单独定义一份几乎一样的
     * AdminUserVo（重复定义反而容易改漏一处）。
     */
    public static class UserVo {
        /** 用户 id */
        public Long id;
        /** 登录账号 */
        public String username;
        /** 姓名 */
        public String name;
        /** 角色：STUDENT / TEACHER / ADMIN */
        public String role;
        /** V2.0 新增：账号状态 ACTIVE / DISABLED */
        public String status;
        /** V2.0 新增：账号状态的中文说明（"可用" / "已停用"） */
        public String statusText;

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
            vo.status = user.getStatus();
            vo.statusText = statusText(user.getStatus());
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
        /** V2.0 新增：可接待人数上限；null 表示不限制人数 */
        public Integer capacity;
        /** V2.0 新增：参加条件（自由文本）；null 表示无特殊条件 */
        public String eligibility;
        public String status;
        /** 活动状态的中文说明 */
        public String statusText;
        public Long teacherId;
        /** 发布教师姓名（关联查询得到） */
        public String teacherName;
        /** 当前"已报名"人数（待审核 + 候补 + 正式参加） */
        public int registeredCount;
        /** V2.0 新增：已正式参加人数（与 capacity 比较即可判断是否已满） */
        public int confirmedCount;
        /** V2.0 新增：候补人数 */
        public int waitlistedCount;
        /** 当前学生是否已"占位"报名；教师/管理员身份查看时为 null */
        public Boolean joined;
        /** V2.0 新增：当前学生自己的报名状态（未报名时为 null），学生端据此显示状态提示 */
        public String myStatus;
        /** V2.0 新增：当前学生自己报名状态的中文说明 */
        public String myStatusText;

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
            vo.capacity = activity.getCapacity();
            vo.eligibility = activity.getEligibility();
            vo.status = activity.getStatus();
            vo.statusText = statusText(activity.getStatus());
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
        /** V2.0 新增：审核时间（也是进入候补队列的时间）；未审核时为 null */
        public LocalDateTime reviewTime;
        /** 报名状态：PENDING_REVIEW / CONFIRMED / WAITLISTED / REJECTED / CANCELLED */
        public String status;
        /** V2.0 新增：报名状态的中文说明（"待老师审核" / "候补中" / …） */
        public String statusText;
        /** V2.0 新增：活动的人数上限；null 表示不限制 */
        public Integer capacity;
        /** V2.0 新增：活动已正式参加人数 */
        public int confirmedCount;
        /** V2.0 新增：该活动的参加条件；null 表示无特殊条件 */
        public String eligibility;

        /**
         * 由报名记录实体转换而来（活动相关字段由接口层补全）。
         *
         * @param registration 报名记录实体
         * @return 我的报名视图对象
         */
        public static MyRegistrationVo from(ActivityRegistration registration) {
            MyRegistrationVo vo = new MyRegistrationVo();
            vo.registrationId = registration.getId();
            vo.activityId = registration.getActivityId();
            vo.registerTime = registration.getRegisterTime();
            vo.reviewTime = registration.getReviewTime();
            vo.status = registration.getStatus();
            vo.statusText = statusText(registration.getStatus());
            return vo;
        }
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
        /** V2.0 新增：审核时间（候补队列按它升序） */
        public LocalDateTime reviewTime;
        /** 报名状态（V2.0 五种之一） */
        public String status;
        /** V2.0 新增：报名状态的中文说明 */
        public String statusText;

        /**
         * 由报名记录实体转换而来（学生相关字段由接口层补全）。
         *
         * @param registration 报名记录实体
         * @return 名单项视图对象
         */
        public static RegistrationRecordVo from(ActivityRegistration registration) {
            RegistrationRecordVo vo = new RegistrationRecordVo();
            vo.registrationId = registration.getId();
            vo.studentId = registration.getStudentId();
            vo.registerTime = registration.getRegisterTime();
            vo.reviewTime = registration.getReviewTime();
            vo.status = registration.getStatus();
            vo.statusText = statusText(registration.getStatus());
            return vo;
        }
    }

    // ==================================================================
    // 6. 教师端报名名单（V2.0 新增：按状态分组）
    // ==================================================================

    /**
     * 教师端报名名单：三个分组 + 三个计数 + 人数上限。
     *
     * <p>【为什么不直接返回一个大列表让前端自己分组】
     * 教师最关心的是"还有几条要审"和"现在满没满"，这两个数字必须准确。
     * 如果前端自己数，一旦分组逻辑写错，教师会看到一个和实际不符的数字
     * （例如把已取消的也算进去）。由后端一次算好并返回，前端只负责展示。
     *
     * <p>只返回<b>占位</b>的三种状态：待审核 / 候补 / 正式参加。
     * 已取消与未通过不占名额，放在名单里只会干扰教师判断。
     */
    public static class RosterVo {
        /** 活动 id */
        public Long activityId;
        /** 活动标题 */
        public String activityTitle;
        /** 人数上限；null 表示不限制人数 */
        public Integer capacity;
        /** 已正式参加人数（与 capacity 比较即可判断是否已满） */
        public int confirmedCount;
        /** 候补人数 */
        public int waitlistedCount;
        /** 待审核人数 */
        public int pendingReviewCount;
        /** 是否已经满员（未设上限时恒为 false），前端据此禁用"递补"按钮 */
        public boolean full;

        /** 待审核分组（教师要点"通过/驳回"的就是这些） */
        public List<RegistrationRecordVo> pendingReview;
        /** 候补分组，**按进入候补的时间升序**（先进入的排前面） */
        public List<RegistrationRecordVo> waitlisted;
        /** 正式参加分组 */
        public List<RegistrationRecordVo> confirmed;
    }
}
