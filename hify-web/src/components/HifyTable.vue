<!--
  HifyTable — 通用列表页表格
  - columns 配置驱动；prop 渲染默认值，slot 走具名插槽（slot 名 = col-{slot}）
  - 内部自动管理 loading / page / size / total
  - api 返回 PageResult<T>；非分页模式时也接受 { records, total } 形态
  - 暴露 refresh() / setQuery(extra) 供父组件调用

  用法：
    <HifyTable
      ref="tableRef"
      :columns="columns"
      :api="getAgentPage"
      row-key="id"
    >
      <template #col-status="{ row }">
        <span class="tag-soft" :class="row.enabled ? 'is-success' : 'is-danger'">
          {{ row.enabled ? '启用' : '停用' }}
        </span>
      </template>
      <template #col-actions="{ row }">
        <el-button text @click="onEdit(row)">编辑</el-button>
        <el-button text type="danger" @click="onDelete(row)">删除</el-button>
      </template>
    </HifyTable>
-->
<template>
  <div class="hify-table">
    <el-table
      v-loading="loading"
      :data="records"
      :row-key="rowKey"
      :stripe="stripe"
      :border="border"
      :height="height"
      :max-height="maxHeight"
      :row-style="rowStyle"
      :cell-style="cellStyle"
      style="width: 100%"
      @row-click="(row: T) => emit('row-click', row)"
    >
      <el-table-column
        v-for="col in columns"
        :key="col.prop ?? col.slot ?? col.label"
        :label="col.label"
        :prop="col.prop"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align ?? 'left'"
        :fixed="col.fixed"
        :show-overflow-tooltip="col.showOverflowTooltip ?? !col.slot"
        :formatter="col.formatter as never"
      >
        <template v-if="col.slot" #default="scope">
          <slot
            :name="`col-${col.slot}`"
            :row="scope.row as T"
            :$index="scope.$index"
          />
        </template>
      </el-table-column>

      <template #empty>
        <slot name="empty">
          <el-empty :description="emptyText" :image-size="80" />
        </slot>
      </template>
    </el-table>

    <div v-if="pagination && total > 0" class="hify-table-pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="pageSizes"
        :layout="paginationLayout"
        background
        @current-change="fetchData"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts" generic="T extends object">
import { ref, shallowRef, onMounted, watch } from 'vue'
import type { PageResult, PageQuery } from '@/types'

export interface HifyTableColumn<R = object> {
  /** 列头文案 */
  label: string
  /** 字段 prop；用 slot 时可不传 */
  prop?: string
  /** 列宽，固定像素 */
  width?: number | string
  /** 最小列宽 */
  minWidth?: number | string
  /** 自定义渲染插槽名（实际插槽名 = `col-{slot}`） */
  slot?: string
  /** 对齐方式 */
  align?: 'left' | 'center' | 'right'
  /** 固定列 */
  fixed?: 'left' | 'right' | boolean
  /** 文本溢出 tooltip（默认无 slot 时开启） */
  showOverflowTooltip?: boolean
  /** 格式化函数 */
  formatter?: (row: R, prop: string, value: unknown, index: number) => string
}

/** API 既可返回标准 PageResult，也可返回只有 records 的非分页结构 */
export type TableApi<R> = (
  query: PageQuery & Record<string, unknown>,
) => Promise<PageResult<R> | { records: R[]; total?: number }>

interface Props<R> {
  columns: HifyTableColumn<R>[]
  api: TableApi<R>
  pagination?: boolean
  pageSize?: number
  pageSizes?: number[]
  rowKey?: string
  stripe?: boolean
  border?: boolean
  height?: number | string
  maxHeight?: number | string
  emptyText?: string
  /** 透传给 api 的固定附加查询参数 */
  extraQuery?: Record<string, unknown>
  /** 挂载时立即拉取 */
  immediate?: boolean
  /** 行样式（透传 el-table row-style），可传对象或函数 */
  rowStyle?: Record<string, string> | ((scope: { row: T; rowIndex: number }) => Record<string, string>)
  /** 单元格样式（透传 el-table cell-style） */
  cellStyle?: Record<string, string> | ((scope: { row: T; column: unknown; rowIndex: number; columnIndex: number }) => Record<string, string>)
}

const props = withDefaults(defineProps<Props<T>>(), {
  pagination: true,
  pageSize: 10,
  pageSizes: () => [10, 20, 50, 100],
  rowKey: 'id',
  stripe: false,
  border: false,
  height: undefined,
  maxHeight: undefined,
  emptyText: '暂无数据',
  extraQuery: () => ({}),
  immediate: true,
  rowStyle: undefined,
  cellStyle: undefined,
})

const emit = defineEmits<{
  (e: 'row-click', row: T): void
  (e: 'loaded', records: T[], total: number): void
}>()

const records = shallowRef<T[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(props.pageSize)

const paginationLayout = 'total, sizes, prev, pager, next, jumper'

async function fetchData() {
  loading.value = true
  try {
    const query: PageQuery & Record<string, unknown> = props.pagination
      ? { page: page.value, size: size.value, ...props.extraQuery }
      : { page: 1, size: 9999, ...props.extraQuery }

    const result = await props.api(query)
    records.value = result.records ?? []
    total.value = result.total ?? records.value.length
    emit('loaded', records.value, total.value)
  } catch {
    // 错误已被全局 request 拦截器 toast，这里只确保 loading 复位
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSizeChange(newSize: number) {
  size.value = newSize
  page.value = 1
  fetchData()
}

/** 外部触发刷新：保持当前分页 */
function refresh() {
  return fetchData()
}

/** 重置到第一页并拉取 */
function reload() {
  page.value = 1
  return fetchData()
}

defineExpose({ refresh, reload })

// extraQuery 变化时重置到首页
watch(
  () => props.extraQuery,
  () => {
    page.value = 1
    fetchData()
  },
  { deep: true },
)

onMounted(() => {
  if (props.immediate) fetchData()
})
</script>

<style scoped>
.hify-table {
  width: 100%;
}

/* 表头浅灰、悬停行微变色，全部通过 el-table 自带的 CSS 变量覆盖，
   不直接改组件选择器，便于父级再次微调 */
.hify-table :deep(.el-table) {
  --el-table-header-bg-color: var(--color-bg-secondary);
  --el-table-header-text-color: var(--text-regular);
  --el-table-row-hover-bg-color: var(--bg-subtle);
  --el-table-border-color: var(--border-light);
  --el-table-tr-bg-color: var(--color-bg-primary);
}
.hify-table :deep(.el-table th.el-table__cell) {
  font-weight: var(--weight-semi);
}

/* 表格底部：细分割线 + 右对齐分页器 */
.hify-table-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-light);
}
</style>
