<template>
  <div class="page-content">
    <PageHeader :title="kbName" :description="`管理文档，上传后自动分块与向量化`">
      <template #actions>
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回</span>
        </el-button>
        <el-button type="primary" @click="uploadVisible = true">
          <el-icon><Upload /></el-icon>
          <span>上传文档</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="content-card">
      <div class="content-card-title">文档列表</div>
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        row-key="id"
        :row-style="{ height: '52px' }"
      >
        <template #col-name="{ row }">
          <span class="doc-name">{{ row.name }}</span>
        </template>

        <template #col-fileType="{ row }">
          <el-tag size="small" type="info" effect="plain">{{ row.fileType.toUpperCase() }}</el-tag>
        </template>

        <template #col-fileSize="{ row }">
          {{ formatSize(row.fileSize) }}
        </template>

        <template #col-status="{ row }">
          <div class="status-cell">
            <el-tag
              :type="statusType(row.status)"
              :class="['status-tag', `is-${row.status.toLowerCase()}`]"
              size="small"
              effect="light"
            >
              <el-icon v-if="row.status === 'PROCESSING'" class="is-loading status-icon">
                <Loading />
              </el-icon>
              {{ statusLabel(row.status) }}
            </el-tag>
            <el-tooltip
              v-if="row.status === 'FAILED' && row.errorMessage"
              :content="row.errorMessage"
              placement="top"
              effect="dark"
            >
              <el-icon class="error-hint"><WarningFilled /></el-icon>
            </el-tooltip>
          </div>
        </template>

        <template #col-actions="{ row }">
          <el-button text type="primary" size="small" @click="openChunks(row)">
            查看分块
          </el-button>
          <el-button
            text
            type="danger"
            size="small"
            :disabled="row.status === 'PROCESSING'"
            @click="onDelete(row)"
          >
            删除
          </el-button>
        </template>
      </HifyTable>
    </div>

    <!-- ══════════ 上传弹窗 ══════════ -->
    <el-dialog
      v-model="uploadVisible"
      title="上传文档"
      width="480px"
      :close-on-click-modal="false"
      align-center
    >
      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        multiple
        :accept="'.txt,.md,.pdf'"
        :limit="10"
        :http-request="doUpload"
        :before-upload="beforeUpload"
        :on-success="onUploadSuccess"
        :on-error="onUploadError"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">
          <p>将文件拖到此处，或 <em>点击上传</em></p>
          <p class="upload-hint">支持 TXT / MD / PDF，单个最大 10 MB</p>
        </div>
      </el-upload>
      <template #footer>
        <el-button @click="uploadVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ══════════ 查看分块弹窗 ══════════ -->
    <el-dialog
      v-model="chunksVisible"
      :title="`分块列表 - ${chunksDocName}`"
      width="720px"
      align-center
    >
      <div v-loading="chunksLoading">
        <el-empty v-if="!chunksLoading && chunks.length === 0" description="暂无分块" :image-size="64" />
        <div v-else class="chunks-list">
          <div v-for="c in chunks" :key="c.id" class="chunk-item">
            <div class="chunk-head">
              <el-tag size="small" type="info">#{{ c.chunkIndex }}</el-tag>
              <span class="chunk-tokens">{{ c.tokenCount }} tokens</span>
            </div>
            <div class="chunk-body">
              <template v-if="c.collapsed && c.content.length > 200">
                {{ c.content.slice(0, 200) }}
                <el-button text type="primary" size="small" @click="c.collapsed = false">
                  ...展开全文
                </el-button>
              </template>
              <template v-else>
                {{ c.content }}
                <el-button
                  v-if="c.content.length > 200"
                  text
                  type="primary"
                  size="small"
                  @click="c.collapsed = true"
                >
                  收起
                </el-button>
              </template>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="chunksVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import {
  ArrowLeft,
  Loading,
  Upload,
  UploadFilled,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import HifyTable from '@/components/HifyTable.vue'
