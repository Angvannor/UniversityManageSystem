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
 *   activity_registration.status        ->  ActivityRegistration.status
 * </pre>
 *
 * <p>【设计说明：为什么取消报名不删除记录】
 * 数据库里的 (activity_id, student_id) 有唯一约束，所以同一学生对同一活动
 * 只能存在一行记录。取消报名时把 status 改成 CANCELLED 而不是 DELETE，
 * 好处是：
 * <ol>
 *   <li>保留报名痕迹，便于查数据和写报告；</li>
 *   <li>学生想重新报名时，直接把这行的 status 改回 REGISTERED 即可，
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

    /** 报名状态，对应 status：REGISTERED 已报名 / CANCELLED 已取消 */
    private String status;

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
     * @param status       报名状态：REGISTERED / CANCELLED
     */
    public ActivityRegistration(Long id, Long activityId, Long studentId,
                                LocalDateTime registerTime, String status) {
        this.id = id;
        this.activityId = activityId;
        this.studentId = studentId;
        this.registerTime = registerTime;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 判断这条报名记录是否仍然有效（未被取消）。
     *
     * <p>业务层统计报名人数时用它过滤；学生再次报名时也靠它判断
     * 「是新增记录还是把已取消的记录改回已报名」。
     *
     * @return true 表示状态为 REGISTERED
     */
    public boolean isRegistered() {
        return "REGISTERED".equals(status);
    }

    /**
     * 输出对象内容，便于调试。
     *
     * @return 形如 ActivityRegistration{id=1, activityId=2, studentId=3, status='REGISTERED'} 的字符串
     */
    @Override
    public String toString() {
        return "ActivityRegistration{id=" + id
                + ", activityId=" + activityId
                + ", studentId=" + studentId
                + ", registerTime=" + registerTime
                + ", status='" + status + "'"
                + "}";
    }
}
