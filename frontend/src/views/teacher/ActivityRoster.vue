<!--
  ============================================================================
  文件：frontend/src/views/teacher/ActivityRoster.vue
  用途：教师端「报名名单」页面（对应需求 REQ-06）。
    功能：
      1. 下拉选择自己发布的活动；
      2. 展示该活动的报名名单（学号、姓名、报名时间、状态）；
      3. 顶部显示活动摘要与有效报名人数；
      4. 支持按学号 / 姓名在前端筛选名单；
      5. 没发布过活动时提示去发布。
    对应接口：GET /api/activities?onlyMine=true
             GET /api/activities/{id}/registrations

  【本页数据流】
    ① listActivities({ onlyMine: true })  → 填充下拉框，并默认选中第一个
    ② registrationsOfActivity(活动id)     → 拉取该活动的报名名单
    先有活动列表，才知道要查哪个活动的名单。

  【注意】SFC 最外层的 <template> 是模板容器，上面写 v-if 是无效的（编译时会被忽略），
         条件渲染要写在里面的真实元素上。
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">报名名单</h2>
        <p class="page-tip">共 {{ activities.length }} 个我发布的活动</p>
      </div>
      <el-button :icon="Refresh" @click="loadActivities">刷新</el-button>
    </div>

    <!-- 情况一：还没有发布过任何活动 -->
    <el-empty v-if="activities.length === 0" description="你还没有发布任何活动">
      <el-button type="primary" @click="$router.push('/teacher/activities')">去发布活动</el-button>
    </el-empty>

    <!-- 情况二：选择活动并查看名单（v-else 与上面的 v-if 配对） -->
    <template v-else>
      <!-- 筛选栏：先选活动，再按关键字筛选名单 -->
      <div class="filter-bar">
        <el-select
          v-model="selectedActivityId"
          placeholder="请选择活动"
          style="width: 320px"
          @change="loadRoster"
        >
          <el-option
            v-for="item in activities"
            :key="item.id"
            :label="`${item.title}（已报名 ${item.registeredCount} 人）`"
            :value="item.id"
          />
        </el-select>

        <el-input v-model="keyword" placeholder="按学号 / 姓名筛选" clearable style="width: 220px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <!-- 活动摘要：显示当前选中活动的基本信息与有效报名人数 -->
      <el-descriptions v-if="currentActivity" :column="4" border>
        <el-descriptions-item label="活动标题">{{ currentActivity.title }}</el-descriptions-item>
        <el-descriptions-item label="活动地点">{{ currentActivity.location }}</el-descriptions-item>
        <el-descriptions-item label="活动时间">{{ currentActivity.startTime }}</el-descriptions-item>
        <el-descriptions-item label="有效报名">{{ registeredCount }} 人</el-descriptions-item>
      </el-descriptions>

      <!-- 名单表格：数据用 filteredRecords（已经过关键字筛选） -->
      <el-table
        v-loading="loading"
        :data="filteredRecords"
        border
        stripe
        style="margin-top: 16px"
        empty-text="该活动暂无学生报名"
      >
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="username" label="学号" min-width="140" />
        <el-table-column prop="studentName" label="姓名" min-width="120" />
        <el-table-column prop="registerTime" label="报名时间" min-width="180" />
        <el-table-column label="报名状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 筛选后没有结果时给出提示（表格的 empty-text 已覆盖"无报名"的情况） -->
      <p v-if="records.length > 0 && filteredRecords.length === 0" class="page-tip">
        没有匹配「{{ keyword }}」的报名记录
      </p>
    </template>
  </div>
</template>

<script setup>
// ---------- 1. 引入依赖 ----------
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listActivities } from '@/api/activity'
// 方法名是 registrationsOfActivity（复数 registrations），必须用 Alt+Enter 生成导入
import { registrationsOfActivity } from '@/api/registration'

// ---------- 2. 响应式数据 ----------
/** 我发布的活动列表（填充下拉框） */
const activities = ref([])

/** 当前选中的活动 id */
const selectedActivityId = ref(null)

/** 当前活动的报名名单 */
const records = ref([])

/** 是否正在加载名单 */
const loading = ref(false)

/** 名单筛选关键字 */
const keyword = ref('')

// ---------- 3. 派生数据 ----------
/**
 * 有效报名人数：只统计状态为 REGISTERED 的记录。
 * 注意 .filter() 返回的是【数组】，必须再取 .length 才是人数。
 */
const registeredCount = computed(
  () => records.value.filter((r) => r.status === 'REGISTERED').length
)

/** 当前选中的活动对象（用于顶部摘要显示） */
const currentActivity = computed(
  () => activities.value.find((a) => a.id === selectedActivityId.value) || null
)

/**
 * 按关键字过滤后的名单。
 * 依赖 keyword，输入框一变会自动重算，不需要写 @input 事件。
 */
const filteredRecords = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) {
    return records.value
  }
  return records.value.filter(
    (r) =>
      String(r.username || '').toLowerCase().includes(key) ||
      String(r.studentName || '').toLowerCase().includes(key)
  )
})

// ---------- 4. 数据加载 ----------
/**
 * 查询我发布的活动；拿到后默认选中第一个并加载其名单。
 */
async function loadActivities() {
  activities.value = await listActivities({ onlyMine: true })
  // 打印时要用 .value，否则控制台只会显示 RefImpl 包装对象
  console.log('我发布的活动：', activities.value)

  if (activities.value.length > 0) {
    selectedActivityId.value = activities.value[0].id
    await loadRoster() // ★ 默认选中后立刻拉名单，页面打开就有内容
  } else {
    // 一个活动都没有时清空名单，避免残留上次的数据
    records.value = []
  }
}

/**
 * 查询当前选中活动的报名名单。
 */
async function loadRoster() {
  if (!selectedActivityId.value) {
    records.value = []
    return
  }

  loading.value = true
  try {
    records.value = await registrationsOfActivity(selectedActivityId.value)
    console.log('报名名单：', records.value)
  } catch (e) {
    // 失败提示由 axios 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

// ---------- 5. 界面辅助函数 ----------
/**
 * 报名状态：英文 → 中文。
 * @param {string} status REGISTERED / CANCELLED
 */
function statusText(status) {
  return status === 'REGISTERED' ? '已报名' : '已取消'
}

/**
 * 报名状态：英文 → 标签颜色。
 * @param {string} status REGISTERED / CANCELLED
 */
function statusType(status) {
  return status === 'REGISTERED' ? 'success' : 'info'
}

// ---------- 6. 生命周期 ----------
onMounted(loadActivities)
</script>

<style scoped>
/* 摘要与表格之间的间距由内联样式控制，这里只微调筛选栏 */
.filter-bar {
  margin-bottom: 16px;
}
</style>
