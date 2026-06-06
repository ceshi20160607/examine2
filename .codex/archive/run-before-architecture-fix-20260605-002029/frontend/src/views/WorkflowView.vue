<template>
  <div>
    <div class="page-title">
      <div>
        <h1>流程工作台</h1>
        <p>维护流程模板和版本，提供可视节点编排、流程发起、待办查询与任务处理。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Plus" type="primary" @click="openTemplate">模板</el-button>
        <el-button :icon="VideoPlay" type="primary" @click="startDialog = true">发起流程</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="tab === 'tasks' ? loadTasks() : loadTemplates()">
      <el-tab-pane label="流程模板" name="templates" />
      <el-tab-pane label="待办任务" name="tasks" />
    </el-tabs>

    <section v-if="tab === 'templates'" class="split-grid">
      <div class="content-panel">
        <div class="toolbar">
          <el-input-number v-model="templateQuery.moduleId" placeholder="moduleId" :min="1" controls-position="right" />
          <el-button :icon="Search" @click="loadTemplates">查询</el-button>
          <el-button :icon="Refresh" @click="loadTemplates" />
        </div>
        <el-table :data="templates.records" border height="560" v-loading="loading.templates" :empty-text="templateEmptyText" highlight-current-row @current-change="selectTemplate">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="templateName" label="模板名称" min-width="160" />
          <el-table-column prop="moduleId" label="moduleId" width="110" />
          <el-table-column prop="currentVersion" label="版本" width="100" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }"><status-tag :value="row.status" /></template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button :icon="Edit" size="small" @click="selectTemplate(row)">设计</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="content-panel">
        <div class="page-title">
          <div>
            <h1>流程设计</h1>
            <p>{{ selectedTemplate ? `模板 ID ${selectedTemplate.id ?? selectedTemplate.templateId}` : '选择模板后创建版本' }}</p>
          </div>
        </div>
        <div class="toolbar">
          <el-button :icon="Plus" @click="addNode('APPROVE')">审批</el-button>
          <el-button :icon="Plus" @click="addNode('CONDITION')">条件</el-button>
          <el-button :icon="Plus" @click="addNode('CC')">抄送</el-button>
        </div>
        <div class="workflow-canvas">
          <template v-for="(node, index) in nodes" :key="node.id">
            <div class="workflow-node">
              <el-icon><Connection /></el-icon>
              <span>{{ node.name }}</span>
            </div>
            <span v-if="index < nodes.length - 1" class="workflow-edge">→</span>
          </template>
        </div>
        <el-form :model="versionForm" label-width="90px" class="designer-form">
          <el-form-item label="版本号"><el-input-number v-model="versionForm.versionNo" :min="1" controls-position="right" placeholder="空值由后端生成" /></el-form-item>
          <el-form-item label="设置 JSON">
            <el-input v-model="versionForm.settingJson" type="textarea" :rows="4" class="json-box" />
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="Check" :disabled="!selectedTemplate" @click="saveVersion">保存版本</el-button>
          <el-input-number v-model="publishVersionId" placeholder="versionId" :min="1" controls-position="right" />
          <el-button :icon="Upload" @click="publishVersion">发布版本</el-button>
        </div>
      </div>
    </section>

    <section v-else class="content-panel">
      <div class="toolbar">
        <el-input-number v-model="taskQuery.moduleId" placeholder="moduleId" :min="1" controls-position="right" />
        <el-select v-model="taskQuery.status" clearable placeholder="任务状态" style="width: 160px">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已同意" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
        <el-button :icon="Search" @click="loadTasks">查询</el-button>
        <el-button :icon="Refresh" @click="loadTasks" />
      </div>
      <el-table :data="tasks.records" border height="560" v-loading="loading.tasks" :empty-text="taskEmptyText">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="instanceId" label="实例 ID" width="110" />
        <el-table-column prop="moduleId" label="moduleId" width="110" />
        <el-table-column prop="taskName" label="任务名称" min-width="160" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }"><status-tag :value="row.status" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="处理" width="310" fixed="right">
          <template #default="{ row }">
            <el-button :icon="CircleCheck" size="small" @click="openHandle(row, 'APPROVE')">同意</el-button>
            <el-button :icon="CircleClose" size="small" @click="openHandle(row, 'REJECT')">拒绝</el-button>
            <el-button :icon="Right" size="small" @click="openHandle(row, 'TRANSFER')">转交</el-button>
            <el-button :icon="CloseBold" size="small" type="danger" @click="openHandle(row, 'TERMINATE')">终止</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="templateDialog" title="新建流程模板" width="560px">
      <el-form :model="templateForm" label-width="108px">
        <el-form-item label="系统 ID" required><el-input-number v-model="templateForm.systemId" :min="1" /></el-form-item>
        <el-form-item label="租户 ID" required><el-input-number v-model="templateForm.tenantId" :min="1" /></el-form-item>
        <el-form-item label="模块 ID" required><el-input-number v-model="templateForm.moduleId" :min="1" /></el-form-item>
        <el-form-item label="模板名称" required><el-input v-model="templateForm.templateName" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="templateForm.status"><el-option label="草稿" value="DRAFT" /><el-option label="发布" value="PUBLISHED" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="startDialog" title="发起流程" width="560px">
      <el-form :model="startForm" label-width="108px">
        <el-form-item label="系统 ID" required><el-input-number v-model="startForm.systemId" :min="1" /></el-form-item>
        <el-form-item label="租户 ID" required><el-input-number v-model="startForm.tenantId" :min="1" /></el-form-item>
        <el-form-item label="模块 ID" required><el-input-number v-model="startForm.moduleId" :min="1" /></el-form-item>
        <el-form-item label="记录 ID" required><el-input-number v-model="startForm.recordId" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialog = false">取消</el-button>
        <el-button type="primary" :icon="VideoPlay" @click="startWorkflow">发起</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="handleDialog" :title="`任务处理：${handleForm.action}`" width="540px">
      <el-form :model="handleForm" label-width="96px">
        <el-form-item label="处理意见"><el-input v-model="handleForm.comment" type="textarea" :rows="4" /></el-form-item>
        <el-form-item v-if="handleForm.action === 'TRANSFER'" label="转交给"><el-input-number v-model="handleForm.transferTo" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="handleTask">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  Check,
  CircleCheck,
  CircleClose,
  CloseBold,
  Connection,
  Edit,
  Plus,
  Refresh,
  Right,
  Search,
  Upload,
  VideoPlay
} from '@element-plus/icons-vue';
import { workflowApi } from '../api/modules';
import type { AnyRecord, PageResult } from '../api/types';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';

