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

      <el-table-column label="报名状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <!-- 已报名：可以取消 -->
          <el-button
            v-if="row.status === 'REGISTERED'"
            type="danger"
            link
            :loading="cancellingId === row.activityId"
            @click="handleCancel(row)"
          >
            取消报名
          </el-button>

          <!-- 已取消：可以回到活动列表重新报名 -->
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
 * 报名状态：英文 → 中文。
 * @param {string} status REGISTERED / CANCELLED
 * @returns {string}
 */
function statusText(status) {
  return status === 'REGISTERED' ? '已报名' : '已取消'
}

/**
 * 报名状态：英文 → 标签颜色。
 * @param {string} status REGISTERED / CANCELLED
 * @returns {string} success / info
 */
function statusType(status) {
  return status === 'REGISTERED' ? 'success' : 'info'
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
    // 重新拉数据，状态标签才会从"已报名"变成"已取消"
    await loadData()
  } finally {
    cancellingId.value = null
  }
}

// ---------- 6. 生命周期 ----------
onMounted(loadData)
</script>
