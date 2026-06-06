<template>
  <div>
    <div class="page-title">
      <div>
        <h1>文件与导入导出</h1>
        <p>当前后端实现文件元数据、文件关联和导入导出任务元数据，页面不伪造真实文件上传。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Plus" type="primary" @click="openFile">文件元数据</el-button>
        <el-button :icon="Plus" type="primary" @click="openTask">任务元数据</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="tab === 'files' ? loadFiles() : loadTasks()">
      <el-tab-pane label="文件元数据" name="files" />
      <el-tab-pane label="导入导出任务" name="tasks" />
    </el-tabs>

    <section v-if="tab === 'files'" class="content-panel">
      <div class="toolbar">
        <el-input v-model="fileQuery.fileName" clearable placeholder="文件名" style="width: 220px" />
        <el-button :icon="Search" @click="loadFiles">查询</el-button>
        <el-button :icon="Refresh" @click="loadFiles" />
      </div>
      <el-table :data="files.records" border height="560" v-loading="loading.files" :empty-text="fileEmptyText">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="fileName" label="文件名" min-width="180" />
        <el-table-column prop="contentType" label="类型" min-width="150" />
        <el-table-column prop="fileSize" label="大小" width="110" />
        <el-table-column prop="storagePath" label="存储路径" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }"><status-tag :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button :icon="Link" size="small" @click="openRelation(row)">关联</el-button>
            <el-button :icon="Delete" size="small" type="danger" @click="removeFile(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-else class="content-panel">
      <div class="toolbar">
        <el-select v-model="taskQuery.taskType" clearable placeholder="任务类型" style="width: 160px">
          <el-option label="导入" value="IMPORT" />
          <el-option label="导出" value="EXPORT" />
        </el-select>
        <el-button :icon="Search" @click="loadTasks">查询</el-button>
        <el-button :icon="Refresh" @click="loadTasks" />
      </div>
      <el-table :data="tasks.records" border height="560" v-loading="loading.tasks" :empty-text="taskEmptyText">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="taskType" label="类型" width="110" />
        <el-table-column prop="templateId" label="模板 ID" width="110" />
        <el-table-column prop="moduleId" label="moduleId" width="110" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }"><status-tag :value="row.status" /></template>
        </el-table-column>
        <el-table-column prop="failureReason" label="失败原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="resultFileId" label="结果文件" width="110" />
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
      </el-table>
    </section>

    <el-dialog v-model="fileDialog" title="新建文件元数据" width="620px">
      <el-form :model="fileForm" label-width="108px">
        <el-form-item label="系统 ID" required><el-input-number v-model="fileForm.systemId" :min="1" /></el-form-item>
        <el-form-item label="租户 ID" required><el-input-number v-model="fileForm.tenantId" :min="1" /></el-form-item>
        <el-form-item label="文件名" required><el-input v-model="fileForm.fileName" /></el-form-item>
        <el-form-item label="存储路径" required><el-input v-model="fileForm.storagePath" /></el-form-item>
        <el-form-item label="文件大小"><el-input-number v-model="fileForm.fileSize" :min="0" /></el-form-item>
        <el-form-item label="MIME 类型"><el-input v-model="fileForm.contentType" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fileDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveFile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="relationDialog" title="关联文件" width="560px">
      <el-form :model="relationForm" label-width="108px">
        <el-form-item label="文件 ID"><el-input-number v-model="relationForm.fileId" :min="1" /></el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="relationForm.objectType">
            <el-option label="记录" value="RECORD" />
            <el-option label="任务" value="TASK" />
            <el-option label="导入导出" value="IMPORT_EXPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象 ID"><el-input-number v-model="relationForm.objectId" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveRelation">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="taskDialog" title="新建导入导出任务" width="620px">
      <el-form :model="taskForm" label-width="118px">
        <el-form-item label="系统 ID" required><el-input-number v-model="taskForm.systemId" :min="1" /></el-form-item>
        <el-form-item label="租户 ID" required><el-input-number v-model="taskForm.tenantId" :min="1" /></el-form-item>
        <el-form-item label="应用 ID"><el-input-number v-model="taskForm.appId" :min="1" /></el-form-item>
        <el-form-item label="模块 ID" required><el-input-number v-model="taskForm.moduleId" :min="1" /></el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="taskForm.taskType"><el-option label="导入" value="IMPORT" /><el-option label="导出" value="EXPORT" /></el-select>
        </el-form-item>
        <el-form-item label="模板 ID"><el-input-number v-model="taskForm.templateId" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Check, Delete, Link, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { fileApi } from '../api/modules';