import type { HifyTableColumn } from '@/components/HifyTable.vue'
import { confirmDelete } from '@/composables/useConfirm'
import { useViewport } from '@/composables/useViewport'
import { getKnowledgeBase } from '@/api/knowledge-base'
import {
  listDocuments,
  uploadDocument,
  getDocumentDetail,
  listChunks,
  deleteDocument,
} from '@/api/document'
import type { DocumentInfo, ChunkInfo } from '@/types'

const route = useRoute()
const kbId = Number(route.params.kbId)
const kbName = ref('')

onMounted(async () => {
  try {
    const kb = await getKnowledgeBase(kbId)
    kbName.value = kb.name
  } catch {
    kbName.value = `知识库 #${kbId}`
  }
})

// ============ 表格列配置 ============
const { isNarrow } = useViewport(1200)

const columns = computed<HifyTableColumn<DocumentInfo>[]>(() => {
  const list: HifyTableColumn<DocumentInfo>[] = [
    { label: '文件名', slot: 'name', minWidth: 200 },
    { label: '类型', slot: 'fileType', width: 72, align: 'center' },
    { label: '大小', slot: 'fileSize', width: 90, align: 'right' },
    { label: '分块', prop: 'chunkCount', width: 64, align: 'center' },
    { label: '状态', slot: 'status', width: 130, align: 'center' },
  ]
  if (!isNarrow.value) {
    list.push({ label: '创建时间', prop: 'createdAt', width: 170 })
  }
  list.push({ label: '操作', slot: 'actions', width: 160, align: 'right', fixed: 'right' })
  return list
})

async function fetchList(params: { page: number; size: number }) {
  const res = await listDocuments(kbId, { page: params.page, size: params.size })
  const arr = res as unknown as DocumentInfo[]
  return { records: arr, total: arr.length }
}

const tableRef = ref<{ refresh: () => Promise<void>; reload: () => Promise<void> }>()

// ============ 状态展示 ============
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const STATUS_MAP: Record<string, { label: string; type: TagType }> = {
  PENDING:    { label: '等待中',   type: 'info' },
  PROCESSING: { label: '处理中',   type: 'primary' },
  DONE:       { label: '已完成',   type: 'success' },
  FAILED:     { label: '失败',     type: 'danger' },
}

function statusType(s: string): TagType {
  return STATUS_MAP[s]?.type ?? 'info'
}
function statusLabel(s: string) {
  return STATUS_MAP[s]?.label ?? s
}

