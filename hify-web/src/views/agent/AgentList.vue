<template>
  <div class="page-content">
    <PageHeader
      title="Agent 管理"
      description="配置 AI 助手的身份、模型、参数和可用工具"
    >
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新增 Agent</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="content-card">
      <div class="content-card-title">Agent 列表</div>
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="getAgentList"
        row-key="id"
        :row-style="{ height: '52px' }"
      >
        <template #col-name="{ row }">
          <div class="name-cell">
            <span class="name-text">{{ row.name }}</span>
            <span v-if="row.description" class="name-desc">{{ row.description }}</span>
          </div>
        </template>

        <template #col-modelName="{ row }">
          <span :class="row.modelName ? '' : 'text-tertiary'">
            {{ row.modelName || '—' }}
          </span>
        </template>

        <template #col-toolCount="{ row }">
          <span :class="row.toolCount > 0 ? '' : 'text-tertiary'">
            {{ row.toolCount }}
          </span>
        </template>

        <template #col-temperature="{ row }">
          <span>{{ row.temperature != null ? row.temperature.toFixed(1) : '—' }}</span>
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

    <HifyFormDialog
      ref="dialogRef"
      v-model="dialogVisible"
      title="Agent"
      :rules="rules"
      :default-form="defaultForm"
      width="640px"
      label-width="110px"
      label-position="right"
      @submit="handleSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="给 Agent 起一个名字"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="一句话说明这个 Agent 做什么"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="绑定模型" prop="modelConfigId">
          <el-select
            v-model="form.modelConfigId"
            placeholder="选择模型"
            style="width: 100%"
            :loading="modelLoading"
          >
            <el-option-group
              v-for="group in modelGroups"
              :key="group.provider"
              :label="group.provider"
            >
              <el-option
                v-for="opt in group.options"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-option-group>
          </el-select>
        </el-form-item>

        <el-form-item label="System Prompt" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="6"
            placeholder="定义 Agent 的角色、语气、职责边界和禁止事项"
          />
        </el-form-item>

        <el-divider content-position="left">模型参数</el-divider>

        <el-form-item label="Temperature" prop="temperature">
          <div class="slider-row">
            <el-slider
              v-model="form.temperature"
              :min="0"
              :max="1"
              :step="0.1"
              :marks="temperatureMarks"
              show-input
              style="flex: 1"
            />
          </div>
        </el-form-item>

        <el-form-item label="Max Tokens" prop="maxTokens">
          <el-input-number
            v-model="form.maxTokens"
            :min="1"
            :max="131072"
            :step="256"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="上下文轮数" prop="maxContextTurns">
          <el-input-number
            v-model="form.maxContextTurns"
            :min="0"
            :max="50"
            controls-position="right"
            style="width: 100%"
          />
          <div class="field-hint">保留最近几轮对话上下文，0 表示不保留历史</div>
        </el-form-item>

        <el-divider content-position="left">工具绑定</el-divider>

        <el-form-item label="MCP 工具">
          <el-checkbox-group v-model="form.toolIds" class="tool-checkbox-group">
            <el-checkbox
              v-for="tool in toolOptions"
              :key="tool.value"
              :value="tool.value"
              :label="tool.value"
            >
              <span class="tool-label">{{ tool.label }}</span>
              <span v-if="tool.description" class="tool-desc">{{ tool.description }}</span>
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="toolOptions.length === 0" class="text-tertiary" style="font-size:13px">
            暂无可用的 MCP 工具
          </div>
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
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
  getAgentList,
  getAgentDetail,
  createAgent,
  updateAgent,
  updateAgentTools,
  deleteAgent,
  fetchModelOptions,
  fetchToolOptions,
  type AgentCreateReq,
  type AgentUpdateReq,
  type ModelOption,
  type McpToolOption,
} from '@/api/agent'
import type { AgentConfig } from '@/types'

// ============ 表格列配置 ============
const { isNarrow } = useViewport(1200)

const columns = computed<HifyTableColumn<AgentConfig>[]>(() => {
  const list: HifyTableColumn<AgentConfig>[] = [
    { label: '名称', slot: 'name', minWidth: 180 },
    { label: '关联模型', slot: 'modelName', minWidth: 140 },
    { label: '工具', slot: 'toolCount', width: 70, align: 'center' },
    { label: 'Temperature', slot: 'temperature', width: 120, align: 'center' },
    { label: '状态', slot: 'enabled', width: 80, align: 'center' },
  ]
  if (!isNarrow.value) {
    list.push({ label: '创建时间', prop: 'createdAt', width: 170 })
  }
  list.push({ label: '操作', slot: 'actions', width: 140, align: 'right', fixed: 'right' })
  return list
})

// ============ 表单 ============
interface AgentForm {
  id?: number
  name: string
  description: string
  modelConfigId: number | null
  systemPrompt: string
  temperature: number
  maxTokens: number
  maxContextTurns: number
  enabled: number
  toolIds: number[]
}

const defaultForm: AgentForm = {
  name: '',
  description: '',
  modelConfigId: null,
  systemPrompt: '',
  temperature: 0.7,
  maxTokens: 2048,
  maxContextTurns: 10,
  enabled: 1,
  toolIds: [],
}

