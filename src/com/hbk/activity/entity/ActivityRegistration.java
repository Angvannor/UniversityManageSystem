package com.hbk.activity.entity;

// LocalDateTime 需要显式导入
import java.time.LocalDateTime;

/**
 * 活动报名实体类，对应数据库表 {@code activity_registration}。
 *
 * <p>【这个类是干什么的】
 * 用 Java 对象表示数据库里的一行报名记录，也就是「某个学生报名了某个活动」这件事。
 * 对应报告中的需求 REQ-03（报名活动）、REQ-04（取消报名）、
 * REQ-06（教师查看报名情况）。
 *
 * <p>【与数据库的对应关系】（见 db/schema.sql 第 4 节）
 * <pre>
 *   activity_registration.id            ->  ActivityRegistration.id
 *   activity_registration.activity_id   ->  ActivityRegistration.activityId
 *   activity_registration.student_id    ->  ActivityRegistration.studentId
 *   activity_registration.register_time ->  ActivityRegistration.registerTime
 *   activity_registration.review_time   ->  ActivityRegistration.reviewTime   （V2.0 新增，可为 NULL）
 *   activity_registration.status        ->  ActivityRegistration.status
 * </pre>
 *
 * <p>【V2.0 的关键变化：status 由 2 态扩展为 5 态】
 * 教师访谈 T6 说「没通过说明不符合这次活动的参加条件；通过以后还要看当时有没有位置，
 * 并不代表一定已经正式参加」——这说明「审核通过」与「正式参加」是两件事，
 * 所以原来的 REGISTERED / CANCELLED 两态不够用了，改为：
 * <pre>
 *   PENDING_REVIEW  待审核    学生刚提交报名，等教师判定是否符合参加条件
 *   CONFIRMED       正式参加  审核通过，且当时有名额
 *   WAITLISTED      候补      审核通过，但名额已满，按进入候补的时间排队
 *   REJECTED        未通过    教师判定不符合参加条件
 *   CANCELLED       已取消    学生主动取消（保留记录，可重新报名）
 * </pre>
 * 对应的常量见本类的 STATUS_xxx，取值必须与 db/schema.sql 完全一致。
 *
 * <p>【review_time 为什么必须单独存】
 * 教师访谈 T2 要求「候补要有明确先后…按进入候补的时间处理，先进入的人排在前面」。
 * 「进入候补的时间」是<b>教师审核通过的那一刻</b>，不是学生报名的时刻
 * （学生可能很早就报名，教师过两天才审）。两个时间可能差很远，所以必须分开存。
 * 候补排序一律用 reviewTime 升序，<b>不能</b>用 registerTime，否则顺序会排反。
 *
 * <p>【设计说明：为什么取消报名不删除记录】
 * 数据库里的 (activity_id, student_id) 有唯一约束，所以同一学生对同一活动
 * 只能存在一行记录。取消报名时把 status 改成 CANCELLED 而不是 DELETE，
 * 好处是：
 * <ol>
 *   <li>保留报名痕迹，便于查数据和写报告；</li>
 *   <li>学生想重新报名时，直接把这行的 status 改成 PENDING_REVIEW 即可，
 *       不会与唯一约束冲突（这也是不删除记录的关键原因）。</li>
 * </ol>
 *
 * <p>【关于类名】
 * Java 要求文件名必须与 public 类名完全一致。类名应该用驼峰写法
 * {@code ActivityRegistration}，不能写成 {@code Activity_registration}
 * （下划线不属于类名规范）。
 *
 * @author HBK组
 */
public class ActivityRegistration {

    /** 报名记录id，对应 id，主键自增 */
    private Long id;

    /** 活动id，对应 activity_id（外键指向 activity.id） */
    private Long activityId;

    /** 学生id，对应 student_id（外键指向 user.id） */
    private Long studentId;

    /** 报名时间，对应 register_time，数据库默认取当前时间 */
    private LocalDateTime registerTime;

    /**
     * 审核时间，对应 review_time（V2.0 新增，可为 NULL）。
     *
     * <p>教师审核该条报名的那一刻。它<b>同时</b>是「进入候补队列的时间」：
     * 审核通过时若名额已满，记录转成 WAITLISTED，这个时间就是它在候补队列里的排队依据。
     * 还没被审核的记录该字段为 null。
     */
    private LocalDateTime reviewTime;

    /** 报名状态，对应 status，取值见本类的 STATUS_xxx 常量（5 种） */
    private String status;

