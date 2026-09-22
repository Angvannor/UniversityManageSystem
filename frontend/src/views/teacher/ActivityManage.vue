<!--
  ============================================================================
  文件：frontend/src/views/teacher/ActivityManage.vue
  用途：教师端「活动管理」页面（对应需求 REQ-05）。
    功能：
      1. 表格列出我发布的活动（标题、时间、地点、报名人数、状态）；
      2. 「发布活动」按钮打开弹窗填写新活动；
      3. 行内「编辑」复用同一个弹窗并回填原数据；
      4. 「关闭报名」把活动状态改为 CLOSED；
      5. 「删除」二次确认（已有有效报名的活动后端会拒绝）。
    对应接口：GET    /api/activities?onlyMine=true
             POST   /api/activities
             PUT    /api/activities/{id}
             PUT    /api/activities/{id}/close
             DELETE /api/activities/{id}

  【本页核心：弹窗表单（新增与编辑共用一个弹窗）】
    靠 form.id 区分：
      form.id === null  → 新增，调用 createActivity
      form.id 有值      → 编辑，调用 updateActivity
    这样只需要维护一套表单和一套提交逻辑。

  【最容易踩的坑：el-date-picker 的 value-format】
    只写 format 只是"显示格式"，v-model 拿到的仍然是 Date 对象；
    必须再写 value-format="YYYY-MM-DD HH:mm:ss"，
    v-model 才会是后端需要的字符串数组。
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">活动管理</h2>
        <p class="page-tip">共 {{ activities.length }} 个我发布的活动</p>
      </div>
      <div>
        <el-button :icon="Refresh" @click="loadActivities">刷新</el-button>
        <!-- 头部这个按钮负责打开"新增"弹窗 -->
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">发布活动</el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="activities"
      border
      stripe
      empty-text="你还没有发布任何活动"
    >
      <el-table-column prop="title" label="活动标题" min-width="160" show-overflow-tooltip />
      <el-table-column label="活动时间" min-width="220">
        <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
      </el-table-column>
      <el-table-column prop="location" label="活动地点" min-width="130" show-overflow-tooltip />
      <el-table-column label="报名人数" width="110" align="center">
        <template #default="{ row }">{{ row.registeredCount }} 人</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button
            v-if="row.status === 'OPEN'"
            link
            type="warning"
            @click="handleClose(row)"
          >
            关闭报名
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- ============ 发布 / 编辑活动弹窗（两者共用） ============ -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑活动' : '发布活动'"
      width="620px"
      :close-on-click-modal="false"
      @closed="onDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="活动标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入活动标题" maxlength="100" show-word-limit />
        </el-form-item>

        <el-form-item label="活动地点" prop="location">
          <el-input v-model="form.location" placeholder="请输入活动地点" maxlength="100" />
        </el-form-item>

        <!-- 时间范围：一个选择器同时选开始与结束 -->
        <el-form-item label="活动时间" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="活动状态" prop="status">
          <el-select v-model="form.status" style="width: 180px">
            <el-option label="开放报名" value="OPEN" />
            <el-option label="关闭报名" value="CLOSED" />
            <el-option label="已结束" value="FINISHED" />
          </el-select>
        </el-form-item>

        <el-form-item label="活动说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入活动说明" />
        </el-form-item>
      </el-form>

      <!-- 弹窗底部：只放取消和保存，不要放"发布活动"（那是头部按钮的职责） -->
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// ---------- 1. 引入依赖 ----------
// 同一个包里的导入合并成一行；大括号是 {}，不是 ()
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  closeActivity,
  createActivity,
  deleteActivity,
  listActivities,
  updateActivity
} from '@/api/activity'

// ---------- 2. 响应式数据 ----------
/** 我发布的活动列表 */
const activities = ref([])

/** 表格加载状态 */
const loading = ref(false)

/** 弹窗是否显示 */
const dialogVisible = ref(false)

/** 保存中状态（按钮转圈，防止重复提交） */
const saving = ref(false)

/** 表单实例：handleSubmit 里要用它调用校验，缺了会报 formRef is not defined */
const formRef = ref(null)

/**
 * 活动表单。
 * id 是区分"新增 / 编辑"的关键字段：null = 新增，有值 = 编辑。
 */
const form = reactive({
  id: null,
  title: '',
  location: '',
  description: '',
  status: 'OPEN',
  timeRange: [] // 时间选择器用的字符串数组 [开始, 结束]
})

