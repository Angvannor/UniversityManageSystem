# 校园活动管理系统 V1.0

> 实验一《基于工程意图的软件迭代开发》项目仓库
> 技术栈：**Java（控制台程序）+ JDBC + MySQL**

---

## 一、项目定位

校园活动管理系统 V1.0 是一个**命令行控制台程序**，用纯 Java 实现校园活动的发布、
浏览与报名管理，数据保存在 MySQL 中，通过 JDBC 访问，不使用任何 Web 框架。

核心业务过程：**教师发布活动 → 学生查看活动 → 学生报名 → 教师查看报名信息**。

### 需求与实现对应关系

| 需求编号 | 需求描述 | 实现位置 |
| --- | --- | --- |
| REQ-01 | 用户注册和登录，按身份进入对应菜单 | `AuthService`、`PasswordUtil`、`MainMenu` |
| REQ-02 | 学生查看活动列表和活动详情 | `ActivityService.listAll()/detail()` |
| REQ-03 | 学生报名活动 | `RegistrationService.register()` |
| REQ-04 | 学生查看自己的报名记录、取消报名 | `RegistrationService.myRegistrations()/cancel()` |
| REQ-05 | 教师发布、修改、关闭、删除自己的活动 | `ActivityService.publish()/update()/close()/delete()` |
| REQ-06 | 教师查看自己活动的报名学生 | `RegistrationService.activityRoster()` |

### 业务规则（由程序强制校验）

| 规则 | 落实位置 |
| --- | --- |
| 同一学生不能重复报名同一活动 | `RegistrationService.register()` + 数据库唯一约束 |
| 只有处于可报名状态（OPEN）的活动才能报名 | `RegistrationService.register()` |
| 取消报名后记录保留，可重新报名（复用原记录） | `RegistrationService.cancel()` / `register()` |
| 教师只能管理、查看自己发布的活动 | `ActivityService.checkOwnership()`、`RegistrationService.activityRoster()` |
| 已有学生报名的活动不能删除 | `ActivityService.delete()` |
| 密码不以明文保存（SHA-256 加盐哈希） | `PasswordUtil` |

---

## 二、目录结构

```
UniversityManageSystem/
├── src/
│   ├── Main.java                              实验背景说明占位文件（原始提交内容）
│   └── com/hbk/activity/                      业务代码
│       ├── entity/                            实体类（一行表数据 = 一个对象）
│       │   ├── User.java                      用户：id/username/password/name/role
│       │   ├── Activity.java                  活动：含 startTime/endTime/status/teacherId
│       │   └── ActivityRegistration.java      报名记录：activityId/studentId/status/registerTime
│       ├── dao/                               数据访问层：所有 SQL 都在这里
│       │   ├── UserDAO.java                   账号查重、新增、按账号/按id查询
│       │   ├── ActivityDAO.java               活动增删改查、按教师查询、改状态
│       │   └── RegistrationDAO.java           报名记录增改查、人数统计、按活动清理
│       ├── service/                           业务逻辑层：业务规则都在这里
│       │   ├── AuthService.java               注册、登录（REQ-01）
│       │   ├── ActivityService.java           发布、修改、关闭、删除、查询（REQ-02/05）
│       │   └── RegistrationService.java       报名、取消、我的报名、报名名单（REQ-03/04/06）
│       ├── ui/
│       │   └── MainMenu.java                  控制台界面：主菜单 + 学生菜单 + 教师菜单
│       ├── util/
│       │   ├── DBUtil.java                    数据库连接与资源关闭
│       │   └── PasswordUtil.java              密码 SHA-256 加盐哈希
│       └── tool/                              开发期验证工具（不属于系统正式功能）
│           ├── UserDaoTest.java               DAO 手工测试
│           ├── ActivityDaoTest.java
│           ├── RegistrationDaoTest.java
│           ├── AuthServiceTest.java           带断言框架的自动化测试（19 个用例）
│           ├── ActivityServiceTest.java       （24 个用例）
│           └── RegistrationServiceTest.java   （21 个用例）
├── lib/
│   └── mysql-connector-j-8.2.0.jar            MySQL JDBC 驱动（项目自带，无需联网下载）
├── db/
│   └── schema.sql                             建库建表脚本 + 初始账号
├── docs/
│   ├── 原始材料文本/                           实验报告 docx、任务说明 pdf
│   └── tools/                                 报告技术栈改写与校验的 Python 脚本
├── build.bat                                  编译 + 运行脚本
└── README.md                                  本文件
```

