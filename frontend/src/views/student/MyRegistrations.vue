<!--
  ============================================================================
  文件：frontend/src/views/student/MyRegistrations.vue
  用途：学生端「我的报名」页面（对应需求 REQ-04）。
  状态：★ 待实现 ★

  需要实现的内容：
    1. 调用 myRegistrations() 获取我的报名记录（含已取消的）
    2. 用 el-table 展示：活动名称、地点、活动时间、报名时间、报名状态
    3. 状态用 el-tag 区分颜色：REGISTERED → 已报名（绿色）、CANCELLED → 已取消（灰色）
    4. 对「已报名」的记录提供「取消报名」按钮（二次确认后调用 cancelRegistration 并刷新）
    5. 空数据显示 el-empty
  对应接口：GET /api/registrations/mine、DELETE /api/activities/{id}/registrations
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">我的报名</h2>
        <p class="page-tip">★ 这个页面留给你实现 ★</p>
      </div>
    </div>

    <el-alert type="warning" :closable="false" show-icon>
      <template #title>待实现：报名记录表格 + 取消报名</template>
      <template #default>
        <div style="font-size: 13px; line-height: 1.8">
          接口：myRegistrations() / cancelRegistration(activityId)<br />
          提示：el-table 的列用 &lt;el-table-column&gt;，自定义内容用 #default="{ row }"
        </div>
      </template>
    </el-alert>
  </div>
</template>
