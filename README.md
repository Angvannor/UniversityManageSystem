# 校园活动管理系统

> 实验一《基于工程意图的软件迭代开发》项目仓库
>
> | 版本 | 形态 | 技术栈 | 说明 |
> | --- | --- | --- | --- |
> | **V1.0** | 命令行控制台程序 | 纯 Java + JDBC + MySQL | 实验一交付，已打 tag `v1.0` |
> | **V1.5**（当前） | 前后端分离的 Web 应用 | Vue 3 + JDK 内置 HttpServer + JDBC + MySQL | **技术架构升级，需求未变** |
> | **V2.0** | 同 V1.5 | 同 V1.5 | 实验二：按**需求访谈**结论迭代需求 |
> | V3.0 | 同 V1.5 | 后端换 Spring Boot 3 | 计划中 |
>
> 说明：V1.5 只是把控制台界面换成了浏览器界面，**六项需求和业务规则一条都没改**，
> 属于技术架构升级而非需求迭代，所以不占用 V2.0 这个版本号；
> V2.0 留给实验二中「由需求访谈驱动」的那一版。

---

## 一、项目定位

校园活动管理系统用于校园活动的发布、浏览与报名管理，核心业务过程是：

> **教师发布活动 → 学生查看活动 → 学生报名 → 教师查看报名信息**

V1.5 在保留 V1.0 控制台入口的同时，增加了 **Vue 3 前端** 和 **REST 接口层**，
形成前后端分离结构。两个入口（Web 与控制台）**共用同一套业务逻辑与数据访问代码** ——
这正是 V1.0 分层设计带来的价值。

### 需求与实现对应关系

| 需求编号 | 需求描述 | 后端实现 | 前端页面 |
| --- | --- | --- | --- |
| REQ-01 | 用户注册和登录，按身份进入对应界面 | `AuthService`、`AuthApi` | `Login.vue`、`Register.vue` |
| REQ-02 | 学生查看活动列表和活动详情 | `ActivityService`、`ActivityApi` | `ActivityList.vue`、`ActivityDetail.vue` |
| REQ-03 | 学生报名活动 | `RegistrationService`、`RegistrationApi` | `ActivityList.vue`、`ActivityDetail.vue` |
| REQ-04 | 学生查看我的报名、取消报名 | `RegistrationService`、`RegistrationApi` | `MyRegistrations.vue` |
| REQ-05 | 教师发布、修改、关闭、删除自己的活动 | `ActivityService`、`ActivityApi` | `ActivityManage.vue` |
| REQ-06 | 教师查看自己活动的报名学生 | `RegistrationService`、`RegistrationApi` | `ActivityRoster.vue` |

### 业务规则（均由业务层强制校验，绕过前端也无法违反）

| 规则 | 落实位置 |
| --- | --- |
| 同一学生不能重复报名同一活动 | `RegistrationService.register()` + 数据库唯一约束 |
| 只有处于可报名状态（OPEN）的活动才能报名 | `RegistrationService.register()` |
| 取消报名只改状态、记录保留，可重新报名（复用原记录） | `RegistrationService.cancel()` / `register()` |
| 教师只能管理、查看自己发布的活动 | `ActivityService.checkOwnership()`、`RegistrationService.activityRoster()` |
| 已有有效报名的活动不能删除 | `ActivityService.delete()` |
| 密码不以明文保存（SHA-256 加盐哈希） | `PasswordUtil` |
| 接口层再次校验角色（不依赖前端隐藏菜单） | `RequestContext.requireRole()` |

---

## 二、目录结构

