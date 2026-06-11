<template>
  <div class="page-content">
    <PageHeader :title="server?.name || 'MCP Server'" description="查看详情、调试工具">
      <template #actions>
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
      </template>
    </PageHeader>

    <div v-loading="loading">
      <el-empty v-if="!loading && !server" description="Server 不存在" :image-size="80" />
      <template v-if="server">
        <!-- 基本信息 -->
        <div class="content-card">
          <div class="content-card-title">基本信息</div>
          <div class="info-grid">
            <div class="info-item"><span class="info-label">名称</span><span class="info-val">{{ server.name }}</span></div>
            <div class="info-item"><span class="info-label">描述</span><span class="info-val">{{ server.description || '—' }}</span></div>
            <div class="info-item"><span class="info-label">Endpoint</span><code class="info-code">{{ server.endpoint }}</code></div>
            <div class="info-item">
              <span class="info-label">状态</span>
              <el-tag :type="server.enabled===1?'success':'info'" size="small" effect="light">{{ server.enabled===1?'启用':'禁用' }}</el-tag>
            </div>
            <div class="info-item"><span class="info-label">工具数</span><span class="info-val">{{ tools.length }} 个</span></div>
            <div class="info-item"><span class="info-label">创建时间</span><span class="info-val">{{ server.createdAt }}</span></div>
          </div>
        </div>

        <!-- Tab: 工具列表 / 调试 -->
        <div class="content-card">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="工具列表" name="list">
              <el-table :data="tools" size="small" style="width:100%">
                <el-table-column prop="name" label="名称" min-width="140" />
                <el-table-column prop="description" label="描述" min-width="200">
                  <template #default="{ row }"><span :class="row.description?'':'text-tertiary'">{{ row.description || '无描述' }}</span></template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="right">
                  <template #default="{ row }">
                    <el-button text type="primary" size="small" @click="selectTool(row)">调试</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane v-if="selectedTool" label="调试" name="debug">
              <div class="debug-layout">
                <!-- 左侧工具列表 -->
                <div class="debug-tool-list">
                  <div class="debug-section-title">工具</div>
                  <div
                    v-for="t in tools" :key="t.id"
                    class="debug-tool-item"
                    :class="{ 'is-active': t.name === selectedTool.name }"
                    @click="selectTool(t)"
                  >
                    <div class="debug-tool-name">{{ t.name }}</div>
                    <div class="debug-tool-desc">{{ t.description || '无描述' }}</div>
                  </div>
                </div>
                <!-- 右侧调试面板 -->
                <div class="debug-panel">
                  <template v-if="selectedTool">
                    <div class="debug-panel-head">
                      <div class="debug-tool-title">{{ selectedTool.name }}</div>
                      <div class="debug-tool-desc">{{ selectedTool.description || '无描述' }}</div>
                    </div>

                    <el-divider />
                    <el-form ref="debugFormRef" :model="debugArgs" label-width="90px" @submit.prevent>
                      <el-form-item
                        v-for="(prop, key) in paramSchema.properties"
                        :key="key"
                        :label="key"
                        :required="paramSchema.required?.includes(key)"
                      >
                        <el-input
                          v-if="prop.type === 'string'"
                          v-model="debugArgs[key]"
                          :placeholder="prop.description || key"
                        />
                        <el-input-number
                          v-else-if="prop.type === 'number' || prop.type === 'integer'"
                          v-model="debugArgs[key]"
                          :placeholder="prop.description || key"
                          controls-position="right"
                          style="width:100%"
                        />
                        <el-input v-else v-model="debugArgs[key]" :placeholder="prop.description || key" />
                        <div v-if="prop.description" class="field-hint">{{ prop.description }}</div>
                      </el-form-item>
                    </el-form>

                    <div class="debug-actions">
                      <el-button type="primary" :loading="debugBusy" @click="doDebug">
                        <el-icon><CaretRight /></el-icon> 调用
                      </el-button>
                    </div>

                    <el-divider v-if="debugHistory.length" />

                    <!-- 结果 & 历史 -->
                    <div v-if="debugHistory.length" class="debug-results">
                      <div class="debug-section-title">
                        调用结果（最近 {{ debugHistory.length }} 次）
                        <span class="debug-elapsed">耗时 {{ latestResult?.elapsedMs }}ms</span>
                      </div>

                      <div class="debug-result-body">
                        <div class="debug-result-text">{{ latestResult?.result || '(空)' }}</div>
                      </div>

                      <!-- 历史记录 -->
                      <el-collapse v-if="debugHistory.length > 1" class="debug-history">
                        <el-collapse-item :title="`历史记录 (${debugHistory.length - 1} 条)`">
                          <div v-for="(h, i) in debugHistory.slice(0, -1).reverse()" :key="i" class="history-item">
                            <div class="history-meta">{{ formatTime(h.ts) }} · {{ h.elapsedMs }}ms</div>
                            <pre class="history-text">{{ h.result }}</pre>
                          </div>
                        </el-collapse-item>
                      </el-collapse>
                    </div>
                  </template>
                  <el-empty v-else description="在左侧选择工具开始调试" :image-size="64" />
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, CaretRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import { listMcpServers, debugMcpTool, type McpServerInfo, type McpToolBrief } from '@/api/mcp'

