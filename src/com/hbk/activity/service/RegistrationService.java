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
 *   ① 同一学生不能重复报名同一活动        -> register() 第 4 步
 *   ② 只有处于可报名状态的活动才能报名    -> register() 第 2 步
 *   ③ 取消报名后记录保留、可重新报名      -> register() 第 5 步 + cancel()
 *   ④ 教师只能查看自己发布活动的报名名单  -> activityRoster() / review() / promote()
 *   ⑤ 审核通过 ≠ 正式参加（V2.0 新增）    -> review()
 *   ⑥ 候补按进入候补的时间排队（V2.0 新增）-> promote() + DAO 的排序
 * </pre>
 *
 * <p>【状态取值说明】必须与数据库中的取值完全一致（大写）：
 * <pre>
 *   activity.status              : OPEN / CLOSED / FINISHED
 *   activity_registration.status : PENDING_REVIEW / CONFIRMED / WAITLISTED
 *                                  / REJECTED / CANCELLED
 * </pre>
 * 报名状态的取值请一律使用 {@link ActivityRegistration} 里的 STATUS_xxx 常量，
 * 不要在本类里重新定义一份，否则两处容易改漏一处而不一致。
 *
 * <p>【返回值约定】与其他 Service 保持一致：
 * null 表示操作成功；非空字符串表示失败原因（菜单层直接打印给用户）。
 *
 * <p>⚠️【接口层的错误码依赖这些提示语的措辞】
 * Web 接口层是按提示语里的关键词来映射错误码的（见 RegistrationApi）。
 * 修改下面这些提示语时，必须同步检查接口层的映射，否则前端会收到错误的错误码：
 * <pre>
 *   含"不能重复报名"          -> 3001
 *   含"已关闭报名"            -> 3003
 *   含"尚未报名"              -> 3004
 *   含"不是待审核"或"不是候补" -> 3005（当前状态不允许该操作）
 *   含"名额已满"              -> 3006（没有空余名额）
 *   含"活动不存在"            -> 404
 *   含"只能"                  -> 403
 * </pre>
 *
 * @author HBK组
 */
public class RegistrationService {

    /** 活动状态：开放报名 */
    private static final String STATUS_OPEN = "OPEN";

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
     * 学生报名活动（V2.0：报名后进入「待审核」，不再直接算数）。
     *
     * <p>按顺序执行以下业务校验，任一条不通过就返回原因：
     * <ol>
     *   <li>活动必须存在；</li>
     *   <li>活动状态必须是 OPEN（未关闭、未结束）；</li>
     *   <li>不能重复报名：已有「占位」状态的记录则拒绝
     *       （占位 = 待审核 / 候补 / 正式参加，见 {@link ActivityRegistration#occupiesSlot()}）；</li>
     *   <li>若曾取消过（CANCELLED）或被驳回（REJECTED），复用该行把状态改回
     *       PENDING_REVIEW，而不是新增记录 —— 因为 (activity_id, student_id) 上有唯一约束，
     *       同一对组合只允许存在一行；</li>
     *   <li>首次报名则新增一条记录。</li>
     * </ol>
     *
     * <p>【为什么初始状态是「待审核」而不是「正式参加」】
     * 教师访谈 T6 说「没通过说明不符合这次活动的参加条件」，
     * 说明报名之后需要有人判定是否符合条件 —— 这个判定由负责该活动的教师做。
     * 按最小假设 A7，本轮所有报名都统一先进入待审核，不做"无条件活动跳过审核"的特例。
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

        // 3. 查询该学生对本次活动的报名记录（含已取消、已驳回的）
        ActivityRegistration existing =
                registrationDAO.findByActivityAndStudent(activityId, studentId);

        if (existing != null && existing.occupiesSlot()) {
            // 处于待审核 / 候补 / 正式参加：拒绝重复报名（报告重要约束①）
            return "你已经报名过该活动，不能重复报名";
        }

        if (existing != null) {
            // 4. 曾取消或被驳回：复用原来那一行，回到待审核。
            //    必须把 review_time 一并清成 null —— 否则上一轮的审核时间会残留，
            //    学生在新一轮里的排队位置就错了。
            int rows = registrationDAO.updateStatusAndReviewTime(
                    activityId, studentId, ActivityRegistration.STATUS_PENDING_REVIEW, null);
            return rows > 0 ? null : "重新报名失败，请稍后重试";
        }

        // 5. 首次报名：新增一条报名记录，初始状态为待审核
        ActivityRegistration registration = new ActivityRegistration();
        registration.setActivityId(activityId);
        registration.setStudentId(studentId);
        registration.setStatus(ActivityRegistration.STATUS_PENDING_REVIEW);
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

        // 没有记录，或者记录已经处于"不占位"的状态（已取消 / 未通过），
        // 都视为"没有可取消的报名"。
        // 注意这里用的是 occupiesSlot() 而不是判断某个具体状态：
        // V2.0 里"占位"的一共有三种（待审核 / 候补 / 正式参加），
        // 三种都应当允许学生取消 —— 学生临时去不了，不管审核到哪一步都该能退出。
        if (existing == null || !existing.occupiesSlot()) {
            return "你尚未报名该活动，无法取消";
        }

        // 把报名记录的状态改为已取消，并持久化到数据库。
        // 这里有意不清理 review_time：它记录的是"最后一次被处理的时间"，
        // 保留下来便于追溯；学生重新报名时 register() 会把它清成 null。
        int rows = registrationDAO.updateStatus(
                activityId, studentId, ActivityRegistration.STATUS_CANCELLED);
        return rows > 0 ? null : "取消报名失败，请稍后重试";
    }

