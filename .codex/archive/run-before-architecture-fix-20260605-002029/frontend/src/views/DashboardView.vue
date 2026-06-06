<template>
  <div>
    <div class="page-title">
      <div>
        <h1>工作台</h1>
        <p>按当前登录态查看平台关键入口，健康检查为匿名接口，其余卡片使用 Bearer Token 调用。</p>
      </div>
      <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
    </div>

    <div class="metric-grid">
      <div class="metric">
        <span>租户</span>
        <strong>{{ metrics.tenants }}</strong>
      </div>
      <div class="metric">
        <span>系统</span>
        <strong>{{ metrics.systems }}</strong>
      </div>
      <div class="metric">
        <span>运行记录</span>
        <strong>{{ metrics.records }}</strong>
      </div>
      <div class="metric">
        <span>待办任务</span>
        <strong>{{ metrics.tasks }}</strong>
      </div>
    </div>

    <div class="split-grid">
      <section class="content-panel">
        <div class="page-title">
          <div>
            <h1>运维健康</h1>
            <p>服务、数据库、Redis、文件存储、脚本版本状态。</p>
          </div>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item v-for="item in healthItems" :key="item.label" :label="item.label">
            <el-tag :type="isOk(item.value) ? 'success' : 'danger'" effect="plain">{{ item.value || '-' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="content-panel">
        <div class="page-title">
          <div>
            <h1>当前身份</h1>
            <p>用户与系统上下文。</p>
          </div>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="账号">{{ auth.user?.account }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ auth.user?.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="systemId">{{ context.current.systemId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="tenantId">{{ context.current.tenantId || '-' }}</el-descriptions-item>
        </el-descriptions>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import { opsApi, platformApi, workflowApi } from '../api/modules';
import type { HealthVO } from '../api/types';
import { useAuthStore } from '../stores/auth';
import { useContextStore } from '../stores/context';

const auth = useAuthStore();
const context = useContextStore();
const health = ref<HealthVO>({});
const metrics = reactive({
  tenants: 0,
  systems: 0,
  records: 0,
  tasks: 0
});

const healthItems = computed(() => [
  { label: '服务', value: String(health.value.serviceStatus || '') },
  { label: '数据库', value: String(health.value.databaseStatus || '') },
  { label: 'Redis', value: String(health.value.redisStatus || '') },
  { label: '文件存储', value: String(health.value.storageStatus || '') },
  { label: '脚本版本', value: String(health.value.scriptVersionStatus || '') }
]);

function isOk(value: string) {
  return ['UP', 'OK', 'ENABLED', 'SUCCESS', 'NORMAL'].includes(value.toUpperCase());
}

async function loadAll() {
  const [healthData, tenants, systems, tasks] = await Promise.allSettled([
    opsApi.health(),
    platformApi.tenants({ pageSize: 1 }),
    platformApi.systems({ pageSize: 1 }),
    context.hasSystemContext ? workflowApi.tasks({ pageSize: 1 }) : Promise.resolve({ total: 0 })
  ]);
  if (healthData.status === 'fulfilled') health.value = healthData.value;
  if (tenants.status === 'fulfilled') metrics.tenants = tenants.value.total || 0;
  if (systems.status === 'fulfilled') metrics.systems = systems.value.total || 0;
  metrics.records = 0;
  if (tasks.status === 'fulfilled') metrics.tasks = tasks.value.total || 0;
}

onMounted(loadAll);
</script>
