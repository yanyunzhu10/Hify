<template>
  <div class="page-content">
    <PageHeader title="对话" description="与已配置的 Agent 进行多轮对话，支持流式响应">
      <template #actions>
        <el-button>
          <el-icon><Refresh /></el-icon>
          <span>新对话</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="content-card">
      <el-empty description="选择左侧 Agent 开始对话" :image-size="80" />
    </div>
  </div>
</template>

<script setup lang="ts">
import PageHeader from '@/components/layout/PageHeader.vue'
</script>