    // ==================================================================
    // 三、教师审核与候补递补（V2.0 新增，US-07 / US-09）
    // ==================================================================

    /**
     * 教师审核一条报名：通过或驳回（US-07）。
     *
     * <p>【这是本轮的核⼼业务规则】
     * 教师访谈 T6 说：「没通过说明不符合这次活动的参加条件；
     * <b>通过以后还要看当时有没有位置，并不代表一定已经正式参加</b>。」
     * 所以"通过"并不等于"正式参加"，还要再看名额：
     * <pre>
     *   驳回                          -> REJECTED（未通过）
     *   通过 且 有名额                 -> CONFIRMED（正式参加）
     *   通过 但 名额已满               -> WAITLISTED（候补）
     *   通过 且 活动未设人数上限       -> CONFIRMED（不限制人数，直接算正式参加）
     * </pre>
     *
     * <p>【为什么两种结果都要写 review_time】
     * review_time 既是审核时间，也是候补队列的排序依据（见 {@link #promote}）。
     * 转成候补的记录必须带上"进入候补的时刻"，否则排队顺序无法确定。
     *
     * <p>【为什么只能审 PENDING_REVIEW 的记录】
     * 已经确认或已在候补的记录再被"审核"一次，会把名额算乱
     * （例如把一条 CONFIRMED 再审成 WAITLISTED，等于凭空多出一个空位）。
     * 因此状态不对时直接拒绝，返回含"不是待审核"的提示（接口层映射为错误码 3005）。
     *
     * @param activityId 活动id
     * @param studentId  被审核的学生id
     * @param teacherId  当前登录教师id
     * @param approve    true 表示通过，false 表示驳回
     * @return 成功返回 null；失败返回原因
     */
    public String review(Long activityId, Long studentId, Long teacherId, boolean approve) {
        // 1. 活动必须存在，而且必须是这位教师发布的
        Activity activity = activityDAO.findById(activityId);
        if (activity == null) {
            return "活动不存在";
        }
        if (!activity.getTeacherId().equals(teacherId)) {
            return "只能审核自己发布活动的报名";
        }

        // 2. 报名记录必须存在，且处于待审核状态
        ActivityRegistration registration =
                registrationDAO.findByActivityAndStudent(activityId, studentId);
        if (registration == null) {
            return "该学生没有报名这个活动";
        }
        if (!registration.isPendingReview()) {
            return "该报名当前状态不是待审核，无法审核";
        }

        // 3. 驳回：直接置为未通过
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (!approve) {
            int rows = registrationDAO.updateStatusAndReviewTime(
                    activityId, studentId, ActivityRegistration.STATUS_REJECTED, now);
            return rows > 0 ? null : "审核失败，请稍后重试";
        }

        // 4. 通过：要再判断有没有名额
        String targetStatus;
        if (activity.hasCapacityLimit()) {
            // 只有"正式参加"的人占名额，候补的人不占
            int confirmed = registrationDAO.countByStatus(
                    activityId, ActivityRegistration.STATUS_CONFIRMED);
            targetStatus = confirmed < activity.getCapacity()
                    ? ActivityRegistration.STATUS_CONFIRMED     // 还有位置
                    : ActivityRegistration.STATUS_WAITLISTED;   // 满了，排队候补
        } else {
            // 活动没设人数上限 -> 不限制人数，通过即为正式参加
            targetStatus = ActivityRegistration.STATUS_CONFIRMED;
        }

        int rows = registrationDAO.updateStatusAndReviewTime(activityId, studentId, targetStatus, now);
        return rows > 0 ? null : "审核失败，请稍后重试";
    }