const temperatureMarks: Record<number, string> = {
  0: '0',
  0.5: '0.5',
  1: '1',
}

const rules: FormRules = {
  name: [
    { required: true, message: '请输入 Agent 名称', trigger: 'blur' },
    { min: 1, max: 100, message: '长度 1-100 个字符', trigger: 'blur' },
  ],
  modelConfigId: [{ required: true, message: '请选择绑定的模型', trigger: 'change' }],
  systemPrompt: [{ required: true, message: '请输入 System Prompt', trigger: 'blur' }],
  temperature: [{ required: true, message: '请设置 Temperature', trigger: 'change' }],
}

// ============ 模型 / 工具选项 ============
const modelOptions = ref<ModelOption[]>([])
const modelLoading = ref(false)
const toolOptions = ref<McpToolOption[]>([])

const modelGroups = computed(() => {
  const map = new Map<string, ModelOption[]>()
  for (const opt of modelOptions.value) {
    const list = map.get(opt.providerName) || []
    list.push(opt)
    map.set(opt.providerName, list)
  }
  return Array.from(map.entries()).map(([provider, options]) => ({ provider, options }))
})

async function loadOptions() {
  modelLoading.value = true
  try {
    const [models, tools] = await Promise.all([
      fetchModelOptions(),
      fetchToolOptions(),
    ])
    modelOptions.value = models
    toolOptions.value = tools
  } finally {
    modelLoading.value = false
  }
}

onMounted(() => {
  loadOptions()
})

// ============ 表格 / 弹窗 ref ============
interface TableExposed {
  refresh: () => Promise<void>
  reload: () => Promise<void>
}
interface DialogExposed {
  open: (data?: Partial<AgentForm>) => void
  close: () => void
}

const tableRef = ref<TableExposed>()
const dialogRef = ref<DialogExposed>()
const dialogVisible = ref(false)

function openCreate() {
  dialogRef.value?.open()
}

async function openEdit(row: AgentConfig) {
  // 编辑时从详情接口拉取完整数据（含 tools），列表只返回 toolCount
  try {
    const detail = await getAgentDetail(row.id)
    dialogRef.value?.open({
      id: detail.id,
      name: detail.name,
      description: detail.description || '',
      modelConfigId: detail.modelConfigId,
      systemPrompt: detail.systemPrompt,
      temperature: detail.temperature,
      maxTokens: detail.maxTokens,
      maxContextTurns: detail.maxContextTurns,
      enabled: detail.enabled,
      toolIds: detail.tools?.map((t) => t.toolId) ?? [],
    })
  } catch {
    // 详情获取失败则用列表行数据兜底（工具列表为空）
    dialogRef.value?.open({
      id: row.id,
      name: row.name,
      description: row.description || '',
      modelConfigId: row.modelConfigId,
      systemPrompt: row.systemPrompt,
      temperature: row.temperature,
      maxTokens: row.maxTokens,
      maxContextTurns: row.maxContextTurns,
      enabled: row.enabled,
      toolIds: [],
    })
  }
}

async function handleSubmit(payload: AgentForm, mode: 'create' | 'edit') {
  if (mode === 'create') {
    const req: AgentCreateReq = {
      name: payload.name,
      description: payload.description || undefined,
      systemPrompt: payload.systemPrompt,
      modelConfigId: payload.modelConfigId!,
      temperature: payload.temperature,
      maxTokens: payload.maxTokens,
      maxContextTurns: payload.maxContextTurns,
      enabled: payload.enabled,
      toolIds: payload.toolIds.length > 0 ? payload.toolIds : undefined,
    }
    await createAgent(req)
    notifySuccess('Agent 创建成功')
  } else if (payload.id != null) {
    const req: AgentUpdateReq = {
      name: payload.name,
      description: payload.description || undefined,
      systemPrompt: payload.systemPrompt,
      modelConfigId: payload.modelConfigId!,
      temperature: payload.temperature,
      maxTokens: payload.maxTokens,
      maxContextTurns: payload.maxContextTurns,
      enabled: payload.enabled,
    }
    await updateAgent(payload.id, req)
    await updateAgentTools(payload.id, payload.toolIds)
    notifySuccess('Agent 已更新')
  }
  tableRef.value?.refresh()
  loadOptions()
}

// ============ 删除 ============
async function onDelete(row: AgentConfig) {
  await confirmDelete(
    {
      message: `确认删除 Agent「${row.name}」？关联的会话历史将保留但不可再使用。`,
      successMessage: 'Agent 已删除',
    },
    () => deleteAgent(row.id),
  )
  tableRef.value?.refresh()
}
</script>

<style scoped>
.name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.name-text {
  font-weight: var(--weight-medium);
}
.name-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.slider-row {
  display: flex;
  align-items: center;
  width: 100%;
}

.field-hint {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

.tool-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.tool-label {
  font-weight: var(--weight-medium);
}
.tool-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-left: 6px;
}

.text-tertiary {
  color: var(--text-tertiary);
}

:deep(.el-table .el-button + .el-button) {
  margin-left: var(--space-2);
}
</style>
