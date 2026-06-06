import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue';
import { configApi } from '../api/modules';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';
const context = useContextStore();
const active = ref('apps');
const dialog = ref(false);
const optionDialog = ref(false);
const loading = ref(false);
const query = reactive({ keyword: '', appId: undefined, moduleId: undefined });
const form = reactive({});
const optionForm = reactive({ fieldId: undefined, optionLabel: '', optionValue: '', sortOrder: 0 });
const page = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
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
const contextFields = [
    { name: 'systemId', label: '系统 ID', type: 'number', required: true },
    { name: 'tenantId', label: '租户 ID', type: 'number', required: true }
];
const resources = [
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
    if (!context.hasSystemContext)
        return 'Enter system context before loading configuration.';
    if (current.value.key === 'modules' && !query.appId)
        return 'Select appId before loading modules.';
    if (['fields', 'pages'].includes(current.value.key) && !query.moduleId)
        return 'Select moduleId before loading this resource.';
    return '';
});
const tableEmptyText = computed(() => requestBlockReason.value || 'No data');
function clearPage() {
    Object.assign(page, { pageNo: 1, pageSize: 20, total: 0, records: [] });
}
function defaultValue(field) {
    if (field.name === 'systemId')
        return context.systemId;
    if (field.name === 'tenantId')
        return context.tenantId;
    if (field.name === 'status')
        return 'ENABLED';
    if (field.type === 'json')
        return '{}';
    return field.type === 'number' ? undefined : '';
}
function numberValue(name) {
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
    }
    finally {
        loading.value = false;
    }
}
async function saveCurrent() {
    const missingRequired = current.value.form.find((field) => field.required && !form[field.name]);
    if (missingRequired) {
        ElMessage.warning(`${missingRequired.label} is required.`);
        return;
    }
    await current.value.create(context.enrichPayload(form));
    ElMessage.success(`${current.value.title}已创建`);
    dialog.value = false;
    loadCurrent();
}
async function publishApp(row) {
    await configApi.publishApp(Number(row.appId ?? row.id));
    ElMessage.success('应用版本已发布');
    loadCurrent();
}
async function publishPage(row) {
    await configApi.publishPage(Number(row.pageId ?? row.id));
    ElMessage.success('页面已发布');
    loadCurrent();
}
function openOption(row) {
    Object.assign(optionForm, { fieldId: Number(row.fieldId ?? row.id), optionLabel: '', optionValue: '', sortOrder: 0 });
    optionDialog.value = true;
}
async function saveOption() {
    await configApi.createFieldOption({ ...optionForm });
    ElMessage.success('字段选项已创建');
    optionDialog.value = false;
}
onMounted(loadCurrent);
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
// CSS variable injection 
// CSS variable injection end 
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "page-title" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h1, __VLS_intrinsicElements.h1)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
const __VLS_0 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Plus),
    type: "primary",
}));
const __VLS_2 = __VLS_1({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Plus),
    type: "primary",
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
let __VLS_4;
let __VLS_5;
let __VLS_6;
const __VLS_7 = {
    onClick: (__VLS_ctx.openCreate)
};
__VLS_3.slots.default;
(__VLS_ctx.current.title);
var __VLS_3;
const __VLS_8 = {}.ElTabs;
/** @type {[typeof __VLS_components.ElTabs, typeof __VLS_components.elTabs, typeof __VLS_components.ElTabs, typeof __VLS_components.elTabs, ]} */ ;
// @ts-ignore
const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
    ...{ 'onTabChange': {} },
    modelValue: (__VLS_ctx.active),
}));
const __VLS_10 = __VLS_9({
    ...{ 'onTabChange': {} },
    modelValue: (__VLS_ctx.active),
}, ...__VLS_functionalComponentArgsRest(__VLS_9));
let __VLS_12;
let __VLS_13;
let __VLS_14;
const __VLS_15 = {
    onTabChange: (__VLS_ctx.loadCurrent)
};
__VLS_11.slots.default;
for (const [item] of __VLS_getVForSourceType((__VLS_ctx.resources))) {
    const __VLS_16 = {}.ElTabPane;
    /** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
    // @ts-ignore
    const __VLS_17 = __VLS_asFunctionalComponent(__VLS_16, new __VLS_16({
        key: (item.key),
        label: (item.title),
        name: (item.key),
    }));
    const __VLS_18 = __VLS_17({
        key: (item.key),
        label: (item.title),
        name: (item.key),
    }, ...__VLS_functionalComponentArgsRest(__VLS_17));
}
var __VLS_11;
__VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
    ...{ class: "content-panel" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "toolbar" },
});
const __VLS_20 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_21 = __VLS_asFunctionalComponent(__VLS_20, new __VLS_20({
    modelValue: (__VLS_ctx.query.keyword),
    clearable: true,
    placeholder: "名称/编码",
    ...{ style: {} },
}));
const __VLS_22 = __VLS_21({
    modelValue: (__VLS_ctx.query.keyword),
    clearable: true,
    placeholder: "名称/编码",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_21));
const __VLS_24 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent(__VLS_24, new __VLS_24({
    modelValue: (__VLS_ctx.query.appId),
    placeholder: "appId",
    min: (1),
    controlsPosition: "right",
}));
const __VLS_26 = __VLS_25({
    modelValue: (__VLS_ctx.query.appId),
    placeholder: "appId",
    min: (1),
    controlsPosition: "right",
}, ...__VLS_functionalComponentArgsRest(__VLS_25));
const __VLS_28 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_29 = __VLS_asFunctionalComponent(__VLS_28, new __VLS_28({
    modelValue: (__VLS_ctx.query.moduleId),
    placeholder: "moduleId",
    min: (1),
    controlsPosition: "right",
}));
const __VLS_30 = __VLS_29({
    modelValue: (__VLS_ctx.query.moduleId),
    placeholder: "moduleId",
    min: (1),
    controlsPosition: "right",
}, ...__VLS_functionalComponentArgsRest(__VLS_29));
const __VLS_32 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_33 = __VLS_asFunctionalComponent(__VLS_32, new __VLS_32({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Search),
}));
const __VLS_34 = __VLS_33({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Search),
}, ...__VLS_functionalComponentArgsRest(__VLS_33));
let __VLS_36;
let __VLS_37;
let __VLS_38;
const __VLS_39 = {
    onClick: (__VLS_ctx.loadCurrent)
};
__VLS_35.slots.default;
var __VLS_35;
const __VLS_40 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_41 = __VLS_asFunctionalComponent(__VLS_40, new __VLS_40({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Refresh),
}));
const __VLS_42 = __VLS_41({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Refresh),
}, ...__VLS_functionalComponentArgsRest(__VLS_41));
let __VLS_44;
let __VLS_45;
let __VLS_46;
const __VLS_47 = {
    onClick: (__VLS_ctx.loadCurrent)
};
var __VLS_43;
const __VLS_48 = {}.ElTable;
/** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
// @ts-ignore
const __VLS_49 = __VLS_asFunctionalComponent(__VLS_48, new __VLS_48({
    data: (__VLS_ctx.page.records),
    border: true,
    height: "560",
    emptyText: (__VLS_ctx.tableEmptyText),
}));
const __VLS_50 = __VLS_49({
    data: (__VLS_ctx.page.records),
    border: true,
    height: "560",
    emptyText: (__VLS_ctx.tableEmptyText),
}, ...__VLS_functionalComponentArgsRest(__VLS_49));
__VLS_asFunctionalDirective(__VLS_directives.vLoading)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.loading) }, null, null);
__VLS_51.slots.default;
const __VLS_52 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_53 = __VLS_asFunctionalComponent(__VLS_52, new __VLS_52({
    prop: "id",
    label: "ID",
    width: "80",
}));
const __VLS_54 = __VLS_53({
    prop: "id",
    label: "ID",
    width: "80",
}, ...__VLS_functionalComponentArgsRest(__VLS_53));
for (const [column] of __VLS_getVForSourceType((__VLS_ctx.current.columns))) {
    const __VLS_56 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_57 = __VLS_asFunctionalComponent(__VLS_56, new __VLS_56({
        key: (column.prop),
        prop: (column.prop),
        label: (column.label),
        minWidth: (column.width || 130),
    }));
    const __VLS_58 = __VLS_57({
        key: (column.prop),
        prop: (column.prop),
        label: (column.label),
        minWidth: (column.width || 130),
    }, ...__VLS_functionalComponentArgsRest(__VLS_57));
    __VLS_59.slots.default;
    {
        const { default: __VLS_thisSlot } = __VLS_59.slots;
        const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
        if (column.status) {
            /** @type {[typeof StatusTag, ]} */ ;
            // @ts-ignore
            const __VLS_60 = __VLS_asFunctionalComponent(StatusTag, new StatusTag({
                value: (row[column.prop]),
            }));
            const __VLS_61 = __VLS_60({
                value: (row[column.prop]),
            }, ...__VLS_functionalComponentArgsRest(__VLS_60));
        }
        else {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
            (row[column.prop] ?? '-');
        }
    }
    var __VLS_59;
}
const __VLS_63 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_64 = __VLS_asFunctionalComponent(__VLS_63, new __VLS_63({
    label: "操作",
    width: "160",
    fixed: "right",
}));
const __VLS_65 = __VLS_64({
    label: "操作",
    width: "160",
    fixed: "right",
}, ...__VLS_functionalComponentArgsRest(__VLS_64));
__VLS_66.slots.default;
{
    const { default: __VLS_thisSlot } = __VLS_66.slots;
    const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
    if (__VLS_ctx.current.key === 'apps') {
        const __VLS_67 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_68 = __VLS_asFunctionalComponent(__VLS_67, new __VLS_67({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Upload),
            size: "small",
        }));
        const __VLS_69 = __VLS_68({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Upload),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_68));
        let __VLS_71;
        let __VLS_72;
        let __VLS_73;
        const __VLS_74 = {
            onClick: (...[$event]) => {
                if (!(__VLS_ctx.current.key === 'apps'))
                    return;
                __VLS_ctx.publishApp(row);
            }
        };
        __VLS_70.slots.default;
        var __VLS_70;
    }
    else if (__VLS_ctx.current.key === 'pages') {
        const __VLS_75 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_76 = __VLS_asFunctionalComponent(__VLS_75, new __VLS_75({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Upload),
            size: "small",
        }));
        const __VLS_77 = __VLS_76({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Upload),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_76));
        let __VLS_79;
        let __VLS_80;
        let __VLS_81;
        const __VLS_82 = {
            onClick: (...[$event]) => {
                if (!!(__VLS_ctx.current.key === 'apps'))
                    return;
                if (!(__VLS_ctx.current.key === 'pages'))
                    return;
                __VLS_ctx.publishPage(row);
            }
        };
        __VLS_78.slots.default;
        var __VLS_78;
    }
    else if (__VLS_ctx.current.key === 'fields') {
        const __VLS_83 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_84 = __VLS_asFunctionalComponent(__VLS_83, new __VLS_83({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Plus),
            size: "small",
        }));
        const __VLS_85 = __VLS_84({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Plus),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_84));
        let __VLS_87;
        let __VLS_88;
        let __VLS_89;
        const __VLS_90 = {
            onClick: (...[$event]) => {
                if (!!(__VLS_ctx.current.key === 'apps'))
                    return;
                if (!!(__VLS_ctx.current.key === 'pages'))
                    return;
                if (!(__VLS_ctx.current.key === 'fields'))
                    return;
                __VLS_ctx.openOption(row);
            }
        };
        __VLS_86.slots.default;
        var __VLS_86;
    }
    else {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "muted" },
        });
    }
}
var __VLS_66;
var __VLS_51;
const __VLS_91 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_92 = __VLS_asFunctionalComponent(__VLS_91, new __VLS_91({
    modelValue: (__VLS_ctx.dialog),
    title: (`新建${__VLS_ctx.current.title}`),
    width: "680px",
}));
const __VLS_93 = __VLS_92({
    modelValue: (__VLS_ctx.dialog),
    title: (`新建${__VLS_ctx.current.title}`),
    width: "680px",
}, ...__VLS_functionalComponentArgsRest(__VLS_92));
__VLS_94.slots.default;
const __VLS_95 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_96 = __VLS_asFunctionalComponent(__VLS_95, new __VLS_95({
    model: (__VLS_ctx.form),
    labelWidth: "116px",
}));
const __VLS_97 = __VLS_96({
    model: (__VLS_ctx.form),
    labelWidth: "116px",
}, ...__VLS_functionalComponentArgsRest(__VLS_96));
__VLS_98.slots.default;
for (const [field] of __VLS_getVForSourceType((__VLS_ctx.current.form))) {
    const __VLS_99 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_100 = __VLS_asFunctionalComponent(__VLS_99, new __VLS_99({
        key: (field.name),
        label: (field.label),
        required: (field.required),
    }));
    const __VLS_101 = __VLS_100({
        key: (field.name),
        label: (field.label),
        required: (field.required),
    }, ...__VLS_functionalComponentArgsRest(__VLS_100));
    __VLS_102.slots.default;
    if (field.type === 'number') {
        const __VLS_103 = {}.ElInputNumber;
        /** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
        // @ts-ignore
        const __VLS_104 = __VLS_asFunctionalComponent(__VLS_103, new __VLS_103({
            ...{ 'onUpdate:modelValue': {} },
            modelValue: (__VLS_ctx.numberValue(field.name)),
            min: (0),
            controlsPosition: "right",
            ...{ style: {} },
        }));
        const __VLS_105 = __VLS_104({
            ...{ 'onUpdate:modelValue': {} },
            modelValue: (__VLS_ctx.numberValue(field.name)),
            min: (0),
            controlsPosition: "right",
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_104));
        let __VLS_107;
        let __VLS_108;
        let __VLS_109;
        const __VLS_110 = {
            'onUpdate:modelValue': ((value) => (__VLS_ctx.form[field.name] = value ?? undefined))
        };
        var __VLS_106;
    }
    else if (field.type === 'select') {
        const __VLS_111 = {}.ElSelect;
        /** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
        // @ts-ignore
        const __VLS_112 = __VLS_asFunctionalComponent(__VLS_111, new __VLS_111({
            modelValue: (__VLS_ctx.form[field.name]),
            clearable: true,
        }));
        const __VLS_113 = __VLS_112({
            modelValue: (__VLS_ctx.form[field.name]),
            clearable: true,
        }, ...__VLS_functionalComponentArgsRest(__VLS_112));
        __VLS_114.slots.default;
        for (const [opt] of __VLS_getVForSourceType((field.options))) {
            const __VLS_115 = {}.ElOption;
            /** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
            // @ts-ignore
            const __VLS_116 = __VLS_asFunctionalComponent(__VLS_115, new __VLS_115({
                key: (opt),
                label: (opt),
                value: (opt),
            }));
            const __VLS_117 = __VLS_116({
                key: (opt),
                label: (opt),
                value: (opt),
            }, ...__VLS_functionalComponentArgsRest(__VLS_116));
        }
        var __VLS_114;
    }
    else if (field.type === 'json') {
        const __VLS_119 = {}.ElInput;
        /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
        // @ts-ignore
        const __VLS_120 = __VLS_asFunctionalComponent(__VLS_119, new __VLS_119({
            modelValue: (__VLS_ctx.form[field.name]),
            type: "textarea",
            rows: (5),
            ...{ class: "json-box" },
        }));
        const __VLS_121 = __VLS_120({
            modelValue: (__VLS_ctx.form[field.name]),
            type: "textarea",
            rows: (5),
            ...{ class: "json-box" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_120));
    }
    else {
        const __VLS_123 = {}.ElInput;
        /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
        // @ts-ignore
        const __VLS_124 = __VLS_asFunctionalComponent(__VLS_123, new __VLS_123({
            modelValue: (__VLS_ctx.form[field.name]),
        }));
        const __VLS_125 = __VLS_124({
            modelValue: (__VLS_ctx.form[field.name]),
        }, ...__VLS_functionalComponentArgsRest(__VLS_124));
    }
    var __VLS_102;
}
var __VLS_98;
{
    const { footer: __VLS_thisSlot } = __VLS_94.slots;
    const __VLS_127 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_128 = __VLS_asFunctionalComponent(__VLS_127, new __VLS_127({
        ...{ 'onClick': {} },
    }));
    const __VLS_129 = __VLS_128({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_128));
    let __VLS_131;
    let __VLS_132;
    let __VLS_133;
    const __VLS_134 = {
        onClick: (...[$event]) => {
            __VLS_ctx.dialog = false;
        }
    };
    __VLS_130.slots.default;
    var __VLS_130;
    const __VLS_135 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_136 = __VLS_asFunctionalComponent(__VLS_135, new __VLS_135({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_137 = __VLS_136({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_136));
    let __VLS_139;
    let __VLS_140;
    let __VLS_141;
    const __VLS_142 = {
        onClick: (__VLS_ctx.saveCurrent)
    };
    __VLS_138.slots.default;
    var __VLS_138;
}
var __VLS_94;
const __VLS_143 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_144 = __VLS_asFunctionalComponent(__VLS_143, new __VLS_143({
    modelValue: (__VLS_ctx.optionDialog),
    title: "新建字段选项",
    width: "520px",
}));
const __VLS_145 = __VLS_144({
    modelValue: (__VLS_ctx.optionDialog),
    title: "新建字段选项",
    width: "520px",
}, ...__VLS_functionalComponentArgsRest(__VLS_144));
__VLS_146.slots.default;
const __VLS_147 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_148 = __VLS_asFunctionalComponent(__VLS_147, new __VLS_147({
    model: (__VLS_ctx.optionForm),
    labelWidth: "100px",
}));
const __VLS_149 = __VLS_148({
    model: (__VLS_ctx.optionForm),
    labelWidth: "100px",
}, ...__VLS_functionalComponentArgsRest(__VLS_148));
__VLS_150.slots.default;
const __VLS_151 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_152 = __VLS_asFunctionalComponent(__VLS_151, new __VLS_151({
    label: "字段 ID",
}));
const __VLS_153 = __VLS_152({
    label: "字段 ID",
}, ...__VLS_functionalComponentArgsRest(__VLS_152));
__VLS_154.slots.default;
const __VLS_155 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_156 = __VLS_asFunctionalComponent(__VLS_155, new __VLS_155({
    modelValue: (__VLS_ctx.optionForm.fieldId),
    min: (1),
}));
const __VLS_157 = __VLS_156({
    modelValue: (__VLS_ctx.optionForm.fieldId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_156));
var __VLS_154;
const __VLS_159 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_160 = __VLS_asFunctionalComponent(__VLS_159, new __VLS_159({
    label: "选项标签",
}));
const __VLS_161 = __VLS_160({
    label: "选项标签",
}, ...__VLS_functionalComponentArgsRest(__VLS_160));
__VLS_162.slots.default;
const __VLS_163 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_164 = __VLS_asFunctionalComponent(__VLS_163, new __VLS_163({
    modelValue: (__VLS_ctx.optionForm.optionLabel),
}));
const __VLS_165 = __VLS_164({
    modelValue: (__VLS_ctx.optionForm.optionLabel),
}, ...__VLS_functionalComponentArgsRest(__VLS_164));
var __VLS_162;
const __VLS_167 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_168 = __VLS_asFunctionalComponent(__VLS_167, new __VLS_167({
    label: "选项值",
}));
const __VLS_169 = __VLS_168({
    label: "选项值",
}, ...__VLS_functionalComponentArgsRest(__VLS_168));
__VLS_170.slots.default;
const __VLS_171 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_172 = __VLS_asFunctionalComponent(__VLS_171, new __VLS_171({
    modelValue: (__VLS_ctx.optionForm.optionValue),
}));
const __VLS_173 = __VLS_172({
    modelValue: (__VLS_ctx.optionForm.optionValue),
}, ...__VLS_functionalComponentArgsRest(__VLS_172));
var __VLS_170;
const __VLS_175 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_176 = __VLS_asFunctionalComponent(__VLS_175, new __VLS_175({
    label: "排序",
}));
const __VLS_177 = __VLS_176({
    label: "排序",
}, ...__VLS_functionalComponentArgsRest(__VLS_176));
__VLS_178.slots.default;
const __VLS_179 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_180 = __VLS_asFunctionalComponent(__VLS_179, new __VLS_179({
    modelValue: (__VLS_ctx.optionForm.sortOrder),
    min: (0),
}));
const __VLS_181 = __VLS_180({
    modelValue: (__VLS_ctx.optionForm.sortOrder),
    min: (0),
}, ...__VLS_functionalComponentArgsRest(__VLS_180));
var __VLS_178;
var __VLS_150;
{
    const { footer: __VLS_thisSlot } = __VLS_146.slots;
    const __VLS_183 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_184 = __VLS_asFunctionalComponent(__VLS_183, new __VLS_183({
        ...{ 'onClick': {} },
    }));
    const __VLS_185 = __VLS_184({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_184));
    let __VLS_187;
    let __VLS_188;
    let __VLS_189;
    const __VLS_190 = {
        onClick: (...[$event]) => {
            __VLS_ctx.optionDialog = false;
        }
    };
    __VLS_186.slots.default;
    var __VLS_186;
    const __VLS_191 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_192 = __VLS_asFunctionalComponent(__VLS_191, new __VLS_191({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_193 = __VLS_192({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_192));
    let __VLS_195;
    let __VLS_196;
    let __VLS_197;
    const __VLS_198 = {
        onClick: (__VLS_ctx.saveOption)
    };
    __VLS_194.slots.default;
    var __VLS_194;
}
var __VLS_146;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['muted']} */ ;
/** @type {__VLS_StyleScopedClasses['json-box']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            Check: Check,
            Plus: Plus,
            Refresh: Refresh,
            Search: Search,
            Upload: Upload,
            StatusTag: StatusTag,
            active: active,
            dialog: dialog,
            optionDialog: optionDialog,
            loading: loading,
            query: query,
            form: form,
            optionForm: optionForm,
            page: page,
            resources: resources,
            current: current,
            tableEmptyText: tableEmptyText,
            numberValue: numberValue,
            openCreate: openCreate,
            loadCurrent: loadCurrent,
            saveCurrent: saveCurrent,
            publishApp: publishApp,
            publishPage: publishPage,
            openOption: openOption,
            saveOption: saveOption,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
