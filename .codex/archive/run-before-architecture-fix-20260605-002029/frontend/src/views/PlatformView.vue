<template>
  <div>
    <div class="page-title">
      <div>
        <h1>平台租户/系统</h1>
        <p>维护租户与系统，进入系统时后端重新签发携带 systemId、tenantId 的 Token。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Plus" type="primary" @click="openTenant">租户</el-button>
        <el-button :icon="Plus" type="primary" @click="openSystem">系统</el-button>
      </div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="租户" name="tenants">
        <section class="content-panel">
          <div class="toolbar">
            <el-input v-model="tenantQuery.keyword" clearable placeholder="租户名称/编码" style="width: 220px" />
            <el-button :icon="Search" @click="loadTenants">查询</el-button>
            <el-button :icon="Refresh" @click="loadTenants" />
          </div>
          <el-table :data="tenants.records" border height="520" v-loading="loading.tenants">
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="tenantName" label="租户名称" min-width="160" />
            <el-table-column prop="tenantCode" label="编码" min-width="140" />
            <el-table-column prop="ownerAccountId" label="负责人账号" width="130" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }"><status-tag :value="row.status" /></template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>
      <el-tab-pane label="系统" name="systems">
        <section class="content-panel">
          <div class="toolbar">
            <el-input v-model="systemQuery.keyword" clearable placeholder="系统名称/编码" style="width: 220px" />
            <el-button :icon="Search" @click="loadSystems">查询</el-button>
            <el-button :icon="Refresh" @click="loadSystems" />
          </div>
          <el-table :data="systems.records" border height="520" v-loading="loading.systems">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="systemName" label="系统名称" min-width="160" />
            <el-table-column prop="systemCode" label="编码" min-width="130" />
            <el-table-column prop="tenantId" label="租户 ID" width="100" />
            <el-table-column prop="ownerAccountId" label="负责人账号" width="130" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }"><status-tag :value="row.status" /></template>
            </el-table-column>
            <el-table-column label="操作" width="250" fixed="right">
              <template #default="{ row }">
                <el-button :icon="SwitchButton" size="small" @click="context.enterSystem(row)">进入</el-button>
                <el-button :icon="CircleCheck" size="small" @click="changeStatus(row, 'ENABLED')">启用</el-button>
                <el-button :icon="CircleClose" size="small" @click="changeStatus(row, 'DISABLED')">停用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="tenantDialog" title="新建租户" width="520px">
      <el-form :model="tenantForm" label-width="100px">
        <el-form-item label="租户名称" required><el-input v-model="tenantForm.tenantName" /></el-form-item>
        <el-form-item label="租户编码" required><el-input v-model="tenantForm.tenantCode" /></el-form-item>
        <el-form-item label="负责人账号"><el-input-number v-model="tenantForm.ownerAccountId" :min="1" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="tenantForm.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tenantDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveTenant">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="systemDialog" title="新建系统" width="560px">
      <el-form :model="systemForm" label-width="108px">
        <el-form-item label="系统名称" required><el-input v-model="systemForm.systemName" /></el-form-item>
        <el-form-item label="系统编码" required><el-input v-model="systemForm.systemCode" /></el-form-item>
        <el-form-item label="租户 ID" required><el-input-number v-model="systemForm.tenantId" :min="1" /></el-form-item>
        <el-form-item label="负责人账号"><el-input-number v-model="systemForm.ownerAccountId" :min="1" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="systemForm.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="systemForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="systemDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveSystem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, CircleCheck, CircleClose, Plus, Refresh, Search, SwitchButton } from '@element-plus/icons-vue';
import { platformApi } from '../api/modules';
import type { AnyRecord, PageResult } from '../api/types';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';

const context = useContextStore();
const tab = ref('systems');
const tenantDialog = ref(false);
const systemDialog = ref(false);
const tenantQuery = reactive({ keyword: '' });
const systemQuery = reactive({ keyword: '' });
const loading = reactive({ tenants: false, systems: false });
const tenants = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const systems = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });

const tenantForm = reactive({ tenantName: '', tenantCode: '', ownerAccountId: undefined as number | undefined, status: 'ENABLED' });
const systemForm = reactive({
  systemName: '',
  systemCode: '',
  tenantId: undefined as number | undefined,
  ownerAccountId: undefined as number | undefined,
  status: 'ENABLED',
  description: ''
});

function openTenant() {
  Object.assign(tenantForm, { tenantName: '', tenantCode: '', ownerAccountId: undefined, status: 'ENABLED' });
  tenantDialog.value = true;
}

function openSystem() {
  Object.assign(systemForm, { systemName: '', systemCode: '', tenantId: undefined, ownerAccountId: undefined, status: 'ENABLED', description: '' });
  systemDialog.value = true;
}

async function loadTenants() {
  loading.tenants = true;
  try {
    Object.assign(tenants, await platformApi.tenants({ keyword: tenantQuery.keyword }));
  } finally {
    loading.tenants = false;
  }
}

async function loadSystems() {
  loading.systems = true;
  try {
    Object.assign(systems, await platformApi.systems({ keyword: systemQuery.keyword }));
  } finally {
    loading.systems = false;
  }
}

async function saveTenant() {
  await platformApi.createTenant({ ...tenantForm });
  ElMessage.success('租户已创建');
  tenantDialog.value = false;
  loadTenants();
}

async function saveSystem() {
  await platformApi.createSystem({ ...systemForm });
  ElMessage.success('系统已创建');
  systemDialog.value = false;
  loadSystems();
}

async function changeStatus(row: AnyRecord, status: string) {
  await platformApi.updateSystemStatus(Number(row.systemId ?? row.id), status);
  ElMessage.success('状态已更新');
  loadSystems();
}

onMounted(() => {
  loadTenants();
  loadSystems();
});
</script>
