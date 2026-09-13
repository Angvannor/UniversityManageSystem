package com.hbk.activity.service;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.dao.RegistrationDAO;
import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.ActivityRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * 活动报名业务类：负责学生报名与取消报名的业务规则
 * （REQ-03 报名、REQ-04 查看与取消报名、REQ-06 教师查看报名名单）。
 *
 * <p>【本类是报告"重要约束"最集中的落点】
 * <pre>
 *   ① 同一学生不能重复报名同一活动        -> register() 第 3 步
 *   ② 只有处于可报名状态的活动才能报名    -> register() 第 2 步
 *   ③ 取消报名后记录保留、可重新报名      -> register() 第 4 步 + cancel()
 *   ④ 教师只能查看自己发布活动的报名名单  -> activityRoster()
 * </pre>
 *
 * <p>【状态取值说明】必须与数据库中的取值完全一致（大写）：
 * <pre>
 *   activity.status              : OPEN / CLOSED / FINISHED
 *   activity_registration.status : REGISTERED / CANCELLED
 * </pre>
 *
 * <p>【返回值约定】与其他 Service 保持一致：
 * null 表示操作成功；非空字符串表示失败原因（菜单层直接打印给用户）。
 *
 * @author HBK组
 */
public class RegistrationService {

    /** 活动状态：开放报名 */
    private static final String STATUS_OPEN = "OPEN";

    /** 报名状态：已报名 */
    private static final String STATUS_REGISTERED = "REGISTERED";

    /** 报名状态：已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** 报名表数据访问对象 */
    private final RegistrationDAO registrationDAO = new RegistrationDAO();

    /**
     * 活动表数据访问对象。
     * 报名前要确认活动存在且处于可报名状态，
     * 查看报名名单时要校验活动归属，因此需要用到活动 DAO。
     */
    private final ActivityDAO activityDAO = new ActivityDAO();

    // ==================================================================
    // 一、学生报名（REQ-03）
    // ==================================================================

    /**
     * 学生报名活动。
     *
     * <p>按顺序执行以下业务校验，任一条不通过就返回原因：
     * <ol>
     *   <li>活动必须存在；</li>
     *   <li>活动状态必须是 OPEN（未关闭、未结束）；</li>
     *   <li>不能重复报名：已有 REGISTERED 记录则拒绝；</li>
     *   <li>若曾取消过（存在 CANCELLED 记录），复用该行把状态改回 REGISTERED，
     *       而不是新增记录 —— 因为 (activity_id, student_id) 上有唯一约束，
     *       同一对组合只允许存在一行；</li>
     *   <li>首次报名则新增一条记录。</li>
     * </ol>
     *
     * @param activityId 活动id
     * @param studentId  学生id
     * @return 成功返回 null；失败返回原因
     */
    public String register(Long activityId, Long studentId) {
        // 1. 活动必须存在
        Activity activity = activityDAO.findById(activityId);
        if (activity == null) {
            return "活动不存在";
        }

        // 2. 活动必须处于可报名状态。
        //    比较字符串必须用 equals：== 比较的是对象地址，不是内容。
        //    写成 常量.equals(变量) 可以避免变量为 null 时的空指针异常。
        if (!STATUS_OPEN.equals(activity.getStatus())) {
            return "该活动已关闭报名，无法报名";
        }

        // 3. 查询该学生对本次活动的报名记录（含已取消的）
        ActivityRegistration existing =
                registrationDAO.findByActivityAndStudent(activityId, studentId);

        if (existing != null && STATUS_REGISTERED.equals(existing.getStatus())) {
            // 已报名：拒绝重复报名（报告重要约束①）
            return "你已经报名过该活动，不能重复报名";
        }

        if (existing != null) {
            // 4. 曾经取消过：复用原来那一行，把状态改回 REGISTERED
            int rows = registrationDAO.updateStatus(activityId, studentId, STATUS_REGISTERED);
            return rows > 0 ? null : "重新报名失败，请稍后重试";
        }

        // 5. 首次报名：新增一条报名记录
        ActivityRegistration registration = new ActivityRegistration();
        registration.setActivityId(activityId);
        registration.setStudentId(studentId);
        registration.setStatus(STATUS_REGISTERED);
        int rows = registrationDAO.insert(registration);
        return rows > 0 ? null : "报名失败，请稍后重试";
    }

    // ==================================================================
    // 二、学生取消报名（REQ-04）
    // ==================================================================

    /**
     * 学生取消报名。
     *
     * <p>【设计决定】取消报名<b>不检查活动状态</b>。
     * 活动关闭报名只是不允许"再报名"，学生想退出已经报名的活动应当允许，
     * 否则学生会被"困"在一个已关闭的活动中。
     *
     * <p>【实现方式】把报名记录的 status 改为 CANCELLED，而不是删除记录。
     * 好处是保留报名痕迹，并且学生重新报名时可以复用这一行
     * （见 register() 第 4 步）。
     *
     * <p>注意：改的是<b>报名记录</b>的状态，不是活动的状态 ——
     * 取消一个报名不应该影响其他学生继续报名该活动。
     *
     * @param activityId 活动id
     * @param studentId  学生id
     * @return 成功返回 null；失败返回原因
     */
    public String cancel(Long activityId, Long studentId) {
        ActivityRegistration existing =
                registrationDAO.findByActivityAndStudent(activityId, studentId);

        // 没有记录，或者记录已经是 CANCELLED，都视为"没有可取消的报名"
        if (existing == null || !STATUS_REGISTERED.equals(existing.getStatus())) {
            return "你尚未报名该活动，无法取消";
        }

        // 把报名记录的状态改为已取消，并持久化到数据库
        int rows = registrationDAO.updateStatus(activityId, studentId, STATUS_CANCELLED);
        return rows > 0 ? null : "取消报名失败，请稍后重试";
    }

    // ==================================================================
    // 三、查询（REQ-04、REQ-06）
    // ==================================================================

    /**
     * 查询某个学生的全部报名记录（学生端"我的报名"，REQ-04）。
     *
     * <p>直接转发给 DAO：没有业务规则需要校验。
     * 返回的列表同时包含已报名和已取消的记录，由菜单层用状态区分展示。
     *
     * @param studentId 学生id
     * @return 报名记录列表（可能为空列表，不会是 null）
     */
    public List<ActivityRegistration> myRegistrations(Long studentId) {
        return registrationDAO.findByStudentId(studentId);
    }

    /**
     * 查询某个活动的报名名单（教师端查看报名情况，REQ-06）。
     *
     * <p>先校验活动归属：只有活动的发布教师才能查看报名名单。
     * 校验不通过时返回<b>空列表</b>（本方法返回类型是 List，无法返回错误文字；
     * 菜单层会提示"没有报名记录或无权查看"）。
     *
     * <p>注意不能用 {@code ==} 比较 Long，要用 {@code equals}。
     *
     * @param activityId 活动id
     * @param teacherId  当前登录教师id
     * @return 报名记录列表；活动不存在或不属于该教师时返回空列表
     */
    public List<ActivityRegistration> activityRoster(Long activityId, Long teacherId) {
        Activity activity = activityDAO.findById(activityId);

        // 活动不存在，或不属于当前教师 → 不返回任何数据
        if (activity == null || !activity.getTeacherId().equals(teacherId)) {
            return new ArrayList<>();
        }

        return registrationDAO.findByActivityId(activityId);
    }
}