const context = useContextStore();
const tab = ref('templates');
const templateDialog = ref(false);
const startDialog = ref(false);
const handleDialog = ref(false);
const selectedTemplate = ref<AnyRecord | null>(null);
const publishVersionId = ref<number | undefined>();
const handleTaskId = ref<number | null>(null);
const loading = reactive({ templates: false, tasks: false });
const templateQuery = reactive({ moduleId: undefined as number | undefined });
const taskQuery = reactive({ moduleId: undefined as number | undefined, status: '' });
const templates = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const tasks = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const nodes = ref([
  { id: 'start', type: 'START', name: '开始' },
  { id: 'approve-1', type: 'APPROVE', name: '部门审批' },
  { id: 'end', type: 'END', name: '结束' }
]);
const versionForm = reactive({ versionNo: undefined as number | undefined, settingJson: '{ "timeoutHours": 24 }' });
const templateForm = reactive({ systemId: undefined as number | undefined, tenantId: undefined as number | undefined, moduleId: undefined as number | undefined, templateName: '', status: 'DRAFT' });
const startForm = reactive({ systemId: undefined as number | undefined, tenantId: undefined as number | undefined, moduleId: undefined as number | undefined, recordId: undefined as number | undefined });
const handleForm = reactive({ action: 'APPROVE', comment: '', transferTo: undefined as number | undefined });
const templateBlockReason = computed(() => {
  if (!context.hasSystemContext) return 'Enter system context before loading workflow templates.';
  if (!templateQuery.moduleId) return 'Select moduleId before loading workflow templates.';
  return '';
});
const taskBlockReason = computed(() => (!context.hasSystemContext ? 'Enter system context before loading workflow tasks.' : ''));
const templateEmptyText = computed(() => templateBlockReason.value || 'No data');
const taskEmptyText = computed(() => taskBlockReason.value || 'No data');

