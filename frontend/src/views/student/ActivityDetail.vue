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

          <!-- V2.0：显示"人数上限"与"已确定参加"，而不是笼统的已报名人数。
               未设上限时 capacity 是 null，要显示"不限制"而不是空白。 -->
          <el-descriptions-item label="人数上限">
            {{ activity.capacity == null ? '不限制人数' : activity.capacity + ' 人' }}
          </el-descriptions-item>
          <el-descriptions-item label="已确定参加">
            {{ activity.confirmedCount }} 人
            <span v-if="activity.waitlistedCount > 0" class="quota-warn">
              （另有 {{ activity.waitlistedCount }} 人候补）
            </span>
          </el-descriptions-item>

          <!-- V2.0：我的报名状态。直接显示后端给的中文，
               五种状态各有对应文案：待老师审核 / 候补中 / 已确认参加 / 未通过 / 已取消 -->
          <el-descriptions-item label="我的报名状态" :span="2">
            <el-tag v-if="activity.myStatus" :type="myStatusType(activity.myStatus)">
              {{ activity.myStatusText }}
            </el-tag>
            <el-tag v-else type="info">尚未报名</el-tag>
          </el-descriptions-item>

          <!-- V2.0：参加条件。这是学生最需要看到的信息 ——
               访谈 S1/S4 表明学生完全不知道老师按什么条件判断，
               而 S3 说明学生了解活动的唯一途径就是这个详情页。 -->
          <el-descriptions-item label="参加条件" :span="2">
            <span v-if="activity.eligibility">{{ activity.eligibility }}</span>
            <span v-else class="muted">无特殊条件</span>
          </el-descriptions-item>

          <el-descriptions-item label="活动说明" :span="2">
            {{ activity.description || '暂无说明' }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- V2.0：针对当前状态给一条明确的说明，避免学生不知道下一步等什么。
             访谈 S4 的原话是「就算后来有人不参加，我也不太清楚自己还能不能补进去」——
             所以候补状态必须说清楚"你在排队、有人退出就轮到你"。 -->
        <el-alert
          v-if="statusAlert"
          class="status-alert"
          :type="statusAlert.type"
          :closable="false"
          show-icon
          :title="statusAlert.title"
          :description="statusAlert.description"
        />

        <!-- 操作区：按 actionType 三选一 -->
        <div class="action-bar">
          <!-- 情况一：已占位（待审核 / 候补 / 正式参加）→ 取消报名 -->
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
 * 【V2.0 的判断依据变了】
 * V1.5 只看 joined（报没报过）。V2.0 里"报名"是一条状态链，
 * 所以改成看 myStatus：
 *   待审核 / 候补 / 正式参加  → 都算"占着位置"，可以取消（CANCEL）
 *   未通过 / 已取消 / 没报过  → 不占位置，只要活动开放就能再报（REGISTER）
 *
 * @returns {'CANCEL'|'REGISTER'|'NONE'}
 */
const actionType = computed(() => {
  const a = activity.value
  if (!a) {
    return 'NONE' // 数据还没加载出来
  }
  // joined 由后端算好（= 记录处于占位三态之一），比前端自己列状态更不容易漏
  if (a.joined) {
    return 'CANCEL'
  }
  if (a.status === 'OPEN') {
    return 'REGISTER'
  }
  return 'NONE'
})

/**
 * 当前状态下给学生的一句说明。
 *
 * 【为什么必须给这句话】
 * 教师访谈 T6 表明"审核通过 ≠ 正式参加"，学生访谈 S4 又表明
 * 学生根本不知道"有人退出后自己能不能补进去"。
 * 如果不说明，学生报完名就不知道自己在等什么、要等多久、还有没有希望。
 *
 * @returns {{type: string, title: string, description: string}|null}
 */
const statusAlert = computed(() => {
  const a = activity.value
  if (!a || !a.myStatus) {
    return null
  }
  if (a.myStatus === 'PENDING_REVIEW') {
    return {
      type: 'info',
      title: '报名已提交，等待老师审核',
      description: '负责这个活动的老师会判断你是否符合参加条件，通过后还要看当时有没有空位。'
    }
  }
  if (a.myStatus === 'WAITLISTED') {
    return {
      type: 'warning',
      title: '你正在候补等待中',
      description: '名额已满，你已进入候补队列。如果有同学取消报名，空出的名额会按进入候补的先后顺序处理，请留意变化。'
    }
  }
  if (a.myStatus === 'CONFIRMED') {
    return {
      type: 'success',
      title: '你已正式参加这个活动',
      description: '如果临时有事不能参加，请尽早取消报名，把名额让给候补的同学。'
    }
  }
  if (a.myStatus === 'REJECTED') {
    return {
      type: 'error',
      title: '未通过这次活动的参加条件',
      description: '你的报名没有通过老师的审核。如果想再次尝试，可以重新报名。'
    }
  }
  if (a.myStatus === 'CANCELLED') {
    return {
      type: 'info',
      title: '你已取消报名',
      description: '如果改变主意，只要活动还在报名中，你可以重新报名。'
    }
  }
  return null
})

/**
 * 报名状态：英文 → 标签颜色。
 * @param {string} status 五种报名状态之一
 */
function myStatusType(status) {
  const map = {
    PENDING_REVIEW: 'warning',
    CONFIRMED: 'success',
    WAITLISTED: 'warning',
    REJECTED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || 'info'
}

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
  const hasEligibility = Boolean(activity.value.eligibility)
  const tip = hasEligibility
    ? `该活动的参加条件是：\n${activity.value.eligibility}\n\n确认报名吗？报名后需要等老师审核。`
    : '确认报名吗？报名后需要等老师审核。'
  try {
    await ElMessageBox.confirm(tip, '报名确认', {
      type: 'info',
      // 参加条件可能较长，用自定义换行展示更清楚
      customClass: 'pre-wrap-message'
    })
  } catch (e) {
    return // 用户点了取消
  }

  submitting.value = true
  try {
    await registerActivity(activityId)
    ElMessage.success('报名已提交，请等待老师审核')
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

/* 状态说明条：和表格之间留点距离 */
.status-alert {
  margin-top: 16px;
}

/* 表格/描述列表里的次要文字 */
.muted {
  color: #909399;
}

.quota-warn {
  color: #e6a23c;
}
</style>

<style>
/* 报名确认框里要显示多行的参加条件，默认的 message 不会换行 */
.pre-wrap-message .el-message-box__message {
  white-space: pre-wrap;
}
</style>
