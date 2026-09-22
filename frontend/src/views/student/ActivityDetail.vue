<!--
  ============================================================================
  文件：frontend/src/views/student/ActivityDetail.vue
  用途：学生端「活动详情」页面（对应需求 REQ-02，是「查看并报名」流程的详情环节）。
    功能：
      1. 从 URL 取活动 id（路由 /activities/:id）；
      2. 用 el-descriptions 展示活动详情；
      3. 根据状态显示「立即报名 / 取消报名 / 不可报名」三种操作；
      4. 操作完成后刷新详情，保证人数与按钮状态同步；
      5. 活动不存在时给出提示。
    对应接口：GET    /api/activities/{id}
             POST   /api/activities/{id}/registrations
             DELETE /api/activities/{id}/registrations

  【本页知识点】
    - useRoute().params.id 取路由参数（注意是字符串）
    - computed 派生状态：按钮该显示什么由 actionType 算出来，activity 变化时自动重算
    - activity?.title 可选链：数据还没加载出来（null）时不会报错
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">活动详情</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <!-- 加载遮罩套在外层：这样首次加载时也能看到转圈效果 -->
    <div v-loading="loading">
      <el-card v-if="activity">
        <template #header>
          <div class="card-header">
            <span class="card-title">{{ activity.title }}</span>
            <el-tag :type="statusType(activity.status)">{{ statusText(activity.status) }}</el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="活动地点">{{ activity.location }}</el-descriptions-item>
          <el-descriptions-item label="发布教师">{{ activity.teacherName }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ activity.startTime }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ activity.endTime }}</el-descriptions-item>
          <el-descriptions-item label="已报名人数">{{ activity.registeredCount }} 人</el-descriptions-item>
          <el-descriptions-item label="我的状态">
            <el-tag :type="activity.joined ? 'success' : 'info'">
              {{ activity.joined ? '已报名' : '未报名' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="活动说明" :span="2">
            {{ activity.description || '暂无说明' }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 操作区：按 actionType 三选一 -->
        <div class="action-bar">
          <!-- 情况一：已报名 → 取消报名 -->
          <el-button
            v-if="actionType === 'CANCEL'"
            type="danger"
            plain
            :loading="submitting"
            @click="handleCancel"
          >
            取消报名
          </el-button>

          <!-- 情况二：可报名 → 立即报名 -->
          <el-button
            v-else-if="actionType === 'REGISTER'"
            type="primary"
            :loading="submitting"
            @click="handleRegister"
          >
            立即报名
          </el-button>

          <!-- 情况三：不可报名 → 提示原因 -->
          <el-alert
            v-else
            type="warning"
            :closable="false"
            show-icon
            :title="disabledReason || '当前不可报名'"
          />
        </div>
      </el-card>

      <!-- 活动不存在（或已被删除）：放在卡片外层，且加载完成后才判断 -->
      <el-empty v-if="!loading && !activity" description="活动不存在或已被删除">
        <el-button type="primary" @click="$router.push('/activities')">返回活动列表</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
// ---------- 1. 引入依赖 ----------
// 同一个包里的多个导入写在一行，保持整洁
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getActivity } from '@/api/activity'
import { cancelRegistration, registerActivity } from '@/api/registration'

// ---------- 2. 路由参数 ----------
const route = useRoute()
// note: params.id 是【字符串】，接口需要数字时会自动转换；
// 变量名要和声明一致（写成 const router = useRoute() 再用 route 会报 route is not defined）
const activityId = route.params.id

// ---------- 3. 响应式数据 ----------
/** 活动详情对象；未加载完时为 null，所以模板里访问字段要用 v-if 或 ?. */
const activity = ref(null)

/** 是否正在加载 */
const loading = ref(false)

/** 是否正在提交（报名 / 取消报名） */
const submitting = ref(false)

// ---------- 4. 派生状态 ----------
/**
 * 当前该显示哪种操作按钮。
 * computed 会随 activity 的变化自动重算，报名成功后按钮自动切换。
 *
 * @returns {'CANCEL'|'REGISTER'|'NONE'}
 *   CANCEL   已报名 → 显示「取消报名」
 *   REGISTER 可报名 → 显示「立即报名」
 *   NONE     不可报名 → 显示提示
 */
const actionType = computed(() => {
  if (!activity.value) {
    return 'NONE' // 数据还没加载出来
  }
  if (activity.value.joined) {
    return 'CANCEL' // 注意：模板里比较的也是 'CANCEL'，两边必须一致
  }
  if (activity.value.status === 'OPEN') {
    return 'REGISTER'
  }
  return 'NONE'
})

/** 不可报名时的原因提示 */
const disabledReason = computed(() => {
  const a = activity.value
  if (!a) {
    return ''
  }
  if (a.status === 'CLOSED') {
    return '该活动已关闭报名'
  }
  if (a.status === 'FINISHED') {
    return '该活动已结束'
  }
  return ''
})

// ---------- 5. 数据加载 ----------
/**
 * 查询活动详情。
 */
async function loadDetail() {
  loading.value = true
  try {
    activity.value = await getActivity(activityId)
    console.log('活动详情：', activity.value)
  } finally {
    loading.value = false
  }
}

// ---------- 6. 报名与取消 ----------
/** 报名：二次确认 → 调用接口 → 刷新详情 */
async function handleRegister() {
  try {
    await ElMessageBox.confirm(`确认报名「${activity.value.title}」吗？`, '报名确认', { type: 'info' })
  } catch (e) {
    return // 用户点了取消
  }

  submitting.value = true
  try {
    await registerActivity(activityId)
    ElMessage.success('报名成功')
    await loadDetail() // ★ 刷新详情，按钮才会变成「取消报名」
  } finally {
    submitting.value = false
  }
}

/** 取消报名：二次确认 → 调用接口 → 刷新详情 */
async function handleCancel() {
  try {
    await ElMessageBox.confirm('确认取消报名吗？取消后名额将被释放。', '取消报名', { type: 'warning' })
  } catch (e) {
    return
  }

  submitting.value = true
  try {
    await cancelRegistration(activityId)
    ElMessage.success('已取消报名')
    await loadDetail() // ★ 刷新详情，按钮变回「立即报名」
  } finally {
    submitting.value = false
  }
}

// ---------- 7. 界面辅助函数 ----------
/**
 * 活动状态：英文 → 中文。
 * @param {string} status OPEN / CLOSED / FINISHED
 */
function statusText(status) {
  const map = { OPEN: '可报名', CLOSED: '已关闭', FINISHED: '已结束' }
  return map[status] || status
}

/**
 * 活动状态：英文 → 标签颜色。
 * @param {string} status OPEN / CLOSED / FINISHED
 */
function statusType(status) {
  const map = { OPEN: 'success', CLOSED: 'info', FINISHED: 'warning' }
  return map[status] || 'info'
}

// ---------- 8. 生命周期 ----------
onMounted(loadDetail)
</script>

<style scoped>
.action-bar {
  margin-top: 20px;
}
</style>