/** 表单校验规则（字段名要与 form 的属性一致） */
const rules = {
  title: [{ required: true, message: '请输入活动标题', trigger: 'blur' }],
  location: [{ required: true, message: '请输入活动地点', trigger: 'blur' }],
  timeRange: [{ required: true, message: '请选择活动开始与结束时间', trigger: 'change' }]
}

// ---------- 3. 数据加载 ----------
/** 查询我发布的活动 */
async function loadActivities() {
  loading.value = true
  try {
    activities.value = await listActivities({ onlyMine: true })
    console.log('我发布的活动：', activities.value)
  } finally {
    loading.value = false
  }
}

// ---------- 4. 弹窗的打开 ----------
/**
 * 打开"发布活动"弹窗：先把表单恢复成初始值，避免带着上次编辑的数据。
 */
function openCreateDialog() {
  Object.assign(form, {
    id: null, // ★ 置空表示新增
    title: '',
    location: '',
    description: '',
    status: 'OPEN',
    timeRange: []
  })
  dialogVisible.value = true
}

/**
 * 打开"编辑活动"弹窗：把该行数据回填到表单。
 * @param {object} row 表格当前行数据
 */
function openEditDialog(row) {
  Object.assign(form, {
    id: row.id, // ★ 有 id 表示编辑
    title: row.title,
    location: row.location,
    description: row.description,
    status: row.status,
    // 接口返回的是 startTime / endTime 两个字段，要拼回数组给时间选择器
    timeRange: [row.startTime, row.endTime]
  })
  dialogVisible.value = true
}

/** 弹窗完全关闭后清掉校验红字（formRef 首次渲染前为 null，用 ?. 兜底） */
function onDialogClosed() {
  formRef.value?.clearValidate()
}

// ---------- 5. 保存（新增 / 编辑） ----------
/**
 * 提交表单：校验 → 组装数据 → 调用对应接口 → 关闭弹窗并刷新列表。
 */
async function handleSubmit() {
  // 1. 表单校验
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  // 2. 组装接口需要的数据：时间选择器给的是数组，接口要两个独立字段
  const payload = {
    title: form.title,
    location: form.location,
    description: form.description,
    status: form.status,
    startTime: form.timeRange[0],
    endTime: form.timeRange[1]
  }

  // 3. 根据是否有 id 决定调用新增还是修改
  saving.value = true
  try {
    if (form.id) {
      await updateActivity(form.id, payload)
      ElMessage.success('活动修改成功')
    } else {
      await createActivity(payload)
      ElMessage.success('活动发布成功')
    }
    dialogVisible.value = false // 关闭弹窗
    await loadActivities() // ★ 刷新列表，数据才是最新的
  } finally {
    saving.value = false
  }
}

// ---------- 6. 关闭报名 / 删除 ----------
/**
 * 关闭报名：把活动状态改为 CLOSED（学生将无法再报名，已有报名记录保留）。
 * @param {object} row 表格当前行数据
 */
async function handleClose(row) {
  try {
    await ElMessageBox.confirm(`确认关闭「${row.title}」的报名吗？`, '关闭报名', { type: 'warning' })
  } catch (e) {
    return // 用户点了取消
  }
  await closeActivity(row.id)
  ElMessage.success('已关闭报名')
  await loadActivities()
}

/**
 * 删除活动。
 * 已有有效报名的活动会被后端拒绝，失败原因由 axios 拦截器统一弹出。
 * @param {object} row 表格当前行数据
 */
async function handleDelete(row) {
  // 确认框里提前提示报名情况，减少无谓的失败
  const tip = row.registeredCount > 0
    ? `该活动已有 ${row.registeredCount} 人报名，删除会失败`
    : '删除后不可恢复'
  try {
    await ElMessageBox.confirm(`确认删除活动「${row.title}」吗？${tip}`, '删除确认', { type: 'warning' })
  } catch (e) {
    return
  }

  try {
    await deleteActivity(row.id)
    ElMessage.success('活动已删除')
  } catch (e) {
    // 失败提示已由拦截器弹出
  } finally {
    // 成功失败都刷新：失败时教师能看到准确的报名人数
    await loadActivities()
  }
}

// ---------- 7. 界面辅助函数 ----------
/**
 * 活动状态：英文 → 中文。
 * @param {string} status OPEN / CLOSED / FINISHED
 */
function statusText(status) {
  const map = { OPEN: '开放报名', CLOSED: '已关闭', FINISHED: '已结束' }
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
onMounted(loadActivities)
</script>