import type { AnyRecord, PageResult } from '../api/types';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';

const context = useContextStore();
const tab = ref('files');
const fileDialog = ref(false);
const relationDialog = ref(false);
const taskDialog = ref(false);
const loading = reactive({ files: false, tasks: false });
const fileQuery = reactive({ fileName: '' });
const taskQuery = reactive({ taskType: '' });
const files = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const tasks = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const fileForm = reactive({ systemId: undefined as number | undefined, tenantId: undefined as number | undefined, storagePath: '', fileName: '', fileSize: 0, contentType: '' });
const relationForm = reactive({ fileId: undefined as number | undefined, objectType: 'RECORD', objectId: undefined as number | undefined });
const taskForm = reactive({ systemId: undefined as number | undefined, tenantId: undefined as number | undefined, appId: undefined as number | undefined, moduleId: undefined as number | undefined, taskType: 'EXPORT', templateId: undefined as number | undefined });
const contextBlockReason = computed(() => (!context.hasSystemContext ? 'Enter system context before loading files and tasks.' : ''));
const fileEmptyText = computed(() => contextBlockReason.value || 'No data');
const taskEmptyText = computed(() => contextBlockReason.value || 'No data');

function clearFiles() {
  Object.assign(files, { pageNo: 1, pageSize: 20, total: 0, records: [] });
}

function clearTasks() {
  Object.assign(tasks, { pageNo: 1, pageSize: 20, total: 0, records: [] });
}

function openFile() {
  if (!context.hasSystemContext) {
    ElMessage.warning('Enter system context before creating file metadata.');
    return;
  }
  Object.assign(fileForm, { systemId: context.systemId, tenantId: context.tenantId, storagePath: '', fileName: '', fileSize: 0, contentType: '' });
  fileDialog.value = true;
}

function openTask() {
  if (!context.hasSystemContext) {
    ElMessage.warning('Enter system context before creating import/export tasks.');
    return;
  }
  Object.assign(taskForm, { systemId: context.systemId, tenantId: context.tenantId, appId: undefined, moduleId: undefined, taskType: 'EXPORT', templateId: undefined });
  taskDialog.value = true;
}

function openRelation(row: AnyRecord) {
  Object.assign(relationForm, { fileId: Number(row.fileId ?? row.id), objectType: 'RECORD', objectId: undefined });
  relationDialog.value = true;
}

async function loadFiles() {
  if (contextBlockReason.value) {
    clearFiles();
    ElMessage.warning(contextBlockReason.value);
    return;
  }
  loading.files = true;
  try {
    Object.assign(files, await fileApi.list(fileQuery));
  } finally {
    loading.files = false;
  }
}

async function loadTasks() {
  if (contextBlockReason.value) {
    clearTasks();
    ElMessage.warning(contextBlockReason.value);
    return;
  }
  loading.tasks = true;
  try {
    Object.assign(tasks, await fileApi.tasks(taskQuery));
  } finally {
    loading.tasks = false;
  }
}

async function saveFile() {
  if (!fileForm.systemId || !fileForm.tenantId || !fileForm.fileName || !fileForm.storagePath) {
    ElMessage.warning('systemId, tenantId, fileName and storagePath are required.');
    return;
  }
  await fileApi.create(context.enrichPayload(fileForm));
  ElMessage.success('文件元数据已保存');
  fileDialog.value = false;
  loadFiles();
}

async function saveRelation() {
  await fileApi.relation({ ...relationForm });
  ElMessage.success('文件已关联');
  relationDialog.value = false;
}

async function saveTask() {
  if (!taskForm.systemId || !taskForm.tenantId || !taskForm.moduleId) {
    ElMessage.warning('systemId, tenantId and moduleId are required.');
    return;
  }
  await fileApi.createTask(context.enrichPayload(taskForm));
  ElMessage.success('任务元数据已创建');
  taskDialog.value = false;
  loadTasks();
}

async function removeFile(row: AnyRecord) {
  await ElMessageBox.confirm('确认删除文件元数据？', '删除文件', { type: 'warning' });
  await fileApi.remove(Number(row.fileId ?? row.id));
  ElMessage.success('文件元数据已删除');
  loadFiles();
}

onMounted(() => {
  loadFiles();
  loadTasks();
});
</script>
