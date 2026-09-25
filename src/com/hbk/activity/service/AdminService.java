package com.hbk.activity.service;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.dao.UserDAO;
import com.hbk.activity.entity.Activity;
import com.hbk.activity.entity.User;

import java.util.List;

/**
 * 系统管理员业务类：负责账号状态管理与平台内容监督（US-11 ~ US-14）。
 *
 * <p>【这个角色是 V2.0 新增的】
 * 需求来自系统管理员访谈：
 * <ul>
 *   <li>A1：「我可以查看平台中的全部活动，用于监督是否有违规或明显错误的信息，
 *       但<b>不会代替教师处理具体报名</b>」—— 说明管理员需要活动全局视图，且只读；</li>
 *   <li>A2：「发现账号存在明显异常时，系统管理员可以停用；问题处理后，
 *       也可以恢复为可用状态」—— 说明需要账号的停用 / 恢复；</li>
 *   <li>A4：「我可以查看平台里的用户账号和基本状态」—— 说明需要用户列表。</li>
 * </ul>
 *
 * <p>【本类的职责边界（对应 REQ-V2-17，本轮证据最充分的结论）】
 * 管理员的权限 = <b>账号 + 活动监督</b>，<b>不包含</b>报名处理与报名审核。
 * 这一点有三条独立证据互相印证：
 * <pre>
 *   A1  明确说"不会代替教师处理具体报名"
 *   A4  被问"报名记录出错怎么查"时，答的是账号状态 —— 权限里根本没有报名记录
 *   A6  被问"账号停用后报名记录怎么办"时，直接推给活动组织教师
 * </pre>
 * 因此本类<b>不提供</b>任何报名相关方法。管理员若调用报名接口，
 * 由接口层的角色校验直接拒绝（403）。
 *
 * <p>【返回值约定】与其他 Service 保持一致：
 * null 表示操作成功；非空字符串表示失败原因。
 *
 * @author HBK组
 */
public class AdminService {

    /** 活动表数据访问对象（复用 findAll，管理员看全部活动） */
    private final ActivityDAO activityDAO = new ActivityDAO();

    /** 用户表数据访问对象 */
    private final UserDAO userDAO = new UserDAO();

    // ==================================================================
    // 一、只读监督视图（US-11、US-12）
    // ==================================================================

    /**
     * 查询平台上的全部活动（US-11：监督是否有违规或明显错误的信息）。
     *
     * <p>直接复用 {@code ActivityDAO.findAll()}，不需要新写 SQL ——
     * 学生端"浏览活动列表"用的就是同一个方法。
     * 区别只在于<b>谁能调用</b>：接口层限制这个入口只能是管理员，
     * 而教师端用的是"只看自己发布的"（findByTeacherId）。
     *
     * <p>注意本类只提供读，<b>不提供</b>发布 / 修改 / 删除活动的方法 ——
     * 管理员对这些活动是旁观者，不是负责人（A1 的原话是"监督"）。
     *
     * @return 全部活动列表；无数据时返回空列表
     */
    public List<Activity> listAllActivities() {
        return activityDAO.findAll();
    }

    /**
     * 查询平台上的全部用户账号及其状态（US-12）。
     *
     * <p>⚠️ 返回的 User 对象里带着 password 字段（DAO 为了登录校验会查出来）。
     * <b>接口层必须把它剔掉再返回给前端</b>（US-12 验收标准 2），
     * 否则密码密文会随响应发出去 —— 属于信息泄露。
     * 这个约束在 ApiVo 层落实，Service 层不负责。
     *
     * @return 全部用户列表；无数据时返回空列表
     */
    public List<User> listAllUsers() {
        return userDAO.findAll();
    }

    // ==================================================================
    // 二、账号停用与恢复（US-13）
    // ==================================================================

    /**
     * 停用账号（US-13）。
     *
     * <p>【为什么是改状态而不是删除账号】
     * 账号与报名记录之间有外键关联（activity_registration.student_id 指向 user.id），
     * 删除账号要么被外键拦住，要么会连带毁掉历史报名数据。
     * 改成 DISABLED 既能立刻阻止登录，又完整保留数据，而且操作可逆。
     *
     * <p>【停用之后已有的报名怎么办】
     * 访谈没有给出答案（A6 把问题推给了活动组织教师）。
     * 按最小假设 A8：<b>停用不自动取消该用户已有的报名</b>，
     * 原记录照常留在教师看到的名单里，由教师自行处理。
     *
     * <p>【为什么不能停用自己】
     * 一旦管理员把自己停用了，就再也没人能进后台把它改回来 ——
     * 属于把系统锁死的操作，必须在业务层直接禁止。
     *
     * @param userId     要被停用的用户id
     * @param operatorId 执行这次操作的管理员id
     * @return 成功返回 null；失败返回原因
     */
    public String disableUser(Long userId, Long operatorId) {
        if (userId == null) {
            return "用户不存在";
        }
        // 包装类型 Long 比较数值必须用 equals，不能用 ==（== 比的是对象地址）
        if (userId.equals(operatorId)) {
            return "不能停用自己的账号";
        }

        User target = userDAO.findById(userId);
        if (target == null) {
            return "用户不存在";
        }
        if (target.isDisabled()) {
            return "该账号已经处于停用状态";
        }

        int rows = userDAO.updateStatus(userId, User.STATUS_DISABLED);
        return rows > 0 ? null : "停用账号失败，请稍后重试";
    }

    /**
     * 恢复账号为可用（US-13：问题处理完之后恢复）。
     *
     * <p>恢复只是把 status 改回 ACTIVE，账号本来就一直在，
     * 所以不需要"重新创建"之类的操作。
     *
     * @param userId 要被恢复的用户id
     * @return 成功返回 null；失败返回原因
     */
    public String enableUser(Long userId) {
        if (userId == null) {
            return "用户不存在";
        }

        User target = userDAO.findById(userId);
        if (target == null) {
            return "用户不存在";
        }
        if (!target.isDisabled()) {
            return "该账号当前是可用的，无需恢复";
        }

        int rows = userDAO.updateStatus(userId, User.STATUS_ACTIVE);
        return rows > 0 ? null : "恢复账号失败，请稍后重试";
    }
}
