<template>
  <div class="page-content">
    <PageHeader
      title="模型提供商管理"
      description="集中管理 OpenAI / Claude / Gemini / Ollama 等 LLM 提供商配置，支持连通性测试与启停切换"
    >
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新增提供商</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="content-card">
      <div class="content-card-title">提供商列表</div>
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="listProviderPage"
        row-key="id"
        :row-style="{ height: '52px' }"
      >
        <template #col-provider="{ row }">
          <el-tag :type="providerTagType(row.provider)" size="small" effect="light">
            {{ providerLabel(row.provider) }}
          </el-tag>
        </template>

        <template #col-baseUrl="{ row }">
          <span class="text-mono mono-cell">{{ row.baseUrl || '—' }}</span>
        </template>

        <template #col-enabled="{ row }">
          <el-tag
            :type="row.enabled ? 'success' : 'info'"
            :class="['status-tag', row.enabled ? 'is-enabled' : 'is-disabled']"
            size="small"
            effect="light"
          >
            {{ row.enabled ? '启用' : '禁用' }}
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

    <HifyFormDialog
      ref="dialogRef"
      v-model="dialogVisible"
      title="提供商"
      :rules="rules"
      :default-form="defaultForm"
      width="520px"
      label-width="100px"
      label-position="right"
      @submit="handleSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="给提供商起一个易识别的名字，如 OpenAI 主账号"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="类型" prop="provider">
          <el-select
            v-model="form.provider"
            placeholder="选择 LLM 提供商"
            style="width: 100%"
          >
            <el-option
              v-for="opt in providerOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            :type="apiKeyVisible ? 'text' : 'password'"
            placeholder="本地 Ollama 可留空"
            autocomplete="off"
          >
            <template #suffix>
              <el-button
                text
                size="small"
                class="apikey-toggle"
                @click="apiKeyVisible = !apiKeyVisible"
              >
                {{ apiKeyVisible ? '隐藏' : '显示' }}
              </el-button>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="Base URL" prop="baseUrl">
          <el-input
            v-model="form.baseUrl"
            placeholder="https://api.openai.com/v1"
          />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { FormRules } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import type { HifyTableColumn } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { confirmDelete } from '@/composables/useConfirm'
import { useViewport } from '@/composables/useViewport'
import { notifySuccess } from '@/utils/notify'
import {
  listProviderPage,
  createProvider,
  updateProvider,
  deleteProvider,
  type ProviderUpsert,
} from '@/api/provider'
import type { ProviderConfig } from '@/types'

type ProviderType = ProviderConfig['provider']

interface ProviderForm extends ProviderUpsert {
  id?: number
}

// ============ 表格列配置 ============
// 窄屏（<1200px）下隐藏 Base URL 与 创建时间，只保留关键信息
const { isNarrow } = useViewport(1200)

const columns = computed<HifyTableColumn<ProviderConfig>[]>(() => {
  const list: HifyTableColumn<ProviderConfig>[] = [
    { label: '名称', prop: 'name', minWidth: 180 },
    { label: '类型', slot: 'provider', width: 120 },
  ]
  if (!isNarrow.value) {
    list.push({ label: 'Base URL', slot: 'baseUrl', minWidth: 260 })
  }
  list.push({ label: '状态', slot: 'enabled', width: 90, align: 'center' })
  if (!isNarrow.value) {
    list.push({ label: '创建时间', prop: 'createdAt', width: 180 })
  }
  list.push({ label: '操作', slot: 'actions', width: 130, align: 'right', fixed: 'right' })
  return list
})

// ============ 类型展示映射 ============
const providerOptions = [
  { value: 'openai' as const, label: 'OpenAI' },
  { value: 'claude' as const, label: 'Claude' },
  { value: 'gemini' as const, label: 'Gemini' },
  { value: 'ollama' as const, label: 'Ollama' },
]

