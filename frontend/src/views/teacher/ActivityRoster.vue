<!--
  ============================================================================
  文件：frontend/src/views/teacher/ActivityRoster.vue
  用途：教师端「报名名单」页面（REQ-06，以及 V2.0 新增的 US-07 审核、US-09 递补）。
    功能：
      1. 下拉选择自己发布的活动；
      2. 顶部摘要显示「已确定参加 / 人数上限 / 候补 / 待审核」四个数字；
      3. 名单按状态分成三个页签：待审核 / 候补 / 正式参加；
      4. 待审核页签里可以「通过」「驳回」；
      5. 候补页签里可以「递补给此人」（没有空位时禁用）。
    对应接口：GET /api/activities?onlyMine=true
              GET /api/activities/{id}/registrations          → 分组名单 + 三个人数
              PUT /api/activities/{id}/registrations/{sid}/approve
              PUT /api/activities/{id}/registrations/{sid}/reject
              PUT /api/activities/{id}/registrations/{sid}/promote

  【V2.0 相对 V1.5 的三个变化】
    ① 返回结构变了：从"一个大列表"变成 { 三个分组 + 三个人数 + capacity + full }。
       所以本页从"一张表 + 前端自己数人数"改成"三个页签 + 直接用后端给的数字"。
       为什么人数不由前端自己数：一旦数错（比如把已取消的也算进去），
       教师会看到一个和实际不符的数字，进而做出错误判断。
    ② 「通过」之后是正式参加还是候补，由后端按名额自动决定 ——
       前端不需要（也不应该）自己算，只要刷新看结果。
    ③ 候补名单的顺序是后端按"进入候补的时间"排好的，
       前端必须原样展示，不能再按报名时间重排（那样顺序就错了）。

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

    <!-- 情况二：选择活动并查看名单 -->
    <template v-else>
      <div class="filter-bar">
        <el-select
          v-model="selectedActivityId"
          placeholder="请选择活动"
          style="width: 380px"
          @change="loadRoster"
        >
          <el-option
            v-for="item in activities"
            :key="item.id"
            :label="activityLabel(item)"
            :value="item.id"
          />
        </el-select>

        <el-input v-model="keyword" placeholder="按学号 / 姓名筛选" clearable style="width: 220px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <!-- 活动摘要：四个数字直接用好后端返回的，前端不自己数 -->
      <el-descriptions v-if="currentActivity" :column="4" border>
        <el-descriptions-item label="活动标题">{{ currentActivity.title }}</el-descriptions-item>
        <el-descriptions-item label="活动地点">{{ currentActivity.location }}</el-descriptions-item>
        <el-descriptions-item label="活动时间">{{ currentActivity.startTime }}</el-descriptions-item>
        <el-descriptions-item label="人数上限">
          {{ roster.capacity == null ? '不限制' : roster.capacity + ' 人' }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="currentActivity" class="stat-row">
        <el-statistic title="已确定参加" :value="roster.confirmedCount" suffix="人" />
        <el-statistic title="候补" :value="roster.waitlistedCount" suffix="人" />
        <el-statistic title="待审核" :value="roster.pendingReviewCount" suffix="人" />
        <div class="stat-note">
          <el-tag v-if="roster.full" type="danger" effect="dark">名额已满</el-tag>
          <el-tag v-else-if="roster.capacity != null" type="success">还有空位</el-tag>
          <el-tag v-else type="info">不限制人数</el-tag>
        </div>
      </div>

      <!-- 三个分组用页签展示：待审核排在最前，因为那是教师要动手处理的事 -->
      <el-tabs v-model="activeTab" class="roster-tabs">
        <!-- ---------- 待审核 ---------- -->
        <el-tab-pane :label="`待审核 (${filteredPending.length})`" name="pending">
          <el-table
            v-loading="loading"
            :data="filteredPending"
            border
            stripe
            empty-text="没有待审核的报名"
          >
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="username" label="学号" min-width="140" />
            <el-table-column prop="studentName" label="姓名" min-width="120" />
            <el-table-column prop="registerTime" label="报名时间" min-width="180" />
            <el-table-column label="操作" width="170" align="center">
              <template #default="{ row }">
                <!-- 通过之后是正式参加还是候补由后端按名额决定，这里不用判断 -->
                <el-button link type="success" @click="handleApprove(row)">通过</el-button>
                <el-button link type="danger" @click="handleReject(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ---------- 候补 ---------- -->
        <el-tab-pane :label="`候补 (${filteredWaitlisted.length})`" name="waitlist">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="候补按「进入候补的时间」排序，先进入的排在前面"
            description="递补不强制按顺序：前面的人联系不上时，可以直接把名额给后面的人。"
            style="margin-bottom: 12px"
          />
          <el-table
            v-loading="loading"
            :data="filteredWaitlisted"
            border
            stripe
            empty-text="没有候补的学生"
          >
            <el-table-column label="候补序" width="80" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column prop="username" label="学号" min-width="140" />
            <el-table-column prop="studentName" label="姓名" min-width="120" />
            <el-table-column prop="registerTime" label="报名时间" min-width="180" />
            <!-- 这一列是这个页签的重点：它才是排序依据，和报名时间可能顺序相反 -->
            <el-table-column prop="reviewTime" label="进入候补时间" min-width="180" />
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  :disabled="roster.full"
                  @click="handlePromote(row)"
                >
                  递补给此人
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ---------- 正式参加 ---------- -->
        <el-tab-pane :label="`正式参加 (${filteredConfirmed.length})`" name="confirmed">
          <el-table
            v-loading="loading"
            :data="filteredConfirmed"
            border
            stripe
            empty-text="还没有正式参加的学生"
          >
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="username" label="学号" min-width="140" />
            <el-table-column prop="studentName" label="姓名" min-width="120" />
            <el-table-column prop="registerTime" label="报名时间" min-width="180" />
            <el-table-column prop="reviewTime" label="确认时间" min-width="180" />
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <p v-if="keyword.trim() && totalFiltered === 0" class="page-tip">
        没有匹配「{{ keyword }}」的报名记录
      </p>
    </template>
  </div>
</template>

<script setup>
// ---------- 1. 引入依赖 ----------
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listActivities } from '@/api/activity'
import {
  approveRegistration,
  promoteRegistration,
  registrationsOfActivity,
  rejectRegistration
} from '@/api/registration'

