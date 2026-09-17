<!--
  ============================================================================
  文件：frontend/src/views/teacher/ActivityManage.vue
  用途：教师端「活动管理」页面（对应需求 REQ-05）。
  状态：★ 待实现 ★

  需要实现的内容：
    1. 调用 listActivities({ onlyMine: true }) 获取自己发布的活动
    2. 用 el-table 展示：标题、时间、地点、状态、报名人数
    3. 顶部「发布活动」按钮 → 打开 el-dialog 表单
       （标题、地点、起止时间 el-date-picker、活动说明）
    4. 表格行操作：编辑（复用同一个 dialog，回填数据）、关闭报名、删除（二次确认）
    5. 表单时间格式：接口需要 'YYYY-MM-DD HH:mm:ss' 字符串，
       el-date-picker 用 value-format="YYYY-MM-DD HH:mm:ss"
  对应接口：GET /api/activities?onlyMine=true
           POST /api/activities、PUT /api/activities/{id}
           PUT /api/activities/{id}/close、DELETE /api/activities/{id}

  【本页是 V2.0 最复杂的一页，重点练】
    - el-dialog 弹窗 + 表单复用（新增 / 编辑共用一个 dialog）
    - 提交前把表单数据组装成接口需要的格式
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">活动管理</h2>
        <p class="page-tip">★ 这个页面留给你实现 ★</p>
      </div>
    </div>

    <el-alert type="warning" :closable="false" show-icon>
      <template #title>待实现：活动表格 + 发布/编辑弹窗 + 关闭/删除</template>
      <template #default>
        <div style="font-size: 13px; line-height: 1.8">
          接口：listActivities / createActivity / updateActivity / closeActivity / deleteActivity<br />
          提示：el-date-picker 加 value-format="YYYY-MM-DD HH:mm:ss" 才能直接提交给后端
        </div>
      </template>
    </el-alert>
  </div>
</template>