const route = useRoute()
const id = Number(route.params.id)
const server = ref<McpServerInfo | null>(null)
const tools = ref<McpToolBrief[]>([])
const loading = ref(false)
const activeTab = ref('list')

// 选中的工具 + 参数表单 + 调用历史
interface HistoryEntry { ts: number; result: string; elapsedMs: number }
const selectedTool = ref<McpToolBrief | null>(null)
const debugArgs = reactive<Record<string, any>>({})
const debugBusy = ref(false)
const debugHistory = ref<HistoryEntry[]>([])
const latestResult = computed(() => debugHistory.value[debugHistory.value.length - 1] ?? null)
const debugFormRef = ref()

// 从 inputSchema 提取参数 schema
const paramSchema = computed(() => {
  const s = selectedTool.value?.inputSchema
  if (!s) return { properties: {}, required: [] as string[] }
  return {
    properties: (s.properties ?? {}) as Record<string, any>,
    required: (s.required ?? []) as string[],
  }
})

onMounted(async () => {
  loading.value = true
  try {
    const res = await listMcpServers({ page: 1, size: 1 })
    const found = (res.records ?? []).find((s: McpServerInfo) => s.id === id)
    if (found) {
      server.value = found
      tools.value = found.tools ?? []
    }
  } finally { loading.value = false }
})

function selectTool(tool: McpToolBrief) {
  selectedTool.value = tool
  // 重置参数表单
  Object.keys(debugArgs).forEach(k => delete debugArgs[k])
  const props = tool.inputSchema?.properties ?? {}
  for (const k of Object.keys(props)) {
    const prop = (props as Record<string, any>)[k]
    debugArgs[k] = prop.type === 'number' || prop.type === 'integer' ? undefined : ''
  }
  activeTab.value = 'debug'
}

async function doDebug() {
  if (!selectedTool.value || debugBusy.value) return
  debugBusy.value = true
  const ts = Date.now()
  try {
    const res = await debugMcpTool(id, selectedTool.value.name, { ...debugArgs })
    const h: HistoryEntry = { ts, result: res.result, elapsedMs: res.elapsedMs }
    debugHistory.value.push(h)
    // 只保留最近 5 次
    if (debugHistory.value.length > 5) debugHistory.value.shift()
    ElMessage.success(`调用完成 (${res.elapsedMs}ms)`)
  } catch {
    const h: HistoryEntry = { ts, result: '(调用失败)', elapsedMs: 0 }
    debugHistory.value.push(h)
    if (debugHistory.value.length > 5) debugHistory.value.shift()
  } finally { debugBusy.value = false }
}

function formatTime(ts: number) {
  return new Date(ts).toLocaleTimeString()
}
</script>

<style scoped>
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: var(--space-4); }
.info-item { display: flex; flex-direction: column; gap: 2px; }
.info-label { font-size: var(--text-xs); color: var(--text-tertiary); }
.info-val { font-size: var(--text-sm); color: var(--text-primary); }
.info-code { font-size: var(--text-xs); background: var(--bg-muted); padding: 2px 6px; border-radius: var(--radius-sm); }
.text-tertiary { color: var(--text-tertiary); }

.debug-layout { display: flex; gap: var(--space-4); min-height: 400px; }
.debug-tool-list { width: 200px; flex-shrink: 0; border-right: 1px solid var(--border-light); padding-right: var(--space-3); }
.debug-panel { flex: 1; min-width: 0; }
.debug-section-title { font-size: var(--text-xs); color: var(--text-tertiary); text-transform: uppercase; margin-bottom: var(--space-2); }
.debug-tool-item { padding: var(--space-2); border-radius: var(--radius-sm); cursor: pointer; margin-bottom: 4px; transition: background var(--duration-fast); }
.debug-tool-item:hover { background: var(--bg-subtle); }
.debug-tool-item.is-active { background: var(--brand-50); font-weight: var(--weight-medium); }
.debug-tool-name { font-size: var(--text-sm); }
.debug-tool-desc { font-size: var(--text-xs); color: var(--text-tertiary); margin-top: 2px; }
.debug-panel-head { margin-bottom: var(--space-2); }
.debug-tool-title { font-size: var(--text-md); font-weight: var(--weight-semi); }
.debug-actions { margin-top: var(--space-3); }
.debug-elapsed { font-size: var(--text-xs); color: var(--brand-500); margin-left: var(--space-4); }
.debug-result-body { background: var(--bg-muted); border: 1px solid var(--border-light); border-radius: var(--radius-md); padding: var(--space-3); font-family: var(--font-mono); font-size: var(--text-xs); white-space: pre-wrap; word-break: break-word; max-height: 300px; overflow-y: auto; }
.field-hint { font-size: var(--text-xs); color: var(--text-tertiary); margin-top: 2px; }
.debug-history { margin-top: var(--space-3); }
.history-item { padding: var(--space-2) 0; border-bottom: 1px solid var(--border-divider); }
.history-item:last-child { border-bottom: none; }
.history-meta { font-size: var(--text-xs); color: var(--text-tertiary); margin-bottom: 4px; }
.history-text { font-family: var(--font-mono); font-size: 11px; white-space: pre-wrap; word-break: break-word; background: var(--bg-muted); padding: var(--space-2); border-radius: var(--radius-sm); max-height: 120px; overflow-y: auto; margin: 0; }
</style>
