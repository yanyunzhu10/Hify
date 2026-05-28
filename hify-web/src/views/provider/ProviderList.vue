<template>
  <div class="page-content">
    <PageHeader
      title="模型管理"
      description="管理 OpenAI / Claude / Gemini / Ollama 等 LLM 提供商配置，支持连通性测试"
    >
      <template #actions>
        <el-button>
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
        <el-button type="primary">
          <el-icon><Plus /></el-icon>
          <span>新增提供商</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="content-card">
      <div class="content-card-title">后端连接状态</div>
      <div class="status-row">
        <span class="tag-soft" :class="connected ? 'is-success' : 'is-danger'">
          {{ connected ? 'Connected' : 'Disconnected' }}
        </span>
        <span class="status-text">{{ connected ? healthMsg : '未连接到后端服务' }}</span>
      </div>
    </div>

    <div class="content-card">
      <div class="content-card-title">提供商列表</div>
      <el-empty description="暂无提供商，点击右上角新增" :image-size="80" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getHealth } from '@/api/health'
import PageHeader from '@/components/layout/PageHeader.vue'

const connected = ref(false)
const healthMsg = ref('')

onMounted(async () => {
  try {
    healthMsg.value = await getHealth()
    connected.value = true
  } catch {
    connected.value = false
  }
})
</script>

<style scoped>
.status-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--text-sm);
}
.status-text {
  color: var(--text-secondary);
}
</style>
