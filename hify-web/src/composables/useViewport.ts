/**
 * 视口尺寸响应式 composable
 * - width：当前 window.innerWidth（只读，resize 时自动更新）
 * - isNarrow：< breakpoint 时为 true，用于驱动侧边栏自动折叠、表格列裁剪等
 *
 * 用法：
 *   const { isNarrow, width } = useViewport()
 *   const { isNarrow: isMobile } = useViewport(768)
 *
 * 注意：同一组件多次调用会各自挂载 listener，保持简单不做共享单例。
 */
import { ref, computed, readonly, onMounted, onBeforeUnmount, type Ref, type ComputedRef } from 'vue'

const DEFAULT_BREAKPOINT = 1200

export interface UseViewportReturn {
  width: Readonly<Ref<number>>
  isNarrow: ComputedRef<boolean>
}

export function useViewport(breakpoint: number = DEFAULT_BREAKPOINT): UseViewportReturn {
  const initial = typeof window !== 'undefined' ? window.innerWidth : 1920
  const width = ref(initial)

  const update = () => {
    width.value = window.innerWidth
  }

  onMounted(() => {
    update()
    window.addEventListener('resize', update, { passive: true })
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', update)
  })

  return {
    width: readonly(width),
    isNarrow: computed(() => width.value < breakpoint),
  }
}