```
UniversityManageSystem/
├── src/com/hbk/activity/
│   ├── entity/                            实体类（一行表数据 = 一个对象）
│   │   ├── User.java                      用户：id/username/password/name/role
│   │   ├── Activity.java                  活动：含 startTime/endTime/status/teacherId
│   │   └── ActivityRegistration.java      报名记录：activityId/studentId/status/registerTime
│   ├── dao/                               数据访问层（所有 SQL 都在这里）
│   │   ├── UserDAO.java                   账号查重、新增、按账号/按 id 查询
│   │   ├── ActivityDAO.java               活动增删改查、按教师查询、改状态
│   │   └── RegistrationDAO.java           报名记录增改查、人数统计、按活动清理
│   ├── service/                           业务逻辑层（业务规则都在这里）
│   │   ├── AuthService.java               注册、登录、按 id/账号查询
│   │   ├── ActivityService.java           发布、修改、关闭、删除、查询
│   │   └── RegistrationService.java       报名、取消、我的报名、报名名单、人数统计
│   ├── web/                               【V1.5】REST 接口层
│   │   ├── WebServerMain.java             启动类：HttpServer + 路由注册 + 线程池
│   │   ├── ApiRouter.java                 路由匹配、令牌校验、异常转换、CORS、日志
│   │   ├── RequestContext.java            请求上下文（路径/查询/请求体参数、当前用户）
│   │   ├── ApiHandler.java                处理器函数式接口
│   │   ├── ApiResult.java                 统一返回结构 {code,message,data}
│   │   ├── ApiException.java              业务异常（带错误码）
│   │   ├── ApiVo.java                     响应视图对象（用户/登录/活动/报名）
│   │   ├── SessionManager.java            登录会话：token → userId
│   │   ├── JsonUtil.java                  Gson 封装 + LocalDateTime 格式适配
│   │   ├── AuthApi.java                   注册 / 登录 / 退出 / 当前用户
│   │   ├── ActivityApi.java               活动列表 / 详情 / 发布 / 修改 / 关闭 / 删除
│   │   └── RegistrationApi.java           报名 / 取消 / 我的报名 / 报名名单
│   ├── ui/MainMenu.java                   【V1.0】控制台界面（主菜单 + 学生 + 教师）
│   ├── util/
│   │   ├── DBUtil.java                    数据库连接与资源关闭
│   │   └── PasswordUtil.java              密码 SHA-256 加盐哈希
│   └── tool/                              开发期验证工具（不属于系统正式功能）
│       ├── UserDaoTest / ActivityDaoTest / RegistrationDaoTest      DAO 手工测试
│       └── AuthServiceTest(19) / ActivityServiceTest(24) / RegistrationServiceTest(21)
├── frontend/                              【V1.5】Vue 3 前端
│   ├── package.json / vite.config.js / index.html
│   ├── mock/mock-api.js                   本地假接口（脱离后端调试前端时使用）
│   └── src/
│       ├── main.js                        应用入口（Router / Pinia / Element Plus）
│       ├── App.vue                        外壳：顶栏 + 按角色显示的侧边菜单
│       ├── styles/global.css              全局样式（响应式卡片、登录页分栏布局）
│       ├── api/                           request.js（Axios 封装）+ auth/activity/registration
│       ├── stores/auth.js                 登录状态（Pinia，持久化到 localStorage）
│       ├── router/index.js                路由表 + 登录守卫 + 角色守卫
│       └── views/
│           ├── Login.vue  Register.vue
│           ├── student/  ActivityList.vue  ActivityDetail.vue  MyRegistrations.vue
│           └── teacher/  ActivityManage.vue  ActivityRoster.vue
├── lib/
│   ├── mysql-connector-j-8.2.0.jar        MySQL JDBC 驱动（项目自带，无需联网下载）
│   └── gson-2.11.0.jar                    JSON 处理（单 jar，无额外依赖）
├── db/schema.sql                          建库建表脚本 + 演示账号与演示活动
├── config/db.properties.example           数据库连接配置模板
├── docs/
│   ├── 原始材料文本/                       实验报告 docx、任务说明 pdf
│   ├── 版本升级路线图.md                     V1.5 / V2.0 / V3.0 的升级目标与验收标准
│   └── tools/                             报告处理与校验的 Python 脚本
├── build.bat                              编译 + 运行脚本
└── README.md                              本文件
```

### 分层结构与数据流

```
浏览器（Vue 3，:5173）                控制台（MainMenu）        ← 两种界面
        │ HTTP /api/xxx                        │
        ▼                                      │
  ApiRouter（路由/令牌/异常/JSON）              │              ← V1.5 新增
        ▼                                      │
  AuthApi / ActivityApi / RegistrationApi      │              ← V1.5 新增
        └──────────────┬───────────────────────┘
                       ▼
              业务逻辑层 Service（业务规则、编排 DAO）           ← V1.0 已存在，未改动
                       ▼
              数据访问层 DAO（JDBC、SQL）                      ← V1.0 已存在
                       ▼
                  MySQL 数据库
```

---

## 三、环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 及以上 | 本机为 JDK 25，javac / java 直接可用 |
| MySQL | 8.x | 数据持久化；需先执行 `db/schema.sql` |
| Node.js | 18 及以上 | **仅前端需要**（本机为 Node 22，npm 11） |

---

## 四、运行步骤

### 1. 启动 MySQL 并初始化数据库

