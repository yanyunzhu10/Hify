<template>
  <div class="page-content">
    <PageHeader title="MCP 工具" description="管理 MCP Server，发现工具列表供 Agent 调用">
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新增 Server</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="content-card">
      <div class="content-card-title">Server 列表</div>
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        row-key="id"
        :row-style="{ height: '52px' }"
      >
        <template #col-name="{ row }">
          <router-link :to="`/mcp/${row.id}`" class="server-name">{{ row.name }}</router-link>
        </template>

        <template #col-endpoint="{ row }">
          <code class="server-endpoint">{{ row.endpoint }}</code>
        </template>

        <template #col-tools="{ row }">
          <span>{{ row.tools?.length ?? 0 }} 个</span>
        </template>

        <template #col-enabled="{ row }">
          <el-tag
            :type="row.enabled === 1 ? 'success' : 'info'"
            :class="['status-tag', row.enabled === 1 ? 'is-enabled' : 'is-disabled']"
            size="small" effect="light"
          >
            {{ row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <template #col-actions="{ row }">
          <el-button text type="primary" size="small" @click="onTest(row)">
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

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑 MCP Server' : '新增 MCP Server'"
      width="500px" :close-on-click-modal="false" destroy-on-close align-center
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" label-position="right" @submit.prevent>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="MCP 服务名称" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="用途描述（可选）" maxlength="256" show-word-limit />
        </el-form-item>
        <el-form-item label="Endpoint" prop="endpoint">
          <el-input v-model="form.endpoint" placeholder="http://localhost:9001/mcp" maxlength="500" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 连通性测试结果弹窗 -->
    <el-dialog v-model="testVisible" title="连通性测试" width="480px" align-center>
      <div v-if="testLoading" style="text-align:center;padding:20px">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p style="margin-top:12px;color:var(--text-secondary)">正在连接并拉取工具列表...</p>
      </div>
      <div v-else-if="testResult">
        <el-result :icon="testResult.success ? 'success' : 'error'" :title="testResult.success ? '连接成功' : '连接失败'">
          <template v-if="testResult.success" #sub-title>
            发现 {{ testResult.toolCount }} 个工具
          </template>
          <template v-else #sub-title>
            {{ testResult.errorMessage || '未知错误' }}
          </template>
        </el-result>
        <div v-if="testResult.success && testResult.tools?.length" class="test-tools">
          <div v-for="t in testResult.tools" :key="t.id" class="test-tool-item">
            <div class="test-tool-name">{{ t.name }}</div>
            <div class="test-tool-desc">{{ t.description || '无描述' }}</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Loading, Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import type { HifyTableColumn } from '@/components/HifyTable.vue'
import { confirmDelete } from '@/composables/useConfirm'
import { useViewport } from '@/composables/useViewport'
import { notifySuccess } from '@/utils/notify'
import {
  listMcpServers, createMcpServer, updateMcpServer, deleteMcpServer, testMcpServer,
  type McpServerInfo, type McpTestResult,
} from '@/api/mcp'

const { isNarrow } = useViewport(1200)

const columns = computed<HifyTableColumn<McpServerInfo>[]>(() => {
  const list: HifyTableColumn<McpServerInfo>[] = [
    { label: '名称', slot: 'name', minWidth: 160 },
    { label: 'Endpoint', slot: 'endpoint', minWidth: 240 },
    { label: '工具', slot: 'tools', width: 70, align: 'center' },
    { label: '状态', slot: 'enabled', width: 80, align: 'center' },
  ]
  if (!isNarrow.value) {
    list.push({ label: '创建时间', prop: 'createdAt', width: 170 })
  }
  list.push({ label: '操作', slot: 'actions', width: 180, align: 'right', fixed: 'right' })
  return list
})

async function fetchList(params: { page: number; size: number }) {
  return listMcpServers({ page: params.page, size: params.size })
    .then(res => ({ records: res.records ?? (res as unknown as McpServerInfo[]), total: res.total ?? 0 }))
}

// ============ 表单 ============
interface ServerForm { id?: number; name: string; description: string; endpoint: string; enabled: number }
const defaultForm = (): ServerForm => ({ name: '', description: '', endpoint: '', enabled: 1 })
const form = ref<ServerForm>(defaultForm())
const isEdit = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  endpoint: [{ required: true, message: '请输入 endpoint', trigger: 'blur' }],
}

function openCreate() { form.value = defaultForm(); isEdit.value = false; dialogVisible.value = true }
function openEdit(row: McpServerInfo) {
  form.value = { id: row.id, name: row.name, description: row.description || '', endpoint: row.endpoint, enabled: row.enabled }
  isEdit.value = true; dialogVisible.value = true
}
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false); if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value && form.value.id != null) {
      await updateMcpServer(form.value.id, { name: form.value.name, description: form.value.description, endpoint: form.value.endpoint, enabled: form.value.enabled })
      notifySuccess('已更新')
    } else {
      await createMcpServer({ name: form.value.name, description: form.value.description, endpoint: form.value.endpoint })
      notifySuccess('创建成功')
    }
    dialogVisible.value = false; tableRef.value?.refresh()
  } finally { submitting.value = false }
}

// ============ 删除 ============
async function onDelete(row: McpServerInfo) {
  await confirmDelete({ message: `确认删除 MCP Server「${row.name}」？`, successMessage: '已删除' }, () => deleteMcpServer(row.id))
  tableRef.value?.refresh()
}

// ============ 连通性测试 ============
const testVisible = ref(false)
const testLoading = ref(false)
const testResult = ref<McpTestResult | null>(null)
async function onTest(row: McpServerInfo) {
  testVisible.value = true; testLoading.value = true; testResult.value = null
  try { testResult.value = await testMcpServer(row.id) }
  catch { testResult.value = { success: false, toolCount: 0, tools: [], errorMessage: '请求失败' } }
  finally { testLoading.value = false }
}

const tableRef = ref<{ refresh: () => Promise<void>; reload: () => Promise<void> }>()
</script>

<style scoped>
.server-name { font-weight: var(--weight-medium); color: var(--brand-500); text-decoration: none; }
.server-name:hover { text-decoration: underline; }
.server-endpoint { font-size: var(--text-xs); color: var(--text-secondary); }
.test-tools { margin-top: var(--space-3); }
.test-tool-item { padding: var(--space-2) 0; border-bottom: 1px solid var(--border-divider); }
.test-tool-item:last-child { border-bottom: none; }
.test-tool-name { font-size: var(--text-sm); font-weight: var(--weight-medium); }
.test-tool-desc { font-size: var(--text-xs); color: var(--text-tertiary); margin-top: 2px; }
:deep(.status-tag.is-enabled) { --el-tag-bg-color: var(--mint-50); --el-tag-border-color: var(--mint-100); --el-tag-text-color: var(--mint-700); }
:deep(.status-tag.is-disabled) { --el-tag-bg-color: var(--bg-muted); --el-tag-border-color: var(--border-base); --el-tag-text-color: var(--text-tertiary); }
.is-loading { animation: rotating 1s linear infinite; }
@keyframes rotating { from { transform: rotate(0deg) } to { transform: rotate(360deg) } }
</style>
