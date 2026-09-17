<!--
  ============================================================================
  文件：frontend/src/views/student/ActivityList.vue
  用途：学生端「浏览活动」页面（对应需求 REQ-02）。
  状态：★ 待实现 ★

  需要实现的内容：
    1. 顶部筛选栏：关键字搜索框 + 状态下拉（可报名 / 已关闭 / 已结束）+ 查询按钮
    2. 活动卡片列表（CSS Grid 布局，global.css 里已有 .card-grid / .meta-row 类）：
       卡片头部：活动标题 + 状态标签
       卡片内容：时间、地点、发布教师、报名人数
       卡片底部：「查看详情」「立即报名」按钮
    3. 报名按钮的禁用逻辑：
       - item.joined === true        → 显示「已报名」并禁用
       - item.status !== 'OPEN'      → 禁用，提示「该活动已关闭报名」
       - 无人数上限概念，暂不考虑满员
    4. 点击报名：二次确认（ElMessageBox.confirm）→ 调用 registerActivity(id)
       → 成功后刷新列表
  对应接口：GET /api/activities、POST /api/activities/{id}/registrations

  提示：接口返回的每个活动对象包含
       { id, title, description, location, startTime, endTime, status,
         teacherId, teacherName, registeredCount, joined }
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">浏览校园活动</h2>
        <p class="page-tip">★ 这个页面留给你实现 ★</p>
      </div>
    </div>

    <el-alert type="warning" :closable="false" show-icon>
      <template #title>待实现：活动列表 + 搜索筛选 + 报名</template>
      <template #default>
        <div style="font-size: 13px; line-height: 1.8">
          接口：listActivities(params) / registerActivity(id)<br />
          布局类：.card-grid、.meta-row、.card-header、.card-footer（已在 global.css 中定义）
        </div>
      </template>
    </el-alert>
  </div>
</template>
