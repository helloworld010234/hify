<template>
  <el-container class="layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <span class="logo-icon">◈</span>
        <span class="logo-text">Hify</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
        background-color="transparent"
        text-color="var(--color-text-sidebar)"
        active-text-color="var(--color-text-sidebar-active)"
      >
        <el-menu-item index="/providers">
          <el-icon><Setting /></el-icon>
          <span>模型管理</span>
        </el-menu-item>
        <el-menu-item index="/agents">
          <el-icon><User /></el-icon>
          <span>Agent 管理</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatLineRound /></el-icon>
          <span>对话</span>
        </el-menu-item>
        <el-menu-item index="/mcp-servers">
          <el-icon><Tools /></el-icon>
          <span>MCP Server</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Setting, User, ChatLineRound, Tools } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.layout {
  height: 100vh;
  background: var(--color-bg-base);
}

.sidebar {
  background: var(--color-bg-sidebar);
  border-right: 1px solid var(--color-bg-sidebar-border);
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 var(--space-5);
  gap: var(--space-3);
  border-bottom: 1px solid var(--color-bg-sidebar-border);
}

.logo-icon {
  font-size: var(--text-xl);
  color: var(--color-primary-400);
  text-shadow: var(--shadow-glow-primary);
}

.logo-text {
  font-size: var(--text-xl);
  font-weight: var(--font-semibold);
  color: var(--color-text-inverse);
  letter-spacing: 0.5px;
}

.sidebar-menu {
  border-right: none;
  margin-top: var(--space-2);
}

.sidebar-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  margin: 0 var(--space-3);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: var(--color-bg-sidebar-hover) !important;
  color: var(--color-text-sidebar-hover) !important;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--color-bg-sidebar-active) !important;
  color: var(--color-text-sidebar-active) !important;
  font-weight: var(--font-medium);
}

.sidebar-menu :deep(.el-icon) {
  color: inherit;
}

.main {
  background: var(--color-bg-base);
  padding: var(--space-6);
  overflow-y: auto;
}
</style>