const providerLabel = (p: ProviderType): string =>
  providerOptions.find((o) => o.value === p)?.label ?? p

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
const providerTagType = (p: ProviderType): TagType => {
  const map: Record<ProviderType, TagType> = {
    openai: 'success',
    claude: 'warning',
    gemini: 'primary',
    ollama: 'info',
  }
  return map[p]
}

// ============ 表单 ============
const defaultForm: ProviderForm = {
  name: '',
  provider: 'openai',
  baseUrl: '',
  apiKey: '',
  modelId: '',
  enabled: true,
}

const rules: FormRules = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { min: 2, max: 50, message: '长度 2-50 个字符', trigger: 'blur' },
  ],
  provider: [{ required: true, message: '请选择提供商类型', trigger: 'change' }],
  apiKey: [
    {
      validator: (rule, value, callback) => {
        const provider = (rule as { fullField?: string }).fullField
        // Ollama 允许空
        if (!value && provider !== 'ollama') {
          callback(new Error('请输入 API Key'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  baseUrl: [
    { required: true, message: '请输入 Base URL', trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+/i,
      message: '需以 http:// 或 https:// 开头',
      trigger: 'blur',
    },
  ],
}

// ============ 表格 / 弹窗 ref ============
interface TableExposed {
  refresh: () => Promise<void>
  reload: () => Promise<void>
}
interface DialogExposed {
  open: (data?: Partial<ProviderForm>) => void
  close: () => void
}

const tableRef = ref<TableExposed>()
const dialogRef = ref<DialogExposed>()
const dialogVisible = ref(false)
const apiKeyVisible = ref(false)

// 弹窗关闭时把 API Key 显示开关复位，下次打开默认隐藏
watch(dialogVisible, (visible) => {
  if (!visible) apiKeyVisible.value = false
})

function openCreate() {
  apiKeyVisible.value = false
  dialogRef.value?.open()
}

function openEdit(row: ProviderConfig) {
  apiKeyVisible.value = false
  // 编辑时不回填 apiKey（脱敏后回填会让用户误以为正确）
  dialogRef.value?.open({
    id: row.id,
    name: row.name,
    provider: row.provider,
    baseUrl: row.baseUrl,
    apiKey: '',
    modelId: row.modelId,
    enabled: row.enabled,
  })
}

async function handleSubmit(payload: ProviderForm, mode: 'create' | 'edit') {
  if (mode === 'create') {
    await createProvider(toUpsert(payload))
    notifySuccess('提供商创建成功')
  } else if (payload.id != null) {
    await updateProvider(payload.id, toUpsert(payload))
    notifySuccess('提供商已更新')
  }
  tableRef.value?.refresh()
}

function toUpsert(form: ProviderForm): ProviderUpsert {
  return {
    name: form.name,
    provider: form.provider,
    baseUrl: form.baseUrl,
    apiKey: form.apiKey,
    modelId: form.modelId,
    enabled: form.enabled,
  }
}

async function onDelete(row: ProviderConfig) {
  await confirmDelete(
    {
      message: `确认删除提供商「${row.name}」？关联的 Agent 将无法使用此模型。`,
      successMessage: '提供商已删除',
    },
    () => deleteProvider(row.id),
  )
  tableRef.value?.refresh()
}
</script>

<style scoped>
.mono-cell {
  font-size: 12px;
  color: var(--text-secondary);
  word-break: break-all;
}

/* 操作列两个按钮间距 8px */
:deep(.el-table .el-button + .el-button) {
  margin-left: var(--space-2);
}

/* 状态列 tag 颜色显式锁定，跟全局 info/success token 解耦
   启用 = 中性绿；禁用 = 中性灰；任何情况都不出现 danger 红 */
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

/* API Key 输入框右侧"显示 / 隐藏"切换按钮 */
.apikey-toggle {
  height: 22px;
  padding: 0 var(--space-2);
  color: var(--text-secondary);
  font-weight: var(--weight-medium);
}
.apikey-toggle:hover {
  color: var(--brand-600);
  background: transparent;
}
</style>