function clearTemplates() {
  Object.assign(templates, { pageNo: 1, pageSize: 20, total: 0, records: [] });
}

function clearTasks() {
  Object.assign(tasks, { pageNo: 1, pageSize: 20, total: 0, records: [] });
}

function edges() {
  return nodes.value.slice(0, -1).map((node, index) => ({ from: node.id, to: nodes.value[index + 1].id }));
}

function addNode(type: string) {
  const node = { id: `${type.toLowerCase()}-${Date.now()}`, type, name: type === 'CONDITION' ? '条件分支' : type === 'CC' ? '抄送' : '审批节点' };
  nodes.value.splice(Math.max(nodes.value.length - 1, 1), 0, node);
}

function openTemplate() {
  if (!context.hasSystemContext) {
    ElMessage.warning('Enter system context before creating workflow templates.');
    return;
  }
  Object.assign(templateForm, { systemId: context.systemId, tenantId: context.tenantId, moduleId: undefined, templateName: '', status: 'DRAFT' });
  templateDialog.value = true;
}

function selectTemplate(row: AnyRecord | null) {
  selectedTemplate.value = row;
}

async function loadTemplates() {
  if (templateBlockReason.value) {
    clearTemplates();
    ElMessage.warning(templateBlockReason.value);
    return;
  }
  loading.templates = true;
  try {
    Object.assign(templates, await workflowApi.templates(templateQuery));
  } finally {
    loading.templates = false;
  }
}

async function loadTasks() {
  if (taskBlockReason.value) {
    clearTasks();
    ElMessage.warning(taskBlockReason.value);
    return;
  }
  loading.tasks = true;
  try {
    Object.assign(tasks, await workflowApi.tasks(taskQuery));
  } finally {
    loading.tasks = false;
  }
}

async function saveTemplate() {
  if (!templateForm.systemId || !templateForm.tenantId || !templateForm.moduleId) {
    ElMessage.warning('systemId, tenantId and moduleId are required.');
    return;
  }
  await workflowApi.createTemplate(context.enrichPayload(templateForm));
  ElMessage.success('流程模板已创建');
  templateDialog.value = false;
  loadTemplates();
}

async function saveVersion() {
  const templateId = Number(selectedTemplate.value?.templateId ?? selectedTemplate.value?.id);
  if (!templateId) {
    ElMessage.warning('Select workflow template before saving version.');
    return;
  }
  await workflowApi.createVersion({
    templateId,
    ...(versionForm.versionNo ? { versionNo: versionForm.versionNo } : {}),
    nodeJson: JSON.stringify(nodes.value),
    edgeJson: JSON.stringify(edges()),
    conditionJson: JSON.stringify(nodes.value.filter((node) => node.type === 'CONDITION')),
    settingJson: versionForm.settingJson || '{}'
  });
  ElMessage.success('流程版本已保存');
}

async function publishVersion() {
  if (!publishVersionId.value) return;
  await workflowApi.publishVersion(publishVersionId.value);
  ElMessage.success('流程版本已发布');
}

async function startWorkflow() {
  if (!startForm.systemId || !startForm.tenantId || !startForm.moduleId || !startForm.recordId) {
    ElMessage.warning('systemId, tenantId, moduleId and recordId are required.');
    return;
  }
  await workflowApi.start(context.enrichPayload(startForm));
  ElMessage.success('流程已发起');
  startDialog.value = false;
}

function openHandle(row: AnyRecord, action: string) {
  handleTaskId.value = Number(row.taskId ?? row.id);
  Object.assign(handleForm, { action, comment: '', transferTo: undefined });
  handleDialog.value = true;
}

async function handleTask() {
  if (!handleTaskId.value) return;
  await workflowApi.handleTask(handleTaskId.value, { ...handleForm });
  ElMessage.success('任务已处理');
  handleDialog.value = false;
  loadTasks();
}

onMounted(() => {
  loadTemplates();
  loadTasks();
});
</script>

<style scoped>
.designer-form {
  margin-top: 14px;
}
</style>
