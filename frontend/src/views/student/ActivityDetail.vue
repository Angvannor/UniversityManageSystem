<!--
  ============================================================================
  文件：frontend/src/views/student/ActivityDetail.vue
  用途：学生端「活动详情」页面（对应需求 REQ-02，是「查看并报名」流程的详情环节）。
  状态：★ 待实现 ★

  需要实现的内容：
    1. 从路由参数取活动 id：const route = useRoute(); route.params.id
    2. 调用 getActivity(id) 获取详情，用 el-descriptions 展示
       （标题、地点、起止时间、发布教师、报名人数、活动说明）
    3. 根据状态显示不同按钮：
       - 未报名且活动可报名 → 「立即报名」（调用 registerActivity）
       - 已报名            → 「取消报名」（调用 cancelRegistration，需二次确认）
       - 活动已关闭/已结束 → 提示不可报名
    4. 操作完成后重新拉取详情，保证人数与按钮状态同步
  对应接口：GET /api/activities/{id}
           POST /api/activities/{id}/registrations
           DELETE /api/activities/{id}/registrations
  ============================================================================
-->
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">活动详情</h2>
        <p class="page-tip">★ 这个页面留给你实现 ★</p>
      </div>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-alert type="warning" :closable="false" show-icon>
      <template #title>待实现：活动详情 + 报名 / 取消报名</template>
      <template #default>
        <div style="font-size: 13px; line-height: 1.8">
          接口：getActivity(id) / registerActivity(id) / cancelRegistration(id)<br />
          提示：用 el-descriptions 展示字段，用 computed 计算按钮该显示什么
        </div>
      </template>
    </el-alert>
  </div>
</template>
