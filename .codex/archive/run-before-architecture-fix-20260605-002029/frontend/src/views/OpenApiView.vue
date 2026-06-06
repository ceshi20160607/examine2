<template>
  <div>
    <div class="page-title">
      <div>
        <h1>OpenAPI 管理</h1>
        <p>管理外部应用、凭证、授权范围和 IP 白名单；调用台按 HMAC 规则访问开放接口。</p>
      </div>
      <el-button :icon="Plus" type="primary" @click="openClient()">外部应用</el-button>
    </div>

    <el-tabs v-model="tab" @tab-change="tab === 'clients' && loadClients()">
      <el-tab-pane label="外部应用" name="clients" />
      <el-tab-pane label="凭证/授权" name="security" />
      <el-tab-pane label="HMAC 调用台" name="console" />
    </el-tabs>

    <section v-if="tab === 'clients'" class="content-panel">
      <div class="toolbar">
        <el-input v-model="clientQuery.keyword" clearable placeholder="应用名称/clientId" style="width: 240px" />
        <el-button :icon="Search" @click="loadClients">查询</el-button>
        <el-button :icon="Refresh" @click="loadClients" />
      </div>
      <el-table :data="clients.records" border height="560" v-loading="loading" :empty-text="clientEmptyText">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="clientId" label="clientId" min-width="170" />
        <el-table-column prop="clientName" label="应用名称" min-width="170" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }"><status-tag :value="row.status" /></template>
        </el-table-column>
        <el-table-column prop="lastCallAt" label="最近调用" min-width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button :icon="Edit" size="small" @click="openClient(row)">编辑</el-button>
            <el-button :icon="Key" size="small" @click="openCredential(row)">凭证</el-button>
            <el-button :icon="Lock" size="small" @click="openScope(row)">授权</el-button>
            <el-button :icon="Location" size="small" @click="openIp(row)">IP</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-else-if="tab === 'security'" class="split-grid">
      <div class="content-panel">
        <div class="page-title">
          <div>
            <h1>创建凭证</h1>
            <p>secretOnce 只在创建结果中展示一次，页面不做持久保存。</p>
          </div>
        </div>
        <security-form mode="credential" :model="credentialForm" @submit="saveCredential" />
      </div>
      <div class="content-panel">
        <div class="page-title">
          <div>
            <h1>授权与白名单</h1>
            <p>保存 scopeType/scopeValue 和 IP 白名单。</p>
          </div>
        </div>
        <el-form :model="scopeForm" label-width="108px">
          <el-form-item label="clientPk"><el-input-number v-model="scopeForm.clientPk" :min="1" /></el-form-item>
          <el-form-item label="授权类型">
            <el-select v-model="scopeForm.scopeType">
              <el-option v-for="item in scopeTypes" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权值"><el-input v-model="scopeForm.scopeValue" /></el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Check" @click="saveScope">保存授权</el-button>
          </el-form-item>
          <el-form-item label="IP 白名单">
            <el-input v-model="ipForm.ipList" type="textarea" :rows="5" placeholder="每行一个 IP 或 CIDR" />
          </el-form-item>
          <el-form-item>
            <el-button :icon="Check" @click="saveIp">保存 IP</el-button>
          </el-form-item>
        </el-form>
      </div>
    </section>

    <section v-else class="split-grid">
      <div class="content-panel">
        <div class="page-title">
          <div>
            <h1>开放接口调用</h1>
            <p>使用 clientId、keyVersion 和一次性密钥签名请求。</p>
          </div>
        </div>
        <el-form :model="consoleForm" label-width="124px">
          <el-form-item label="clientId"><el-input v-model="consoleForm.clientId" /></el-form-item>
          <el-form-item label="keyVersion"><el-input-number v-model="consoleForm.keyVersion" :min="1" controls-position="right" /></el-form-item>
          <el-form-item label="secret"><el-input v-model="consoleForm.secret" type="password" show-password /></el-form-item>
          <el-form-item label="方法">
            <el-select v-model="consoleForm.method"><el-option label="GET" value="GET" /><el-option label="POST" value="POST" /></el-select>
          </el-form-item>
          <el-form-item label="moduleId"><el-input-number v-model="consoleForm.moduleId" :min="1" /></el-form-item>
          <el-form-item label="recordId"><el-input-number v-model="consoleForm.recordId" :min="1" /></el-form-item>
          <el-form-item label="请求体 JSON"><el-input v-model="consoleForm.body" type="textarea" :rows="6" class="json-box" /></el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Connection" @click="callOpenApi">发送签名请求</el-button>
          </el-form-item>
        </el-form>
      </div>
      <div class="content-panel">
        <div class="page-title">
          <div>
            <h1>响应</h1>
            <p>展示实际开放接口返回。</p>
          </div>
        </div>
        <pre class="response-box">{{ consoleResult }}</pre>
      </div>
    </section>

    <el-dialog v-model="clientDialog" :title="clientForm.id ? '编辑 OpenAPI 应用' : '新建 OpenAPI 应用'" width="620px">
      <el-form :model="clientForm" label-width="118px">
        <el-form-item label="clientId" required><el-input v-model="clientForm.clientId" /></el-form-item>
        <el-form-item label="应用名称" required><el-input v-model="clientForm.clientName" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="clientForm.status"><el-option label="启用" value="ENABLED" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item label="限流规则 JSON"><el-input v-model="clientForm.rateLimitRule" type="textarea" :rows="5" class="json-box" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clientDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveClient">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import axios from 'axios';