    /**
     * 教师把候补名额递补给某个学生（US-09）。
     *
     * <p>【为什么是教师手动递补，而不是系统自动】
     * 教师访谈 T2 只说了「候补要有明确先后…按进入候补的时间处理」，
     * 没说这个动作由谁触发；追问（T9）时又原样重复了一遍，没有拿到答案。
     * 按最小假设 A6，本轮做成<b>教师手动点一下</b>：
     * 系统只负责把候补名单按正确顺序排好，教师看着名单决定给谁。
     * 这样不需要"待确认递补"这类中间状态，也不需要通知与超时机制。
     *
     * <p>【是否必须按顺序递补】
     * 不强制。顺序只是展示给教师看的参考，教师可以越过队列里的人把名额给别人
     * （例如前面的人联系不上）。本方法只校验"确实还有空位"。
     *
     * <p>【递补后 review_time 会被刷新】
     * 该字段的含义是"最后一次处理这条报名的时间"，
     * 递补也是一次处理，所以会写入当前时间。它已经从候补队列里出去了，
     * 原来的排队位置不再有意义。
     *
     * @param activityId 活动id
     * @param studentId  要递补给的学生id
     * @param teacherId  当前登录教师id
     * @return 成功返回 null；失败返回原因
     */
    public String promote(Long activityId, Long studentId, Long teacherId) {
        // 1. 活动必须存在，而且必须是这位教师发布的
        Activity activity = activityDAO.findById(activityId);
        if (activity == null) {
            return "活动不存在";
        }
        if (!activity.getTeacherId().equals(teacherId)) {
            return "只能处理自己发布活动的报名";
        }

        // 2. 报名记录必须存在，且处于候补状态
        ActivityRegistration registration =
                registrationDAO.findByActivityAndStudent(activityId, studentId);
        if (registration == null) {
            return "该学生没有报名这个活动";
        }
        if (!registration.isWaitlisted()) {
            return "该报名当前状态不是候补，无法递补";
        }

        // 3. 必须确实还有空位，否则递补之后正式参加人数会超过人数上限
        if (activity.hasCapacityLimit()) {
            int confirmed = registrationDAO.countByStatus(
                    activityId, ActivityRegistration.STATUS_CONFIRMED);
            if (confirmed >= activity.getCapacity()) {
                return "该活动名额已满，无法递补";
            }
        }

        // 4. 置为正式参加
        int rows = registrationDAO.updateStatusAndReviewTime(
                activityId, studentId,
                ActivityRegistration.STATUS_CONFIRMED, java.time.LocalDateTime.now());
        return rows > 0 ? null : "递补失败，请稍后重试";
    }

    // ==================================================================
    // 四、查询（REQ-04、REQ-06）
    // ==================================================================

    /**
     * 查询某个学生的全部报名记录（学生端"我的报名"，REQ-04）。
     *
     * <p>直接转发给 DAO：没有业务规则需要校验。
     * 返回的列表可能包含各种状态（待审核 / 候补 / 正式参加 / 未通过 / 已取消），
     * 由界面层用状态区分展示。
     *
     * @param studentId 学生id
     * @return 报名记录列表（可能为空列表，不会是 null）
     */
    public List<ActivityRegistration> myRegistrations(Long studentId) {
        return registrationDAO.findByStudentId(studentId);
    }