// ---------- 2. 响应式数据 ----------
/** 我发布的活动列表（填充下拉框） */
const activities = ref([])

/** 当前选中的活动 id */
const selectedActivityId = ref(null)

/**
 * 当前活动的分组名单。
 * 结构由后端决定：{ capacity, confirmedCount, waitlistedCount, pendingReviewCount,
 *                  full, pendingReview: [], waitlisted: [], confirmed: [] }
 * 初始值要给出完整的空结构，否则模板里访问 roster.confirmedCount 会取到 undefined。
 */
const roster = ref(emptyRoster())

/** 是否正在加载名单 */
const loading = ref(false)

/** 名单筛选关键字（三个页签共用） */
const keyword = ref('')

/** 当前选中的页签，默认停在"待审核" */
const activeTab = ref('pending')

/** 空名单结构 */
function emptyRoster() {
  return {
    capacity: null,
    confirmedCount: 0,
    waitlistedCount: 0,
    pendingReviewCount: 0,
    full: false,
    pendingReview: [],
    waitlisted: [],
    confirmed: []
  }
}

// ---------- 3. 派生数据 ----------
/** 当前选中的活动对象（用于顶部摘要显示） */
const currentActivity = computed(
  () => activities.value.find((a) => a.id === selectedActivityId.value) || null
)

/**
 * 通用筛选：按关键字匹配学号或姓名。
 * 三个分组都要筛，抽出来避免写三遍一样的逻辑。
 * @param {Array} list 待筛选的名单
 */
function filterByKeyword(list) {
  const key = keyword.value.trim().toLowerCase()
  if (!key) {
    return list
  }
  return list.filter(
    (r) =>
      String(r.username || '').toLowerCase().includes(key) ||
      String(r.studentName || '').toLowerCase().includes(key)
  )
}

const filteredPending = computed(() => filterByKeyword(roster.value.pendingReview))
const filteredWaitlisted = computed(() => filterByKeyword(roster.value.waitlisted))
const filteredConfirmed = computed(() => filterByKeyword(roster.value.confirmed))