import { hmacSha256Base64Url, sha256Hex } from '../api/openapi-sign';
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Check, Connection, Edit, Key, Location, Lock, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { openApiManageApi } from '../api/modules';
import type { AnyRecord, PageResult } from '../api/types';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';
import SecurityForm from '../components/SecurityForm.vue';

const context = useContextStore();
const tab = ref('clients');
const loading = ref(false);
const clientDialog = ref(false);
const clientQuery = reactive({ keyword: '' });
const clients = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const clientForm = reactive({
  id: undefined as number | undefined,
  clientId: '',
  clientName: '',
  status: 'ENABLED',
  rateLimitRule: '{ "qps": 10 }'
});
const credentialForm = reactive({ clientPk: undefined as number | undefined, keyVersion: undefined as number | undefined, expiresAt: '' });
const scopeForm = reactive({ clientPk: undefined as number | undefined, scopeType: 'SYSTEM', scopeValue: '' });
const ipForm = reactive({ clientPk: undefined as number | undefined, ipList: '' });
const scopeTypes = ['SYSTEM', 'TENANT', 'APP', 'MODULE', 'ACTION', 'FIELD'];
const consoleResult = ref('');
const consoleForm = reactive({
  clientId: '',
  keyVersion: 1 as number | undefined,
  secret: '',
  method: 'GET',
  moduleId: undefined as number | undefined,
  recordId: undefined as number | undefined,
  body: '{\n  "systemId": 1,\n  "tenantId": 1,\n  "appId": 1,\n  "moduleId": 1,\n  "values": []\n}'
});
const contextBlockReason = computed(() => (!context.hasSystemContext ? 'Enter system context before loading OpenAPI clients.' : ''));
const clientEmptyText = computed(() => contextBlockReason.value || 'No data');

function clearClients() {
  Object.assign(clients, { pageNo: 1, pageSize: 20, total: 0, records: [] });
}

function openClient(row?: AnyRecord) {
  if (!context.hasSystemContext) {
    ElMessage.warning('Enter system context before creating OpenAPI clients.');
    return;
  }
  Object.assign(clientForm, {
    id: row ? Number(row.id ?? row.clientPk) || undefined : undefined,
    clientId: String(row?.clientId ?? ''),
    clientName: String(row?.clientName ?? ''),
    status: String(row?.status ?? 'ENABLED'),
    rateLimitRule: String(row?.rateLimitRule ?? '{ "qps": 10 }')
  });
  clientDialog.value = true;
}

function openCredential(row: AnyRecord) {
  tab.value = 'security';
  credentialForm.clientPk = Number(row.clientPk ?? row.id);
}

function openScope(row: AnyRecord) {
  tab.value = 'security';
  scopeForm.clientPk = Number(row.clientPk ?? row.id);
}

function openIp(row: AnyRecord) {
  tab.value = 'security';
  ipForm.clientPk = Number(row.clientPk ?? row.id);
}

async function loadClients() {
  if (contextBlockReason.value) {
    clearClients();
    ElMessage.warning(contextBlockReason.value);
    return;
  }
  loading.value = true;
  try {
    Object.assign(clients, await openApiManageApi.clients(clientQuery));
  } finally {
    loading.value = false;
  }
}