    // ------------------------------------------------------------------
    // 报名状态常量：取值必须与 db/schema.sql 中 activity_registration.status 一致。
    //
    // 为什么抽成常量：状态字符串要在 DAO、Service、菜单层、接口层反复使用，
    // 到处手写字符串很容易拼错（例如 WAITLISTED 写成 WAITLISTEDD），
    // 而且编译器不会报错——只在运行时表现为"判断永远不成立"，极难排查。
    // ------------------------------------------------------------------

    /** 报名状态：待审核（学生刚提交，等教师判定是否符合参加条件） */
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";

    /** 报名状态：正式参加（审核通过，且当时有名额） */
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    /** 报名状态：候补（审核通过但名额已满，按进入候补的时间排队） */
    public static final String STATUS_WAITLISTED = "WAITLISTED";

    /** 报名状态：未通过（教师判定不符合参加条件） */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 报名状态：已取消（学生主动取消，保留记录可重新报名） */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * 无参构造方法，供 DAO 从结果集组装对象时使用。
     */
    public ActivityRegistration() {
    }

    /**
     * 全参构造方法，方便测试时直接创建报名对象。
     *
     * @param id           报名记录id
     * @param activityId   活动id
     * @param studentId    学生id
     * @param registerTime 报名时间
     * @param reviewTime   审核时间（未审核传 null）
     * @param status       报名状态：见 STATUS_xxx 常量
     */
    public ActivityRegistration(Long id, Long activityId, Long studentId,
                                LocalDateTime registerTime, LocalDateTime reviewTime,
                                String status) {
        this.id = id;
        this.activityId = activityId;
        this.studentId = studentId;
        this.registerTime = registerTime;
        this.reviewTime = reviewTime;
        this.status = status;
    }

    // ------------------------------------------------------------------
    // getter / setter
    // ------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public LocalDateTime getRegisterTime() {
        return registerTime;
    }

    public void setRegisterTime(LocalDateTime registerTime) {
        this.registerTime = registerTime;
    }

    public LocalDateTime getReviewTime() {
        return reviewTime;
    }

    public void setReviewTime(LocalDateTime reviewTime) {
        this.reviewTime = reviewTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // ------------------------------------------------------------------
    // 状态判断方法。
    // 统一写成「常量.equals(status)」：即使 status 为 null 也不会抛空指针异常
    // （写成 status.equals(...) 就会）。业务层尽量调用这些方法，
    // 不要在 Service 里再手写字符串比较。
    // ------------------------------------------------------------------

    /** @return true 表示待审核 */
    public boolean isPendingReview() {
        return STATUS_PENDING_REVIEW.equals(status);
    }

    /** @return true 表示已正式参加 */
    public boolean isConfirmed() {
        return STATUS_CONFIRMED.equals(status);
    }

    /** @return true 表示在候补队列中 */
    public boolean isWaitlisted() {
        return STATUS_WAITLISTED.equals(status);
    }

    /** @return true 表示被教师判定为未通过 */
    public boolean isRejected() {
        return STATUS_REJECTED.equals(status);
    }

    /** @return true 表示学生已主动取消 */
    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    /**
     * 判断这条记录当前是否「占着报名资格」。
     *
     * <p>占位的三种状态是：待审核、候补、正式参加。
     * 只要处于其中之一，学生就<b>不能</b>再次报名同一活动
     * （否则一个人能提交无数条）。
     *
     * <p>反过来，处于 CANCELLED（学生自己取消）或 REJECTED（教师驳回）时，
     * 记录不占资格，学生可以重新报名 —— 重新报名时<b>复用这一行</b>，
     * 把状态改回 PENDING_REVIEW，而不是新增记录
     * （因为 (activity_id, student_id) 上有唯一约束，同一对组合只能有一行）。
     *
     * @return true 表示该记录占着报名资格
     */
    public boolean occupiesSlot() {
        return isPendingReview() || isWaitlisted() || isConfirmed();
    }

    /**
     * 输出对象内容，便于调试。
     *
     * @return 形如 ActivityRegistration{id=1, activityId=2, studentId=3, status='CONFIRMED'} 的字符串
     */
    @Override
    public String toString() {
        return "ActivityRegistration{id=" + id
                + ", activityId=" + activityId
                + ", studentId=" + studentId
                + ", registerTime=" + registerTime
                + ", reviewTime=" + reviewTime
                + ", status='" + status + "'"
                + "}";
    }
}
