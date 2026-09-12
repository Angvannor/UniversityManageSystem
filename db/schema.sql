-- ============================================================================
-- 文件：db/schema.sql
-- 用途：校园活动管理系统 V1.0 的数据库初始化脚本（建库 + 建表 + 初始数据）。
--       与实验报告 4.3「数据设计」中的三张核心表一一对应。
--
-- 执行方式：
--   IDEA：右键本文件 -> Run 'schema.sql'（必须整份执行）
--   命令行：Get-Content db\schema.sql -Encoding utf8 |
--           & "E:\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
--
-- 说明：脚本开头有 DROP TABLE IF EXISTS，可以反复执行，每次重建空表。
--       顺序不能乱：删除时先删有外键的表，建表时先建有被引用的表。
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
--    对应报告「设计决策二：区分学生和活动组织教师的权限」：
--      role 取值 STUDENT / TEACHER，是程序判断权限的唯一依据；
--      password 保存加密后的密码，不保存明文；
--      username 加唯一约束，防止重复注册同一账号。
--    注意：user 是 MySQL 保留字，建表与引用时都要写成 `user`。
-- ---------------------------------------------------------------------------
CREATE TABLE `user` (
    id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户id，主键',
    username VARCHAR(50)  NOT NULL COMMENT '登录账号（学号/工号），唯一',
    password VARCHAR(100) NOT NULL COMMENT '密码（加密后保存，不存明文）',
    name     VARCHAR(50)  NOT NULL COMMENT '姓名',
    role     VARCHAR(20)  NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT 学生 / TEACHER 教师',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表：学生与教师账号';

-- ---------------------------------------------------------------------------
-- 3. activity 活动表
--    设计说明：
--      teacher_id 记录发布该活动的教师（外键指向 user.id），
--        程序中用它判断「教师只能管理自己发布的活动」；
--      status 表示活动状态，只有 OPEN（可报名）才允许学生报名；
--      start_time / end_time 用 DATETIME，既能存日期也能存具体时间
--        （DATE 只能存日期，会丢掉"上午9点开始"这个信息）。
-- ---------------------------------------------------------------------------
CREATE TABLE activity (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动id，主键',
    title       VARCHAR(100) NOT NULL COMMENT '活动标题',
    description VARCHAR(500)          DEFAULT NULL COMMENT '活动内容说明',
    location    VARCHAR(100) NOT NULL COMMENT '活动地点',
    start_time  DATETIME     NOT NULL COMMENT '活动开始时间',
    end_time    DATETIME     NOT NULL COMMENT '活动结束时间',
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
--    注意：AUTO_INCREMENT 只加在主键 id 上，两个外键字段是普通 BIGINT。
-- ---------------------------------------------------------------------------
CREATE TABLE activity_registration (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '报名记录id，主键',
    activity_id   BIGINT      NOT NULL COMMENT '活动id（对应 activity.id）',
    student_id    BIGINT      NOT NULL COMMENT '学生id（对应 user.id）',
    register_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    status        VARCHAR(20) NOT NULL DEFAULT 'REGISTERED' COMMENT '状态：REGISTERED 已报名 / CANCELLED 已取消',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reg_student_activity (activity_id, student_id),
    KEY idx_reg_student (student_id),
    CONSTRAINT fk_reg_activity FOREIGN KEY (activity_id) REFERENCES activity (id),
    CONSTRAINT fk_reg_student FOREIGN KEY (student_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='活动报名表：学生与活动的多对多关系';

-- ---------------------------------------------------------------------------
-- 5. 初始数据：一个教师账号、一个学生账号
--
--    密码说明（对应报告约束「密码不以明文保存」）：
--      下面 password 字段保存的是 SHA-256 加盐哈希后的密文，不是明文。
--      两个账号的明文密码都是 123456，登录时程序会把用户输入重新哈希后
--      与这里的密文比对（见 util/PasswordUtil.java）。
--      如需更换初始密码，用下面命令重新生成密文后替换：
--        build.bat run com.hbk.activity.util.PasswordUtil 新密码
--
--    role 必须与程序中的角色常量一致：TEACHER / STUDENT。
-- ---------------------------------------------------------------------------
INSERT INTO `user` (username, password, name, role)
VALUES ('teacher01', '5cd539652c23db99e6f0233845bb5696f641d3ac985ca6f8164e62770b580483', '张老师', 'TEACHER'),
       ('student01', '5cd539652c23db99e6f0233845bb5696f641d3ac985ca6f8164e62770b580483', '李同学', 'STUDENT');
