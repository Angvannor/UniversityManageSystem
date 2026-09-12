package com.hbk.activity.entity;

// LocalDateTime 不在 java.lang 包里，必须显式导入才能使用
// （String、Long、Integer 属于 java.lang，可以省略 import）
import java.time.LocalDateTime;

/**
 * 活动实体类，对应数据库表 {@code activity}。
 *
 * <p>【这个类是干什么的】
 * 用 Java 对象表示数据库里的一行 activity 记录：
 * 表的一行数据 = 一个 Activity 对象，表的一个字段 = 对象的一个属性。
 * 对应报告中的需求 REQ-02（学生浏览活动）和 REQ-05（教师发布和管理活动）。
 *
 * <p>【与数据库的对应关系】（见 db/schema.sql 第 3 节）
 * <pre>
 *   activity.id          ->  Activity.id
 *   activity.title       ->  Activity.title
 *   activity.description ->  Activity.description
 *   activity.location    ->  Activity.location
 *   activity.start_time  ->  Activity.startTime     ← 数据库下划线，Java 驼峰
 *   activity.end_time    ->  Activity.endTime       ← 数据库下划线，Java 驼峰
 *   activity.status      ->  Activity.status
 *   activity.teacher_id  ->  Activity.teacherId     ← 数据库下划线，Java 驼峰
 * </pre>
 *
 * <p>【为什么属性名用驼峰、数据库列名用下划线】
 * Java 规范要求属性用小驼峰（startTime），数据库习惯用下划线（start_time），
 * 两边不需要强行统一 —— 转换工作交给 DAO 层：
 * <pre>
 *   // 从结果集读：括号里写【数据库列名】
 *   activity.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
 *   // 写回数据库：用 setObject 绑定 Java 属性的值
 *   ps.setObject(5, activity.getStartTime());
 * </pre>
 *
 * <p>【关键业务字段】
 * <ul>
 *   <li>{@code status}：OPEN 可报名 / CLOSED 已关闭 / FINISHED 已结束，
 *       只有 OPEN 状态才允许学生报名（对应报告「重要约束」）；</li>
 *   <li>{@code teacherId}：发布该活动的教师id，
 *       用于判断「教师只能管理自己发布的活动」。</li>
 * </ul>
 *
 * @author HBK组
 */
public class Activity {

    /** 活动id，对应 activity.id，主键自增 */
    private Long id;

    /** 活动标题，对应 activity.title */
    private String title;

    /** 活动内容说明，对应 activity.description，可以为空 */
    private String description;

    /** 活动地点，对应 activity.location */
    private String location;

    /** 活动开始时间，对应 activity.start_time（数据库类型 DATETIME） */
    private LocalDateTime startTime;

    /** 活动结束时间，对应 activity.end_time（数据库类型 DATETIME） */
    private LocalDateTime endTime;

    /** 活动状态，对应 activity.status：OPEN / CLOSED / FINISHED */
    private String status;

    /** 发布活动的教师id，对应 activity.teacher_id（外键指向 user.id） */
    private Long teacherId;

    /**
     * 无参构造方法，供 DAO 从结果集组装对象时使用。
     */
    public Activity() {
    }

    /**
     * 全参构造方法，方便测试时直接创建活动对象。
     *
     * @param id          活动id
     * @param title       活动标题
     * @param description 活动内容说明
     * @param location    活动地点
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @param status      状态：OPEN / CLOSED / FINISHED
     * @param teacherId   发布活动的教师id
     */
    public Activity(Long id, String title, String description, String location,
                    LocalDateTime startTime, LocalDateTime endTime,
                    String status, Long teacherId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.teacherId = teacherId;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    /**
     * 判断活动当前是否可以被报名。
     *
     * <p>菜单层用它决定显示「报名」还是「不可报名」，
     * 但业务层仍会再校验一次（防止绕过菜单直接调用方法）。
     *
     * @return true 表示活动状态为 OPEN
     */
    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    /**
     * 输出对象内容，便于调试时直接打印查看数据。
     *
     * @return 形如 Activity{id=1, title='程序设计大赛', location='A301', ...} 的字符串
     */
    @Override
    public String toString() {
        return "Activity{id=" + id
                + ", title='" + title + "'"
                + ", description='" + description + "'"
                + ", location='" + location + "'"
                + ", startTime=" + startTime
                + ", endTime=" + endTime
                + ", status='" + status + "'"
                + ", teacherId=" + teacherId
                + "}";
    }
}
