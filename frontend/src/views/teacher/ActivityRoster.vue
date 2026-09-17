<!--
  ============================================================================
  文件：frontend/src/views/teacher/ActivityRoster.vue
  用途：教师端「报名名单」页面（对应需求 REQ-06）。
  状态：★ 待实现 ★

  需要实现的内容：
    1. 调用 listActivities({ onlyMine: true }) 填充活动下拉框
    2. 选择某个活动后，调用 registrationsOfActivity(activityId) 获取名单
    3. 用 el-table 展示：学号（username）、姓名（studentName）、报名时间、状态
    4. 顶部显示该活动的报名人数统计
    5. 无报名时显示 el-empty
  对应接口：GET /api/activities?onlyMine=true
           GET /api/activities/{id}/registrations
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">报名名单</h2>
        <p class="page-tip">★ 这个页面留给你实现 ★</p>
      </div>
    </div>

    <el-alert type="warning" :closable="false" show-icon>
      <template #title>待实现：活动下拉选择 + 报名名单表格</template>
      <template #default>
        <div style="font-size: 13px; line-height: 1.8">
          接口：listActivities({ onlyMine: true }) / registrationsOfActivity(activityId)<br />
          提示：用 el-select 选活动，选中后（@change）再拉名单
        </div>
      </template>
    </el-alert>
  </div>
</template>