function formatSize(bytes: number): string {
  if (bytes == null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1048576).toFixed(2)} MB`
}

// ============ 上传 ============
const uploadVisible = ref(false)
const uploadRef = ref()

function beforeUpload(file: File) {
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !['txt', 'md', 'pdf'].includes(ext)) {
    ElMessage.error(`不支持的文件类型: ${ext || '未知'}，仅接受 txt / md / pdf`)
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error(`文件过大: ${(file.size / 1048576).toFixed(2)} MB，最大 10 MB`)
    return false
  }
  return true
}

async function doUpload(options: { file: File; onSuccess: () => void; onError: (e: Error) => void }) {
  try {
    const doc = await uploadDocument(kbId, options.file)
    options.onSuccess()
    ElMessage.success(`「${doc.name}」上传成功，开始处理`)
    // 将新 doc 加入轮询队列
    if (doc.status === 'PENDING' || doc.status === 'PROCESSING') {
      pollingSet.value.add(doc.id)
      ensurePolling()
    }
    tableRef.value?.reload()
  } catch (e) {
    options.onError(e as Error)
  }
}

function onUploadSuccess() { /* handled in doUpload */ }
function onUploadError() { /* error toast already shown by request interceptor */ }

// ============ 轮询 ============
const pollingSet = ref<Set<number>>(new Set())
let pollingTimer: ReturnType<typeof setInterval> | null = null

function ensurePolling() {
  if (pollingTimer != null) return
  pollingTimer = setInterval(async () => {
    if (pollingSet.value.size === 0) {
      stopPolling()
      return
    }
    const ids = Array.from(pollingSet.value)
    const stillPending = new Set<number>()
    for (const id of ids) {
      try {
        const d = await getDocumentDetail(id)
        if (d.status === 'DONE' || d.status === 'FAILED') {
          // 停止轮询此文档，刷新列表
        } else {
          stillPending.add(id)
        }
      } catch {
        // 文档可能已被删除
      }
    }
    pollingSet.value = stillPending
    if (stillPending.size > 0 || ids.length !== stillPending.size) {
      tableRef.value?.refresh()
    }
  }, 3000)
}

function stopPolling() {
  if (pollingTimer != null) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

onBeforeUnmount(() => stopPolling())

// ============ 查看分块 ============
const chunksVisible = ref(false)
const chunksDocName = ref('')
const chunks = ref<(ChunkInfo & { collapsed: boolean })[]>([])
const chunksLoading = ref(false)

async function openChunks(row: DocumentInfo) {
  chunksVisible.value = true
  chunksDocName.value = row.name
  chunksLoading.value = true
  try {
    const data = await listChunks(row.id)
    chunks.value = (data ?? []).map((c) => ({ ...c, collapsed: true }))
  } catch {
    chunks.value = []
  } finally {
    chunksLoading.value = false
  }
}

// ============ 删除 ============
async function onDelete(row: DocumentInfo) {
  await confirmDelete(
    {
      message: `确认删除文档「${row.name}」？关联的切片也将一并删除。`,
      successMessage: '文档已删除',
    },
    () => deleteDocument(row.id),
  )
  tableRef.value?.refresh()
}
</script>

<style scoped>
.doc-name {
  font-weight: var(--weight-medium);
}

/* 状态标签颜色 */
:deep(.status-tag.is-pending) {
  --el-tag-bg-color: var(--bg-muted);
  --el-tag-border-color: var(--border-base);
  --el-tag-text-color: var(--text-secondary);
}
:deep(.status-tag.is-processing) {
  --el-tag-bg-color: var(--color-info-soft);
  --el-tag-border-color: var(--color-info);
  --el-tag-text-color: var(--color-info);
}
:deep(.status-tag.is-done) {
  --el-tag-bg-color: var(--mint-50);
  --el-tag-border-color: var(--mint-100);
  --el-tag-text-color: var(--mint-700);
}
:deep(.status-tag.is-failed) {
  --el-tag-bg-color: var(--color-danger-soft);
  --el-tag-border-color: var(--color-danger);
  --el-tag-text-color: var(--color-danger);
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}
.status-icon {
  margin-right: 2px;
}
.error-hint {
  font-size: 14px;
  color: var(--color-danger);
  cursor: help;
}

/* 上传区域 */
.upload-area :deep(.el-upload-dragger) {
  padding: var(--space-8) var(--space-4);
}
.upload-icon {
  font-size: 48px;
  color: var(--text-tertiary);
  margin-bottom: var(--space-3);
}
.upload-text p {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text-primary);
}
.upload-text em {
  font-style: normal;
  color: var(--brand-500);
}
.upload-hint {
  margin-top: var(--space-2) !important;
  font-size: var(--text-xs) !important;
  color: var(--text-tertiary) !important;
}

/* 分块列表 */
.chunks-list {
  max-height: 480px;
  overflow-y: auto;
}
.chunk-item {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  margin-bottom: var(--space-3);
}
.chunk-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}
.chunk-tokens {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}
.chunk-body {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--text-regular);
  white-space: pre-wrap;
  word-break: break-word;
}

/* loading 动画 */
.is-loading {
  animation: rotating 1s linear infinite;
}
@keyframes rotating {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
</style>
