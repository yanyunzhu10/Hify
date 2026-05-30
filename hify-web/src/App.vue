<template>
  <div class="layout">
    <aside class="sidebar" :class="{ 'is-collapsed': collapsed }">
      <!-- Logo 区 -->
      <div class="brand">
        <div class="brand-mark">H</div>
        <div class="brand-text" v-show="!collapsed">
          <div class="brand-name">Hify</div>
          <div class="brand-tag">AI Agent Platform</div>
        </div>
      </div>

      <!-- 菜单 -->
      <nav class="nav">
        <RouterLink
          v-for="item in menu"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ 'is-active': isActive(item.path) }"
        >
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <span class="nav-label" v-show="!collapsed">{{ item.label }}</span>
          <span v-if="collapsed" class="nav-tooltip">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <!-- 底部 -->
      <div class="sidebar-footer">
        <button class="collapse-btn" @click="toggle" :title="collapsed ? '展开' : '折叠'">
          <el-icon>
            <component :is="collapsed ? 'Expand' : 'Fold'" />
          </el-icon>
          <span class="collapse-label" v-show="!collapsed">折叠侧边栏</span>
        </button>
        <div class="version" v-show="!collapsed">v{{ version }}</div>
      </div>
    </aside>

    <div class="content">
      <AppHeader />
      <main class="main">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/layout/AppHeader.vue'

const route = useRoute()

const menu = [
  { path: '/provider', label: '模型管理', icon: 'Setting' },
  { path: '/agent', label: 'Agent 管理', icon: 'User' },
  { path: '/chat', label: '对话', icon: 'ChatDotRound' },
]

const COLLAPSE_KEY = 'hify_sidebar_collapsed'
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')

const version = '0.1.0'

const toggle = () => (collapsed.value = !collapsed.value)
watch(collapsed, (v) => localStorage.setItem(COLLAPSE_KEY, v ? '1' : '0'))

const currentTop = computed(() => '/' + route.path.split('/')[1])
const isActive = (path: string) => currentTop.value === path
</script>

<style scoped>
.layout {
  height: 100vh;
  display: flex;
  overflow: hidden;
}

/* ========== 内容主区（顶栏 + 滚动区） ========== */
.content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ========== Sidebar 容器 ========== */
.sidebar {
  width: var(--layout-sidebar-w);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-sidebar);
  color: var(--text-on-dark);
  border-right: 1px solid var(--bg-sidebar-border);
  transition: width var(--duration-base) var(--ease-emphasized);
  position: relative;
  overflow: hidden;
}
.sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 120px;
  background: radial-gradient(
    120% 100% at 0% 0%,
    rgba(99, 91, 255, 0.18) 0%,
    rgba(99, 91, 255, 0) 60%
  );
  pointer-events: none;
}
.sidebar.is-collapsed {
  width: var(--layout-sidebar-w-fold);
}

/* ========== Brand ========== */
.brand {
  height: var(--layout-header-h);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--bg-sidebar-border);
  position: relative;
  z-index: 1;
}
.brand-mark {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--brand-400), var(--brand-600));
  color: #fff;
  font-weight: var(--weight-bold);
  font-size: 14px;
  letter-spacing: -0.5px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.06),
              0 4px 12px rgba(99, 91, 255, 0.4);
}
.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  overflow: hidden;
}
.brand-name {
  font-size: 17px;
  font-weight: var(--weight-bold);
  letter-spacing: 0.5px;
  background: linear-gradient(120deg, #FFFFFF 10%, var(--brand-300) 60%, var(--cyan-300) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.brand-tag {
  margin-top: 2px;
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-on-dark-tertiary);
  white-space: nowrap;
}

/* ========== Nav ========== */
.nav {
  flex: 1;
  padding: var(--space-3) var(--space-2);
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
  position: relative;
  z-index: 1;
}
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  height: 38px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-md);
  color: var(--text-on-dark-secondary);
  text-decoration: none;
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  transition: var(--transition-color);
  cursor: pointer;
  white-space: nowrap;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-on-dark);
}
.nav-item.is-active {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-on-dark);
}
.nav-item.is-active::before {
  content: '';
  position: absolute;
  left: -2px;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: var(--radius-full);
  background: linear-gradient(180deg, var(--brand-400), var(--brand-600));
  box-shadow: 0 0 8px rgba(99, 91, 255, 0.6);
}
.nav-icon {
  font-size: 16px;
  flex-shrink: 0;
}
.nav-item.is-active .nav-icon {
  color: var(--brand-300);
}
.nav-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 折叠态：图标居中 + tooltip */
.sidebar.is-collapsed .nav-item {
  justify-content: center;
  padding: 0;
}
.nav-tooltip {
  position: absolute;
  left: calc(100% + 8px);
  top: 50%;
  transform: translateY(-50%);
  padding: 4px 10px;
  background: var(--bg-sidebar-active);
  color: var(--text-on-dark);
  font-size: var(--text-xs);
  border-radius: var(--radius-sm);
  border: 1px solid var(--bg-sidebar-border);
  box-shadow: var(--shadow-md);
  opacity: 0;
  pointer-events: none;
  white-space: nowrap;
  transition: opacity var(--duration-fast) var(--ease-standard);
  z-index: var(--z-tooltip);
}
.sidebar.is-collapsed .nav-item:hover .nav-tooltip {
  opacity: 1;
}

/* ========== Footer ========== */
.sidebar-footer {
  flex-shrink: 0;
  padding: var(--space-2);
  border-top: 1px solid var(--bg-sidebar-border);
  position: relative;
  z-index: 1;
}
.collapse-btn {
  width: 100%;
  height: 36px;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-3);
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  color: var(--text-on-dark-secondary);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: var(--transition-color);
}
.sidebar.is-collapsed .collapse-btn {
  justify-content: center;
  padding: 0;
}
.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-on-dark);
}
.collapse-label {
  font-weight: var(--weight-medium);
}
.version {
  margin-top: var(--space-2);
  padding: 0 var(--space-3);
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--text-on-dark-tertiary);
  letter-spacing: 0.04em;
}

/* ========== Main ========== */
.main {
  flex: 1;
  background: var(--color-bg-secondary);
  overflow: auto;
}
</style>
