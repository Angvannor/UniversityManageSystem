/**
 * ============================================================================
 * 文件：frontend/mock/mock-api.js
 * 用途：本地假接口（Mock API）。在 V1.5 的后端接口层（JDK HttpServer）写好之前，
 *       让前端可以独立开发与调试。
 *
 * 【为什么用它，而不是在前端代码里写死假数据】
 *   这个 mock 是以「HTTP 中间件」的形式挂在 Vite 开发服务器上的：
 *   前端的 axios 依然发真实请求（POST /api/auth/login），只是被它在中间拦下来，
 *   返回符合约定的 JSON。因此：
 *     - 前端代码 100% 是真实代码，不需要为了假数据改任何逻辑；
 *     - 返回结构 {code, message, data} 与将来的真接口完全一致；
 *     - 后端做好后，把 vite.config.js 里的 USE_MOCK 改成 false 即可切换，
 *       前端一行都不用改。
 *
 * 【它模拟了哪些后端行为】
 *   - 统一响应结构（成功 code=0，失败带错误码与提示）
 *   - 登录令牌（登录后返回 token，受保护的接口要带 Authorization 头）
 *   - 六条业务规则：重复报名、活动已关闭、教师只能管自己的活动、
 *     有报名的活动不能删除、按角色区分权限、密码不返回给前端
 *   - 数据保存在内存中，重启开发服务器后恢复初始数据
 *
 * 【注意】这是开发期的辅助工具，不属于系统正式功能，将来可以整个删掉。
 * ============================================================================
 */

/** 模拟数据库：用户表 */
let users = [
  { id: 1, username: 'teacher01', password: '123456', name: '张老师', role: 'TEACHER' },
  { id: 2, username: 'student01', password: '123456', name: '李同学', role: 'STUDENT' }
]

/** 模拟数据库：活动表 */
let activities = [
  {
    id: 1,
    title: '程序设计大赛',
    description: '面向全校的算法与程序设计竞赛，欢迎同学报名参加。',
    location: '计算机学院 A301',
    startTime: '2026-10-15 09:00:00',
    endTime: '2026-10-15 12:00:00',
    status: 'OPEN',
    teacherId: 1
  },
  {
    id: 2,
    title: '校园歌手大赛',
    description: '校园文艺活动，报名后参加初赛选拔。',
    location: '大学生活动中心',
    startTime: '2026-11-01 19:00:00',
    endTime: '2026-11-01 21:30:00',
    status: 'OPEN',
    teacherId: 1
  },
  {
    id: 3,
    title: '篮球友谊赛',
    description: '院系之间的篮球友谊赛，已截止报名。',
    location: '体育馆',
    startTime: '2026-09-20 15:00:00',
    endTime: '2026-09-20 17:00:00',
    status: 'CLOSED',
    teacherId: 1
  }
]

/** 模拟数据库：报名表 */
let registrations = [
  {
    id: 1,
    activityId: 1,
    studentId: 2,
    registerTime: '2026-09-10 10:20:00',
    status: 'REGISTERED'
  }
]

/** 自增主键计数器 */
let nextUserId = 3
let nextActivityId = 4
let nextRegistrationId = 2

/** 内存中的登录会话：token -> userId */
const sessions = new Map()

// ---------------------------------------------------------------------------
// 工具函数
// ---------------------------------------------------------------------------

/** 成功响应 */
function ok(data = null) {
  return { code: 0, message: 'success', data }
}

/** 失败响应：错误码与后端 ResultCode 保持一致 */
function fail(code, message) {
  return { code, message, data: null }
}

/** 生成简单令牌 */
function createToken(userId) {
  const token = 'mock-token-' + userId + '-' + Date.now()
  sessions.set(token, userId)
  return token
}

/** 从请求头解析当前登录用户；未登录返回 null */
function currentUser(req) {
  const auth = req.headers['authorization'] || ''
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : auth
  const userId = sessions.get(token)
  return userId ? users.find((u) => u.id === userId) || null : null
}

/** 去掉密码字段后返回给前端（对应后端不返回密码的设计） */
function toUserVO(user) {
  if (!user) return null
  return { id: user.id, username: user.username, name: user.name, role: user.role }
}

