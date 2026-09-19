<!--
  ============================================================================
  文件：frontend/src/views/Register.vue
  用途：注册页（对应需求 REQ-01）。
    功能：
      1. 填写账号、姓名、密码、确认密码并选择身份（学生 / 教师）；
      2. 表单校验：账号 3~50 位、密码至少 6 位、两次密码必须一致；
      3. 调用注册接口，成功后跳转登录页（不自动登录）。
    对应接口：POST /api/auth/register

  【与 Login.vue 的写法差异，也是本页要掌握的三个新点】
    1. 自定义校验函数：确认密码要和密码比较，rules 里用 validator
    2. 单选组要放进 el-form-item 里并给 prop，才能参与校验与对齐
    3. 注册成功后不需要保存登录状态，所以直接调用 api/auth.js 的 register()，
       而不走 Pinia store（store 负责的是"登录状态"，注册不改变状态）
  ============================================================================
-->
<template>
  <div class="auth-page">
    <div class="auth-box">
      <!-- 左侧品牌区 -->
      <aside class="auth-brand">
        <h1 class="brand-title">校园活动<br />管理系统</h1>
        <span class="brand-version">V2.0 · 前后端分离</span>
        <ul class="brand-features">
          <li><el-icon><User /></el-icon>注册后即可浏览并报名校园活动</li>
          <li><el-icon><Promotion /></el-icon>教师账号可以发布与管理活动</li>
          <li><el-icon><Lock /></el-icon>密码加密保存，不存明文</li>
        </ul>
      </aside>

      <!-- 右侧表单区 -->
      <section class="auth-form-area">
        <h2 class="auth-title">注册账号</h2>
        <p class="auth-subtitle">请填写以下信息完成注册</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleRegister">
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="学号 / 工号，3~50 位" clearable>
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="姓名" prop="name">
            <el-input v-model="form.name" placeholder="请输入真实姓名" clearable>
              <template #prefix><el-icon><Postcard /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="至少 6 位" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <!-- 单选组必须放进 el-form-item 里，才会有标签、对齐与校验 -->
          <el-form-item label="注册身份" prop="role">
            <el-radio-group v-model="form.role">
              <el-radio value="STUDENT">学生</el-radio>
              <el-radio value="TEACHER">教师</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-button type="primary" class="submit-btn" :loading="loading" @click="handleRegister">
            注册
          </el-button>
        </el-form>

        <div class="auth-footer">
          已有账号？
          <el-button link type="primary" @click="router.push('/login')">返回登录</el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
// ---------- 1. 引入依赖 ----------
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
// 注册不改变登录状态，所以直接用接口函数，不经过 Pinia store
import { register } from '@/api/auth'

// ---------- 2. 准备状态 ----------
const router = useRouter()

/** 表单实例，用于调用校验 */
const formRef = ref(null)
/** 提交中标记 */
const loading = ref(false)

/** 注册表单数据：字段要和下面 rules 的键名一一对应 */
const form = reactive({
  username: '',
  name: '',
  password: '',
  confirmPassword: '',
  role: 'STUDENT' // 默认注册为学生
})

/**
 * 自定义校验函数：确认密码必须与密码一致。
 *
 * Element Plus 的自定义校验规则写法固定为：
 *   { validator: (rule, value, callback) => { ... callback() 或 callback(new Error('提示')) } }
 * - 校验通过调用 callback()
 * - 校验失败调用 callback(new Error('原因'))
 *
 * @param {object}   rule     当前规则对象（这里用不到）
 * @param {string}   value    当前字段的值（即 confirmPassword）
 * @param {Function} callback 回调，用于告诉表单校验结果
 */
function validateConfirmPassword(rule, value, callback) {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

/** 校验规则：与后端接口的参数约束保持一致 */
const rules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 50, message: '账号长度需在 3~50 位之间', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6~32 位之间', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }],
  role: [{ required: true, message: '请选择注册身份', trigger: 'change' }]
}

// ---------- 3. 业务逻辑 ----------
/**
 * 提交注册：校验表单 → 调用接口 → 跳转登录页。
 */
async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    // 只提交接口需要的字段（confirmPassword 只用于前端校验，不发给后端）
    const user = await register({
      username: form.username,
      password: form.password,
      name: form.name,
      role: form.role
    })
    ElMessage.success(`注册成功，${user.name}，请登录`)
    router.push('/login')
  } catch (e) {
    // 失败提示已由 axios 响应拦截器统一弹出（例如"该账号已被注册"），这里不再重复提示
  } finally {
    loading.value = false
  }
}
</script>
