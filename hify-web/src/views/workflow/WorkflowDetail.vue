<template>
  <div class="page-content">
    <PageHeader :title="wf?.name || '工作流详情'" description="查看工作流的节点配置和连线关系">
      <template #actions>
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回</span>
        </el-button>
      </template>
    </PageHeader>

    <div v-loading="loading">
      <el-empty v-if="!loading && !wf" description="工作流不存在" :image-size="80" />

      <template v-if="wf">
        <!-- 基本信息 -->
        <div class="content-card">
          <div class="content-card-title">基本信息</div>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">名称</span>
              <span class="info-value">{{ wf.name }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">描述</span>
              <span class="info-value">{{ wf.description || '—' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">状态</span>
              <span class="info-value">
                <el-tag
                  :class="['status-tag', wf.status === 'PUBLISHED' ? 'is-published' : 'is-draft']"
                  size="small" effect="light"
                >
                  {{ wf.status === 'PUBLISHED' ? '已发布' : '草稿' }}
                </el-tag>
              </span>
            </div>
            <div class="info-item">
              <span class="info-label">创建时间</span>
              <span class="info-value">{{ wf.createdAt }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">节点数</span>
              <span class="info-value">{{ (wf.nodes || []).length }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">连线数</span>
              <span class="info-value">{{ (wf.edges || []).length }}</span>
            </div>
          </div>
        </div>

        <!-- JSON 配置 -->
        <div class="content-card">
          <div class="content-card-title">工作流配置</div>
          <div class="json-toolbar">
            <el-button size="small" @click="copyJson">
              <el-icon><DocumentCopy /></el-icon>
              <span>复制</span>
            </el-button>
            <el-button size="small" @click="formatJson">
              <el-icon><Operation /></el-icon>
              <span>格式化</span>
            </el-button>
          </div>
          <pre class="json-view"><code>{{ configText }}</code></pre>
        </div>

        <!-- 节点列表 -->
        <div class="content-card">
          <div class="content-card-title">节点列表</div>
          <el-table :data="(wf.nodes || [])" size="small" style="width:100%">
            <el-table-column prop="nodeKey" label="Key" width="140" />
            <el-table-column prop="type" label="类型" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="nodeTypeColor(row.type)" effect="light">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="outputVariable" label="输出变量" width="160">
              <template #default="{ row }">
                <code v-if="row.outputVariable">{{ row.nodeKey }}.{{ row.outputVariable }}</code>
                <span v-else class="text-tertiary">—</span>
              </template>
            </el-table-column>
            <el-table-column label="配置" min-width="200">
              <template #default="{ row }">
                <code v-if="row.config && Object.keys(row.config).length" class="config-preview">
                  {{ shortConfig(row.config) }}
                </code>
                <span v-else class="text-tertiary">—</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 连线列表 -->
        <div class="content-card">
          <div class="content-card-title">连线列表</div>
          <el-table :data="(wf.edges || [])" size="small" style="width:100%">
            <el-table-column prop="sourceNodeKey" label="源节点" width="160" />
            <el-table-column label="" width="40" align="center">
              <template #default>
                <el-icon><Right /></el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="targetNodeKey" label="目标节点" width="160" />
            <el-table-column label="条件" min-width="160">
              <template #default="{ row }">
                <code v-if="row.conditionExpr">{{ row.conditionExpr }}</code>
                <span v-else class="text-tertiary">无条件</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, DocumentCopy, Operation, Right } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import { getWorkflowDetail } from '@/api/workflow'
import type { WorkflowInfo } from '@/types'

const route = useRoute()
const id = Number(route.params.id)
const wf = ref<WorkflowInfo | null>(null)
const loading = ref(false)

const configText = computed(() => {
  if (!wf.value) return ''
  return JSON.stringify({ nodes: wf.value.nodes, edges: wf.value.edges }, null, 2)
})

onMounted(async () => {
  loading.value = true
  try {
    wf.value = await getWorkflowDetail(id)
  } catch {
    wf.value = null
  } finally {
    loading.value = false
  }
})

function nodeTypeColor(type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    START: 'info', LLM: 'primary', CONDITION: 'warning', KNOWLEDGE: 'success', TOOL: 'info', API_CALL: 'danger', END: 'info',
  }
  return map[type?.toUpperCase()] || undefined
}

function shortConfig(config: Record<string, unknown>): string {
  const s = JSON.stringify(config)
  return s.length > 60 ? s.slice(0, 60) + '…' : s
}

function formatJson() {
  if (!wf.value) return
  ElMessage.success('JSON 已格式化')
}

async function copyJson() {
  try {
    await navigator.clipboard.writeText(configText.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--space-4);
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.info-label {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}
.info-value {
  font-size: var(--text-sm);
  color: var(--text-primary);
}

.json-toolbar {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.json-view {
  background: var(--bg-muted);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  overflow: auto;
  max-height: 480px;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre;
  tab-size: 2;
  margin: 0;
}

.config-preview {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
}
.text-tertiary { color: var(--text-tertiary); }

/* 连线表中间箭头 */
:deep(.el-table .cell) { display: flex; justify-content: center; }
</style>
