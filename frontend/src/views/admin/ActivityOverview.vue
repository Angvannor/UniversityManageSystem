<!--
  ============================================================================
  文件：frontend/src/views/admin/ActivityOverview.vue
  用途：系统管理员「活动总览」页面（V2.0 新增，对应 US-11）。
    功能：
      1. 表格列出**平台上全部**活动（不受"只看自己发布的"限制）；
      2. 显示发布教师、时间、地点、名额、状态；
      3. **只读**：没有任何发布 / 修改 / 关闭 / 删除按钮。
    对应接口：GET /api/admin/activities

  【这个页面为什么是只读的】
    系统管理员访谈 A1 的原话是：
      「我可以查看平台中的全部活动，用于监督是否有违规或明显错误的信息，
        但不会代替教师处理具体报名。」
    他的职责是"监督"，不是"管理"。活动的发布、修改、关闭、删除
    始终归发布该活动的教师（教师访谈 T7：「我只能管理自己负责的活动，
    其他老师发布的活动不能由我随意修改」）。
    所以本页刻意不放任何写操作按钮 —— 放了就等于越权。

  【为什么单独做一个接口，不复用 /api/activities】
    两个接口的"范围"不同：/api/activities 是"我能看到的活动"，
    /api/admin/activities 是"平台上的全部活动"。
    分开之后，权限边界在路由表上一眼就能看清：
    哪几个接口属于管理员，哪几个属于教师和学生。
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">活动总览</h2>
        <p class="page-tip">
          平台上的全部活动，共 {{ activities.length }} 个（只读，用于监督违规或明显错误的信息）
        </p>
      </div>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="按标题 / 教师筛选" clearable style="width: 240px">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="status" placeholder="活动状态" clearable style="width: 150px">
        <el-option label="可报名" value="OPEN" />
        <el-option label="已关闭" value="CLOSED" />
        <el-option label="已结束" value="FINISHED" />
      </el-select>
    </div>

    <el-table
      v-loading="loading"
      :data="filtered"
      border
      stripe
      empty-text="平台上还没有活动"
    >
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="title" label="活动标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="teacherName" label="发布教师" width="110" />
      <el-table-column prop="location" label="活动地点" min-width="130" show-overflow-tooltip />

      <el-table-column label="活动时间" min-width="200">
        <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
      </el-table-column>

      <el-table-column label="名额" width="140" align="center">
        <template #default="{ row }">
          <span :class="{ 'quota-full': isFull(row) }">
            {{ row.confirmedCount }} 人已确认
          </span>
          <div class="cell-sub">
            {{ row.capacity == null ? '不限制人数' : `上限 ${row.capacity} 人` }}
          </div>
        </template>
      </el-table-column>

      <el-table-column label="候补" width="80" align="center">
        <template #default="{ row }">
          <span v-if="row.waitlistedCount > 0" class="quota-warn">{{ row.waitlistedCount }} 人</span>
          <span v-else class="cell-sub">—</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.statusText }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-alert
      class="tip-alert"
      type="info"
      :closable="false"
      show-icon
      title="管理员的权限边界"
      description="管理员只查看和监督活动，不参与报名审核、不代替教师决定参加条件，也不能修改或删除他人发布的活动。"
    />
  </div>
</template>

<script setup>
// ---------- 1. 引入依赖 ----------
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { allActivities } from '@/api/admin'

// ---------- 2. 响应式数据 ----------
/** 平台上的全部活动 */
const activities = ref([])

/** 是否正在加载 */
const loading = ref(false)

/** 前端筛选（数据量小，直接在前端过滤即可；数据量大时应下推到 SQL） */
const keyword = ref('')
const status = ref('')

// ---------- 3. 派生数据 ----------
/** 按关键字（标题或教师姓名）与状态过滤 */
const filtered = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return activities.value.filter((a) => {
    if (status.value && a.status !== status.value) {
      return false
    }
    if (!key) {
      return true
    }
    return (
      String(a.title || '').toLowerCase().includes(key) ||
      String(a.teacherName || '').toLowerCase().includes(key)
    )
  })
})

// ---------- 4. 数据加载 ----------
async function loadData() {
  loading.value = true
  try {
    activities.value = await allActivities()
    console.log('全部活动：', activities.value)
  } finally {
    loading.value = false
  }
}

// ---------- 5. 界面辅助函数 ----------
/**
 * 活动状态 → 标签颜色。
 * 中文文案用后端返回的 statusText，保证三个端叫法一致。
 * @param {string} status OPEN / CLOSED / FINISHED
 */
function statusType(status) {
  const map = { OPEN: 'success', CLOSED: 'info', FINISHED: 'warning' }
  return map[status] || 'info'
}

/**
 * 判断活动是否已满员。
 * 必须用 `capacity != null` 判断"有没有设上限"，
 * 未设上限时 capacity 是 null（表示不限制人数），此时永远不算满。
 * @param {object} row 当前行
 */
function isFull(row) {
  return row.capacity != null && row.confirmedCount >= row.capacity
}

// ---------- 6. 生命周期 ----------
onMounted(loadData)
</script>

<style scoped>
.filter-bar {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.cell-sub {
  font-size: 12px;
  color: #909399;
}

.quota-full {
  color: #f56c6c;
  font-weight: 600;
}

.quota-warn {
  color: #e6a23c;
  font-weight: 600;
}

.tip-alert {
  margin-top: 16px;
}
</style>
