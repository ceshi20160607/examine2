<template>
  <div>
    <div class="page-title">
      <div>
        <h1>应用配置</h1>
        <p>创建应用、模块、字段、页面、菜单和字典；配置接口会自动带入当前 systemId、tenantId。</p>
      </div>
      <el-button :icon="Plus" type="primary" @click="openCreate">新建{{ current.title }}</el-button>
    </div>

    <el-tabs v-model="active" @tab-change="loadCurrent">
      <el-tab-pane v-for="item in resources" :key="item.key" :label="item.title" :name="item.key" />
    </el-tabs>

    <section class="content-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" clearable placeholder="名称/编码" style="width: 220px" />
        <el-input-number v-model="query.appId" placeholder="appId" :min="1" controls-position="right" />
        <el-input-number v-model="query.moduleId" placeholder="moduleId" :min="1" controls-position="right" />
        <el-button :icon="Search" @click="loadCurrent">查询</el-button>
        <el-button :icon="Refresh" @click="loadCurrent" />
      </div>

      <el-table :data="page.records" border height="560" v-loading="loading" :empty-text="tableEmptyText">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column v-for="column in current.columns" :key="column.prop" :prop="column.prop" :label="column.label" :min-width="column.width || 130">
          <template #default="{ row }">
            <status-tag v-if="column.status" :value="row[column.prop]" />
            <span v-else>{{ row[column.prop] ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="current.key === 'apps'" :icon="Upload" size="small" @click="publishApp(row)">发布</el-button>
            <el-button v-else-if="current.key === 'pages'" :icon="Upload" size="small" @click="publishPage(row)">发布</el-button>
            <el-button v-else-if="current.key === 'fields'" :icon="Plus" size="small" @click="openOption(row)">选项</el-button>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialog" :title="`新建${current.title}`" width="680px">
      <el-form :model="form" label-width="116px">
        <el-form-item v-for="field in current.form" :key="field.name" :label="field.label" :required="field.required">
          <el-input-number
            v-if="field.type === 'number'"
            :model-value="numberValue(field.name)"
            @update:model-value="(value) => (form[field.name] = value ?? undefined)"
            :min="0"
            controls-position="right"
            style="width: 100%"
          />
          <el-select v-else-if="field.type === 'select'" v-model="form[field.name]" clearable>
            <el-option v-for="opt in field.options" :key="opt" :label="opt" :value="opt" />
          </el-select>
          <el-input v-else-if="field.type === 'json'" v-model="form[field.name]" type="textarea" :rows="5" class="json-box" />
          <el-input v-else v-model="form[field.name]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveCurrent">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="optionDialog" title="新建字段选项" width="520px">
      <el-form :model="optionForm" label-width="100px">
        <el-form-item label="字段 ID"><el-input-number v-model="optionForm.fieldId" :min="1" /></el-form-item>
        <el-form-item label="选项标签"><el-input v-model="optionForm.optionLabel" /></el-form-item>
        <el-form-item label="选项值"><el-input v-model="optionForm.optionValue" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="optionForm.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="optionDialog = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveOption">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue';
import { configApi, type Payload, type Query } from '../api/modules';
import type { AnyRecord, PageResult } from '../api/types';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';

type Field = {
  name: string;
  label: string;
  type?: 'text' | 'number' | 'select' | 'json';
  required?: boolean;
  options?: string[];
};

type Resource = {
  key: string;
  title: string;
  columns: Array<{ prop: string; label: string; width?: number; status?: boolean }>;
  form: Field[];
  list: (query?: Query) => Promise<PageResult<AnyRecord>>;
  create: (data: Payload) => Promise<AnyRecord>;
};

const context = useContextStore();
const active = ref('apps');
const dialog = ref(false);
const optionDialog = ref(false);
const loading = ref(false);
const query = reactive({ keyword: '', appId: undefined as number | undefined, moduleId: undefined as number | undefined });
const form = reactive<Record<string, string | number | undefined>>({});
const optionForm = reactive({ fieldId: undefined as number | undefined, optionLabel: '', optionValue: '', sortOrder: 0 });
const page = reactive<PageResult<AnyRecord>>({ pageNo: 1, pageSize: 20, total: 0, records: [] });

const statusOptions = ['DRAFT', 'ENABLED', 'DISABLED', 'PUBLISHED'];
const fieldTypeOptions = [
  'TEXT',
  'NUMBER',
  'AMOUNT',
  'DATE',
  'DATETIME',
  'BOOLEAN',
  'SINGLE_SELECT',
  'MULTI_SELECT',
  'DICTIONARY',
  'DEPARTMENT',
  'MEMBER',
  'ATTACHMENT',
  'RELATION_RECORD',
  'SUB_TABLE',
  'AUTO_NUMBER',
  'FORMULA',
  'READONLY'
];

const contextFields: Field[] = [
  { name: 'systemId', label: '系统 ID', type: 'number', required: true },
  { name: 'tenantId', label: '租户 ID', type: 'number', required: true }
];

const resources: Resource[] = [
  {
    key: 'apps',
    title: '应用',
    list: configApi.apps,
    create: configApi.createApp,
    columns: [
      { prop: 'appName', label: '应用名称' },
      { prop: 'appCode', label: '编码' },
      { prop: 'currentVersion', label: '当前版本' },
      { prop: 'status', label: '状态', status: true }
    ],
    form: [...contextFields, { name: 'appName', label: '应用名称', required: true }, { name: 'appCode', label: '应用编码', required: true }, { name: 'sortOrder', label: '排序', type: 'number' }, { name: 'status', label: '状态', type: 'select', options: statusOptions }]
  },
  {
    key: 'modules',
    title: '模块',
    list: configApi.modules,
    create: configApi.createModule,
    columns: [
      { prop: 'appId', label: 'appId', width: 100 },
      { prop: 'moduleName', label: '模块名称' },
      { prop: 'moduleCode', label: '编码' },
      { prop: 'moduleType', label: '类型' },
      { prop: 'status', label: '状态', status: true }
    ],
    form: [...contextFields, { name: 'appId', label: '应用 ID', type: 'number', required: true }, { name: 'moduleName', label: '模块名称', required: true }, { name: 'moduleCode', label: '模块编码', required: true }, { name: 'moduleType', label: '模块类型' }, { name: 'status', label: '状态', type: 'select', options: statusOptions }]
  },
  {
    key: 'fields',
    title: '字段',
    list: configApi.fields,
    create: configApi.createField,
    columns: [
      { prop: 'moduleId', label: 'moduleId', width: 110 },
      { prop: 'fieldName', label: '字段名称' },
      { prop: 'fieldCode', label: '字段编码' },
      { prop: 'fieldType', label: '类型' },
      { prop: 'requiredFlag', label: '必填' },
      { prop: 'uniqueFlag', label: '唯一' }
    ],
    form: [...contextFields, { name: 'moduleId', label: '模块 ID', type: 'number', required: true }, { name: 'fieldName', label: '字段名称', required: true }, { name: 'fieldCode', label: '字段编码', required: true }, { name: 'fieldType', label: '字段类型', type: 'select', options: fieldTypeOptions, required: true }, { name: 'requiredFlag', label: '必填 0/1', type: 'number' }, { name: 'uniqueFlag', label: '唯一 0/1', type: 'number' }, { name: 'validateRule', label: '校验规则 JSON', type: 'json' }]
  },
  {
    key: 'pages',
    title: '页面',
    list: configApi.pages,
    create: configApi.createPage,
    columns: [
      { prop: 'moduleId', label: 'moduleId', width: 110 },
      { prop: 'pageName', label: '页面名称' },
      { prop: 'pageType', label: '类型' },
      { prop: 'status', label: '状态', status: true }
    ],
    form: [...contextFields, { name: 'moduleId', label: '模块 ID', type: 'number', required: true }, { name: 'pageName', label: '页面名称', required: true }, { name: 'pageType', label: '页面类型', type: 'select', options: ['LIST', 'FORM', 'DETAIL'], required: true }, { name: 'layoutJson', label: '布局 JSON', type: 'json' }, { name: 'blockJson', label: '页面块 JSON', type: 'json' }, { name: 'status', label: '状态', type: 'select', options: statusOptions }]
  },
  {
    key: 'menus',
    title: '菜单',
    list: configApi.menus,
    create: configApi.createMenu,
    columns: [
      { prop: 'menuName', label: '菜单名称' },
      { prop: 'menuCode', label: '编码' },
      { prop: 'appId', label: 'appId', width: 100 },
      { prop: 'moduleId', label: 'moduleId', width: 110 },
      { prop: 'status', label: '状态', status: true }
    ],
    form: [...contextFields, { name: 'menuName', label: '菜单名称', required: true }, { name: 'menuCode', label: '菜单编码', required: true }, { name: 'appId', label: '应用 ID', type: 'number' }, { name: 'moduleId', label: '模块 ID', type: 'number' }, { name: 'pageId', label: '页面 ID', type: 'number' }, { name: 'permissionCode', label: '权限标识' }, { name: 'sortOrder', label: '排序', type: 'number' }, { name: 'status', label: '状态', type: 'select', options: statusOptions }]
  },
  {
    key: 'dictionaries',
    title: '字典',
    list: configApi.dictionaries,
    create: configApi.createDictionary,
    columns: [
      { prop: 'dictName', label: '字典名称' },
      { prop: 'dictCode', label: '编码' },
      { prop: 'status', label: '状态', status: true }
    ],
    form: [...contextFields, { name: 'dictName', label: '字典名称', required: true }, { name: 'dictCode', label: '字典编码', required: true }, { name: 'status', label: '状态', type: 'select', options: statusOptions }]
  }
];

const current = computed(() => resources.find((item) => item.key === active.value) || resources[0]);
const requestBlockReason = computed(() => {
  if (!context.hasSystemContext) return 'Enter system context before loading configuration.';
  if (current.value.key === 'modules' && !query.appId) return 'Select appId before loading modules.';
  if (['fields', 'pages'].includes(current.value.key) && !query.moduleId) return 'Select moduleId before loading this resource.';
  return '';
});
const tableEmptyText = computed(() => requestBlockReason.value || 'No data');

function clearPage() {
  Object.assign(page, { pageNo: 1, pageSize: 20, total: 0, records: [] });
}

function defaultValue(field: Field) {
  if (field.name === 'systemId') return context.systemId;
  if (field.name === 'tenantId') return context.tenantId;
  if (field.name === 'status') return 'ENABLED';
  if (field.type === 'json') return '{}';
  return field.type === 'number' ? undefined : '';
}

function numberValue(name: string) {
  const value = form[name];
  return typeof value === 'number' ? value : undefined;
}

function openCreate() {
  if (!context.hasSystemContext) {
    ElMessage.warning('Enter system context before creating configuration.');
    return;
  }
  Object.keys(form).forEach((key) => delete form[key]);
  current.value.form.forEach((field) => {
    form[field.name] = defaultValue(field);
  });
  dialog.value = true;
}

async function loadCurrent() {
  if (requestBlockReason.value) {
    clearPage();
    ElMessage.warning(requestBlockReason.value);
    return;
  }
  loading.value = true;
  try {
    Object.assign(page, await current.value.list({ keyword: query.keyword, appId: query.appId, moduleId: query.moduleId }));
  } finally {
    loading.value = false;
  }
}

async function saveCurrent() {
  const missingRequired = current.value.form.find((field) => field.required && !form[field.name]);
  if (missingRequired) {
    ElMessage.warning(`${missingRequired.label} is required.`);
    return;
  }
  await current.value.create(context.enrichPayload(form) as Payload);
  ElMessage.success(`${current.value.title}已创建`);
  dialog.value = false;
  loadCurrent();
}

async function publishApp(row: AnyRecord) {
  await configApi.publishApp(Number(row.appId ?? row.id));
  ElMessage.success('应用版本已发布');
  loadCurrent();
}

async function publishPage(row: AnyRecord) {
  await configApi.publishPage(Number(row.pageId ?? row.id));
  ElMessage.success('页面已发布');
  loadCurrent();
}

function openOption(row: AnyRecord) {
  Object.assign(optionForm, { fieldId: Number(row.fieldId ?? row.id), optionLabel: '', optionValue: '', sortOrder: 0 });
  optionDialog.value = true;
}

async function saveOption() {
  await configApi.createFieldOption({ ...optionForm });
  ElMessage.success('字段选项已创建');
  optionDialog.value = false;
}

onMounted(loadCurrent);
</script>

<style scoped>
.muted {
  color: #94a3b8;
}
</style>
