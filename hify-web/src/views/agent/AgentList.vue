<template>
  <div class="page-content">
    <PageHeader
      title="Agent 管理"
      description="配置 Agent 名称、系统提示词、绑定模型，关联知识库和 MCP 工具"
    >
      <template #actions>
        <el-button type="primary">
          <el-icon><Plus /></el-icon>
          <span>新建 Agent</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="content-card">
      <div class="content-card-title">Agent 列表</div>
      <el-empty description="暂无 Agent，点击右上角新建" :image-size="80" />
    </div>
  </div>
</template>

<script setup lang="ts">
import PageHeader from '@/components/layout/PageHeader.vue'
</script>
