<template>
  <div class="page-content">
    <PageHeader title="知识库管理" description="管理知识库，上传文档，构建 RAG 检索能力">
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新建知识库</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="content-card">
      <div class="content-card-title">知识库列表</div>
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        row-key="id"
        :row-style="{ height: '52px' }"
      >
        <template #col-name="{ row }">
          <router-link
            :to="`/knowledge-bases/${row.id}/documents`"
            class="kb-name-link"
          >
            {{ row.name }}
          </router-link>
        </template>

        <template #col-description="{ row }">
          <span :class="row.description ? '' : 'text-tertiary'">
            {{ row.description || '—' }}
          </span>
        </template>

        <template #col-enabled="{ row }">
          <el-tag
            :type="row.enabled === 1 ? 'success' : 'info'"
            :class="['status-tag', row.enabled === 1 ? 'is-enabled' : 'is-disabled']"
            size="small"
            effect="light"
          >
            {{ row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <template #col-actions="{ row }">
          <el-button text type="primary" size="small" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button text type="danger" size="small" @click="onDelete(row)">
            删除
          </el-button>
        </template>
      </HifyTable>
    </div>

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑知识库' : '新建知识库'"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
      align-center
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        label-position="right"
        @submit.prevent
      >
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="知识库名称"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="简要描述知识库的内容（可选）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态" prop="enabled">
          <el-switch
            v-model="form.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import type { HifyTableColumn } from '@/components/HifyTable.vue'
import { confirmDelete } from '@/composables/useConfirm'
import { useViewport } from '@/composables/useViewport'
import { notifySuccess } from '@/utils/notify'
import {
  listKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
} from '@/api/knowledge-base'
import type { KnowledgeBase } from '@/types'

// ============ 表格列配置 ============
const { isNarrow } = useViewport(1200)

const columns = computed<HifyTableColumn<KnowledgeBase>[]>(() => {
  const list: HifyTableColumn<KnowledgeBase>[] = [
    { label: '名称', slot: 'name', minWidth: 180 },
    { label: '描述', slot: 'description', minWidth: 200 },
    { label: '状态', slot: 'enabled', width: 80, align: 'center' },
  ]
  if (!isNarrow.value) {
    list.push({ label: '创建时间', prop: 'createdAt', width: 170 })
  }
  list.push({ label: '操作', slot: 'actions', width: 140, align: 'right', fixed: 'right' })
  return list
})

// 适配器：将 API 的 { records, total } 包裹进 HifyTable 期望的 PageResult 形态
async function fetchList(params: { page: number; size: number }) {
  return listKnowledgeBases({ page: params.page, size: params.size })
    .then((res) => ({
      records: res.records ?? (res as unknown as KnowledgeBase[]),
      total: res.total ?? (res as unknown as KnowledgeBase[]).length ?? 0,
    }))
}

// ============ 表单 ============
interface KbForm {
  id?: number
  name: string
  description: string
  enabled: number
}

const defaultForm = (): KbForm => ({
  name: '',
  description: '',
  enabled: 1,
})

const form = ref<KbForm>(defaultForm())
const isEdit = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const rules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 1, max: 100, message: '长度 1-100 个字符', trigger: 'blur' },
  ],
}

function openCreate() {
  form.value = defaultForm()
  isEdit.value = false
  dialogVisible.value = true
}

function openEdit(row: KnowledgeBase) {
  form.value = {
    id: row.id,
    name: row.name,
    description: row.description || '',
    enabled: row.enabled,
  }
  isEdit.value = true
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value && form.value.id != null) {
      await updateKnowledgeBase(form.value.id, {
        name: form.value.name,
        description: form.value.description || undefined,
        enabled: form.value.enabled,
      })
      notifySuccess('知识库已更新')
    } else {
      await createKnowledgeBase({
        name: form.value.name,
        description: form.value.description || undefined,
      })
      notifySuccess('知识库创建成功')
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } finally {
    submitting.value = false
  }
}

// ============ 删除 ============
async function onDelete(row: KnowledgeBase) {
  await confirmDelete(
    {
      message: `确认删除知识库「${row.name}」？关联的文档和切片将一并逻辑删除。`,
      successMessage: '知识库已删除',
    },
    () => deleteKnowledgeBase(row.id),
  )
  tableRef.value?.refresh()
}

// ============ 表格引用 ============
interface TableExposed {
  refresh: () => Promise<void>
  reload: () => Promise<void>
}
const tableRef = ref<TableExposed>()
</script>

<style scoped>
.kb-name-link {
  font-weight: var(--weight-medium);
  color: var(--brand-500);
  text-decoration: none;
}
.kb-name-link:hover {
  text-decoration: underline;
}

.text-tertiary {
  color: var(--text-tertiary);
}

:deep(.status-tag.is-enabled) {
  --el-tag-bg-color: var(--mint-50);
  --el-tag-border-color: var(--mint-100);
  --el-tag-text-color: var(--mint-700);
}
:deep(.status-tag.is-disabled) {
  --el-tag-bg-color: var(--bg-muted);
  --el-tag-border-color: var(--border-base);
  --el-tag-text-color: var(--text-tertiary);
}
</style>
