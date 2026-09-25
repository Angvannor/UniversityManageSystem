package com.hbk.activity.service;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.dao.RegistrationDAO;
import com.hbk.activity.entity.Activity;

import java.util.List;

/**
 * 活动业务类：负责活动发布与管理的业务规则（REQ-02 浏览、REQ-05 发布与管理）。
 *
 * <p>【Service 层的职责】
 * <ul>
 *   <li>做<b>业务校验</b>：必填项、时间先后、活动归属、能否删除等；</li>
 *   <li><b>编排多个 DAO</b>：例如删除活动时既要查报名表、又要操作活动表；</li>
 *   <li>把 DAO 的返回值翻译成对用户有意义的提示（返回 String，null 表示成功）。</li>
 * </ul>
 * 注意本类<b>不写任何 SQL</b>，SQL 全部在 DAO 里。
 *
 * <p>【本类落实的报告"重要约束"】
 * <pre>
 *   「教师只能管理自己发布的活动」  -> checkOwnership()
 *   「已有报名的活动不能直接删除」  -> delete()
 * </pre>
 *
 * <p>【返回值约定】与 AuthService 保持一致：
 * 返回 null 表示操作成功；返回非空字符串表示失败原因，菜单层直接打印。
 *
 * @author HBK组
 */
public class ActivityService {

    /**
     * 活动表数据访问对象。
     * DAO 是"实例方法"，必须通过对象调用（activityDAO.findAll()），
     * 不能写成 ActivityDAO.findAll()。
     */
    private final ActivityDAO activityDAO = new ActivityDAO();

    /**
     * 报名表数据访问对象。
     * 删除活动前需要查报名人数，所以这里也要用到报名 DAO ——
     * 这种"一个业务动作横跨两张表"的编排正是 Service 层的职责。
     */
    private final RegistrationDAO registrationDAO = new RegistrationDAO();

    /** 活动状态常量：OPEN 开放报名 */
    private static final String STATUS_OPEN = "OPEN";

    /**
     * 参加条件的最大字数，必须与 db/schema.sql 中 {@code eligibility VARCHAR(500)} 一致。
     * 在业务层先拦一道，避免超长文本提交到数据库才报错。
     */
    private static final int MAX_ELIGIBILITY_LENGTH = 500;

    /** 活动状态常量：已关闭 */
    private static final String STATUS_CLOSED = "CLOSED";

    // ==================================================================
    // 一、内部辅助方法
    // ==================================================================

    /**
     * 校验活动是否存在，以及是否属于指定教师。
     *
     * <p>update / close / delete 三个方法都需要这个校验，
     * 抽成一个私有方法避免重复代码，也保证三处校验逻辑完全一致。
     *
     * <p>【返回值约定】null 表示校验通过；非 null 表示失败原因。
     * 调用方的标准写法：
     * <pre>
     *   String error = checkOwnership(id, teacherId);
     *   if (error != null) {
     *       return error;
     *   }
     * </pre>
     *
     * @param activityId 活动id
     * @param teacherId  当前登录教师id
     * @return 校验通过返回 null；否则返回失败原因
     */
    private String checkOwnership(Long activityId, Long teacherId) {
        Activity activity = activityDAO.findById(activityId);
        if (activity == null) {
            return "活动不存在";
        }
        // 包装类型 Long 比较数值必须用 equals，不能用 ==
        // （== 比较的是对象地址，超过 127 的数值就会出错）
        if (!activity.getTeacherId().equals(teacherId)) {
            return "只能管理自己发布的活动";
        }
        return null;
    }