/** 把活动实体转换成前端需要的视图对象（附带教师名、报名人数、当前学生是否已报名） */
function toActivityVO(activity, user) {
  const registeredCount = registrations.filter(
    (r) => r.activityId === activity.id && r.status === 'REGISTERED'
  ).length
  const joined = user
    ? registrations.some(
        (r) => r.activityId === activity.id && r.studentId === user.id && r.status === 'REGISTERED'
      )
    : null
  const teacher = users.find((u) => u.id === activity.teacherId)
  return {
    ...activity,
    teacherName: teacher ? teacher.name : '未知',
    registeredCount,
    joined
  }
}

/** 读取请求体并解析 JSON */
function readBody(req) {
  return new Promise((resolve) => {
    let raw = ''
    req.on('data', (chunk) => (raw += chunk))
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch (e) {
        resolve({})
      }
    })
  })
}

/** 返回 JSON 响应 */
function send(res, body, httpStatus = 200) {
  res.statusCode = httpStatus
  res.setHeader('Content-Type', 'application/json;charset=UTF-8')
  res.end(JSON.stringify(body))
}

// ---------------------------------------------------------------------------
// 接口实现
// ---------------------------------------------------------------------------

/**
 * 路由表：每项为 { method, pattern, handler }。
 * pattern 中用 :id 表示路径参数，handler 返回要发送的响应体。
 */
