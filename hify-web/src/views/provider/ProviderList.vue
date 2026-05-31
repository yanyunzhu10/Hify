<template>
  <div class="page-content">
    <PageHeader
      title="模型提供商管理"
      description="集中管理 LLM 提供商配置，支持连通性测试与启停切换"
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
        :api="getProviderList"
        row-key="id"
        :row-style="{ height: '52px' }"
      >
        <template #col-type="{ row }">
          <el-tag :type="providerTagType(row.type)" size="small" effect="light">
            {{ providerLabel(row.type) }}
          </el-tag>
        </template>

        <template #col-baseUrl="{ row }">
          <span class="text-mono mono-cell">{{ row.baseUrl || '—' }}</span>
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

        <template #col-health="{ row }">
          <div v-if="row.health" class="health-cell">
            <el-tag :type="healthTagType(row.health.status)" size="small" effect="light">
              {{ healthLabel(row.health.status) }}
            </el-tag>
            <span v-if="row.health.latencyMs != null" class="latency-text">
              {{ row.health.latencyMs }}ms
            </span>
          </div>
          <span v-else class="text-tertiary">—</span>
        </template>

        <template #col-models="{ row }">
          <template v-if="row.modelCount > 0">
            <el-popover placement="bottom-start" :width="260" trigger="click">
              <template #reference>
                <el-link type="primary" :underline="false" class="model-count-link">
                  {{ row.modelCount }} 个
                </el-link>
              </template>
              <div class="model-popover-list">
                <div
                  v-for="m in row.modelConfigs"
                  :key="m.id"
                  class="model-popover-item"
                >
                  <span class="model-name">{{ m.name }}</span>
                  <span class="model-id">{{ m.modelId }}</span>
                  <el-tag
                    :type="m.enabled === 1 ? 'primary' : 'info'"
                    size="small"
                    class="model-status-tag"
                  >
                    {{ m.enabled === 1 ? '启用' : '禁用' }}
                  </el-tag>
                </div>
              </div>
            </el-popover>
          </template>
          <span v-else class="text-tertiary">0</span>
        </template>

        <template #col-actions="{ row }">
          <el-button text type="primary" size="small" @click="onTestConnection(row)">
            测试
          </el-button>
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
            placeholder="给提供商起一个易识别的名字"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select
            v-model="form.type"
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
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { FormRules } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import type { HifyTableColumn } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import { confirmDelete } from '@/composables/useConfirm'
import { useViewport } from '@/composables/useViewport'
import { notifySuccess } from '@/utils/notify'
import {
  getProviderList,
  createProvider,
  updateProvider,
  deleteProvider,
  testConnection,
  type ProviderUpsert,
} from '@/api/provider'
import type { ProviderConfig } from '@/types'

// ============ 表格列配置 ============
const { isNarrow } = useViewport(1200)

const columns = computed<HifyTableColumn<ProviderConfig>[]>(() => {
  const list: HifyTableColumn<ProviderConfig>[] = [
    { label: '名称', prop: 'name', minWidth: 160 },
    { label: '类型', slot: 'type', width: 100 },
  ]
  if (!isNarrow.value) {
    list.push({ label: 'Base URL', slot: 'baseUrl', minWidth: 240 })
  }
  list.push(
    { label: '状态', slot: 'enabled', width: 80, align: 'center' },
    { label: '健康状态', slot: 'health', width: 160, align: 'center' },
    { label: '模型数', slot: 'models', width: 90, align: 'center' },
  )
  if (!isNarrow.value) {
    list.push({ label: '创建时间', prop: 'createdAt', width: 170 })
  }
  list.push({ label: '操作', slot: 'actions', width: 170, align: 'right', fixed: 'right' })
  return list
})

// ============ 类型展示映射 ============
const providerOptions = [
  { value: 'openai', label: 'OpenAI' },
  { value: 'anthropic', label: 'Claude' },
  { value: 'gemini', label: 'Gemini' },
  { value: 'ollama', label: 'Ollama' },
  { value: 'openai_compatible', label: 'OpenAI Compatible' },
]