    /**
     * 校验人数上限与参加条件，并顺手把参加条件规范化（V2.0 新增，对应 US-05 / US-06）。
     *
     * <p>规则说明：
     * <ul>
     *   <li><b>人数上限留空 = 不限制人数</b>（数据库里存 NULL）。
     *       所以 capacity 为 null 是合法的，只有"填了但不是正整数"才算错。
     *       注意判断时只能用 {@code capacity != null && capacity <= 0}，
     *       不能直接写 {@code capacity <= 0} —— 那样 null 会自动拆箱，抛空指针异常。</li>
     *   <li><b>参加条件留空 = 无特殊条件</b>。如果用户只输入了空格，
     *       这里会把它规范成 null，避免数据库里存进一堆空格 ——
     *       那样界面上会以为"有条件"，但显示出来是一片空白，很困惑。</li>
     *   <li>参加条件是自由文本，本轮只限制长度，不做结构化校验（最小假设 A3）。</li>
     * </ul>
     *
     * <p>本方法会<b>修改入参对象</b>（把参加条件 trim 并可能置为 null），
     * 这是有意为之：校验通过后调用方直接拿去写库，不必再处理一遍。
     *
     * @param activity 待校验的活动对象
     * @return 校验通过返回 null；否则返回失败原因
     */
    private String checkAndNormalizeCapacity(Activity activity) {
        Integer capacity = activity.getCapacity();
        // 先判 null 再比较：capacity 是包装类型 Integer，直接和 0 比较会因自动拆箱而抛异常
        if (capacity != null && capacity <= 0) {
            return "人数上限必须是大于 0 的整数，留空表示不限制人数";
        }

        String eligibility = activity.getEligibility();
        if (eligibility != null) {
            String trimmed = eligibility.trim();
            if (trimmed.length() > MAX_ELIGIBILITY_LENGTH) {
                return "参加条件不能超过 " + MAX_ELIGIBILITY_LENGTH + " 个字";
            }
            // 全空白视为"没有填写"，存 null 而不是空字符串
            activity.setEligibility(trimmed.isEmpty() ? null : trimmed);
        }
        return null;
    }

    // ==================================================================
    // 二、发布与管理（教师，REQ-05）
    // ==================================================================

    /**
     * 教师发布活动。
     *
     * <p>业务校验：标题、地点必填；结束时间必须晚于开始时间。
     *
     * <p>注意本方法<b>不 new Activity()</b>：
     * 参数里已经有菜单层组装好的活动对象（含 teacherId），
     * 如果在这里重新 new 一个，调用方填的数据就全丢了。
     *
     * @param activity 待发布的活动（teacherId 由菜单层设为当前登录教师）
     * @return 成功返回 null；失败返回原因
     */
    public String publish(Activity activity) {
        // 1. 必填校验
        if (activity.getTitle() == null || activity.getTitle().trim().isEmpty()) {
            return "活动标题不能为空";
        }
        if (activity.getLocation() == null || activity.getLocation().trim().isEmpty()) {
            return "活动地点不能为空";
        }
        if (activity.getStartTime() == null || activity.getEndTime() == null) {
            return "活动开始时间和结束时间不能为空";
        }

        // 2. 业务规则：结束时间必须晚于开始时间。
        //    LocalDateTime 不支持算术运算（不能相减），要用 isAfter / isBefore 比较。
        if (!activity.getEndTime().isAfter(activity.getStartTime())) {
            return "活动结束时间必须晚于开始时间";
        }

        // 3. 人数上限与参加条件校验（V2.0 新增）
        String limitError = checkAndNormalizeCapacity(activity);
        if (limitError != null) {
            return limitError;
        }

        // 4. 状态兜底：菜单层没设置时默认允许报名
        if (activity.getStatus() == null || activity.getStatus().trim().isEmpty()) {
            activity.setStatus(STATUS_OPEN);
        }

        // 5. 交给 DAO 写库
        int rows = activityDAO.insert(activity);
        return rows > 0 ? null : "发布活动失败，请稍后重试";
    }

    /**
     * 教师修改活动信息。
     *
     * <p>先校验归属，再整体更新。注意 dao.update() 是全字段更新，
     * 因此菜单层应当先查出原活动、只改用户想改的字段，再整体传进来。
     *
     * @param activity  带 id 的活动对象
     * @param teacherId 当前登录教师id
     * @return 成功返回 null；失败返回原因
     */
    public String update(Activity activity, Long teacherId) {
        // 归属校验：注意传的是 activity.getId()，不是 activity 本身
        String error = checkOwnership(activity.getId(), teacherId);
        if (error != null) {
            return error;
        }

        // 结束时间晚于开始时间这条规则，修改时同样要校验
        if (activity.getStartTime() != null && activity.getEndTime() != null
                && !activity.getEndTime().isAfter(activity.getStartTime())) {
            return "活动结束时间必须晚于开始时间";
        }

        // 人数上限与参加条件校验（V2.0 新增，与 publish 用同一套规则）
        String limitError = checkAndNormalizeCapacity(activity);
        if (limitError != null) {
            return limitError;
        }

        int rows = activityDAO.update(activity);
        return rows > 0 ? null : "活动更新失败";
    }

