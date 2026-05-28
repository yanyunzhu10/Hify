<template>
  <header class="app-header">
    <!-- 面包屑 -->
    <nav class="breadcrumb">
      <RouterLink to="/" class="crumb crumb-home">
        <el-icon><HomeFilled /></el-icon>
      </RouterLink>
      <template v-for="(item, idx) in trail" :key="`${item.path}-${idx}`">
        <span class="crumb-sep">/</span>
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

    <!-- 用户区 -->
    <div class="user-area">
      <button class="header-icon-btn" title="通知">
        <el-icon><Bell /></el-icon>
      </button>
      <div class="user">
        <div class="avatar">U</div>
        <div class="user-meta">
          <div class="user-name">User</div>
          <div class="user-role">Developer</div>
        </div>
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
  background: var(--bg-canvas);
  border-bottom: 1px solid var(--border-light);
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
}

/* ===== 面包屑 ===== */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
}
.crumb {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  text-decoration: none;
  transition: var(--transition-color);
  white-space: nowrap;
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
  color: var(--text-tertiary);
  font-size: var(--text-xs);
  user-select: none;
}

/* ===== 用户区 ===== */
.user-area {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.header-icon-btn {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition-color);
  position: relative;
}
.header-icon-btn:hover {
  background: var(--bg-subtle);
  color: var(--text-primary);
}
.user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 4px 8px 4px 4px;
  border-radius: var(--radius-md);
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
  background: linear-gradient(135deg, var(--brand-400), var(--cyan-500));
  color: #fff;
  font-size: 12px;
  font-weight: var(--weight-bold);
}
.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}
.user-name {
  font-size: var(--text-sm);
  font-weight: var(--weight-semi);
  color: var(--text-primary);
}
.user-role {
  font-size: 11px;
  color: var(--text-tertiary);
}
</style>
