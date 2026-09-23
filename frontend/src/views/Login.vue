<!--
  ============================================================================
  文件：frontend/src/views/Login.vue
  用途：登录页（对应需求 REQ-01）。
    功能：
      1. 输入账号密码，调用 Pinia 的 authStore.login()；
      2. 登录成功后按角色跳转：教师 → 活动管理，学生 → 浏览活动；
      3. 表单基础校验（账号、密码不能为空）；
      4. 提供「去注册」入口与演示账号提示。
    对应接口：POST /api/auth/login

  【本文件是页面写法样板，重点看这几处】
    - <script setup>：Vue 3 的组合式写法，ref / reactive 定义响应式数据
    - el-form 的 ref + rules：表单校验由 Element Plus 负责
    - await formRef.value.validate()：校验通过才发请求
    - useRouter()：编程式跳转
    - <style scoped>：样式只作用于当前组件
  ============================================================================
-->
<template>
  <div class="auth-page">
    <div class="auth-box">
      <!-- 左侧品牌区：系统名称与功能亮点（窄屏会自动隐藏） -->
      <aside class="auth-brand">
        <h1 class="brand-title">校园活动<br />管理系统</h1>
        <span class="brand-version">V1.5 · 前后端分离</span>
        <ul class="brand-features">
          <li><el-icon><Promotion /></el-icon>教师发布活动，学生在线报名</li>
          <li><el-icon><Tickets /></el-icon>随时查看我的报名与报名状态</li>
          <li><el-icon><UserFilled /></el-icon>学生与教师分权限管理</li>
        </ul>
      </aside>

      <!-- 右侧表单区 -->
      <section class="auth-form-area">
        <h2 class="auth-title">欢迎登录</h2>
        <p class="auth-subtitle">请输入账号与密码</p>

        <!-- 表单：ref 用于在 JS 里调用 validate()，rules 定义校验规则 -->
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="请输入学号 / 工号" clearable>
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form>

        <div class="auth-footer">
          还没有账号？
          <el-button link type="primary" @click="router.push('/register')">立即注册</el-button>
        </div>

        <el-alert type="info" :closable="false" show-icon class="demo-tip">
          <template #title>
            演示账号：教师 teacher01 / 学生 student01，密码均为 123456
          </template>
        </el-alert>
      </section>
    </div>
  </div>
</template>

<script setup>
// ---------- 1. 引入依赖 ----------
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

// ---------- 2. 准备状态 ----------
const router = useRouter()
const authStore = useAuthStore()

/** 表单实例，用来调用校验方法 */
const formRef = ref(null)
/** 提交中标记，用来让按钮显示 loading 并防止重复点击 */
const loading = ref(false)

/** 登录表单数据（reactive 让对象里的字段也是响应式的） */
const form = reactive({
  username: '',
  password: ''
})

/** 校验规则：字段名要和 form 里的属性一致 */
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// ---------- 3. 业务逻辑 ----------
/**
 * 执行登录：校验表单 → 调用接口 → 按角色跳转。
 */
async function handleLogin() {
  // validate() 返回 Promise：校验不通过会 reject，用 catch 兜住变成 false
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    const user = await authStore.login({ ...form })
    ElMessage.success(`欢迎回来，${user.name}`)
    // 教师进入活动管理页，学生进入活动列表页
    router.push(user.role === 'TEACHER' ? '/teacher/activities' : '/activities')
  } catch (e) {
    // 具体错误提示已由 axios 响应拦截器统一弹出，这里不用重复提示
  } finally {
    loading.value = false
  }
}
</script>
