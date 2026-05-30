<template>
  <header class="app-header">
    <!-- 面包屑：显示当前页面路径 -->
    <nav class="breadcrumb" aria-label="breadcrumb">
      <RouterLink to="/" class="crumb crumb-home" title="工作台">
        <el-icon><HomeFilled /></el-icon>
      </RouterLink>
      <template v-for="(item, idx) in trail" :key="`${item.path}-${idx}`">
        <span class="crumb-sep">
          <el-icon><ArrowRight /></el-icon>
        </span>
        <RouterLink
          v-if="idx < trail.length - 1 && item.path && item.path !== '/'"
          :to="item.path"
          class="crumb"
        >{{ item.title }}</RouterLink>
        <span
          v-else
          class="crumb"
          :class="{ 'is-current': idx === trail.length - 1 }"
        >{{ item.title }}</span>
      </template>
    </nav>

    <!-- 用户区：头像 + 用户名 placeholder -->
    <div class="user-area">
      <div class="user">
        <div class="avatar">U</div>
        <span class="user-name">用户名</span>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const trail = computed(() => {
  return route.matched
    .filter((m) => m.meta?.title)
    .map((m) => ({
      path: m.path,
      title: m.meta.title as string,
    }))
})
</script>

<style scoped>
.app-header {
  height: var(--layout-header-h);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-6);
  background: var(--color-bg-primary);
  border-bottom: 1px solid var(--border-light);
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
}

/* ===== 面包屑 ===== */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm);
  min-width: 0;
}
.crumb {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  text-decoration: none;
  transition: var(--transition-color);
  white-space: nowrap;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.crumb:hover {
  color: var(--text-primary);
  background: var(--bg-subtle);
}
.crumb.is-current {
  color: var(--text-primary);
  font-weight: var(--weight-semi);
  cursor: default;
}
.crumb.is-current:hover {
  background: transparent;
}
.crumb-home .el-icon {
  font-size: 14px;
}
.crumb-sep {
  display: inline-flex;
  align-items: center;
  color: var(--text-tertiary);
  font-size: 10px;
  user-select: none;
}

/* ===== 用户区 ===== */
.user-area {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 4px 10px 4px 4px;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: var(--transition-color);
}
.user:hover {
  background: var(--bg-subtle);
}
.avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--radius-full);
  background: var(--gradient-brand);
  color: #fff;
  font-size: 12px;
  font-weight: var(--weight-bold);
  box-shadow: 0 2px 6px rgba(99, 91, 255, 0.28);
}
.user-name {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
}
</style>