/** 三个分组筛选后的总条数，用于判断"是不是关键字筛没了" */
const totalFiltered = computed(
  () => filteredPending.value.length + filteredWaitlisted.value.length + filteredConfirmed.value.length
)

// ---------- 4. 数据加载 ----------
/** 查询我发布的活动；拿到后默认选中第一个并加载其名单 */
async function loadActivities() {
  activities.value = await listActivities({ onlyMine: true })
  console.log('我发布的活动：', activities.value)

  if (activities.value.length > 0) {
    selectedActivityId.value = activities.value[0].id
    await loadRoster() // ★ 默认选中后立刻拉名单，页面打开就有内容
  } else {
    roster.value = emptyRoster() // 一个活动都没有时清空，避免残留上次的数据
  }
}

/** 查询当前选中活动的分组名单 */
async function loadRoster() {
  if (!selectedActivityId.value) {
    roster.value = emptyRoster()
    return
  }

  loading.value = true
  try {
    roster.value = await registrationsOfActivity(selectedActivityId.value)
    console.log('分组名单：', roster.value)
    // 拉完名单后停在"有待审核就看待审核，否则看候补，再否则看正式参加"
    if (roster.value.pendingReviewCount > 0) {
      activeTab.value = 'pending'
    } else if (roster.value.waitlistedCount > 0) {
      activeTab.value = 'waitlist'
    } else {
      activeTab.value = 'confirmed'
    }
  } catch (e) {
    // 失败提示由 axios 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

// ---------- 5. 审核与递补 ----------
/**
 * 审核通过。
 *
 * 【注意】通过之后是"正式参加"还是"候补"，由后端按名额自动判断，
 * 前端不自己算 —— 算错了教师会看到与实际不符的状态。
 * 这里只需刷新名单看结果。
 *
 * @param {object} row 名单当前行
 */
async function handleApprove(row) {
  await approveRegistration(selectedActivityId.value, row.studentId)
  ElMessage.success(`已通过 ${row.studentName} 的报名`)
  await loadRoster()
}

/**
 * 审核驳回（判定为不符合参加条件）。
 * @param {object} row 名单当前行
 */
async function handleReject(row) {
  try {
    await ElMessageBox.confirm(
      `确认驳回 ${row.studentName} 的报名吗？驳回后对方会看到「未通过」。`,
      '驳回确认',
      { type: 'warning' }
    )
  } catch (e) {
    return // 用户点了取消
  }
  await rejectRegistration(selectedActivityId.value, row.studentId)
  ElMessage.success(`已驳回 ${row.studentName} 的报名`)
  await loadRoster()
}

/**
 * 候补递补：把空出的名额给这位候补学生。
 * 没有空位时按钮是禁用的（roster.full），后端也会再校验一次并返回 3006。
 * @param {object} row 名单当前行
 */
async function handlePromote(row) {
  try {
    await ElMessageBox.confirm(
      `确认把名额递给候补的 ${row.studentName} 吗？递补后他就成为正式参加。`,
      '递补确认',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  await promoteRegistration(selectedActivityId.value, row.studentId)
  ElMessage.success(`已把名额递给 ${row.studentName}`)
  await loadRoster()
}

// ---------- 6. 界面辅助函数 ----------
/**
 * 下拉框里每个活动的显示文案。
 * V2.0 改成"已确认 / 上限"，因为教师关心的是名额而不是报名总数。
 * @param {object} item 活动对象
 */
function activityLabel(item) {
  const quota = item.capacity == null ? '不限制' : item.capacity
  return `${item.title}（已确认 ${item.confirmedCount} / ${quota}）`
}

// ---------- 7. 生命周期 ----------
onMounted(loadActivities)
</script>

<style scoped>
.filter-bar {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
}

/* 四个统计数字横向排布 */
.stat-row {
  display: flex;
  align-items: center;
  gap: 48px;
  margin: 16px 0 8px;
  padding: 12px 20px;
  background: #fff;
  border-radius: 4px;
}

.stat-note {
  margin-left: auto;
}

.roster-tabs {
  margin-top: 8px;
}
</style>
