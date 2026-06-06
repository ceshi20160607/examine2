<template>
  <div>
    <div class="page-title">
      <div>
        <h1>运行记录</h1>
        <p>按应用和模块查询业务记录，创建与更新时提交字段值数组，编号为空时由后端生成。</p>
      </div>
      <el-button :icon="Plus" type="primary" @click="openRecord()">新增记录</el-button>
    </div>

    <section class="content-panel">
      <div class="toolbar">
        <el-input-number v-model="query.appId" placeholder="appId" :min="1" controls-position="right" />
        <el-input-number v-model="query.moduleId" placeholder="moduleId" :min="1" controls-position="right" />
        <el-input v-model="query.recordNo" clearable placeholder="记录编号" style="width: 180px" />
        <el-select v-model="query.status" clearable placeholder="状态" style="width: 150px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已提交" value="SUBMITTED" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
        <el-button :icon="Search" @click="load">查询</el-button>
        <el-button :icon="Refresh" @click="load" />
      </div>

      <el-table :data="page.records" border height="560" v-loading="loading" :empty-text="tableEmptyText">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="recordNo" label="记录编号" min-width="150" />
        <el-table-column prop="appId" label="appId" width="90" />
        <el-table-column prop="moduleId" label="moduleId" width="110" />
        <el-table-column prop="recordStatus" label="记录状态" width="120">
          <template #default="{ row }"><status-tag :value="row.recordStatus" /></template>
        </el-table-column>
        <el-table-column prop="processStatus" label="流程状态" width="120">
          <template #default="{ row }"><status-tag :value="row.processStatus" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button :icon="View" size="small" @click="showDetail(row)">详情</el-button>
            <el-button :icon="Edit" size="small" @click="openRecord(row)">编辑</el-button>
            <el-button :icon="ChatLineSquare" size="small" @click="openComment(row)">评论</el-button>
            <el-button :icon="Delete" size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialog" :title="editingId ? '编辑记录' : '新增记录'" width="760px">
      <el-form :model="recordForm" label-width="118px">
        <div class="form-grid">
          <el-form-item label="系统 ID" required><el-input-number v-model="recordForm.systemId" :min="1" /></el-form-item>
          <el-form-item label="租户 ID" required><el-input-number v-model="recordForm.tenantId" :min="1" /></el-form-item>
          <el-form-item label="应用 ID" required><el-input-number v-model="recordForm.appId" :min="1" /></el-form-item>
          <el-form-item label="模块 ID" required><el-input-number v-model="recordForm.moduleId" :min="1" /></el-form-item>
          <el-form-item label="记录编号"><el-input v-model="recordForm.recordNo" placeholder="为空时后端自动生成" /></el-form-item>
          <el-form-item label="记录状态">
            <el-select v-model="recordForm.recordStatus">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="提交" value="SUBMITTED" />
            </el-select>
          </el-form-item>
          <el-form-item label="配置版本" class="full"><el-input-number v-model="recordForm.appVersionId" :min="1" /></el-form-item>
          <el-form-item label="配置快照" class="full">
            <el-input v-model="recordForm.configSnapshot" type="textarea" :rows="4" class="json-box" />
          </el-form-item>
          <el-form-item label="字段值数组" class="full">
            <el-input v-model="valuesText" type="textarea" :rows="8" class="json-box" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer" title="记录详情" size="520px">
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="(value, key) in detail" :key="String(key)" :label="String(key)">
          <pre v-if="typeof value === 'object'">{{ JSON.stringify(value, null, 2) }}</pre>
          <span v-else>{{ value ?? '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <el-dialog v-model="commentDialog" title="添加评论" width="520px">
      <el-input v-model="commentText" type="textarea" :rows="5" placeholder="审批、协作或记录说明" />
      <template #footer>
        <el-button @click="commentDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveComment">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ChatLineSquare, Check, Delete, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue';
import { recordApi } from '../api/modules';
import type { AnyRecord, PageResult } from '../api/types';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';

const context = useContextStore();
const loading = ref(false);
const dialog = ref(false);
const detailDrawer = ref(false);
const commentDialog = ref(false);
const editingId = ref<number | null>(null);
const commentRecordId = ref<number | null>(null);
const commentText = ref('');
const valuesText = ref('[\n  { "fieldId": 1, "value": "示例值" }\n]');
const detail = ref<AnyRecord>({});
const query = reactive({ appId: undefined as number | undefined, moduleId: undefined as number | undefined, recordNo: '', status: '' });
const page = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const recordForm = reactive({
  systemId: undefined as number | undefined,
  tenantId: undefined as number | undefined,
  appId: undefined as number | undefined,
  moduleId: undefined as number | undefined,
  recordNo: '',
  recordStatus: 'DRAFT',
  appVersionId: undefined as number | undefined,
  configSnapshot: '{}'
});
const requestBlockReason = computed(() => {
  if (!context.hasSystemContext) return 'Enter system context before loading records.';
  if (!query.appId) return 'Select appId before loading records.';
  if (!query.moduleId) return 'Select moduleId before loading records.';
  return '';
});
const tableEmptyText = computed(() => requestBlockReason.value || 'No data');

function clearPage() {
  Object.assign(page, { pageNo: 1, pageSize: 20, total: 0, records: [] });
}

async function load() {
  if (requestBlockReason.value) {
    clearPage();
    ElMessage.warning(requestBlockReason.value);
    return;
  }
  loading.value = true;
  try {
    Object.assign(page, await recordApi.list(query));
  } finally {
    loading.value = false;
  }
}

function openRecord(row?: AnyRecord) {
  if (!row && requestBlockReason.value) {
    ElMessage.warning(requestBlockReason.value);
    return;
  }
  editingId.value = row ? Number(row.id ?? row.recordId) : null;
  Object.assign(recordForm, {
    systemId: Number(row?.systemId ?? context.systemId) || undefined,
    tenantId: Number(row?.tenantId ?? context.tenantId) || undefined,
    appId: Number(row?.appId ?? query.appId) || undefined,
    moduleId: Number(row?.moduleId ?? query.moduleId) || undefined,
    recordNo: String(row?.recordNo ?? ''),
    recordStatus: String(row?.recordStatus ?? 'DRAFT'),
    appVersionId: Number(row?.appVersionId) || undefined,
    configSnapshot: String(row?.configSnapshot ?? '{}')
  });
  valuesText.value = JSON.stringify(row?.values ?? [{ fieldId: 1, value: '示例值' }], null, 2);
  dialog.value = true;
}

function buildPayload() {
  let values: unknown = [];
  try {
    values = JSON.parse(valuesText.value || '[]');
  } catch {
    throw new Error('字段值数组不是合法 JSON');
  }
  return context.enrichPayload({
    ...recordForm,
    values
  });
}

async function save() {
  const payload = buildPayload();
  if (!payload.systemId || !payload.tenantId || !payload.appId || !payload.moduleId) {
    ElMessage.warning('systemId, tenantId, appId and moduleId are required.');
    return;
  }
  if (editingId.value) {
    await recordApi.update(editingId.value, payload);
  } else {
    await recordApi.create(payload);
  }
  ElMessage.success('记录已保存');
  dialog.value = false;
  load();
}

async function showDetail(row: AnyRecord) {
  detail.value = await recordApi.detail(Number(row.id ?? row.recordId));
  detailDrawer.value = true;
}

function openComment(row: AnyRecord) {
  commentRecordId.value = Number(row.id ?? row.recordId);
  commentText.value = '';
  commentDialog.value = true;
}

async function saveComment() {
  await recordApi.comment({ recordId: commentRecordId.value, commentText: commentText.value });
  ElMessage.success('评论已添加');
  commentDialog.value = false;
}

async function remove(row: AnyRecord) {
  await ElMessageBox.confirm('确认软删除该记录？', '删除记录', { type: 'warning' });
  await recordApi.remove(Number(row.id ?? row.recordId));
  ElMessage.success('记录已删除');
  load();
}

onMounted(load);
</script>

<style scoped>
pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
