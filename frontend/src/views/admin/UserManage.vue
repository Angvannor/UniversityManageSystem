<!--
  ============================================================================
  文件：frontend/src/views/admin/UserManage.vue
  用途：系统管理员「用户管理」页面（V2.0 新增，对应 US-12、US-13）。
    功能：
      1. 表格列出平台上全部用户账号及其状态；
      2. 对可用账号执行「停用」，对已停用账号执行「恢复」；
      3. 两种操作都二次确认。
    对应接口：GET /api/admin/users
              PUT /api/admin/users/{id}/disable
              PUT /api/admin/users/{id}/enable

  【需求依据（系统管理员访谈 A2）】
    「发现账号存在明显异常时，系统管理员可以停用；问题处理后，
      也可以恢复为可用状态。」
    所以是"停用 / 恢复"这一对可逆操作，而不是删除账号。

  【两个必须注意的点】
    ① 返回的数据里**没有密码**：后端 AdminService 读出来的实体虽然带 password，
       但接口层会转成不含密码的 VO。前端拿不到，也不该想办法拿。
    ② 管理员不能停用自己：一旦把自己停用了，就再也没人能进后台把它改回来。
       后端会拒绝（返回 403），前端也提前把按钮禁用掉，避免白点一次。

  【停用之后会发生什么】
    被停用的账号无法登录（后端登录接口返回错误码 2003）。
    它已有的报名记录**不会被自动取消**（最小假设 A8）——
    教师看到的名单里那个人仍然在，由教师自行处理。
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-tip">
          平台上的全部账号，共 {{ users.length }} 个（
          {{ activeCount }} 个可用 / {{ disabledCount }} 个已停用）
        </p>
      </div>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="按账号 / 姓名筛选" clearable style="width: 240px">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="role" placeholder="角色" clearable style="width: 150px">
        <el-option label="学生" value="STUDENT" />
        <el-option label="教师" value="TEACHER" />
        <el-option label="系统管理员" value="ADMIN" />
      </el-select>
      <el-select v-model="status" placeholder="账号状态" clearable style="width: 150px">
        <el-option label="可用" value="ACTIVE" />
        <el-option label="已停用" value="DISABLED" />
      </el-select>
    </div>

    <el-table
      v-loading="loading"
      :data="filtered"
      border
      stripe
      empty-text="没有匹配的账号"
    >
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="username" label="账号（学号/工号）" min-width="160" />
      <el-table-column prop="name" label="姓名" min-width="120" />

      <el-table-column label="角色" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="roleType(row.role)" effect="plain">{{ roleText(row.role) }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="账号状态" width="120" align="center">
        <template #default="{ row }">
          <!-- 中文文案用后端返回的 statusText，与其它页面保持一致 -->
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ row.statusText }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <!-- 可用 → 停用；已停用 → 恢复。同一列按状态二选一 -->
          <el-button
            v-if="row.status === 'ACTIVE'"
            type="danger"
            link
            :disabled="isSelf(row)"
            :loading="operatingId === row.id"
            @click="handleDisable(row)"
          >
            停用
          </el-button>
          <el-button
            v-else
            type="success"
            link
            :loading="operatingId === row.id"
            @click="handleEnable(row)"
          >
            恢复
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-alert
      class="tip-alert"
      type="info"
      :closable="false"
      show-icon
      title="停用不是删除"
      description="停用后账号与历史数据都保留，只是无法登录；问题处理完可以随时恢复。管理员不能停用自己的账号（按钮已禁用）。"
    />
  </div>
</template>

<script setup>
// ---------- 1. 引入依赖 ----------
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { allUsers, disableUser, enableUser } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'

// ---------- 2. 响应式数据 ----------
const authStore = useAuthStore()

/** 全部用户账号 */
const users = ref([])

/** 是否正在加载 */
const loading = ref(false)

/** 正在操作的用户 id（只让对应按钮转圈） */
const operatingId = ref(null)

/** 前端筛选条件 */
const keyword = ref('')
const role = ref('')
const status = ref('')

// ---------- 3. 派生数据 ----------
const activeCount = computed(() => users.value.filter((u) => u.status === 'ACTIVE').length)
const disabledCount = computed(() => users.value.filter((u) => u.status === 'DISABLED').length)

/** 按关键字、角色、状态过滤 */
const filtered = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return users.value.filter((u) => {
    if (role.value && u.role !== role.value) {
      return false
    }
    if (status.value && u.status !== status.value) {
      return false
    }
    if (!key) {
      return true
    }
    return (
      String(u.username || '').toLowerCase().includes(key) ||
      String(u.name || '').toLowerCase().includes(key)
    )
  })
})

// ---------- 4. 数据加载 ----------
async function loadData() {
  loading.value = true
  try {
    users.value = await allUsers()
    console.log('全部账号：', users.value)
  } finally {
    loading.value = false
  }
}

// ---------- 5. 停用与恢复 ----------
/**
 * 停用账号。
 * @param {object} row 当前行
 */
async function handleDisable(row) {
  try {
    await ElMessageBox.confirm(
      `确认停用「${row.name}（${row.username}）」吗？停用后该账号将无法登录。`,
      '停用确认',
      { type: 'warning' }
    )
  } catch (e) {
    return // 用户点了取消
  }

  operatingId.value = row.id
  try {
    await disableUser(row.id)
    ElMessage.success(`已停用「${row.name}」`)
    await loadData() // 刷新后状态标签才会变成"已停用"
  } finally {
    operatingId.value = null
  }
}

/**
 * 恢复账号为可用。
 * @param {object} row 当前行
 */
async function handleEnable(row) {
  try {
    await ElMessageBox.confirm(
      `确认恢复「${row.name}（${row.username}）」吗？恢复后该账号可以正常登录。`,
      '恢复确认',
      { type: 'info' }
    )
  } catch (e) {
    return
  }

  operatingId.value = row.id
  try {
    await enableUser(row.id)
    ElMessage.success(`已恢复「${row.name}」`)
    await loadData()
  } finally {
    operatingId.value = null
  }
}

// ---------- 6. 界面辅助函数 ----------
/**
 * 判断某一行是不是当前登录的管理员自己。
 *
 * 【为什么要判断】停用自己是不可逆的：一旦执行，就用这个账号进不了后台了，
 * 也就没人能把它改回来。后端会拒绝（403），这里提前禁用按钮，
 * 让管理员从界面上就知道"这一行不能点"，而不是点了才报错。
 *
 * @param {object} row 当前行
 * @returns {boolean} true 表示是自己
 */
function isSelf(row) {
  return row.id === authStore.user?.id
}

/**
 * 角色 → 中文。
 * @param {string} role STUDENT / TEACHER / ADMIN
 */
function roleText(role) {
  const map = { STUDENT: '学生', TEACHER: '教师', ADMIN: '系统管理员' }
  return map[role] || role
}

/**
 * 角色 → 标签颜色。
 * @param {string} role STUDENT / TEACHER / ADMIN
 */
function roleType(role) {
  const map = { STUDENT: 'success', TEACHER: 'warning', ADMIN: 'danger' }
  return map[role] || 'info'
}

// ---------- 7. 生命周期 ----------
onMounted(loadData)
</script>

<style scoped>
.filter-bar {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.tip-alert {
  margin-top: 16px;
}
</style>
