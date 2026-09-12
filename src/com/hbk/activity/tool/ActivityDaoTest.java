package com.hbk.activity.tool;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.entity.Activity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ActivityDAO 手工测试类。
 *
 * <p>【用途】
 * 编译通过只说明语法正确，不代表 SQL 能跑通。
 * 本类依次调用 ActivityDAO 的 7 个方法，实际读写数据库，
 * 用来验证 SQL 语句、参数绑定和结果集转换是否正确。
 * 测试结束后会把新增的数据删掉，不给数据库留垃圾数据。
 *
 * <p>【运行方式】在项目根目录执行：
 * <pre>
 *   build.bat run com.hbk.activity.tool.ActivityDaoTest
 * </pre>
 *
 * <p>【说明】本类属于开发期的验证工具，不属于系统正式功能。
 *
 * @author HBK组
 */
public class ActivityDaoTest {

    public static void main(String[] args) {
        ActivityDAO dao = new ActivityDAO();

        System.out.println("=== ActivityDAO 测试开始 ===");

        // ---------- 1. 查全部：初始状态 ----------
        List<Activity> before = dao.findAll();
        System.out.println("[1] findAll() 初始条数: " + before.size());

        // ---------- 2. 新增一条活动 ----------
        Activity activity = new Activity();
        activity.setTitle("测试活动-程序设计大赛");
        activity.setDescription("由 ActivityDaoTest 创建的测试数据");
        activity.setLocation("计算机学院 A301");
        activity.setStartTime(LocalDateTime.of(2026, 10, 15, 9, 0, 0));
        activity.setEndTime(LocalDateTime.of(2026, 10, 15, 12, 0, 0));
        activity.setStatus("OPEN");
        activity.setTeacherId(1L);          // teacher01 的 id 是 1

        int rows = dao.insert(activity);
        System.out.println("[2] insert() 影响行数: " + rows + "（期望 1）");

        // ---------- 3. 再查全部，确认新增成功并取得新记录的 id ----------
        List<Activity> afterInsert = dao.findAll();
        System.out.println("[3] findAll() 新增后条数: " + afterInsert.size() + "（期望 +1）");

        if (afterInsert.isEmpty()) {
            System.out.println("新增失败，后续测试无法继续");
            return;
        }
        // insert() 只返回影响行数，拿不到自增主键，这里从列表里取最后一条的 id
        Activity created = afterInsert.get(afterInsert.size() - 1);
        Long newId = created.getId();
        System.out.println("    新活动 id = " + newId + "，内容: " + created);

        // ---------- 4. 按 id 查单条 ----------
        Activity byId = dao.findById(newId);
        System.out.println("[4] findById() 结果: " + byId);

        // ---------- 5. 按教师id查列表 ----------
        List<Activity> byTeacher = dao.findByTeacherId(1L);
        System.out.println("[5] findByTeacherId(1) 条数: " + byTeacher.size() + "（期望 >= 1）");
        List<Activity> byOtherTeacher = dao.findByTeacherId(999L);
        System.out.println("    findByTeacherId(999) 条数: " + byOtherTeacher.size() + "（期望 0）");

        // ---------- 6. 修改活动 ----------
        created.setTitle("测试活动-已改名");
        created.setLocation("新地点 B202");
        created.setStatus("CLOSED");
        int updateRows = dao.update(created);
        System.out.println("[6] update() 影响行数: " + updateRows + "（期望 1）");
        System.out.println("    修改后查询: " + dao.findById(newId));

        // ---------- 7. 单独修改状态 ----------
        int statusRows = dao.updateStatus(newId, "OPEN");
        System.out.println("[7] updateStatus() 影响行数: " + statusRows + "（期望 1）");
        System.out.println("    状态已改回: " + dao.findById(newId).getStatus());

        // ---------- 8. 删除测试数据 ----------
        int deleteRows = dao.deleteById(newId);
        System.out.println("[8] deleteById() 影响行数: " + deleteRows + "（期望 1）");
        System.out.println("    删除后 findAll() 条数: " + dao.findAll().size());

        System.out.println("\n=== 测试结束 ===");
    }
}
