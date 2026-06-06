<template>
  <el-container class="app-shell">
    <el-aside width="244px" class="shell-aside">
      <div class="brand">
        <div class="brand-mark">U</div>
        <div>
          <strong>业务平台</strong>
          <span>Config Runtime</span>
        </div>
      </div>
      <el-menu :default-active="route.path" router class="shell-menu">
        <el-menu-item index="/">
          <el-icon><DataBoard /></el-icon><span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/platform">
          <el-icon><OfficeBuilding /></el-icon><span>平台租户/系统</span>
        </el-menu-item>
        <el-menu-item index="/config">
          <el-icon><Operation /></el-icon><span>应用配置</span>
        </el-menu-item>
        <el-menu-item index="/records">
          <el-icon><Tickets /></el-icon><span>运行记录</span>
        </el-menu-item>
        <el-menu-item index="/workflow">
          <el-icon><Connection /></el-icon><span>流程工作台</span>
        </el-menu-item>
        <el-menu-item index="/files">
          <el-icon><FolderOpened /></el-icon><span>文件与导入导出</span>
        </el-menu-item>
        <el-menu-item index="/openapi">
          <el-icon><Key /></el-icon><span>OpenAPI 管理</span>
        </el-menu-item>
        <el-menu-item index="/ops">
          <el-icon><Monitor /></el-icon><span>运维中心</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="shell-header">
        <div class="header-context">
          <el-tag effect="plain" type="info">系统 {{ context.current.systemId || '未进入' }}</el-tag>
          <el-tag effect="plain" type="info">租户 {{ context.current.tenantId || '未选择' }}</el-tag>
          <span class="context-name">{{ context.current.systemName || '平台态' }}</span>
        </div>
        <div class="header-actions">
          <el-button :icon="Refresh" circle title="刷新用户" @click="refreshMe" />
          <el-dropdown>
            <el-button text>
              <el-icon><User /></el-icon>
              <span>{{ auth.user?.realName || auth.user?.account || '用户' }}</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="shell-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import {
  Connection,
  DataBoard,
  FolderOpened,
  Key,
  Monitor,
  OfficeBuilding,
  Operation,
  Refresh,
  Tickets,
  User
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../stores/auth';
import { useContextStore } from '../stores/context';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const context = useContextStore();

async function refreshMe() {
  await auth.loadMe();
  ElMessage.success('用户状态已刷新');
}

async function logout() {
  await auth.logout();
  context.clear();
  router.push('/login');
}
</script>