const routes = [
  // ==================== 认证（REQ-01） ====================
  {
    method: 'POST',
    pattern: /^\/api\/auth\/register$/,
    async handler(req, res, params, body) {
      const { username, password, name, role } = body
      if (!username || !password || !name) return fail(1000, '账号、密码、姓名不能为空')
      if (password.length < 6) return fail(1000, '密码长度不能少于 6 位')
      if (users.some((u) => u.username === username)) return fail(2002, '该账号已被注册')

      const user = {
        id: nextUserId++,
        username,
        password,
        name,
        role: role === 'TEACHER' ? 'TEACHER' : 'STUDENT'
      }
      users.push(user)
      return ok(toUserVO(user))
    }
  },
  {
    method: 'POST',
    pattern: /^\/api\/auth\/login$/,
    async handler(req, res, params, body) {
      const { username, password } = body
      const user = users.find((u) => u.username === username && u.password === password)
      // 账号不存在与密码错误返回同一提示，避免暴露账号是否存在
      if (!user) return fail(2001, '账号或密码错误')
      return ok({ token: createToken(user.id), user: toUserVO(user) })
    }
  },
  {
    method: 'POST',
    pattern: /^\/api\/auth\/logout$/,
    async handler(req) {
      const auth = req.headers['authorization'] || ''
      sessions.delete(auth.startsWith('Bearer ') ? auth.slice(7) : auth)
      return ok()
    }
  },
  {
    method: 'GET',
    pattern: /^\/api\/auth\/me$/,
    async handler(req) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')
      return ok(toUserVO(user))
    }
  },

  // ==================== 活动查询（REQ-02 / REQ-05） ====================
  {
    method: 'GET',
    pattern: /^\/api\/activities$/,
    async handler(req, res, params, body, query) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')

      let list = activities.slice()
      // 关键字搜索（按标题模糊匹配）
      if (query.keyword) {
        list = list.filter((a) => a.title.includes(query.keyword))
      }
      // 状态筛选
      if (query.status) {
        list = list.filter((a) => a.status === query.status)
      }
      // 教师端：只看自己发布的活动
      if (query.onlyMine === 'true') {
        list = list.filter((a) => a.teacherId === user.id)
      }
      // 按开始时间排序
      list.sort((a, b) => a.startTime.localeCompare(b.startTime))
      return ok(list.map((a) => toActivityVO(a, user)))
    }
  },
  {
    method: 'GET',
    pattern: /^\/api\/activities\/(\d+)$/,
    async handler(req, res, params) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')
      const activity = activities.find((a) => a.id === Number(params[0]))
      if (!activity) return fail(404, '活动不存在或已被删除')
      return ok(toActivityVO(activity, user))
    }
  },

  // ==================== 活动管理（REQ-05，仅教师） ====================
  {
    method: 'POST',
    pattern: /^\/api\/activities$/,
    async handler(req, res, params, body) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')
      if (user.role !== 'TEACHER') return fail(403, '当前角色无权执行该操作')
      if (!body.title) return fail(1000, '活动标题不能为空')
      if (!body.location) return fail(1000, '活动地点不能为空')
      if (!body.startTime || !body.endTime) return fail(1000, '活动开始时间和结束时间不能为空')
      if (body.endTime <= body.startTime) return fail(1000, '活动结束时间必须晚于开始时间')

      const activity = {
        id: nextActivityId++,
        title: body.title,
        description: body.description || '',
        location: body.location,
        startTime: body.startTime,
        endTime: body.endTime,
        status: body.status || 'OPEN',
        teacherId: user.id
      }
      activities.push(activity)
      return ok(activity.id)
    }
  },
  {
    method: 'PUT',
    pattern: /^\/api\/activities\/(\d+)\/close$/,
    async handler(req, res, params) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')
      const activity = activities.find((a) => a.id === Number(params[0]))
      if (!activity) return fail(404, '活动不存在或已被删除')
      if (activity.teacherId !== user.id) return fail(403, '只能管理自己发布的活动')
      activity.status = 'CLOSED'
      return ok(true)
    }
  },
  {
    method: 'PUT',
    pattern: /^\/api\/activities\/(\d+)$/,
    async handler(req, res, params, body) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')
      const activity = activities.find((a) => a.id === Number(params[0]))
      if (!activity) return fail(404, '活动不存在或已被删除')
      if (activity.teacherId !== user.id) return fail(403, '只能管理自己发布的活动')
      if (body.endTime && body.startTime && body.endTime <= body.startTime) {
        return fail(1000, '活动结束时间必须晚于开始时间')
      }

      activity.title = body.title ?? activity.title
      activity.description = body.description ?? activity.description
      activity.location = body.location ?? activity.location
      activity.startTime = body.startTime ?? activity.startTime
      activity.endTime = body.endTime ?? activity.endTime
      activity.status = body.status ?? activity.status
      return ok(true)
    }
  },
  {
    method: 'DELETE',
    pattern: /^\/api\/activities\/(\d+)$/,
    async handler(req, res, params) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')
      const id = Number(params[0])
      const activity = activities.find((a) => a.id === id)
      if (!activity) return fail(404, '活动不存在或已被删除')
      if (activity.teacherId !== user.id) return fail(403, '只能管理自己发布的活动')

      // 业务规则：已有有效报名的活动不能删除
      const registered = registrations.filter(
        (r) => r.activityId === id && r.status === 'REGISTERED'
      ).length
      if (registered > 0) {
        return fail(1001, `该活动已有 ${registered} 人报名，不能删除，请先关闭报名`)
      }

      // 先清掉子表记录（含已取消的），再删活动 —— 与真实后端的外键处理一致
      registrations = registrations.filter((r) => r.activityId !== id)
      activities = activities.filter((a) => a.id !== id)
      return ok(true)
    }
  },

  // ==================== 报名（REQ-03 / REQ-04 / REQ-06） ====================
  {
    method: 'GET',
    pattern: /^\/api\/registrations\/mine$/,
    async handler(req) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')
      if (user.role !== 'STUDENT') return fail(403, '当前角色无权执行该操作')

      const mine = registrations
        .filter((r) => r.studentId === user.id)
        .map((r) => {
          const activity = activities.find((a) => a.id === r.activityId)
          return {
            registrationId: r.id,
            activityId: r.activityId,
            title: activity ? activity.title : '（活动已删除）',
            location: activity ? activity.location : '-',
            startTime: activity ? activity.startTime : null,
            endTime: activity ? activity.endTime : null,
            registerTime: r.registerTime,
            status: r.status
          }
        })
        .sort((a, b) => b.registerTime.localeCompare(a.registerTime))
      return ok(mine)
    }
  },
  {
    method: 'POST',
    pattern: /^\/api\/activities\/(\d+)\/registrations$/,
    async handler(req, res, params) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')
      if (user.role !== 'STUDENT') return fail(403, '当前角色无权执行该操作')

      const activityId = Number(params[0])
      const activity = activities.find((a) => a.id === activityId)
      if (!activity) return fail(404, '活动不存在或已被删除')
      if (activity.status !== 'OPEN') return fail(3003, '该活动已关闭报名，无法报名')

      const existing = registrations.find(
        (r) => r.activityId === activityId && r.studentId === user.id
      )
      if (existing && existing.status === 'REGISTERED') {
        return fail(3001, '你已经报名过该活动，不能重复报名')
      }
      if (existing) {
        // 曾取消过：复用原记录改回已报名
        existing.status = 'REGISTERED'
        existing.registerTime = nowText()
        return ok(existing.id)
      }

      const registration = {
        id: nextRegistrationId++,
        activityId,
        studentId: user.id,
        registerTime: nowText(),
        status: 'REGISTERED'
      }
      registrations.push(registration)
      return ok(registration.id)
    }
  },
  {
    method: 'DELETE',
    pattern: /^\/api\/activities\/(\d+)\/registrations$/,
    async handler(req, res, params) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')
      if (user.role !== 'STUDENT') return fail(403, '当前角色无权执行该操作')

      const activityId = Number(params[0])
      const existing = registrations.find(
        (r) => r.activityId === activityId && r.studentId === user.id
      )
      if (!existing || existing.status !== 'REGISTERED') {
        return fail(3004, '你尚未报名该活动，无法取消')
      }
      existing.status = 'CANCELLED'
      return ok(true)
    }
  },
  {
    method: 'GET',
    pattern: /^\/api\/activities\/(\d+)\/registrations$/,
    async handler(req, res, params) {
      const user = currentUser(req)
      if (!user) return fail(401, '未登录或登录状态已失效')
      if (user.role !== 'TEACHER') return fail(403, '当前角色无权执行该操作')

      const activityId = Number(params[0])
      const activity = activities.find((a) => a.id === activityId)
      // 教师只能查看自己发布活动的报名名单
      if (!activity || activity.teacherId !== user.id) return ok([])

      const roster = registrations
        .filter((r) => r.activityId === activityId)
        .map((r) => {
          const student = users.find((u) => u.id === r.studentId)
          return {
            registrationId: r.id,
            studentId: r.studentId,
            username: student ? student.username : '-',
            studentName: student ? student.name : '-',
            registerTime: r.registerTime,
            status: r.status
          }
        })
        .sort((a, b) => a.registerTime.localeCompare(b.registerTime))
      return ok(roster)
    }
  }
]