    /**
     * 查询某个学生在某个活动上的报名记录（US-01：详情页显示"我的状态"）。
     *
     * <p>活动详情页要告诉学生"你现在是什么情况"：待老师审核 / 候补中 /
     * 已确认参加 / 未通过。前端需要拿到状态本身来判断，光有"报没报过"不够，
     * 所以这里直接返回整条记录。
     *
     * @param activityId 活动id
     * @param studentId  学生id
     * @return 报名记录；从未报过名时返回 null
     */
    public ActivityRegistration myRegistration(Long activityId, Long studentId) {
        if (activityId == null || studentId == null) {
            return null;
        }
        return registrationDAO.findByActivityAndStudent(activityId, studentId);
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

    /**
     * 统计某个活动「已报名」的人数（界面显示"已报名 N 人"）。
     *
     * <p>【接口层为什么需要它】
     * 活动列表要显示"已报名 N 人"，而活动表本身不保存人数
     * （人数是报名表算出来的，这样才不会有冗余数据不一致的问题）。
     *
     * <p>⚠️【统计口径】统计的是<b>占位</b>的三种状态之和：待审核 + 候补 + 正式参加。
     * 已取消（学生自己退的）和未通过（教师驳回的）不计入。
     *
     * <p>它<b>不是</b>"已正式参加人数"。要判断名额是否已满，
     * 必须用 {@link #countByStatus(Long, String)} 传 CONFIRMED ——
     * 候补的人和待审核的人都还没有占住正式名额。
     *
     * @param activityId 活动id
     * @return 已报名人数
     */
    public int countRegistered(Long activityId) {
        return registrationDAO.countRegistered(activityId);
    }

    /**
     * 按状态统计某个活动的人数（V2.0 新增，US-08）。
     *
     * <p>教师端报名名单顶部要同时显示三个数字：
     * <pre>
     *   countByStatus(id, STATUS_CONFIRMED)      -> 已确定参加 N 人
     *   countByStatus(id, STATUS_WAITLISTED)     -> 候补 K 人
     *   countByStatus(id, STATUS_PENDING_REVIEW) -> 待审核 J 人
     * </pre>
     * 本方法只做转发，具体 SQL 在 DAO 里。
     *
     * @param activityId 活动id
     * @param status     报名状态，取值见 {@link ActivityRegistration} 的 STATUS_xxx 常量
     * @return 该状态下的记录数
     */
    public int countByStatus(Long activityId, String status) {
        return registrationDAO.countByStatus(activityId, status);
    }

    /**
     * 查询某个活动的候补队列，按「进入候补的时间」升序（US-09）。
     *
     * <p>只做转发。排序依据的说明见 {@code RegistrationDAO.findWaitlistOrderByReviewTime}：
     * 必须用 review_time（教师审核通过的时刻），不能用 register_time（学生报名的时刻）。
     *
     * <p>注意它<b>不是</b>按 register_time 排的，所以不能拿
     * {@link #rosterByStatus} 传 WAITLISTED 来代替。
     *
     * @param activityId 活动id
     * @param teacherId  当前登录教师id（校验归属）
     * @return 候补记录列表，先进入候补的排前面；无权或不存在时返回空列表
     */
    public List<ActivityRegistration> waitlist(Long activityId, Long teacherId) {
        if (!belongsTo(activityId, teacherId)) {
            return new ArrayList<>();
        }
        return registrationDAO.findWaitlistOrderByReviewTime(activityId);
    }

    /**
     * 按状态查询某个活动的报名名单（V2.0 新增，教师端名单分组展示）。
     *
     * <p>用它拼出「待审核」「正式参加」两个分组；「候补」分组要用
     * {@link #waitlist}，因为候补有自己的排序规则。
     *
     * @param activityId 活动id
     * @param teacherId  当前登录教师id（校验归属）
     * @param status     报名状态，取值见 {@link ActivityRegistration} 的 STATUS_xxx 常量
     * @return 该状态的报名记录，按报名时间正序；无权或不存在时返回空列表
     */
    public List<ActivityRegistration> rosterByStatus(Long activityId, Long teacherId, String status) {
        if (!belongsTo(activityId, teacherId)) {
            return new ArrayList<>();
        }
        return registrationDAO.findByActivityIdAndStatus(activityId, status);
    }

    /**
     * 判断活动是否存在且属于该教师（内部辅助方法）。
     *
     * <p>抽出来的目的：名单、候补、审核、递补都要做同样的判断，
     * 写一次可以保证各处判断逻辑完全一致（尤其别把 {@code equals} 写成 {@code ==}）。
     *
     * @param activityId 活动id
     * @param teacherId  教师id
     * @return true 表示活动存在且归该教师所有
     */
    private boolean belongsTo(Long activityId, Long teacherId) {
        if (activityId == null || teacherId == null) {
            return false;
        }
        Activity activity = activityDAO.findById(activityId);
        // 包装类型 Long 比较数值必须用 equals，不能用 ==
        return activity != null && teacherId.equals(activity.getTeacherId());
    }

    /**
     * 判断某个学生是否已经报名某个活动（即是否存在"占位"的记录）。
     *
     * <p>【接口层为什么需要它】
     * 活动列表要告诉前端"当前这个学生报没报过"，
     * 前端据此把按钮显示成「已报名」还是「立即报名」。
     *
     * <p>V2.0 后"报过名"包括三种状态：待审核 / 候补 / 正式参加。
     * 已取消和未通过都<b>不算</b>报过名（学生可以重新报名），
     * 判断逻辑统一交给 {@link ActivityRegistration#occupiesSlot()}，
     * 避免"改了实体忘了改这里"的不一致。
     *
     * @param activityId 活动id
     * @param studentId  学生id
     * @return true 表示该学生当前有一条占位中的报名记录
     */
    public boolean hasRegistered(Long activityId, Long studentId) {
        if (activityId == null || studentId == null) {
            return false;
        }
        ActivityRegistration existing =
                registrationDAO.findByActivityAndStudent(activityId, studentId);
        return existing != null && existing.occupiesSlot();
    }
}
