<template>
  <div>
    <div class="page-title">
      <div>
        <h1>运维中心</h1>
        <p>查看健康检查、审计日志和全局配置，不展示明文敏感值。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
        <el-button :icon="Plus" type="primary" @click="openConfig">配置</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="tabChanged">
      <el-tab-pane label="健康检查" name="health" />
      <el-tab-pane label="审计日志" name="logs" />
      <el-tab-pane label="全局配置" name="configs" />
    </el-tabs>

    <section v-if="tab === 'health'" class="content-panel">
      <div class="metric-grid">
        <div v-for="item in healthItems" :key="item.label" class="metric">
          <span>{{ item.label }}</span>
          <strong :class="isOk(item.value) ? 'status-ok' : 'status-bad'">{{ item.value || '-' }}</strong>
        </div>
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item v-for="(value, key) in health" :key="String(key)" :label="String(key)">
          {{ value ?? '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </section>

    <section v-else-if="tab === 'logs'" class="content-panel">
      <div class="toolbar">
        <el-input v-model="logQuery.account" clearable placeholder="账号" style="width: 180px" />
        <el-input v-model="logQuery.action" clearable placeholder="操作类型" style="width: 180px" />
        <el-button :icon="Search" @click="loadLogs">查询</el-button>
        <el-button :icon="Refresh" @click="loadLogs" />
      </div>
      <el-table :data="logs.records" border height="560" v-loading="loading.logs">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="account" label="账号" width="140" />
        <el-table-column prop="action" label="操作" min-width="150" />
        <el-table-column prop="result" label="结果" width="110">
          <template #default="{ row }"><status-tag :value="row.result" /></template>
        </el-table-column>
        <el-table-column prop="traceId" label="traceId" min-width="180" show-overflow-tooltip />
        <el-table-column prop="errorMessage" label="错误摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" min-width="160" />
      </el-table>
    </section>

    <section v-else class="content-panel">
      <div class="toolbar">
        <el-input v-model="configQuery.configKey" clearable placeholder="配置键" style="width: 220px" />
        <el-button :icon="Search" @click="loadConfigs">查询</el-button>
        <el-button :icon="Refresh" @click="loadConfigs" />
      </div>
      <el-table :data="configs.records" border height="560" v-loading="loading.configs">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="configKey" label="配置键" min-width="180" />
        <el-table-column prop="configValue" label="配置值" min-width="220">
          <template #default="{ row }">{{ maskValue(row.configKey, row.configValue) }}</template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }"><status-tag :value="row.status" /></template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="configDialog" title="新建全局配置" width="620px">
      <el-form :model="configForm" label-width="108px">
        <el-form-item label="配置键" required><el-input v-model="configForm.configKey" /></el-form-item>
        <el-form-item label="配置值" required><el-input v-model="configForm.configValue" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="configForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="configForm.status"><el-option label="启用" value="ENABLED" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { opsApi } from '../api/modules';
import type { AnyRecord, HealthVO, PageResult } from '../api/types';
import StatusTag from '../components/StatusTag.vue';

const tab = ref('health');
const configDialog = ref(false);
const health = ref<HealthVO>({});
const loading = reactive({ logs: false, configs: false });
const logQuery = reactive({ account: '', action: '' });
const configQuery = reactive({ configKey: '' });
const logs = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const configs = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const configForm = reactive({ configKey: '', configValue: '', description: '', status: 'ENABLED' });

const healthItems = computed(() => [
  { label: '服务', value: String(health.value.serviceStatus || '') },
  { label: '数据库', value: String(health.value.databaseStatus || '') },
  { label: 'Redis', value: String(health.value.redisStatus || '') },
  { label: '文件存储', value: String(health.value.storageStatus || '') },
  { label: '脚本版本', value: String(health.value.scriptVersionStatus || '') }
]);

function isOk(value: string) {
  return ['UP', 'OK', 'NORMAL', 'SUCCESS'].includes(value.toUpperCase());
}

function maskValue(key: unknown, value: unknown) {
  const name = String(key || '').toLowerCase();
  if (/(password|secret|token|key|credential|sk)/.test(name)) return '******';
  return value ?? '-';
}

function openConfig() {
  Object.assign(configForm, { configKey: '', configValue: '', description: '', status: 'ENABLED' });
  configDialog.value = true;
}

async function loadHealth() {
  health.value = await opsApi.health();
}

async function loadLogs() {
  loading.logs = true;
  try {
    Object.assign(logs, await opsApi.auditLogs(logQuery));
  } finally {
    loading.logs = false;
  }
}

async function loadConfigs() {
  loading.configs = true;
  try {
    Object.assign(configs, await opsApi.configs(configQuery));
  } finally {
    loading.configs = false;
  }
}

async function saveConfig() {
  await opsApi.createConfig({ ...configForm });
  ElMessage.success('配置已保存');
  configDialog.value = false;
  loadConfigs();
}

function tabChanged() {
  if (tab.value === 'health') loadHealth();
  if (tab.value === 'logs') loadLogs();
  if (tab.value === 'configs') loadConfigs();
}

function refreshAll() {
  tabChanged();
}

onMounted(() => {
  loadHealth();
  loadLogs();
  loadConfigs();
});
</script>