```powershell
net start MySQL80
Get-Content db\schema.sql -Encoding utf8 | & "E:\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

> cmd 里可以用 `<` 重定向：`mysql -u root -p < db\schema.sql`

脚本开头的 `DROP TABLE IF EXISTS` 允许反复执行，每次重置为演示数据
（6 个账号 + 3 个活动 + 6 条覆盖四种状态的报名记录，详见第六节）。

### 2. 配置数据库连接

数据库密码不写在代码里（对应任务说明书「不得提交密码等敏感信息」的要求）：

```powershell
copy config\db.properties.example config\db.properties
```

然后编辑 `config\db.properties`，把 `db.password` 改成自己的 MySQL 密码：

```properties
db.url=jdbc:mysql://localhost:3306/campus_activity?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
db.username=root
db.password=你的MySQL密码
```

`config/db.properties` 已加入 `.gitignore`，只保存在本机，不会被提交。

### 3. 启动后端（V1.5 方式：REST 接口服务）

```powershell
.\build.bat web
```

看到下面这段说明启动成功（**这个窗口要保持运行**）：

```
  校园活动管理系统 V1.5 —— 接口服务已启动
  接口前缀： http://localhost:8080/api
  按 Ctrl+C 停止服务
```

### 4. 启动前端

**另开一个终端**：

```powershell
cd frontend
npm install     # 首次运行需要，之后可跳过
npm run dev
```

浏览器打开 **http://localhost:5173** 即可使用图形界面。

> 前端 Vite 会把 `/api` 请求代理到 `http://localhost:8080`，因此不需要额外处理跨域。
>
> 如果只想调试前端界面、不想启动后端，把 `frontend/vite.config.js` 里的
> `USE_MOCK` 改成 `true` 即可使用本地假接口（前端代码无需改动）。

### 5. 启动控制台版本（V1.0 方式，可选）

```powershell
.\build.bat
```

### 6. 其它常用命令

```powershell
.\build.bat compile                                    只编译
.\build.bat web                                        启动 REST 接口服务
.\build.bat DBUtil                                     数据库连接自检
.\build.bat run com.hbk.activity.tool.AuthServiceTest  编译后运行指定类
.\build.bat clean                                      清理编译产物
```

---

## 五、接口清单（14 个）

所有接口返回统一结构：

```json
{ "code": 0, "message": "success", "data": { } }
```