async function saveClient() {
  if (!clientForm.clientId || !clientForm.clientName) {
    ElMessage.warning('clientId and clientName are required.');
    return;
  }
  const payload = {
    ...(clientForm.id ? { id: clientForm.id } : {}),
    clientId: clientForm.clientId,
    clientName: clientForm.clientName,
    status: clientForm.status,
    rateLimitRule: clientForm.rateLimitRule
  };
  await openApiManageApi.createClient(payload);
  ElMessage.success(clientForm.id ? '外部应用已保存' : '外部应用已创建');
  clientDialog.value = false;
  loadClients();
}

async function saveCredential() {
  if (!credentialForm.clientPk) {
    ElMessage.warning('clientPk is required.');
    return;
  }
  const result = await openApiManageApi.createCredential({
    clientPk: credentialForm.clientPk,
    ...(credentialForm.keyVersion ? { keyVersion: credentialForm.keyVersion } : {}),
    ...(credentialForm.expiresAt ? { expiresAt: credentialForm.expiresAt } : {})
  });
  const secretOnce = String(result.secretOnce || '');
  await ElMessageBox.alert(secretOnce || '后端未返回 secretOnce', '一次性密钥', {
    confirmButtonText: '我已记录',
    type: 'warning'
  });
}

async function saveScope() {
  if (!scopeForm.clientPk || !scopeForm.scopeType || !scopeForm.scopeValue) {
    ElMessage.warning('clientPk, scopeType and scopeValue are required.');
    return;
  }
  await openApiManageApi.saveScope({ ...scopeForm });
  ElMessage.success('授权范围已保存');
}

async function saveIp() {
  if (!ipForm.clientPk || !ipForm.ipList.trim()) {
    ElMessage.warning('clientPk and IP whitelist are required.');
    return;
  }
  const ipList = Array.from(new Set(ipForm.ipList.split(/\r?\n/).map((item) => item.trim()).filter(Boolean)));
  await openApiManageApi.saveIpWhitelist({ clientPk: ipForm.clientPk, ipList });
  ElMessage.success('IP 白名单已保存');
}

async function callOpenApi() {
  const method = consoleForm.method;
  if (!consoleForm.clientId || !consoleForm.keyVersion || !consoleForm.secret) {
    ElMessage.warning('clientId, keyVersion and secret are required.');
    return;
  }
  if (method === 'GET' && (!consoleForm.moduleId || !consoleForm.recordId)) {
    ElMessage.warning('moduleId and recordId are required for OpenAPI GET.');
    return;
  }
  const body = method === 'POST' ? consoleForm.body || '{}' : '';
  if (method === 'POST') {
    const payload = JSON.parse(body);
    if (!payload.systemId || !payload.tenantId || !payload.appId || !payload.moduleId) {
      ElMessage.warning('POST body must include systemId, tenantId, appId and moduleId.');
      return;
    }
  }
  const uri =
    method === 'GET'
      ? `/api/v1/open/records/${consoleForm.moduleId}/${consoleForm.recordId}`
      : '/api/v1/open/records';
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = crypto.randomUUID();
  const bodyHash = await sha256Hex(body);
  const canonical = `${method}\n${uri}\n${timestamp}\n${nonce}\n${bodyHash}`;
  const signature = await hmacSha256Base64Url(consoleForm.secret, canonical);
  const response = await axios.request({
    method,
    url: uri,
    baseURL: import.meta.env.VITE_API_BASE_URL || '',
    data: method === 'POST' ? JSON.parse(body) : undefined,
    headers: {
      'X-Open-Client-Id': consoleForm.clientId,
      'X-Open-Key-Version': String(consoleForm.keyVersion),
      'X-Open-Timestamp': timestamp,
      'X-Open-Nonce': nonce,
      'X-Open-Signature': signature,
      'Idempotency-Key': method === 'POST' ? crypto.randomUUID() : undefined
    }
  });
  consoleResult.value = JSON.stringify(response.data, null, 2);
}

onMounted(loadClients);
</script>

<style scoped>
.response-box {
  min-height: 420px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid #dce4ef;
  border-radius: 8px;
  background: #0f172a;
  color: #e5edf6;
  white-space: pre-wrap;
}
</style>
