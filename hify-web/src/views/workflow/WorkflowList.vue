<template>
  <div class="page-content">
    <PageHeader title="工作流管理" description="配置和发布工作流，串联 LLM、条件判断、知识库检索">
      <template #actions>
        <el-button type="primary" @click="$router.push('/workflows/create')">
          <el-icon><Plus /></el-icon>
          <span>新建工作流</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="content-card">
      <div class="content-card-title">工作流列表</div>
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        row-key="id"
        :row-style="{ height: '52px' }"
      >
        <template #col-name="{ row }">
          <router-link :to="`/workflows/${row.id}`" class="wf-link">
            {{ row.name }}
          </router-link>
        </template>

        <template #col-description="{ row }">
          <span :class="row.description ? '' : 'text-tertiary'">
            {{ row.description || '—' }}
          </span>
        </template>

        <template #col-status="{ row }">
          <el-tag
            :class="['status-tag', row.status === 'PUBLISHED' ? 'is-published' : 'is-draft']"
            size="small"
            effect="light"
          >
            {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>

        <template #col-actions="{ row }">
          <el-button text type="danger" size="small" @click="onDelete(row)">
            删除
          </el-button>
        </template>
      </HifyTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import type { HifyTableColumn } from '@/components/HifyTable.vue'
import { confirmDelete } from '@/composables/useConfirm'
import { useViewport } from '@/composables/useViewport'
import { listWorkflows, deleteWorkflow } from '@/api/workflow'
import type { WorkflowInfo } from '@/types'

const { isNarrow } = useViewport(1200)

const columns = computed<HifyTableColumn<WorkflowInfo>[]>(() => {
  const list: HifyTableColumn<WorkflowInfo>[] = [
    { label: '名称', slot: 'name', minWidth: 200 },
    { label: '描述', slot: 'description', minWidth: 240 },
    { label: '状态', slot: 'status', width: 90, align: 'center' },
  ]
  if (!isNarrow.value) {
    list.push({ label: '创建时间', prop: 'createdAt', width: 170 })
  }
  list.push({ label: '操作', slot: 'actions', width: 80, align: 'right', fixed: 'right' })
  return list
})

async function fetchList(params: { page: number; size: number }) {
  return listWorkflows({ page: params.page, size: params.size })
    .then((res) => ({
      records: res.records ?? (res as unknown as WorkflowInfo[]),
      total: res.total ?? (res as unknown as WorkflowInfo[]).length ?? 0,
    }))
}

const tableRef = ref<{ refresh: () => Promise<void>; reload: () => Promise<void> }>()

async function onDelete(row: WorkflowInfo) {
  await confirmDelete(
    {
      message: `确认删除工作流「${row.name}」？`,
      successMessage: '工作流已删除',
    },
    () => deleteWorkflow(row.id),
  )
  tableRef.value?.refresh()
}
</script>

<style scoped>
.wf-link { font-weight: var(--weight-medium); color: var(--brand-500); text-decoration: none; }
.wf-link:hover { text-decoration: underline; }
.text-tertiary { color: var(--text-tertiary); }

:deep(.status-tag.is-published) {
  --el-tag-bg-color: var(--mint-50);
  --el-tag-border-color: var(--mint-100);
  --el-tag-text-color: var(--mint-700);
}
:deep(.status-tag.is-draft) {
  --el-tag-bg-color: var(--bg-muted);
  --el-tag-border-color: var(--border-base);
  --el-tag-text-color: var(--text-secondary);
}
</style>