/** 当前时间，格式与后端一致：yyyy-MM-dd HH:mm:ss */
function nowText() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return (
    d.getFullYear() +
    '-' + p(d.getMonth() + 1) +
    '-' + p(d.getDate()) +
    ' ' + p(d.getHours()) +
    ':' + p(d.getMinutes()) +
    ':' + p(d.getSeconds())
  )
}

// ---------------------------------------------------------------------------
// Vite 插件：把上面的路由挂到开发服务器上
// ---------------------------------------------------------------------------

/**
 * 创建一个 Vite 插件，拦截 /api 开头的请求并用上面的路由处理。
 *
 * @returns {import('vite').Plugin}
 */
export default function mockApi() {
  return {
    name: 'mock-api',

    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        // 只处理 /api 开头的请求，其余交给 Vite 正常处理
        if (!req.url || !req.url.startsWith('/api')) {
          return next()
        }

        // 拆分路径与查询参数
        const [path, queryString = ''] = req.url.split('?')
        const query = Object.fromEntries(new URLSearchParams(queryString))
        const method = req.method.toUpperCase()
        // 请求体只在需要时读取（GET 没有 body）
        const body = method === 'GET' || method === 'DELETE' ? {} : await readBody(req)

        // 依次匹配路由
        for (const route of routes) {
          const match = route.pattern.exec(path)
          if (match && route.method === method) {
            const result = await route.handler(req, res, match.slice(1), body, query)
            // 在控制台打印一次，方便观察前端到底调了哪个接口
            const flag = result.code === 0 ? '✔' : '✘'
            console.log(`[mock] ${flag} ${method} ${path} -> code=${result.code} ${result.message}`)
            return send(res, result, result.code === 401 ? 401 : 200)
          }
        }

        // 没有匹配到任何路由
        console.log(`[mock] ? 未实现的接口: ${method} ${path}`)
        return send(res, fail(404, `假接口未实现: ${method} ${path}`))
      })
    }
  }
}