除注册、登录外，都需要在请求头携带令牌：`Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 注册 | 匿名 |
| POST | `/api/auth/login` | 登录，返回 token | 匿名 |
| POST | `/api/auth/logout` | 退出登录 | 已登录 |
| GET | `/api/auth/me` | 当前登录用户 | 已登录 |
| GET | `/api/activities` | 活动列表（支持 `keyword` / `status` / `onlyMine`） | 学生 + 教师 |
| GET | `/api/activities/{id}` | 活动详情 | 学生 + 教师 |
| POST | `/api/activities` | 发布活动 | 教师 |
| PUT | `/api/activities/{id}` | 修改活动 | 教师 |
| PUT | `/api/activities/{id}/close` | 关闭报名 | 教师 |
| DELETE | `/api/activities/{id}` | 删除活动 | 教师 |
| POST | `/api/activities/{id}/registrations` | 报名活动 | 学生 |
| DELETE | `/api/activities/{id}/registrations` | 取消报名 | 学生 |
| GET | `/api/registrations/mine` | 我的报名 | 学生 |
| GET | `/api/activities/{id}/registrations` | 活动报名名单 | 教师 |

### 错误码约定

| 错误码 | 含义 |
| --- | --- |
| 0 | 成功 |
| 1000 | 参数不合法 |
| 1001 | 业务处理失败（具体原因见 message） |
| 401 | 未登录或登录状态失效（前端自动跳登录页） |
| 403 | 当前角色无权执行该操作 |
| 404 | 数据不存在 |
| 2001 | 账号或密码错误 |
| 2002 | 账号已被注册 |
| 3001 | 重复报名 |
| 3003 | 活动当前不可报名 |
| 3004 | 尚未报名，无法取消 |

---

## 六、演示账号

| 账号 | 密码 | 角色 | 状态 |
| --- | --- | --- | --- |
| teacher01 | 123456 | 活动组织教师 | 可用 |
| student01 | 123456 | 学生（李同学） | 可用 |
| student02 | 123456 | 学生（王同学） | 可用 |
| student04 | 123456 | 学生（孙同学） | 可用 |
| student03 | 123456 | 学生（赵同学） | **已停用**（用来验证停用账号不能登录） |
| admin01 | 123456 | 系统管理员 | 可用 |

### 演示数据

| 活动 | 人数上限 | 参加条件 | 状态 |
| --- | --- | --- | --- |
| 程序设计大赛 | 30 | 需具备基础编程能力 | 可报名 |
| 校园歌手大赛 | **1**（故意设很小） | 需通过初赛选拔 | 可报名 |
| 篮球友谊赛 | 12 | 无特殊条件 | 已关闭 |

6 条报名记录覆盖四种状态，各角色登录后立刻有数据可看：

| 谁 | 报名了哪个活动 | 状态 |
| --- | --- | --- |
| 李同学 | 程序设计大赛 | 已确认参加 |
| 李同学 | 校园歌手大赛 | 已确认参加（占掉唯一名额） |
| 王同学 | 程序设计大赛 | 待老师审核 |
| 王同学 | 校园歌手大赛 | 候补（第 1 位） |
| 孙同学 | 校园歌手大赛 | 候补（第 2 位） |
| 王同学 | 篮球友谊赛 | 未通过 |

> **为什么「校园歌手大赛」的上限设成 1**：只要两个人报名就必然产生候补，
> 这样不必手工造数据就能演示「满员 → 候补 → 有人取消 → 教师手动递补」的完整流程。
>
> **为什么候补是「王同学第 1、孙同学第 2」而不是按报名时间**：
> 孙同学 07:30 报名最早，但教师 09:10 才审核他；王同学 07:50 报名，09:05 被审核。
> 候补顺序按**审核时间**算，所以王同学在前。
> 这正是 `review_time` 字段存在的理由，详见 `db/schema.sql` 的说明。

密码在数据库中以 SHA-256 密文保存。如需更换初始密码，先生成新密文再改 `db/schema.sql`：

```powershell
.\build.bat run com.hbk.activity.util.PasswordUtil 新密码
```

---

## 七、测试

业务逻辑层的自动化测试（需要 MySQL 已启动）：

```powershell
.\build.bat run com.hbk.activity.tool.AuthServiceTest          # 19 个用例
.\build.bat run com.hbk.activity.tool.ActivityServiceTest      # 24 个用例
.\build.bat run com.hbk.activity.tool.RegistrationServiceTest  # 21 个用例
```

测试自带数据清理，可以反复运行。

接口层验证方式：启动 `build.bat web` 后，用 Postman 或浏览器直接调用上述接口，
控制台会打印每个请求的访问日志，例如：

```
[22:41:03] OK   POST   /api/auth/login -> code=0 success (12ms)
[22:41:12] FAIL POST   /api/activities/1/registrations -> code=3001 你已经报名过该活动，不能重复报名 (6ms)
```

---

## 八、开发进度

### V1.0（控制台版本，已完成）
- [x] `db/schema.sql` 建库建表脚本（三张表 + 唯一约束 + 三个外键 + 初始数据）
- [x] 数据库连接工具类 `DBUtil`、密码加密工具类 `PasswordUtil`
- [x] 实体类、数据访问层（3 个 DAO）、业务逻辑层（3 个 Service）
- [x] 控制台菜单（主菜单 / 学生菜单 / 教师菜单）
- [x] 64 个自动化测试用例

### V1.5（前后端分离 = 技术架构升级，需求未变，已完成）
- [x] REST 接口层（14 个接口 + 统一返回结构 + 令牌认证 + 角色校验 + CORS）
- [x] Vue 3 前端工程（Vite + Router + Pinia + Element Plus + Axios）
- [x] 7 个页面：登录、注册、浏览活动、活动详情、我的报名、活动管理、报名名单
- [x] 前端路由守卫（未登录跳登录页、按角色限制页面）
- [x] 本地假接口（脱离后端也能独立调试前端）
- [x] 前端与真实后端联调通过
- [x] 数据库密码外置到 `config/db.properties`（技术债 T2）

### V2.0（实验二：需求演化的迭代版本，进行中）
- [ ] 三个角色（学生 / 教师 / 系统管理员）的需求访谈
- [ ] 访谈记录 → 用户故事 + 验收标准
- [ ] 需求冲突消解、确定本轮范围
- [ ] 影响分析（改了哪些文件、哪些没改、为什么）
- [ ] 按新需求实现并回归测试（V1.5 的六项需求不得回归）
- [ ] 实验二报告

---

## 九、后续版本规划（V3.0）

V1.0 的分层结构已经支撑了 V1.5 的界面升级，V3.0 计划用 **Spring Boot 3**
替换手写的 HTTP 接口层与 JDBC 数据访问层，**前端不需要任何改动**。
详细的升级目标、技术方案、验收标准，以及 V1.0/V1.5 遗留的技术债清单，见
[`docs/版本升级路线图.md`](docs/版本升级路线图.md)。

主要待办：接口层换 Spring Boot、DAO 换 MyBatis-Plus、令牌换 JWT、
密码换 BCrypt、删除活动加事务保护、查询分页。

---

## 十、团队与版本

- 小组：HBK 组
- 当前版本：V1.5（V2.0 需求演化进行中）
- 仓库：https://github.com/Angvannor/UniversityManageSystem
