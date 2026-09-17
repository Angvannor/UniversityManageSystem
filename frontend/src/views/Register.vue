<!--
  ============================================================================
  文件：frontend/src/views/Register.vue
  用途：注册页（对应需求 REQ-01）。
  状态：★ 待实现（下一步由你按 Login.vue 的样板改写）★

  需要实现的内容：
    1. 表单字段：账号、姓名、密码、确认密码、身份（学生 / 教师单选框）
    2. 校验规则：
       - 账号 3~50 位
       - 密码至少 6 位
       - 确认密码要与密码一致（自定义校验函数 validator）
    3. 调用 api/auth.js 的 register(data) 提交
    4. 注册成功后提示并跳转到登录页
  对应接口：POST /api/auth/register
  ============================================================================
-->
<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2 class="auth-title">注册账号</h2>
      <p class="auth-subtitle">★ 这个页面留给你实现 ★</p>

      <el-alert type="warning" :closable="false" show-icon>
        <template #title>待实现：按 Login.vue 的样板改写本页面</template>
        <template #default>
          <div style="font-size: 13px; line-height: 1.8">
            字段：账号 / 姓名 / 密码 / 确认密码 / 身份<br />
            接口：api/auth.js 里的 register(data)<br />
            成功后：ElMessage 提示 → router.push('/login')
          </div>
        </template>
      </el-alert>

      <div class="auth-footer">
        已有账号？
        <el-button link type="primary" @click="$router.push('/login')">返回登录</el-button>
      </div>
    </div>
  </div>
</template>