const providerLabel = (p: string): string =>
  providerOptions.find((o) => o.value === p)?.label ?? p

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
const providerTagType = (p: string): TagType => {
  const map: Record<string, TagType> = {
    openai: 'success',
    anthropic: 'warning',
    gemini: 'primary',
    ollama: 'info',
    openai_compatible: 'primary',
  }
  return map[p] ?? 'info'
}

// ============ 健康状态映射 ============
const healthLabel = (s: string): string => {
  const map: Record<string, string> = {
    UP: '正常',
    DOWN: '异常',
    DEGRADED: '降级',
    UNKNOWN: '未知',
  }
  return map[s] ?? s
}

const healthTagType = (s: string): TagType => {
  const map: Record<string, TagType> = {
    UP: 'success',
    DOWN: 'danger',
    DEGRADED: 'warning',
    UNKNOWN: 'info',
  }
  return map[s] ?? 'info'
}

// ============ 表单 ============
interface ProviderForm {
  id?: number
  name: string
  type: string
  apiKey: string
  baseUrl: string
}

const defaultForm: ProviderForm = {
  name: '',
  type: 'openai',
  apiKey: '',
  baseUrl: '',
}

const rules: FormRules = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { min: 2, max: 50, message: '长度 2-50 个字符', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择提供商类型', trigger: 'change' }],
  apiKey: [
    {
      validator: (_rule, value, callback) => {
        // Ollama 允许空 apiKey
        if (!value && dialogRef.value) {
          callback()
          return
        }
        callback()
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

watch(dialogVisible, (visible) => {
  if (!visible) apiKeyVisible.value = false
})

function openCreate() {
  apiKeyVisible.value = false
  if (!dialogRef.value) {
    console.error('dialogRef 未绑定，请检查 HifyFormDialog 是否正确渲染')
    return
  }
  dialogRef.value.open()
}

function openEdit(row: ProviderConfig) {
  apiKeyVisible.value = false
  dialogRef.value?.open({
    id: row.id,
    name: row.name,
    type: row.type,
    baseUrl: row.baseUrl,
    apiKey: '',
  })
}

async function handleSubmit(payload: ProviderForm, mode: 'create' | 'edit') {
  const upsert: ProviderUpsert = {
    name: payload.name,
    type: payload.type,
    baseUrl: payload.baseUrl,
    authConfig: payload.apiKey ? { apiKey: payload.apiKey } : undefined,
    enabled: 1,
  }

  if (mode === 'create') {
    await createProvider(upsert)
    notifySuccess('提供商创建成功')
  } else if (payload.id != null) {
    await updateProvider(payload.id, upsert)
    notifySuccess('提供商已更新')
  }
  tableRef.value?.refresh()
}

// ============ 删除 ============
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

// ============ 连通性测试 ============
async function onTestConnection(row: ProviderConfig) {
  try {
    const result = await testConnection(row.id)
    if (result.success) {
      ElMessage.success(
        `连通性测试通过：延迟 ${result.latencyMs}ms，可用模型 ${result.modelCount ?? 0} 个`,
      )
    } else {
      ElMessage.warning(`连通性测试失败：${result.errorMessage ?? '未知错误'}`)
    }
  } catch {
    // 错误已由全局拦截器 toast
  } finally {
    tableRef.value?.refresh()
  }
}
</script>

<style scoped>
.mono-cell {
  font-size: 12px;
  color: var(--text-secondary);
  word-break: break-all;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: var(--space-2);
}

/* 状态列 tag */
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

/* 健康状态列 */
.health-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  justify-content: center;
}
.latency-text {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

/* 模型数弹窗 */
.model-count-link {
  font-size: var(--font-size-base);
}
.model-popover-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.model-popover-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) 0;
  border-bottom: 1px solid var(--border-light);
}
.model-popover-item:last-child {
  border-bottom: none;
}
.model-name {
  font-weight: var(--weight-medium);
  flex: 1;
}
.model-id {
  font-size: 12px;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
}
.model-status-tag {
  flex-shrink: 0;
}

/* API Key 切换 */
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

.text-tertiary {
  color: var(--text-tertiary);
}
.text-mono {
  font-family: var(--font-mono);
}
</style>