    /**
     * 教师关闭活动报名（状态改为 CLOSED）。
     *
     * <p>关闭后学生不能再报名，但已有的报名记录保留，
     * 教师仍可查看报名名单。
     *
     * @param activityId 活动id
     * @param teacherId  当前登录教师id
     * @return 成功返回 null；失败返回原因
     */
    public String close(Long activityId, Long teacherId) {
        String error = checkOwnership(activityId, teacherId);
        if (error != null) {
            return error;
        }

        // 状态值必须与数据库中的取值一致：大写 CLOSED
        int rows = activityDAO.updateStatus(activityId, STATUS_CLOSED);
        return rows > 0 ? null : "关闭活动失败";
    }

    /**
     * 教师删除活动。
     *
     * <p>业务规则：<b>已有有效报名的活动不允许删除</b>。
     * 原因有两个：
     * <ol>
     *   <li>业务上：学生已经报了名，活动突然消失会影响学生；</li>
     *   <li>技术上：activity_registration 有外键指向 activity，
     *       直接删除会触发外键约束异常。</li>
     * </ol>
     * 因此必须先查报名人数，确认没有报名才删除 ——
     * 顺序不能反（先删再查就晚了）。
     *
     * @param activityId 活动id
     * @param teacherId  当前登录教师id
     * @return 成功返回 null；失败返回原因
     */
    public String delete(Long activityId, Long teacherId) {
        String error = checkOwnership(activityId, teacherId);
        if (error != null) {
            return error;
        }

        // 1. 先查有效报名人数（countRegistered 返回 int，不是 boolean）
        int registered = registrationDAO.countRegistered(activityId);
        if (registered > 0) {
            return "该活动已有 " + registered + " 人报名，不能删除，请先关闭报名";
        }

        // 2. 清理由该活动产生的报名记录（含学生取消报名后残留的 CANCELLED 行）。
        //    这些记录不参与人数统计，但外键约束会阻止活动被删除，
        //    所以必须先删子表记录，再删主表记录，顺序不能反。
        registrationDAO.deleteByActivityId(activityId);

        // 3. 确认无人报名、子表已清空后再删除活动
        int rows = activityDAO.deleteById(activityId);
        return rows > 0 ? null : "活动删除失败";
    }

    // ==================================================================
    // 三、查询（学生浏览 / 教师查看，REQ-02、REQ-05）
    // ==================================================================

    /**
     * 查询全部活动（学生浏览活动列表）。
     *
     * <p>直接转发给 DAO 即可，没有业务规则需要校验。
     * DAO 查不到数据时返回的是空列表，所以这里不需要判 null。
     *
     * @return 活动列表（可能为空列表，不会是 null）
     */
    public List<Activity> listAll() {
        return activityDAO.findAll();
    }

    /**
     * 查询某个教师发布的活动（教师端"我的活动"）。
     *
     * @param teacherId 教师id
     * @return 活动列表（可能为空列表，不会是 null）
     */
    public List<Activity> listByTeacher(Long teacherId) {
        return activityDAO.findByTeacherId(teacherId);
    }

    /**
     * 查询活动详情。
     *
     * <p>只做转发：把 DAO 查到的实体原样返回（本方法返回类型是 Activity）。
     * 界面上要显示成什么样（拼接标题、地点等文字）是菜单层的职责，
     * Service 不负责拼字符串，更不能用字符串替换对象返回。
     *
     * @param activityId 活动id
     * @return 活动对象；不存在时返回 null
     */
    public Activity detail(Long activityId) {
        return activityDAO.findById(activityId);
    }
}
