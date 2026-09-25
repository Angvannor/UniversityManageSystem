-- ============================================================================
-- 文件：db/schema.sql
-- 用途：校园活动管理系统 V2.0 的数据库初始化脚本（建库 + 建表 + 初始数据）。
--       与实验报告 4.3「数据设计」及实验二报告第八节「数据/领域对象调整」对应。
--
-- 执行方式：
--   IDEA：右键本文件 -> Run 'schema.sql'（必须整份执行）
--   命令行：Get-Content db\schema.sql -Encoding utf8 |
--           & "E:\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
--
-- 说明：脚本开头有 DROP TABLE IF EXISTS，可以反复执行，每次重建空表。
--       顺序不能乱：删除时先删有外键的表，建表时先建有被引用的表。
--
-- ============================================================================
-- 【V1.5 -> V2.0 本轮的数据改动一览】（需求依据见 docs/作业2/V2.0需求基线.md）
-- ============================================================================
--   表           | 改动                          | 对应需求
--   -------------|-------------------------------|---------------------------
--   user         | 新增 status（账号状态）        | US-13 停用/恢复账号
--   user         | role 取值新增 ADMIN            | US-11~US-14 系统管理员角色
--   activity     | 新增 capacity（人数上限）      | US-05 教师设置人数上限
--   activity     | 新增 eligibility（参加条件）   | US-06 教师填写参加条件
--   activity_    | 新增 review_time（审核时间）   | US-07 审核、US-09 候补排序
--   registration | status 取值 2 态扩展为 5 态    | US-02~US-04、US-07~US-09
--
--   唯一约束 uk_reg_student_activity 保持不变 —— 一个学生在同一活动上
--   始终只有一行记录，状态怎么流转都是改这一行。详见文件末尾的说明。
--
--   如果是在**已有数据**的库上增量升级（不重建表），对应的 ALTER 语句见
--   本文件末尾「附：增量升级语句」一节。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 创建数据库（utf8mb4 支持中文）
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS campus_activity
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE campus_activity;

-- 按外键依赖倒序删除旧表：被引用的表最后删
DROP TABLE IF EXISTS activity_registration;
DROP TABLE IF EXISTS activity;
DROP TABLE IF EXISTS `user`;

