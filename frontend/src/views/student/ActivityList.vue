<!--
  ============================================================================
  文件：frontend/src/views/student/ActivityList.vue
  用途：学生端「浏览活动」页面（对应需求 REQ-02）。
    功能：
      1. 顶部筛选栏：关键字搜索 + 状态筛选；
      2. 卡片列表展示活动的标题、状态、地点、时间、发布教师、报名人数；
      3. 按状态显示不同按钮：已报名 / 不可报名 / 立即报名；
      4. 报名前二次确认，报名成功后刷新列表。
    对应接口：GET  /api/activities
             POST /api/activities/{id}/registrations

  【本页用到的 Vue 知识点】
    - ref / reactive 定义响应式数据（JS 里 ref 要写 .value）
    - onMounted 组件挂载后拉取数据
    - v-for 渲染列表（必须配 :key）
    - v-if / v-else-if / v-else 控制按钮显示
    - v-model 绑定筛选条件；@change / @keyup.enter 触发查询
    - v-loading 加载遮罩
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">浏览校园活动</h2>
        <p class="page-tip">共 {{ activities.length }} 个活动</p>
      </div>
    </div>

    <!-- 筛选栏：放在 page-header 外面，单独占一行 -->
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="按活动标题搜索"
        clearable
        style="width: 240px"
        @keyup.enter="loadActivities"
        @clear="loadActivities"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-select
        v-model="query.status"
        placeholder="活动状态"
        clearable
        style="width: 150px"
        @change="loadActivities"
      >
        <el-option label="可报名" value="OPEN" />
        <el-option label="已关闭" value="CLOSED" />
        <el-option label="已结束" value="FINISHED" />
      </el-select>

      <el-button type="primary" @click="loadActivities">查询</el-button>
    </div>

    <!-- 活动卡片列表：v-loading 在数据加载时显示遮罩 -->
    <div v-loading="loading" class="card-grid">
      <el-card v-for="item in activities" :key="item.id" shadow="hover">
        <!-- 卡片头部：标题与状态标签左右分布 -->
        <template #header>
          <div class="card-header">
            <span class="card-title">{{ item.title }}</span>
            <el-tag :type="statusType(item.status)" size="small">
              {{ statusText(item.status) }}
            </el-tag>
          </div>
        </template>

        <!-- 卡片内容：一行行活动信息 -->
        <div class="meta-row">
          <el-icon><Location /></el-icon>
          <span>{{ item.location }}</span>
        </div>
        <div class="meta-row">
          <el-icon><Clock /></el-icon>
          <span>{{ item.startTime }} ~ {{ item.endTime }}</span>
        </div>
        <div class="meta-row">
          <el-icon><UserFilled /></el-icon>
          <span>发布教师：{{ item.teacherName }}</span>
        </div>
        <div class="meta-row">
          <el-icon><Tickets /></el-icon>
          <span>已报名 {{ item.registeredCount }} 人</span>
        </div>

        <!-- 卡片底部：操作按钮，按状态显示不同按钮 -->
        <template #footer>
          <div class="card-footer">
            <el-button link type="primary" @click="goDetail(item)">查看详情</el-button>

            <!-- 情况一：已经报名 -->
            <el-button v-if="item.joined" type="success" disabled>已报名</el-button>

            <!-- 情况二：活动已关闭或已结束（注意 'OPEN' 要加引号，否则会被当成变量） -->
            <el-button v-else-if="item.status !== 'OPEN'" disabled>不可报名</el-button>

            <!-- 情况三：可以报名；:loading 只让被点击的那个按钮转圈 -->
            <el-button
              v-else
              type="primary"
              :loading="submittingId === item.id"
              @click="handleRegister(item)"
            >
              立即报名
            </el-button>
          </div>
        </template>
      </el-card>
    </div>

    <!-- 空状态：必须放在卡片容器【外面】。
         放在里面的话，没有活动就不会渲染卡片，这段代码也就永远不执行 -->
    <el-empty v-if="!loading && activities.length === 0" description="暂无符合条件的活动" />
  </div>
</template>

<script setup>
// ---------- 1. 引入依赖 ----------
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
// 注意方法名是 listActivities：拼写错误会导致整个模块加载失败、页面打不开
import { listActivities } from '@/api/activity'
import { registerActivity } from '@/api/registration'

// ---------- 2. 响应式数据 ----------
const router = useRouter()

/** 活动列表：ref 包数组，JS 里用 activities.value */
const activities = ref([])

/** 筛选条件：reactive 包对象，用 query.keyword 访问 */
const query = reactive({
  keyword: '',
  status: ''
})

/** 是否正在加载（控制遮罩） */
const loading = ref(false)

/** 正在提交报名的活动 id（只让对应按钮转圈，其他按钮不受影响） */
const submittingId = ref(null)

// ---------- 3. 数据加载 ----------
/**
 * 查询活动列表。
 * 用 try/finally 保证：即使请求失败也会关掉加载遮罩，否则页面会一直转圈。
 */
async function loadActivities() {
  loading.value = true
  try {
    activities.value = await listActivities({
      // 空字符串会被后端当作有效的筛选条件，所以空值传 undefined（axios 会忽略该参数）
      keyword: query.keyword || undefined,
      status: query.status || undefined
    })
    console.log('活动列表数据：', activities.value)
  } finally {
    loading.value = false
  }
}

// ---------- 4. 界面辅助函数 ----------
/**
 * 把数据库里的状态英文转换成界面上的中文。
 * @param {string} status OPEN / CLOSED / FINISHED
 * @returns {string} 中文说明
 */
function statusText(status) {
  const map = {
    OPEN: '可报名',
    CLOSED: '已关闭',
    FINISHED: '已结束' // 拼写注意：两个 S、两个 E
  }
  return map[status] || status
}

/**
 * 把状态转换成 el-tag 的颜色类型。
 * @param {string} status 状态英文
 * @returns {string} success / info / warning
 */
function statusType(status) {
  const map = {
    OPEN: 'success',
    CLOSED: 'info',
    FINISHED: 'warning'
  }
  return map[status] || 'info'
}

/** 跳转活动详情页 */
function goDetail(item) {
  router.push(`/activities/${item.id}`)
}

// ---------- 5. 报名 ----------
/**
 * 学生报名活动：二次确认 → 调用接口 → 刷新列表。
 * @param {object} item 当前卡片对应的活动对象
 */
async function handleRegister(item) {
  // 二次确认：用户点"取消"会走 catch，直接 return 不做任何事
  try {
    await ElMessageBox.confirm(`确认报名「${item.title}」吗？`, '报名确认', { type: 'info' })
  } catch (e) {
    return
  }

  submittingId.value = item.id
  try {
    await registerActivity(item.id)
    ElMessage.success('报名成功')
    // ★ 关键：重新拉取数据，报名人数和按钮状态（已报名）才会更新
    await loadActivities()
  } catch (e) {
    // 失败原因（如"你已经报名过该活动"）已由 axios 响应拦截器弹出
  } finally {
    submittingId.value = null
  }
}

// ---------- 6. 生命周期 ----------
// 组件挂载完成后加载一次数据（页面打开时自动刷新）
onMounted(loadActivities)
</script>
