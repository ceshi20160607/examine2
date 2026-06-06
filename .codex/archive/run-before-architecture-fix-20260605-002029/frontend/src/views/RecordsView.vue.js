import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ChatLineSquare, Check, Delete, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue';
import { recordApi } from '../api/modules';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';
const context = useContextStore();
const loading = ref(false);
const dialog = ref(false);
const detailDrawer = ref(false);
const commentDialog = ref(false);
const editingId = ref(null);
const commentRecordId = ref(null);
const commentText = ref('');
const valuesText = ref('[\n  { "fieldId": 1, "value": "示例值" }\n]');
const detail = ref({});
const query = reactive({ appId: undefined, moduleId: undefined, recordNo: '', status: '' });
const page = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const recordForm = reactive({
    systemId: undefined,
    tenantId: undefined,
    appId: undefined,
    moduleId: undefined,
    recordNo: '',
    recordStatus: 'DRAFT',
    appVersionId: undefined,
    configSnapshot: '{}'
});
const requestBlockReason = computed(() => {
    if (!context.hasSystemContext)
        return 'Enter system context before loading records.';
    if (!query.appId)
        return 'Select appId before loading records.';
    if (!query.moduleId)
        return 'Select moduleId before loading records.';
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
    }
    finally {
        loading.value = false;
    }
}
function openRecord(row) {
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
    let values = [];
    try {
        values = JSON.parse(valuesText.value || '[]');
    }
    catch {
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
    }
    else {
        await recordApi.create(payload);
    }
    ElMessage.success('记录已保存');
    dialog.value = false;
    load();
}
async function showDetail(row) {
    detail.value = await recordApi.detail(Number(row.id ?? row.recordId));
    detailDrawer.value = true;
}
function openComment(row) {
    commentRecordId.value = Number(row.id ?? row.recordId);
    commentText.value = '';
    commentDialog.value = true;
}
async function saveComment() {
    await recordApi.comment({ recordId: commentRecordId.value, commentText: commentText.value });
    ElMessage.success('评论已添加');
    commentDialog.value = false;
}
async function remove(row) {
    await ElMessageBox.confirm('确认软删除该记录？', '删除记录', { type: 'warning' });
    await recordApi.remove(Number(row.id ?? row.recordId));
    ElMessage.success('记录已删除');
    load();
}
onMounted(load);
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
    onClick: (...[$event]) => {
        __VLS_ctx.openRecord();
    }
};
__VLS_3.slots.default;
var __VLS_3;
__VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
    ...{ class: "content-panel" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "toolbar" },
});
const __VLS_8 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
    modelValue: (__VLS_ctx.query.appId),
    placeholder: "appId",
    min: (1),
    controlsPosition: "right",
}));
const __VLS_10 = __VLS_9({
    modelValue: (__VLS_ctx.query.appId),
    placeholder: "appId",
    min: (1),
    controlsPosition: "right",
}, ...__VLS_functionalComponentArgsRest(__VLS_9));
const __VLS_12 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent(__VLS_12, new __VLS_12({
    modelValue: (__VLS_ctx.query.moduleId),
    placeholder: "moduleId",
    min: (1),
    controlsPosition: "right",
}));
const __VLS_14 = __VLS_13({
    modelValue: (__VLS_ctx.query.moduleId),
    placeholder: "moduleId",
    min: (1),
    controlsPosition: "right",
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
const __VLS_16 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_17 = __VLS_asFunctionalComponent(__VLS_16, new __VLS_16({
    modelValue: (__VLS_ctx.query.recordNo),
    clearable: true,
    placeholder: "记录编号",
    ...{ style: {} },
}));
const __VLS_18 = __VLS_17({
    modelValue: (__VLS_ctx.query.recordNo),
    clearable: true,
    placeholder: "记录编号",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_17));
const __VLS_20 = {}.ElSelect;
/** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
// @ts-ignore
const __VLS_21 = __VLS_asFunctionalComponent(__VLS_20, new __VLS_20({
    modelValue: (__VLS_ctx.query.status),
    clearable: true,
    placeholder: "状态",
    ...{ style: {} },
}));
const __VLS_22 = __VLS_21({
    modelValue: (__VLS_ctx.query.status),
    clearable: true,
    placeholder: "状态",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_21));
__VLS_23.slots.default;
const __VLS_24 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent(__VLS_24, new __VLS_24({
    label: "草稿",
    value: "DRAFT",
}));
const __VLS_26 = __VLS_25({
    label: "草稿",
    value: "DRAFT",
}, ...__VLS_functionalComponentArgsRest(__VLS_25));
const __VLS_28 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_29 = __VLS_asFunctionalComponent(__VLS_28, new __VLS_28({
    label: "已提交",
    value: "SUBMITTED",
}));
const __VLS_30 = __VLS_29({
    label: "已提交",
    value: "SUBMITTED",
}, ...__VLS_functionalComponentArgsRest(__VLS_29));
const __VLS_32 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_33 = __VLS_asFunctionalComponent(__VLS_32, new __VLS_32({
    label: "已归档",
    value: "ARCHIVED",
}));
const __VLS_34 = __VLS_33({
    label: "已归档",
    value: "ARCHIVED",
}, ...__VLS_functionalComponentArgsRest(__VLS_33));
var __VLS_23;
const __VLS_36 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_37 = __VLS_asFunctionalComponent(__VLS_36, new __VLS_36({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Search),
}));
const __VLS_38 = __VLS_37({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Search),
}, ...__VLS_functionalComponentArgsRest(__VLS_37));
let __VLS_40;
let __VLS_41;
let __VLS_42;
const __VLS_43 = {
    onClick: (__VLS_ctx.load)
};
__VLS_39.slots.default;
var __VLS_39;
const __VLS_44 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_45 = __VLS_asFunctionalComponent(__VLS_44, new __VLS_44({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Refresh),
}));
const __VLS_46 = __VLS_45({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Refresh),
}, ...__VLS_functionalComponentArgsRest(__VLS_45));
let __VLS_48;
let __VLS_49;
let __VLS_50;
const __VLS_51 = {
    onClick: (__VLS_ctx.load)
};
var __VLS_47;
const __VLS_52 = {}.ElTable;
/** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
// @ts-ignore
const __VLS_53 = __VLS_asFunctionalComponent(__VLS_52, new __VLS_52({
    data: (__VLS_ctx.page.records),
    border: true,
    height: "560",
    emptyText: (__VLS_ctx.tableEmptyText),
}));
const __VLS_54 = __VLS_53({
    data: (__VLS_ctx.page.records),
    border: true,
    height: "560",
    emptyText: (__VLS_ctx.tableEmptyText),
}, ...__VLS_functionalComponentArgsRest(__VLS_53));
__VLS_asFunctionalDirective(__VLS_directives.vLoading)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.loading) }, null, null);
__VLS_55.slots.default;
const __VLS_56 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_57 = __VLS_asFunctionalComponent(__VLS_56, new __VLS_56({
    prop: "id",
    label: "ID",
    width: "80",
}));
const __VLS_58 = __VLS_57({
    prop: "id",
    label: "ID",
    width: "80",
}, ...__VLS_functionalComponentArgsRest(__VLS_57));
const __VLS_60 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_61 = __VLS_asFunctionalComponent(__VLS_60, new __VLS_60({
    prop: "recordNo",
    label: "记录编号",
    minWidth: "150",
}));
const __VLS_62 = __VLS_61({
    prop: "recordNo",
    label: "记录编号",
    minWidth: "150",
}, ...__VLS_functionalComponentArgsRest(__VLS_61));
const __VLS_64 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_65 = __VLS_asFunctionalComponent(__VLS_64, new __VLS_64({
    prop: "appId",
    label: "appId",
    width: "90",
}));
const __VLS_66 = __VLS_65({
    prop: "appId",
    label: "appId",
    width: "90",
}, ...__VLS_functionalComponentArgsRest(__VLS_65));
const __VLS_68 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_69 = __VLS_asFunctionalComponent(__VLS_68, new __VLS_68({
    prop: "moduleId",
    label: "moduleId",
    width: "110",
}));
const __VLS_70 = __VLS_69({
    prop: "moduleId",
    label: "moduleId",
    width: "110",
}, ...__VLS_functionalComponentArgsRest(__VLS_69));
const __VLS_72 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_73 = __VLS_asFunctionalComponent(__VLS_72, new __VLS_72({
    prop: "recordStatus",
    label: "记录状态",
    width: "120",
}));
const __VLS_74 = __VLS_73({
    prop: "recordStatus",
    label: "记录状态",
    width: "120",
}, ...__VLS_functionalComponentArgsRest(__VLS_73));
__VLS_75.slots.default;
{
    const { default: __VLS_thisSlot } = __VLS_75.slots;
    const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
    /** @type {[typeof StatusTag, ]} */ ;
    // @ts-ignore
    const __VLS_76 = __VLS_asFunctionalComponent(StatusTag, new StatusTag({
        value: (row.recordStatus),
    }));
    const __VLS_77 = __VLS_76({
        value: (row.recordStatus),
    }, ...__VLS_functionalComponentArgsRest(__VLS_76));
}
var __VLS_75;
const __VLS_79 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_80 = __VLS_asFunctionalComponent(__VLS_79, new __VLS_79({
    prop: "processStatus",
    label: "流程状态",
    width: "120",
}));
const __VLS_81 = __VLS_80({
    prop: "processStatus",
    label: "流程状态",
    width: "120",
}, ...__VLS_functionalComponentArgsRest(__VLS_80));
__VLS_82.slots.default;
{
    const { default: __VLS_thisSlot } = __VLS_82.slots;
    const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
    /** @type {[typeof StatusTag, ]} */ ;
    // @ts-ignore
    const __VLS_83 = __VLS_asFunctionalComponent(StatusTag, new StatusTag({
        value: (row.processStatus),
    }));
    const __VLS_84 = __VLS_83({
        value: (row.processStatus),
    }, ...__VLS_functionalComponentArgsRest(__VLS_83));
}
var __VLS_82;
const __VLS_86 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_87 = __VLS_asFunctionalComponent(__VLS_86, new __VLS_86({
    prop: "createdAt",
    label: "创建时间",
    minWidth: "160",
}));
const __VLS_88 = __VLS_87({
    prop: "createdAt",
    label: "创建时间",
    minWidth: "160",
}, ...__VLS_functionalComponentArgsRest(__VLS_87));
const __VLS_90 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_91 = __VLS_asFunctionalComponent(__VLS_90, new __VLS_90({
    label: "操作",
    width: "280",
    fixed: "right",
}));
const __VLS_92 = __VLS_91({
    label: "操作",
    width: "280",
    fixed: "right",
}, ...__VLS_functionalComponentArgsRest(__VLS_91));
__VLS_93.slots.default;
{
    const { default: __VLS_thisSlot } = __VLS_93.slots;
    const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
    const __VLS_94 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_95 = __VLS_asFunctionalComponent(__VLS_94, new __VLS_94({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.View),
        size: "small",
    }));
    const __VLS_96 = __VLS_95({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.View),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_95));
    let __VLS_98;
    let __VLS_99;
    let __VLS_100;
    const __VLS_101 = {
        onClick: (...[$event]) => {
            __VLS_ctx.showDetail(row);
        }
    };
    __VLS_97.slots.default;
    var __VLS_97;
    const __VLS_102 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_103 = __VLS_asFunctionalComponent(__VLS_102, new __VLS_102({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Edit),
        size: "small",
    }));
    const __VLS_104 = __VLS_103({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Edit),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_103));
    let __VLS_106;
    let __VLS_107;
    let __VLS_108;
    const __VLS_109 = {
        onClick: (...[$event]) => {
            __VLS_ctx.openRecord(row);
        }
    };
    __VLS_105.slots.default;
    var __VLS_105;
    const __VLS_110 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_111 = __VLS_asFunctionalComponent(__VLS_110, new __VLS_110({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.ChatLineSquare),
        size: "small",
    }));
    const __VLS_112 = __VLS_111({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.ChatLineSquare),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_111));
    let __VLS_114;
    let __VLS_115;
    let __VLS_116;
    const __VLS_117 = {
        onClick: (...[$event]) => {
            __VLS_ctx.openComment(row);
        }
    };
    __VLS_113.slots.default;
    var __VLS_113;
    const __VLS_118 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_119 = __VLS_asFunctionalComponent(__VLS_118, new __VLS_118({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Delete),
        size: "small",
        type: "danger",
    }));
    const __VLS_120 = __VLS_119({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Delete),
        size: "small",
        type: "danger",
    }, ...__VLS_functionalComponentArgsRest(__VLS_119));
    let __VLS_122;
    let __VLS_123;
    let __VLS_124;
    const __VLS_125 = {
        onClick: (...[$event]) => {
            __VLS_ctx.remove(row);
        }
    };
    __VLS_121.slots.default;
    var __VLS_121;
}
var __VLS_93;
var __VLS_55;
const __VLS_126 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_127 = __VLS_asFunctionalComponent(__VLS_126, new __VLS_126({
    modelValue: (__VLS_ctx.dialog),
    title: (__VLS_ctx.editingId ? '编辑记录' : '新增记录'),
    width: "760px",
}));
const __VLS_128 = __VLS_127({
    modelValue: (__VLS_ctx.dialog),
    title: (__VLS_ctx.editingId ? '编辑记录' : '新增记录'),
    width: "760px",
}, ...__VLS_functionalComponentArgsRest(__VLS_127));
__VLS_129.slots.default;
const __VLS_130 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_131 = __VLS_asFunctionalComponent(__VLS_130, new __VLS_130({
    model: (__VLS_ctx.recordForm),
    labelWidth: "118px",
}));
const __VLS_132 = __VLS_131({
    model: (__VLS_ctx.recordForm),
    labelWidth: "118px",
}, ...__VLS_functionalComponentArgsRest(__VLS_131));
__VLS_133.slots.default;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "form-grid" },
});
const __VLS_134 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_135 = __VLS_asFunctionalComponent(__VLS_134, new __VLS_134({
    label: "系统 ID",
    required: true,
}));
const __VLS_136 = __VLS_135({
    label: "系统 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_135));
__VLS_137.slots.default;
const __VLS_138 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_139 = __VLS_asFunctionalComponent(__VLS_138, new __VLS_138({
    modelValue: (__VLS_ctx.recordForm.systemId),
    min: (1),
}));
const __VLS_140 = __VLS_139({
    modelValue: (__VLS_ctx.recordForm.systemId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_139));
var __VLS_137;
const __VLS_142 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_143 = __VLS_asFunctionalComponent(__VLS_142, new __VLS_142({
    label: "租户 ID",
    required: true,
}));
const __VLS_144 = __VLS_143({
    label: "租户 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_143));
__VLS_145.slots.default;
const __VLS_146 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_147 = __VLS_asFunctionalComponent(__VLS_146, new __VLS_146({
    modelValue: (__VLS_ctx.recordForm.tenantId),
    min: (1),
}));
const __VLS_148 = __VLS_147({
    modelValue: (__VLS_ctx.recordForm.tenantId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_147));
var __VLS_145;
const __VLS_150 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_151 = __VLS_asFunctionalComponent(__VLS_150, new __VLS_150({
    label: "应用 ID",
    required: true,
}));
const __VLS_152 = __VLS_151({
    label: "应用 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_151));
__VLS_153.slots.default;
const __VLS_154 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_155 = __VLS_asFunctionalComponent(__VLS_154, new __VLS_154({
    modelValue: (__VLS_ctx.recordForm.appId),
    min: (1),
}));
const __VLS_156 = __VLS_155({
    modelValue: (__VLS_ctx.recordForm.appId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_155));
var __VLS_153;
const __VLS_158 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_159 = __VLS_asFunctionalComponent(__VLS_158, new __VLS_158({
    label: "模块 ID",
    required: true,
}));
const __VLS_160 = __VLS_159({
    label: "模块 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_159));
__VLS_161.slots.default;
const __VLS_162 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_163 = __VLS_asFunctionalComponent(__VLS_162, new __VLS_162({
    modelValue: (__VLS_ctx.recordForm.moduleId),
    min: (1),
}));
const __VLS_164 = __VLS_163({
    modelValue: (__VLS_ctx.recordForm.moduleId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_163));
var __VLS_161;
const __VLS_166 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_167 = __VLS_asFunctionalComponent(__VLS_166, new __VLS_166({
    label: "记录编号",
}));
const __VLS_168 = __VLS_167({
    label: "记录编号",
}, ...__VLS_functionalComponentArgsRest(__VLS_167));
__VLS_169.slots.default;
const __VLS_170 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_171 = __VLS_asFunctionalComponent(__VLS_170, new __VLS_170({
    modelValue: (__VLS_ctx.recordForm.recordNo),
    placeholder: "为空时后端自动生成",
}));
const __VLS_172 = __VLS_171({
    modelValue: (__VLS_ctx.recordForm.recordNo),
    placeholder: "为空时后端自动生成",
}, ...__VLS_functionalComponentArgsRest(__VLS_171));
var __VLS_169;
const __VLS_174 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_175 = __VLS_asFunctionalComponent(__VLS_174, new __VLS_174({
    label: "记录状态",
}));
const __VLS_176 = __VLS_175({
    label: "记录状态",
}, ...__VLS_functionalComponentArgsRest(__VLS_175));
__VLS_177.slots.default;
const __VLS_178 = {}.ElSelect;
/** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
// @ts-ignore
const __VLS_179 = __VLS_asFunctionalComponent(__VLS_178, new __VLS_178({
    modelValue: (__VLS_ctx.recordForm.recordStatus),
}));
const __VLS_180 = __VLS_179({
    modelValue: (__VLS_ctx.recordForm.recordStatus),
}, ...__VLS_functionalComponentArgsRest(__VLS_179));
__VLS_181.slots.default;
const __VLS_182 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_183 = __VLS_asFunctionalComponent(__VLS_182, new __VLS_182({
    label: "草稿",
    value: "DRAFT",
}));
const __VLS_184 = __VLS_183({
    label: "草稿",
    value: "DRAFT",
}, ...__VLS_functionalComponentArgsRest(__VLS_183));
const __VLS_186 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_187 = __VLS_asFunctionalComponent(__VLS_186, new __VLS_186({
    label: "提交",
    value: "SUBMITTED",
}));
const __VLS_188 = __VLS_187({
    label: "提交",
    value: "SUBMITTED",
}, ...__VLS_functionalComponentArgsRest(__VLS_187));
var __VLS_181;
var __VLS_177;
const __VLS_190 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_191 = __VLS_asFunctionalComponent(__VLS_190, new __VLS_190({
    label: "配置版本",
    ...{ class: "full" },
}));
const __VLS_192 = __VLS_191({
    label: "配置版本",
    ...{ class: "full" },
}, ...__VLS_functionalComponentArgsRest(__VLS_191));
__VLS_193.slots.default;
const __VLS_194 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_195 = __VLS_asFunctionalComponent(__VLS_194, new __VLS_194({
    modelValue: (__VLS_ctx.recordForm.appVersionId),
    min: (1),
}));
const __VLS_196 = __VLS_195({
    modelValue: (__VLS_ctx.recordForm.appVersionId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_195));
var __VLS_193;
const __VLS_198 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_199 = __VLS_asFunctionalComponent(__VLS_198, new __VLS_198({
    label: "配置快照",
    ...{ class: "full" },
}));
const __VLS_200 = __VLS_199({
    label: "配置快照",
    ...{ class: "full" },
}, ...__VLS_functionalComponentArgsRest(__VLS_199));
__VLS_201.slots.default;
const __VLS_202 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_203 = __VLS_asFunctionalComponent(__VLS_202, new __VLS_202({
    modelValue: (__VLS_ctx.recordForm.configSnapshot),
    type: "textarea",
    rows: (4),
    ...{ class: "json-box" },
}));
const __VLS_204 = __VLS_203({
    modelValue: (__VLS_ctx.recordForm.configSnapshot),
    type: "textarea",
    rows: (4),
    ...{ class: "json-box" },
}, ...__VLS_functionalComponentArgsRest(__VLS_203));
var __VLS_201;
const __VLS_206 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_207 = __VLS_asFunctionalComponent(__VLS_206, new __VLS_206({
    label: "字段值数组",
    ...{ class: "full" },
}));
const __VLS_208 = __VLS_207({
    label: "字段值数组",
    ...{ class: "full" },
}, ...__VLS_functionalComponentArgsRest(__VLS_207));
__VLS_209.slots.default;
const __VLS_210 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_211 = __VLS_asFunctionalComponent(__VLS_210, new __VLS_210({
    modelValue: (__VLS_ctx.valuesText),
    type: "textarea",
    rows: (8),
    ...{ class: "json-box" },
}));
const __VLS_212 = __VLS_211({
    modelValue: (__VLS_ctx.valuesText),
    type: "textarea",
    rows: (8),
    ...{ class: "json-box" },
}, ...__VLS_functionalComponentArgsRest(__VLS_211));
var __VLS_209;
var __VLS_133;
{
    const { footer: __VLS_thisSlot } = __VLS_129.slots;
    const __VLS_214 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_215 = __VLS_asFunctionalComponent(__VLS_214, new __VLS_214({
        ...{ 'onClick': {} },
    }));
    const __VLS_216 = __VLS_215({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_215));
    let __VLS_218;
    let __VLS_219;
    let __VLS_220;
    const __VLS_221 = {
        onClick: (...[$event]) => {
            __VLS_ctx.dialog = false;
        }
    };
    __VLS_217.slots.default;
    var __VLS_217;
    const __VLS_222 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_223 = __VLS_asFunctionalComponent(__VLS_222, new __VLS_222({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_224 = __VLS_223({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_223));
    let __VLS_226;
    let __VLS_227;
    let __VLS_228;
    const __VLS_229 = {
        onClick: (__VLS_ctx.save)
    };
    __VLS_225.slots.default;
    var __VLS_225;
}
var __VLS_129;
const __VLS_230 = {}.ElDrawer;
/** @type {[typeof __VLS_components.ElDrawer, typeof __VLS_components.elDrawer, typeof __VLS_components.ElDrawer, typeof __VLS_components.elDrawer, ]} */ ;
// @ts-ignore
const __VLS_231 = __VLS_asFunctionalComponent(__VLS_230, new __VLS_230({
    modelValue: (__VLS_ctx.detailDrawer),
    title: "记录详情",
    size: "520px",
}));
const __VLS_232 = __VLS_231({
    modelValue: (__VLS_ctx.detailDrawer),
    title: "记录详情",
    size: "520px",
}, ...__VLS_functionalComponentArgsRest(__VLS_231));
__VLS_233.slots.default;
const __VLS_234 = {}.ElDescriptions;
/** @type {[typeof __VLS_components.ElDescriptions, typeof __VLS_components.elDescriptions, typeof __VLS_components.ElDescriptions, typeof __VLS_components.elDescriptions, ]} */ ;
// @ts-ignore
const __VLS_235 = __VLS_asFunctionalComponent(__VLS_234, new __VLS_234({
    column: (1),
    border: true,
}));
const __VLS_236 = __VLS_235({
    column: (1),
    border: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_235));
__VLS_237.slots.default;
for (const [value, key] of __VLS_getVForSourceType((__VLS_ctx.detail))) {
    const __VLS_238 = {}.ElDescriptionsItem;
    /** @type {[typeof __VLS_components.ElDescriptionsItem, typeof __VLS_components.elDescriptionsItem, typeof __VLS_components.ElDescriptionsItem, typeof __VLS_components.elDescriptionsItem, ]} */ ;
    // @ts-ignore
    const __VLS_239 = __VLS_asFunctionalComponent(__VLS_238, new __VLS_238({
        key: (String(key)),
        label: (String(key)),
    }));
    const __VLS_240 = __VLS_239({
        key: (String(key)),
        label: (String(key)),
    }, ...__VLS_functionalComponentArgsRest(__VLS_239));
    __VLS_241.slots.default;
    if (typeof value === 'object') {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.pre, __VLS_intrinsicElements.pre)({});
        (JSON.stringify(value, null, 2));
    }
    else {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        (value ?? '-');
    }
    var __VLS_241;
}
var __VLS_237;
var __VLS_233;
const __VLS_242 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_243 = __VLS_asFunctionalComponent(__VLS_242, new __VLS_242({
    modelValue: (__VLS_ctx.commentDialog),
    title: "添加评论",
    width: "520px",
}));
const __VLS_244 = __VLS_243({
    modelValue: (__VLS_ctx.commentDialog),
    title: "添加评论",
    width: "520px",
}, ...__VLS_functionalComponentArgsRest(__VLS_243));
__VLS_245.slots.default;
const __VLS_246 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_247 = __VLS_asFunctionalComponent(__VLS_246, new __VLS_246({
    modelValue: (__VLS_ctx.commentText),
    type: "textarea",
    rows: (5),
    placeholder: "审批、协作或记录说明",
}));
const __VLS_248 = __VLS_247({
    modelValue: (__VLS_ctx.commentText),
    type: "textarea",
    rows: (5),
    placeholder: "审批、协作或记录说明",
}, ...__VLS_functionalComponentArgsRest(__VLS_247));
{
    const { footer: __VLS_thisSlot } = __VLS_245.slots;
    const __VLS_250 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_251 = __VLS_asFunctionalComponent(__VLS_250, new __VLS_250({
        ...{ 'onClick': {} },
    }));
    const __VLS_252 = __VLS_251({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_251));
    let __VLS_254;
    let __VLS_255;
    let __VLS_256;
    const __VLS_257 = {
        onClick: (...[$event]) => {
            __VLS_ctx.commentDialog = false;
        }
    };
    __VLS_253.slots.default;
    var __VLS_253;
    const __VLS_258 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_259 = __VLS_asFunctionalComponent(__VLS_258, new __VLS_258({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_260 = __VLS_259({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_259));
    let __VLS_262;
    let __VLS_263;
    let __VLS_264;
    const __VLS_265 = {
        onClick: (__VLS_ctx.saveComment)
    };
    __VLS_261.slots.default;
    var __VLS_261;
}
var __VLS_245;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['form-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['full']} */ ;
/** @type {__VLS_StyleScopedClasses['full']} */ ;
/** @type {__VLS_StyleScopedClasses['json-box']} */ ;
/** @type {__VLS_StyleScopedClasses['full']} */ ;
/** @type {__VLS_StyleScopedClasses['json-box']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            ChatLineSquare: ChatLineSquare,
            Check: Check,
            Delete: Delete,
            Edit: Edit,
            Plus: Plus,
            Refresh: Refresh,
            Search: Search,
            View: View,
            StatusTag: StatusTag,
            loading: loading,
            dialog: dialog,
            detailDrawer: detailDrawer,
            commentDialog: commentDialog,
            editingId: editingId,
            commentText: commentText,
            valuesText: valuesText,
            detail: detail,
            query: query,
            page: page,
            recordForm: recordForm,
            tableEmptyText: tableEmptyText,
            load: load,
            openRecord: openRecord,
            save: save,
            showDetail: showDetail,
            openComment: openComment,
            saveComment: saveComment,
            remove: remove,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
