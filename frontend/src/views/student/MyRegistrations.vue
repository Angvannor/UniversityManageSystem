<!--
  ============================================================================
  文件：frontend/src/views/student/MyRegistrations.vue
  用途：学生端「我的报名」页面（对应需求 REQ-04）。
    功能：
      1. 用表格展示我的报名记录（含已取消的）；
      2. 状态用不同颜色的标签区分；
      3. 对「已报名」的记录可以取消报名（二次确认后刷新）；
      4. 已取消的记录可以点「重新报名」回到活动列表；
      5. 没有数据时表格内部显示提示文字。
    对应接口：GET    /api/registrations/mine
             DELETE /api/activities/{id}/registrations

  【本页新知识点：el-table 表格组件】
    el-table 是「声明式」的：只声明有哪些列，它自己渲染每一行。
      <el-table :data="records">              ← 把数组交给表格
        <el-table-column prop="title" label="活动名称" />   ← prop 指定取哪个字段
        <el-table-column label="自定义列">
          <template #default="{ row }">       ← 需要自定义内容时用插槽，row 是当前行数据
            ...
          </template>
        </el-table-column>
      </el-table>
    注意：el-table 的子元素只能是 el-table-column，不要往里塞 el-empty 等其它组件。
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">我的报名</h2>
        <p class="page-tip">共 {{ records.length }} 条报名记录</p>
      </div>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <!-- 表格：empty-text 在数据为空时显示在表格内部 -->
    <el-table
      v-loading="loading"
      :data="records"
      border
      stripe
      empty-text="你还没有报名任何活动"
    >
      <el-table-column prop="title" label="活动名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="location" label="活动地点" min-width="140" show-overflow-tooltip />

      <!-- 活动时间：把开始与结束拼起来显示，所以用插槽 -->
      <el-table-column label="活动时间" min-width="200">
        <template #default="{ row }">
          {{ row.startTime }} ~ {{ row.endTime }}
        </template>
      </el-table-column>

      <!-- 报名时间：19 个字符，宽度给足否则会被截断 -->
      <el-table-column prop="registerTime" label="报名时间" min-width="170" />

      <!-- V2.0 新增：审核时间。
           它在候补场景下也是"进入候补的时间"，是候补队列的排序依据，
           学生看到它能明白自己大概排在什么位置。 -->
      <el-table-column label="老师处理时间" min-width="170">
        <template #default="{ row }">
          <span v-if="row.reviewTime">{{ row.reviewTime }}</span>
          <span v-else class="muted">待处理</span>
        </template>
      </el-table-column>

      <el-table-column label="报名状态" width="130" align="center">
        <template #default="{ row }">
          <!-- V2.0：直接用后端返回的 statusText，五种状态的中文在三个界面里保持一致 -->
          <el-tag :type="statusType(row.status)">{{ row.statusText }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <!-- 占位状态（待审核 / 候补 / 正式参加）：可以取消 -->
          <el-button
            v-if="occupiesSlot(row.status)"
            type="danger"
            link
            :loading="cancellingId === row.activityId"
            @click="handleCancel(row)"
          >
            取消报名
          </el-button>

          <!-- 未通过 / 已取消：可以回到详情页重新报名 -->
          <el-button v-else type="primary" link @click="goDetail(row)">重新报名</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
// ---------- 1. 引入依赖 ----------
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
// 方法名是 myRegistrations（复数），路径是 registration（结尾是 tion）
import { cancelRegistration, myRegistrations } from '@/api/registration'

// ---------- 2. 响应式数据 ----------
const router = useRouter()

/** 报名记录列表 */
const records = ref([])

/** 是否正在加载（控制表格遮罩） */
const loading = ref(false)

/** 正在取消的记录对应的活动 id（只让对应按钮转圈） */
const cancellingId = ref(null)

// ---------- 3. 数据加载 ----------
/**
 * 查询我的报名记录。
 * try/finally 保证请求失败时也会关掉遮罩。
 */
async function loadData() {
  loading.value = true
  try {
    records.value = await myRegistrations()
    console.log('我的报名数据：', records.value)
  } finally {
    loading.value = false
  }
}

// ---------- 4. 界面辅助函数 ----------
/**
 * 判断某个状态是否"占着位置"（对应后端的 occupiesSlot()）。
 *
 * 待审核 / 候补 / 正式参加 这三种状态都算"报了名"，学生可以取消；
 * 未通过（被老师驳回）和已取消不占位置，可以重新报名。
 *
 * 【为什么不直接判断某一个具体状态】
 * V1.5 只有"已报名/已取消"两种，写成 `status === 'REGISTERED'` 没问题；
 * V2.0 变成五种之后，如果这里漏写一种，就会出现"报名了却没有取消按钮"
 * 或者"没报上却显示取消报名"的错误。把判断集中到一个函数里，只改一处。
 *
 * @param {string} status 报名状态
 * @returns {boolean} true 表示可以取消
 */
function occupiesSlot(status) {
  return ['PENDING_REVIEW', 'WAITLISTED', 'CONFIRMED'].includes(status)
}

/**
 * 报名状态：英文 → 标签颜色。
 *
 * 中文文案不在这里映射 —— 后端每条记录都返回了 statusText，
 * 直接用后端的值，保证学生端、教师端、管理员端三处的叫法完全一致。
 *
 * @param {string} status 五种报名状态之一
 * @returns {string} Element Plus 的标签类型
 */
function statusType(status) {
  const map = {
    PENDING_REVIEW: 'warning',
    CONFIRMED: 'success',
    WAITLISTED: 'warning',
    REJECTED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || 'info'
}

/**
 * 跳转到活动详情页（已取消的记录可从这里重新报名）。
 * @param {object} row 当前行数据
 */
function goDetail(row) {
  router.push(`/activities/${row.activityId}`)
}

// ---------- 5. 取消报名 ----------
/**
 * 取消报名：二次确认 → 调用接口 → 刷新列表。
 * @param {object} row 当前行数据
 */
async function handleCancel(row) {
  // 注意传的是 row.activityId：取消报名接口需要【活动 id】，不是报名记录 id
  try {
    await ElMessageBox.confirm(`确认取消报名「${row.title}」吗？`, '取消报名', { type: 'warning' })
  } catch (e) {
    return // 用户点了取消
  }

  cancellingId.value = row.activityId
  try {
    await cancelRegistration(row.activityId)
    ElMessage.success('已取消报名')
    // 重新拉数据，状态标签才会从"待老师审核/候补中/已确认参加"变成"已取消"
    await loadData()
  } finally {
    cancellingId.value = null
  }
}

// ---------- 6. 生命周期 ----------
onMounted(loadData)
</script>

<style scoped>
/* 表格里的次要文字（例如"待处理"） */
.muted {
  color: #909399;
}
</style>
