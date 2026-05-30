/**
 * 删除/危险操作确认 composable
 * 一行代码完成 "确认弹窗 → 调接口 → 成功提示" 全流程
 *
 * 用法：
 *   await confirmDelete({
 *     message: `确认删除 Agent "${row.name}"？此操作不可恢复`,
 *     successMessage: '删除成功',
 *   }, () => deleteAgent(row.id))
 *
 *   // 返回值：成功 → API 返回值；取消 → undefined
 */
import { ElMessageBox } from 'element-plus'
import type { ElMessageBoxOptions } from 'element-plus'
import { notifySuccess } from '@/utils/notify'

export interface ConfirmOptions {
  /** 弹窗主体文案 */
  message: string
  /** 弹窗标题，默认 "确认操作" */
  title?: string
  /** 类型，默认 warning */
  type?: 'warning' | 'error' | 'info' | 'success'
  /** 确认按钮文案，默认 "确定" */
  confirmButtonText?: string
  /** 取消按钮文案，默认 "取消" */
  cancelButtonText?: string
  /** 确认按钮使用危险样式（红色） */
  danger?: boolean
  /** 成功后的 toast 文案，默认 "操作成功" */
  successMessage?: string
}

/**
 * 通用确认。用户取消返回 undefined，确认成功返回 API 结果。
 * API 抛错由全局 request 拦截器 toast，本方法不再吞错也不二次 toast。
 */
export async function useConfirm<T>(
  options: ConfirmOptions,
  apiCall: () => Promise<T>,
): Promise<T | undefined> {
  const boxOptions: ElMessageBoxOptions = {
    type: options.type ?? 'warning',
    confirmButtonText: options.confirmButtonText ?? '确定',
    cancelButtonText: options.cancelButtonText ?? '取消',
    confirmButtonClass: options.danger ? 'el-button--danger' : undefined,
    draggable: true,
  }

  try {
    await ElMessageBox.confirm(options.message, options.title ?? '确认操作', boxOptions)
  } catch {
    return undefined
  }

  const result = await apiCall()
  notifySuccess(options.successMessage ?? '操作成功')
  return result
}

/**
 * useConfirm 的删除专用语法糖：默认红色确认按钮 + "删除成功" 提示
 */
export function confirmDelete<T>(
  options: Omit<ConfirmOptions, 'danger' | 'type'> & { type?: ConfirmOptions['type'] },
  apiCall: () => Promise<T>,
): Promise<T | undefined> {
  return useConfirm(
    {
      title: '删除确认',
      successMessage: '删除成功',
      ...options,
      danger: true,
      type: options.type ?? 'warning',
    },
    apiCall,
  )
}
