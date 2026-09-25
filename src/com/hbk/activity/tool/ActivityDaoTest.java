package com.hbk.activity.tool;

import com.hbk.activity.dao.ActivityDAO;
import com.hbk.activity.entity.Activity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ActivityDAO 的自动化测试（V2.0 阶段 6 改造）。
 *
 * <p>【用途】
 * 编译通过只说明语法正确，不代表 SQL 能跑通。
 * 本类依次调用 ActivityDAO 的方法，实际读写数据库，
 * 验证 SQL 语句、参数绑定和结果集转换是否正确。
 *
 * <p>【V2.0 相对 V1.5 的两处改造】
 * <ol>
 *   <li><b>只打印 → 断言</b>（技术债 T6）：旧版全靠人眼看控制台，
 *       出问题不会自动报错；现在末尾直接给出通过/失败计数。</li>
 *   <li><b>修掉一个会误删演示数据的缺陷</b>：
 *       旧版用「取列表最后一条」来定位刚插入的活动，但
 *       {@code findAll()} 是<b>按 start_time 排序</b>的，
 *       "最后一条"根本不是刚插入的那条 —— 曾经因此把演示活动
 *       「书法体验课」改名、关闭并误删。现在改用 {@code insert()}
 *       回填的自增主键定位，见下面的 ★ 标注。</li>
 * </ol>
 *
 * <p>【运行方式】需要 MySQL 已启动且已执行过 db/schema.sql：
 * <pre>
 *   build.bat run com.hbk.activity.tool.ActivityDaoTest
 * </pre>
 * 测试结束会删除自己新增的数据，可以反复运行。
 *
 * @author HBK组
 */
public class ActivityDaoTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        ActivityDAO dao = new ActivityDAO();

        System.out.println("=== ActivityDAO 测试开始 ===");

        // ---------- 1. 查全部：初始条数 ----------
        List<Activity> before = dao.findAll();
        check("findAll() 能查出演示活动（>= 3 条，实际 " + before.size() + "）",
                before.size() >= 3);

        // ---------- 2. 新增一条活动 ----------
        Activity activity = new Activity();
        activity.setTitle("测试活动-程序设计大赛");
        activity.setDescription("由 ActivityDaoTest 创建的测试数据");
        activity.setLocation("计算机学院 A301");
        activity.setStartTime(LocalDateTime.of(2026, 10, 15, 9, 0, 0));
        activity.setEndTime(LocalDateTime.of(2026, 10, 15, 12, 0, 0));
        activity.setCapacity(20);                 // V2.0 新增字段
        activity.setEligibility("测试用参加条件");  // V2.0 新增字段
        activity.setStatus("OPEN");
        activity.setTeacherId(1L);                // teacher01 的 id 是 1

        int rows = dao.insert(activity);
        check("insert() 影响行数为 1（实际 " + rows + "）", rows == 1);

        // ★ 用 insert() 回填的自增主键来定位刚插入的那一行。
        //   千万不要用「取列表最后一条」的办法：findAll() 是按 start_time 排序的，
        //   不是按 id 排序，所以"最后一条"很可能是别的活动。
        //   这个坑真的踩过：曾经因此把演示活动「书法体验课」改名、关闭并误删。
        Long newId = activity.getId();
        check("insert() 回填了自增主键（id=" + newId + "）", newId != null);
        if (newId == null) {
            System.out.println("新增失败，后续测试无法继续");
            printSummary();
            return;
        }

        try {
            // ---------- 3. 新增后条数 +1 ----------
            List<Activity> afterInsert = dao.findAll();
            check("新增后 findAll() 条数 +1（" + before.size() + " -> " + afterInsert.size() + "）",
                    afterInsert.size() == before.size() + 1);

            // ---------- 4. 按 id 查单条 ----------
            Activity byId = dao.findById(newId);
            check("findById() 能查到刚插入的活动", byId != null);
            if (byId != null) {
                check("标题正确（实际 " + byId.getTitle() + "）",
                        "测试活动-程序设计大赛".equals(byId.getTitle()));
                check("★ V2.0 新增的 capacity 正确写入并读出（实际 "
                                + byId.getCapacity() + "）",
                        Integer.valueOf(20).equals(byId.getCapacity()));
                check("★ V2.0 新增的 eligibility 正确写入并读出",
                        "测试用参加条件".equals(byId.getEligibility()));
            }
            check("findById() 查不存在的 id 返回 null", dao.findById(999999L) == null);

            // ---------- 5. 按教师 id 查列表 ----------
            List<Activity> byTeacher = dao.findByTeacherId(1L);
            check("findByTeacherId(1) 能查到活动（>= 1 条）", byTeacher.size() >= 1);
            check("findByTeacherId(999) 查不到任何活动",
                    dao.findByTeacherId(999L).isEmpty());

            // ---------- 6. 修改活动 ----------
            byId.setTitle("测试活动-已改名");
            byId.setLocation("新地点 B202");
            byId.setCapacity(null);        // 改成"不限制人数"，验证 NULL 也能写入
            int updateRows = dao.update(byId);
            check("update() 影响行数为 1（实际 " + updateRows + "）", updateRows == 1);

            Activity afterUpdate = dao.findById(newId);
            check("标题已更新（实际 " + afterUpdate.getTitle() + "）",
                    "测试活动-已改名".equals(afterUpdate.getTitle()));
            check("地点已更新",
                    "新地点 B202".equals(afterUpdate.getLocation()));
            check("★ capacity 改成 null 后读回来仍是 null（而不是 0）",
                    afterUpdate.getCapacity() == null);

            // ---------- 7. 只改状态 ----------
            int statusRows = dao.updateStatus(newId, "CLOSED");
            check("updateStatus() 影响行数为 1（实际 " + statusRows + "）", statusRows == 1);
            check("状态已改为 CLOSED",
                    "CLOSED".equals(dao.findById(newId).getStatus()));

            // ---------- 8. 删除 ----------
            int deleteRows = dao.deleteById(newId);
            check("deleteById() 影响行数为 1（实际 " + deleteRows + "）", deleteRows == 1);
            check("删除后查不到了", dao.findById(newId) == null);
            check("删除后总数回到初始值",
                    dao.findAll().size() == before.size());

        } finally {
            // 兜底清理：万一中间断言失败没走到第 8 步
            if (dao.findById(newId) != null) {
                dao.deleteById(newId);
                System.out.println("[清理] 兜底删除了未清理的测试活动 id=" + newId);
            }
        }

        System.out.println();
        System.out.println("=== 测试结束 ===");
        printSummary();
    }

    /** 打印结论 */
    private static void printSummary() {
        System.out.println("总计: " + (passed + failed) + "，通过: " + passed + "，失败: " + failed);
        if (failed > 0) {
            System.out.println("结论：存在未通过的检查，请查看上面的输出。");
        } else {
            System.out.println("结论：ActivityDAO 的增删改查与 V2.0 新字段读写全部符合预期。");
        }
    }

    /** 断言并打印结果 */
    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [OK]   " + description);
        } else {
            failed++;
            System.out.println("  [FAIL] " + description);
        }
    }
}
