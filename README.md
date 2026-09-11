# 校园活动管理系统 V1.0

> 实验一《基于工程意图的软件迭代开发》项目仓库
> 技术栈：**Java（控制台程序）+ JDBC + MySQL**

---

## 一、项目定位

校园活动管理系统 V1.0 是一个**命令行控制台程序**，用纯 Java 实现校园活动的发布、
浏览与报名管理，数据保存在 MySQL 中，通过 JDBC 访问，不使用任何 Web 框架。

核心业务过程：**教师发布活动 → 学生查看活动 → 学生报名 → 教师查看报名信息**。

对照实验报告的需求编号：

| 需求编号 | 需求描述 | 实现位置（待开发） |
| --- | --- | --- |
| REQ-01 | 用户注册和登录，按身份进入对应菜单 | service/AuthService、ui/ConsoleApp |
| REQ-02 | 学生查看活动列表和活动详情 | service/ActivityService |
| REQ-03 | 学生报名活动、取消报名 | service/RegistrationService |
| REQ-04 | 学生查看自己的报名记录 | service/RegistrationService |
| REQ-05 | 教师发布、修改、删除自己的活动 | service/ActivityService |
| REQ-06 | 教师查看自己活动的报名学生与人数 | service/RegistrationService |

业务规则（对应报告"重要约束"，均由程序校验）：
1. 同一学生不能重复报名同一活动；
2. 活动人数达到上限后不能继续报名；
3. 只有处于可报名状态的活动才能报名；
4. 教师只能管理自己发布的活动；
5. 密码不以明文保存。

---

## 二、目录结构

    UniversityManageSystem/
    ├── src/                           Java 源代码（纯 Java，javac 直接编译）
    │   ├── Main.java                  实验背景说明占位文件（原始提交内容）
    │   └── com/hbk/activity/          业务代码（待开发）
    │       ├── entity/                实体类：User、Activity、ActivityRegistration
    │       ├── dao/                   数据访问类：用 JDBC 执行增删改查 SQL
    │       ├── service/               业务逻辑类：实现业务规则
    │       ├── util/                  工具类：数据库连接、日期处理、输入校验
    │       └── ui/                    界面层：控制台菜单，接收输入、显示结果
    ├── lib/                           第三方 jar（JDBC 驱动、密码加密库）
    ├── db/
    │   └── schema.sql                 建库建表脚本 + 初始数据
    ├── docs/
    │   ├── 原始材料文本/               实验报告 docx、任务说明 pdf
    │   └── tools/                     报告处理与校验的 Python 小工具
    ├── build.bat                      编译 + 运行脚本
    └── README.md                      本文件

---

## 三、环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 及以上 | 本机为 JDK 25，javac/java 直接可用 |
| MySQL | 8.x | 数据持久化；需先执行 db/schema.sql |

---

## 四、运行步骤

### 1. 初始化数据库

    mysql -u root -p < db/schema.sql

### 2. 编译并运行

    build.bat

脚本会编译 src 下的全部 Java 文件到 build/classes，然后启动控制台程序。
数据库账号密码在 src/com/hbk/activity/util/DBUtil.java 中配置（后续开发时创建）。

---

## 五、开发进度

- [x] 实验报告技术栈表述已同步为「纯 Java + JDBC + MySQL」
- [ ] db/schema.sql 建库建表脚本
- [ ] 数据库连接工具类 DBUtil
- [ ] 实体类（User / Activity / ActivityRegistration）
- [ ] 数据访问类（DAO，JDBC 实现）
- [ ] 业务逻辑类（注册登录、活动管理、报名与取消）
- [ ] 控制台菜单（学生菜单 / 教师菜单）
- [ ] 功能验证与测试记录（对应报告 7.1 TEST-01 ~ TEST-06）

---

## 六、团队与版本

- 小组：HBK 组
- 版本：V1.0
- 仓库：https://github.com/Angvannor/UniversityManageSystem
