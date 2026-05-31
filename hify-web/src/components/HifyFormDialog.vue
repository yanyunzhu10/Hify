<!--
  HifyFormDialog — 通用表单弹窗
  - v-model 控制显隐；也可通过 ref 调 open(data?) 主动打开
  - open() / open(undefined) = 新增模式；open({ id, ... }) = 编辑模式
  - 内部管理 formData / submitLoading；关闭时自动重置 + clearValidate
  - submit 事件交给父组件处理 API 调用：(payload, mode) => Promise<void>
  - 父组件 await emit('submit',...) 完成后弹窗自动关闭

  用法：
    <HifyFormDialog
      ref="dialogRef"
      v-model="visible"
      title="Agent"
      :rules="rules"
      @submit="handleSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
      </template>
    </HifyFormDialog>

    const dialogRef = ref<InstanceType<typeof HifyFormDialog<AgentForm>>>()
    dialogRef.value?.open()                  // 新增
    dialogRef.value?.open({ id: 1, name: '...' }) // 编辑

    async function handleSubmit(payload: AgentForm, mode: 'create' | 'edit') {
      if (mode === 'create') await createAgent(payload)
      else await updateAgent(payload.id!, payload)
      notifySuccess(mode === 'create' ? '创建成功' : '保存成功')
      tableRef.value?.refresh()
    }
-->
<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    :width="width"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    align-center
    destroy-on-close
    @closed="handleClosed"
  >
    <el-form
      v-if="formData"
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-width="labelWidth"
      :label-position="labelPosition"
      :disabled="submitting"
      @submit.prevent="handleSubmit"
    >
      <slot :form="formData" :mode="mode" />
    </el-form>

    <template #footer>
      <slot name="footer" :submit="handleSubmit" :cancel="handleCancel" :loading="submitting">
        <div class="hify-dialog-footer">
          <el-button :disabled="submitting" @click="handleCancel">
            {{ cancelText }}
          </el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ submitText }}
          </el-button>
        </div>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" generic="T extends object">
import { ref, computed, nextTick } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

interface Props<R> {
  /** 弹窗标题前缀，最终显示 "新建{title}" / "编辑{title}"；也可整体由 titleFormatter 覆盖 */
  title?: string
  /** 自定义标题函数（拿到模式自行渲染） */
  titleFormatter?: (mode: 'create' | 'edit') => string
  width?: string | number
  rules?: FormRules
  labelWidth?: string | number
  labelPosition?: 'left' | 'right' | 'top'
  /** 表单初始值（新增模式 reset 时回到这里） */
  defaultForm?: Partial<R>
  /** 自定义按钮文案 */
  submitText?: string
  cancelText?: string
  /** 判定编辑模式的字段，默认 'id'；该字段有值即编辑 */
  editKey?: string
}

const props = withDefaults(defineProps<Props<T>>(), {
  title: '',
  titleFormatter: undefined,
  width: '520px',
  rules: () => ({}),
  labelWidth: '90px',
  labelPosition: 'right',
  defaultForm: () => ({}),
  submitText: '保存',
  cancelText: '取消',
  editKey: 'id',
})

const visible = defineModel<boolean>({ default: false })

const emit = defineEmits<{
  /** 提交：父组件可 return Promise；reject/throw 则弹窗保持打开 */
  (e: 'submit', payload: T, mode: 'create' | 'edit'): void | Promise<void>
  (e: 'opened', mode: 'create' | 'edit', payload: T): void
  (e: 'closed'): void
}>()

const formRef = ref<FormInstance>()
const formData = ref<any>({})
const submitting = ref(false)

const mode = computed<'create' | 'edit'>(() => {
  const value = (formData.value as Record<string, unknown>)[props.editKey]
  return value != null && value !== '' ? 'edit' : 'create'
})

const dialogTitle = computed(() => {
  if (props.titleFormatter) return props.titleFormatter(mode.value)
  const action = mode.value === 'edit' ? '编辑' : '新建'
  return props.title ? `${action}${props.title}` : action
})

/** 打开弹窗。传 data 进入编辑模式，不传则新增模式 */
function open(data?: Partial<T>) {
  formData.value = {
    ...(props.defaultForm as T),
    ...(data ?? {}),
  } as T
  nextTick(() => {
    visible.value = true
    nextTick(() => {
      formRef.value?.clearValidate()
      emit('opened', mode.value, formData.value)
    })
  })
}

function close() {
  visible.value = false
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 父组件可同步或异步处理；await 任意值
    await Promise.resolve(emit('submit', formData.value, mode.value) as unknown)
    close()
  } catch {
    // 父组件抛错（API 失败）时保持弹窗打开，错误已由全局拦截器 toast
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  if (submitting.value) return
  close()
}

/** Dialog 关闭动画结束后才重置表单，避免淡出时跳变 */
function handleClosed() {
  formData.value = { ...(props.defaultForm as T) } as T
  formRef.value?.resetFields()
  formRef.value?.clearValidate()
  submitting.value = false
  emit('closed')
}

defineExpose({ open, close })
</script>

<style scoped>
.hify-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
