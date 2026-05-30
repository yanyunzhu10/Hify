/**
 * 统一通知封装
 * 底层调 Element Plus 的 ElMessage，固定 duration / grouping / 关闭按钮
 * 业务层只调 notifySuccess / notifyError / notifyWarning / notifyInfo
 */
import { ElMessage } from 'element-plus'
import type { MessageOptions, MessageHandler } from 'element-plus'

const DEFAULT_DURATION = 2500
const ERROR_DURATION = 4000

type NotifyInput = string | (Omit<MessageOptions, 'type'> & { message: string })

function build(
  type: 'success' | 'error' | 'warning' | 'info',
  input: NotifyInput,
  fallbackDuration: number,
): MessageHandler {
  const base: MessageOptions =
    typeof input === 'string'
      ? { message: input }
      : { ...input }

  return ElMessage({
    type,
    duration: base.duration ?? fallbackDuration,
    grouping: base.grouping ?? true,
    showClose: base.showClose ?? type === 'error',
    ...base,
  })
}

export const notifySuccess = (input: NotifyInput): MessageHandler =>
  build('success', input, DEFAULT_DURATION)

export const notifyError = (input: NotifyInput): MessageHandler =>
  build('error', input, ERROR_DURATION)

export const notifyWarning = (input: NotifyInput): MessageHandler =>
  build('warning', input, DEFAULT_DURATION)

export const notifyInfo = (input: NotifyInput): MessageHandler =>
  build('info', input, DEFAULT_DURATION)