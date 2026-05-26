<template>
  <el-container class="layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">Hify</div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#1e1e2e"
        text-color="#cdd6f4"
        active-text-color="#89b4fa"
      >
        <el-menu-item index="/chat">
          <el-icon><ChatLineRound /></el-icon>
          <span>对话</span>
        </el-menu-item>
        <el-menu-item index="/agent">
          <el-icon><Setting /></el-icon>
          <span>Agent 配置</span>
        </el-menu-item>
        <el-menu-item index="/provider">
          <el-icon><Monitor /></el-icon>
          <span>模型提供商</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-main class="main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => '/' + route.path.split('/')[1])
</script>

<style scoped>
.layout {
  height: 100vh;
}

.sidebar {
  background-color: #1e1e2e;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  color: #89b4fa;
  letter-spacing: 2px;
  border-bottom: 1px solid #313244;
  flex-shrink: 0;
}

.el-menu {
  border-right: none;
}

.main {
  background-color: #f5f7fa;
  padding: 24px;
}
</style>
