<template>
  <div class="page-content">
    <PageHeader title="知识库管理" description="管理 RAG 知识库，上传文档自动分块向量化">
      <template #actions>
        <el-input
          v-model="searchName"
          placeholder="搜索知识库名称"
          clearable
          class="kb-search"
          @input="onSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新建知识库</span>
        </el-button>
      </template>
    </PageHeader>

    <!-- 空状态 -->
    <div v-if="!loading && list.length === 0" class="content-card">
      <el-empty :description="searchName ? '没有匹配的知识库' : '还没有知识库，点右上角新建一个'" :image-size="90" />
    </div>

    <!-- 卡片网格 -->
    <div v-else v-loading="loading" class="kb-grid">
      <div v-for="kb in list" :key="kb.id" class="kb-card">
        <div class="kb-card-top">
          <div class="kb-icon">
            <el-icon><FolderOpened /></el-icon>
          </div>
          <div class="kb-actions">
            <el-tag
              :type="kb.enabled === 1 ? 'success' : 'info'"
              :class="['status-tag', kb.enabled === 1 ? 'is-enabled' : 'is-disabled']"
              size="small"
              effect="light"
            >
              {{ kb.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
            <el-button text type="primary" size="small" @click="openEdit(kb)">编辑</el-button>
            <el-button text type="danger" size="small" @click="onDelete(kb)">删除</el-button>
          </div>
        </div>

        <div class="kb-name">{{ kb.name }}</div>
        <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>

        <div class="kb-divider"></div>

        <div class="kb-card-bottom">
          <span class="kb-date">{{ formatDate(kb.createdAt) }}</span>
          <router-link :to="`/knowledge-bases/${kb.id}/documents`" class="kb-link">
            查看文档 →
          </router-link>
        </div>
      </div>
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
import { ref, onMounted } from 'vue'
import { FolderOpened, Plus, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import { confirmDelete } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import {
  listKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
} from '@/api/knowledge-base'
import type { KnowledgeBase } from '@/types'

// ============ 列表数据 ============
const list = ref<KnowledgeBase[]>([])
const loading = ref(false)
const searchName = ref('')

async function loadList() {
  loading.value = true
  try {
    const res = await listKnowledgeBases({ page: 1, size: 100, name: searchName.value || undefined })
    list.value = res.records ?? (res as unknown as KnowledgeBase[]) ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

// 输入防抖搜索
let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(loadList, 300)
}

function formatDate(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso.includes('T') ? iso : iso.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return iso
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`
}

onMounted(loadList)

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
    loadList()
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
  loadList()
}
</script>

<style scoped>
.kb-search {
  width: 240px;
}

/* ════════ 卡片网格 ════════ */
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-4);
  min-height: 120px;
}

.kb-card {
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: var(--space-5);
  transition: box-shadow var(--duration-base) var(--ease-standard),
              transform var(--duration-base) var(--ease-standard);
}
.kb-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.kb-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2);
}
.kb-icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--brand-50);
  color: var(--brand-500);
  font-size: 22px;
}
.kb-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.kb-name {
  margin-top: var(--space-4);
  font-size: var(--text-md);
  font-weight: var(--weight-semi);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kb-desc {
  margin-top: var(--space-1);
  font-size: var(--text-sm);
  color: var(--text-secondary);
  line-height: var(--leading-normal);
  /* 最多两行 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}

.kb-divider {
  margin: var(--space-4) 0 var(--space-3);
  border-top: 1px solid var(--border-light);
}

.kb-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kb-date {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}
.kb-link {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--brand-500);
  text-decoration: none;
}
.kb-link:hover {
  text-decoration: underline;
}

/* 状态标签配色 */
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