### 分层结构（对应报告 4.1 总体设计）

```
界面层 MainMenu          显示菜单、读取输入（不含业务规则、不写 SQL）
      ↓ 调用
业务逻辑层 Service       业务校验、编排多个 DAO（不写 SQL）
      ↓ 调用
数据访问层 DAO           执行 SQL、结果集与对象互转
      ↓ JDBC
数据库 MySQL             user / activity / activity_registration
```

---

## 三、环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 及以上 | 本机为 JDK 25，javac/java 直接可用 |
| MySQL | 8.x | 数据持久化；需先执行 db/schema.sql |

---

## 四、运行步骤

### 1. 启动 MySQL 并初始化数据库

```cmd
net start MySQL80
"E:\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p < db\schema.sql
```

PowerShell 中不能用 `<` 重定向，改用：

```powershell
Get-Content db\schema.sql -Encoding utf8 | & "E:\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

脚本开头的 `DROP TABLE IF EXISTS` 允许反复执行，每次重建空表和初始账号。

### 2. 编译并运行

```powershell
.\build.bat
```

其它可用命令：

```powershell
.\build.bat compile                                 只编译
.\build.bat run com.hbk.activity.tool.AuthServiceTest   编译后运行指定类
.\build.bat clean                                   清理编译产物
```

数据库账号密码配置在 `src/com/hbk/activity/util/DBUtil.java` 中的
`USERNAME` / `PASSWORD` 两个常量里，换机器时只需改这两处。

### 3. 演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| teacher01 | 123456 | 活动组织教师 |
| student01 | 123456 | 学生 |

密码在数据库中以 SHA-256 密文保存，登录时由程序重新哈希后比对。
如需更换初始密码，先生成新密文再改 `db/schema.sql`：

```powershell
.\build.bat run com.hbk.activity.util.PasswordUtil 新密码
```

### 4. 运行测试（需要 MySQL 已启动）

```powershell
.\build.bat run com.hbk.activity.tool.AuthServiceTest
.\build.bat run com.hbk.activity.tool.ActivityServiceTest
.\build.bat run com.hbk.activity.tool.RegistrationServiceTest
```

测试自带数据清理，可以反复运行。

---

## 五、开发进度

- [x] 实验报告技术栈表述同步为「纯 Java + JDBC + MySQL」
- [x] `db/schema.sql` 建库建表脚本（三张表 + 唯一约束 + 三个外键 + 初始账号）
- [x] 数据库连接工具类 `DBUtil`
- [x] 密码加密工具类 `PasswordUtil`
- [x] 实体类（User / Activity / ActivityRegistration）
- [x] 数据访问类（UserDAO / ActivityDAO / RegistrationDAO）
- [x] 业务逻辑类（注册登录、活动管理、报名与取消）
- [x] 控制台菜单（主菜单 / 学生菜单 / 教师菜单）
- [x] 功能验证与测试记录（64 个自动化用例，对应报告 7.1）

---

## 六、后续版本规划

V1.0 采用的分层结构（界面层 / 业务逻辑层 / 数据访问层）为后续升级预留了空间：
**换界面或换框架时，业务规则与数据访问层可以复用**。两次升级的详细目标、技术方案、
验收标准与对报告的影响，见 [`docs/版本升级路线图.md`](docs/版本升级路线图.md)。

| 版本 | 主题 | 技术栈变化 | 复用情况 |
| --- | --- | --- | --- |
| V2.0 | 加图形界面（前后端分离） | 新增 JDK 内置 HttpServer 提供 REST 接口；新增 Vue 3 前端 | 业务层、实体类、数据访问层全部复用 |
| V3.0 | 换成主流后端框架 | Spring Boot 3 替换手写 HTTP 层；MyBatis-Plus 替换手写 JDBC | 前端零改动；业务规则与数据库设计不变 |

V1.0 中已知的技术债（如删除活动缺少事务保护、数据库密码硬编码、连接未复用等）
也在这份文档中列出，并标注了建议处理的版本。

---

## 七、团队与版本

- 小组：HBK 组
- 版本：V1.0
- 仓库：https://github.com/Angvannor/UniversityManageSystem