-- ---------------------------------------------------------------------------
-- 2. user 用户表
--    对应报告「设计决策二：区分角色的权限」：
--      role 取值 STUDENT / TEACHER / ADMIN，是程序判断权限的唯一依据；
--        V2.0 新增 ADMIN（系统管理员），只负责账号与活动监督，不介入报名；
--      status 表示账号是否可用（V2.0 新增）：
--        ACTIVE   可用     —— 正常登录使用；
--        DISABLED 已停用   —— 不能登录，但账号与历史数据都保留（不是删除）。
--        （对应 US-13：管理员发现账号异常时停用，问题处理后可恢复）
--      password 保存加密后的密码，不保存明文；
--      username 加唯一约束，防止重复注册同一账号。
--    注意：user 是 MySQL 保留字，建表与引用时都要写成 `user`。
-- ---------------------------------------------------------------------------
CREATE TABLE `user` (
    id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户id，主键',
    username VARCHAR(50)  NOT NULL COMMENT '登录账号（学号/工号），唯一',
    password VARCHAR(100) NOT NULL COMMENT '密码（加密后保存，不存明文）',
    name     VARCHAR(50)  NOT NULL COMMENT '姓名',
    role     VARCHAR(20)  NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT 学生 / TEACHER 教师 / ADMIN 系统管理员',
    status   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE 可用 / DISABLED 已停用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表：学生、教师与系统管理员账号';

-- ---------------------------------------------------------------------------
-- 3. activity 活动表
--    设计说明：
--      teacher_id 记录发布该活动的教师（外键指向 user.id），
--        程序中用它判断「教师只能管理自己发布的活动」；
--      status 表示活动状态，只有 OPEN（可报名）才允许学生报名；
--      start_time / end_time 用 DATETIME，既能存日期也能存具体时间
--        （DATE 只能存日期，会丢掉"上午9点开始"这个信息）。
--
--    V2.0 新增两个字段（对应教师访谈 T1 / T3 / T4 / T8）：
--      capacity    可接待人数上限。NULL 表示不限制人数。
--                  访谈原话：「场地只能坐30人」
--                  —— 上限由**每个活动的负责教师**独立设定，不是全局统一值。
--      eligibility 参加条件，自由文本。NULL 表示无特殊条件。
--                  访谈原话：「有些活动并不适合所有学生，
--                  具体条件要看活动本身，由负责组织的老师来确定」
--                  本轮按最小假设 A3 只做自由文本，不做结构化条件与自动判定。
-- ---------------------------------------------------------------------------
CREATE TABLE activity (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动id，主键',
    title       VARCHAR(100) NOT NULL COMMENT '活动标题',
    description VARCHAR(500)          DEFAULT NULL COMMENT '活动内容说明',
    location    VARCHAR(100) NOT NULL COMMENT '活动地点',
    start_time  DATETIME     NOT NULL COMMENT '活动开始时间',
    end_time    DATETIME     NOT NULL COMMENT '活动结束时间',
    capacity    INT                   DEFAULT NULL COMMENT '可接待人数上限，NULL 表示不限制',
    eligibility VARCHAR(500)          DEFAULT NULL COMMENT '参加条件（自由文本），NULL 表示无特殊条件',
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN 可报名 / CLOSED 已关闭 / FINISHED 已结束',
    teacher_id  BIGINT       NOT NULL COMMENT '发布活动的教师id（对应 user.id）',
    PRIMARY KEY (id),
    KEY idx_activity_teacher (teacher_id),
    CONSTRAINT fk_activity_teacher FOREIGN KEY (teacher_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='活动表：校园活动基本信息';

-- ---------------------------------------------------------------------------
-- 4. activity_registration 活动报名表
--    对应报告「设计决策三、四」：
--      学生与活动是多对多关系，因此单独建表保存报名记录；
--      (activity_id, student_id) 唯一约束是「同一学生不能重复报名同一活动」
--        的数据库层保障，程序里也会先校验一次，双保险；
--      取消报名时把 status 改为 CANCELLED 而不是删除记录，
--        既保留报名痕迹，也便于学生重新报名时复用该行。
--
--    【V2.0 的关键变化：报名状态由 2 态扩展为 5 态】
--    教师访谈 T6 的原话：
--      「没通过说明不符合这次活动的参加条件；通过以后还要看当时有没有位置，
--        并不代表一定已经正式参加。」
--    这句话说明「审核通过」与「正式参加」是两件事，所以状态必须拆开：
--
--      PENDING_REVIEW 待审核    —— 学生刚提交报名，等教师判定是否符合条件
--      CONFIRMED      正式参加  —— 审核通过，且当时有名额
--      WAITLISTED     候补      —— 审核通过，但名额已满，按进入候补的时间排队
--      REJECTED       未通过    —— 教师判定不符合参加条件
--      CANCELLED      已取消    —— 学生主动取消（保留记录，可重新报名）
--
--    状态流转（详见 docs/作业2/V2.0需求基线.md 第一节）：
--      报名 -> PENDING_REVIEW
--                ├── 教师驳回             -> REJECTED
--                └── 教师通过 ┬─ 有名额    -> CONFIRMED
--                             └─ 名额已满  -> WAITLISTED
--                                             └─ 教师手动递补 -> CONFIRMED
--      任意状态 -> CANCELLED
--
--    V2.0 新增 review_time 字段：
--      它是「审核时间」，同时**也是进入候补队列的时间**。
--      教师访谈 T2 要求「候补要有明确先后…按进入候补的时间处理，
--      先进入的人排在前面」——注意「进入候补的时间」是**教师审核通过的时刻**，
--      不是学生报名的时刻，两者可能相差很久，所以必须单独记录。
--      候补排序一律用 review_time 升序，不能用 register_time。
--      本文件末尾的演示数据里特意构造了「报名早但审核晚」的例子来体现这一点。
-- ---------------------------------------------------------------------------
CREATE TABLE activity_registration (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '报名记录id，主键',
    activity_id   BIGINT      NOT NULL COMMENT '活动id（对应 activity.id）',
    student_id    BIGINT      NOT NULL COMMENT '学生id（对应 user.id）',
    register_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    review_time   DATETIME             DEFAULT NULL COMMENT '审核时间；也是进入候补队列的时间，候补按此升序排序',
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '状态：PENDING_REVIEW 待审核 / CONFIRMED 正式参加 / WAITLISTED 候补 / REJECTED 未通过 / CANCELLED 已取消',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reg_student_activity (activity_id, student_id),
    KEY idx_reg_student (student_id),
    KEY idx_reg_activity_status (activity_id, status),
    CONSTRAINT fk_reg_activity FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT fk_reg_student FOREIGN KEY (student_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='活动报名表：学生与活动的多对多关系';

-- ---------------------------------------------------------------------------
-- 5. 初始数据
-- ---------------------------------------------------------------------------

-- 5.1 用户：教师、学生、系统管理员，各角色都有
--
--    密码说明（对应报告约束「密码不以明文保存」）：
--      下面 password 字段保存的是 SHA-256 加盐哈希后的密文，不是明文。
--      所有演示账号的明文密码都是 123456，登录时程序会把用户输入重新哈希后
--      与这里的密文比对（见 util/PasswordUtil.java）。
--      如需更换初始密码，用下面命令重新生成密文后替换：
--        build.bat run com.hbk.activity.util.PasswordUtil 新密码
--
--    role 必须与程序中的角色常量一致：TEACHER / STUDENT / ADMIN。
--    ★ 插入顺序决定 id，后面 5.3 的报名记录按 id 引用，不要随意调换顺序：
--        teacher01=1  student01=2  student02=3  student03=4  student04=5  admin01=6
--    status 演示了两种情况：
--      student03 预设为 DISABLED，用来验证「被停用账号不能登录」（US-13），
--      用它登录应返回错误码 2003「账号已被停用」。
INSERT INTO `user` (username, password, name, role, status)
VALUES ('teacher01', '5cd539652c23db99e6f0233845bb5696f641d3ac985ca6f8164e62770b580483', '张老师', 'TEACHER', 'ACTIVE'),
       ('student01', '5cd539652c23db99e6f0233845bb5696f641d3ac985ca6f8164e62770b580483', '李同学', 'STUDENT', 'ACTIVE'),
       ('student02', '5cd539652c23db99e6f0233845bb5696f641d3ac985ca6f8164e62770b580483', '王同学', 'STUDENT', 'ACTIVE'),
       ('student03', '5cd539652c23db99e6f0233845bb5696f641d3ac985ca6f8164e62770b580483', '赵同学', 'STUDENT', 'DISABLED'),
       ('student04', '5cd539652c23db99e6f0233845bb5696f641d3ac985ca6f8164e62770b580483', '孙同学', 'STUDENT', 'ACTIVE'),
       ('admin01',   '5cd539652c23db99e6f0233845bb5696f641d3ac985ca6f8164e62770b580483', '系统管理员', 'ADMIN',  'ACTIVE');

-- 5.2 活动：4 条演示数据，覆盖「设了上限 / 上限很小 / 不限制 / 已关闭」四种情况
--      teacher_id = 1 对应上面的 teacher01（发布教师）
--
--      程序设计大赛：capacity 30，对应教师访谈 T8 的「场地只能坐30人」，
--                    适合演示正常报名（30 个名额远未满）。
--      校园歌手大赛：capacity 1，**故意设成 1**，
--                    只要两个人报名就必然产生候补，用于演示 US-03 / US-09。
--      篮球友谊赛：  capacity 12，但状态是 CLOSED，
--                    用于验证「已关闭的活动不能报名」（V1.0 规则不回归）。
--      书法体验课：  capacity 与 eligibility **都是 NULL**，
--                    用于验证「留空 = 不限制人数 / 无特殊条件」（US-05 验收标准 2、
--                    US-01 验收标准 2 要求界面显示「无特殊条件」而不是空白）。
--                    NULL 值的读写是 DAO 层最容易出错的地方，必须有一条真实数据覆盖它。
INSERT INTO activity (title, description, location, start_time, end_time, capacity, eligibility, status, teacher_id)
VALUES ('程序设计大赛', '面向全校的算法与程序设计竞赛，欢迎同学报名参加。', '计算机学院 A301',
        '2026-10-15 09:00:00', '2026-10-15 12:00:00', 30,
        '面向全校本科生，需具备基础编程能力（C/C++/Java 任一）。', 'OPEN', 1),
       ('校园歌手大赛', '校园文艺活动，报名后参加初赛选拔。', '大学生活动中心',
        '2026-11-01 19:00:00', '2026-11-01 21:30:00', 1,
        '无年级限制，需通过初赛选拔。', 'OPEN', 1),
       ('篮球友谊赛', '院系之间的篮球友谊赛，报名已截止。', '体育馆',
        '2026-09-20 15:00:00', '2026-09-20 17:00:00', 12,
        NULL, 'CLOSED', 1),
       ('书法体验课', '面向全校师生的书法体验活动，无需基础。', '图书馆 302',
        '2026-11-10 14:00:00', '2026-11-10 16:00:00', NULL,
        NULL, 'OPEN', 1);

-- 5.3 报名记录：6 条演示数据，覆盖 4 种状态（CANCELLED 点一下取消就能产生）
--
--     登录哪个账号能看到什么（角色登录后各页面都立刻有数据）：
--       student01 李同学：① 程序设计大赛「已确认参加」
--                         ② 校园歌手大赛「已确认参加」
--       student02 王同学：① 程序设计大赛「待老师审核」——教师登录后可审核它
--                         ② 校园歌手大赛「候补中·排第 1」
--                         ③ 篮球友谊赛「未通过」——演示被驳回的情况
--       student04 孙同学：校园歌手大赛「候补中·排第 2」
--       teacher01 张老师：程序设计大赛 1 条待审核 + 1 条已确认（上限 30，远未满）
--                         校园歌手大赛 上限 1 已满 + 候补 2 人
--                         → 可演示「学生取消后，教师手动递补给候补的人」
--
--   ★★ 这个例子专门用来验证「候补必须按 review_time 排序」：
--
--        学生    报名时间(register_time)   审核时间(review_time)   结果
--        ------  ----------------------   --------------------   ----------
--        孙同学  07:30  ← 最早            09:10  ← 最晚          候补第 2
--        王同学  07:50                    09:05                  候补第 1
--        李同学  08:00  ← 最晚            09:00  ← 最早          正式参加
--
--     原因：校园歌手大赛只招 1 人。教师先审了李同学（09:00，占满唯一名额），
--     再依次审王同学（09:05）和孙同学（09:10），两人都因已满而转入候补。
--
--     如果错误地按 register_time 排序，候补队列会变成「孙同学 → 王同学」；
--     按 review_time 排序才是正确的「王同学 → 孙同学」。
--     两张表一对比就能看出差别 —— 这条可以直接写进实验二报告的测试章节。
--
--   （学生与活动的完整对应关系见上面 5.2；student_id 对应 5.1 的插入顺序）
INSERT INTO activity_registration (activity_id, student_id, register_time, review_time, status)
VALUES (1, 2, '2026-09-10 10:20:00', '2026-09-10 11:00:00', 'CONFIRMED'),
       (1, 3, '2026-09-11 09:15:00', NULL,                  'PENDING_REVIEW'),
       (2, 2, '2026-10-01 08:00:00', '2026-10-01 09:00:00', 'CONFIRMED'),
       (2, 3, '2026-10-01 07:50:00', '2026-10-01 09:05:00', 'WAITLISTED'),
       (2, 5, '2026-10-01 07:30:00', '2026-10-01 09:10:00', 'WAITLISTED'),
       (3, 3, '2026-09-01 10:00:00', '2026-09-02 09:00:00', 'REJECTED');

-- ============================================================================
-- 附：增量升级语句（仅当要在**已有数据的库**上升级、不想重建表时使用）
-- ============================================================================
-- 平时开发用上面的重建脚本即可（每次重置为演示数据）。
-- 下面这段保留下来作为「V1.5 -> V2.0 数据库改了什么」的对照材料，
-- 可以直接写进实验二报告第八节「数据/领域对象调整」。
--
--   -- ① user：新增账号状态；role 取值扩展为 STUDENT / TEACHER / ADMIN
--   --    role 是 VARCHAR(20) 且没有 CHECK 约束，新增 ADMIN 无需改结构
--   ALTER TABLE `user`
--       ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
--           COMMENT '账号状态：ACTIVE 可用 / DISABLED 已停用' AFTER role;
--
--   -- ② activity：新增人数上限与参加条件
--   ALTER TABLE activity
--       ADD COLUMN capacity    INT          NULL COMMENT '可接待人数上限，NULL 表示不限制' AFTER end_time,
--       ADD COLUMN eligibility VARCHAR(500) NULL COMMENT '参加条件（自由文本），NULL 表示无特殊条件' AFTER capacity;
--
--   -- ③ activity_registration：新增审核时间（同时是候补排序依据）
--   ALTER TABLE activity_registration
--       ADD COLUMN review_time DATETIME NULL
--           COMMENT '审核时间（也是进入候补队列的时间，候补按此升序）' AFTER register_time,
--       ADD KEY idx_reg_activity_status (activity_id, status);
--
--   -- ④ 历史数据迁移：V1.5 没有审核环节，原有报名视为已确定参加
--   UPDATE activity_registration SET status = 'CONFIRMED' WHERE status = 'REGISTERED';
--
-- 【为什么唯一约束不用改】
--   uk_reg_student_activity (activity_id, student_id) 在新状态模型下依然成立：
--   一个学生在同一活动上始终只有一行记录，从待审核到候补、到正式参加、
--   再到取消后重新报名，全都改这一行的 status，不新增行。
--   这与 V1.5 中「取消报名改 status 而不删记录、重新报名复用该行」的做法完全一致，
--   说明 V1.0 的数据设计经得起这次需求变化（可作为报告「影响分析」的论据）。
-- ============================================================================
