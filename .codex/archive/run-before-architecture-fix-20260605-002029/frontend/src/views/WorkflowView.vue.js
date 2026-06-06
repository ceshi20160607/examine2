import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, CircleCheck, CircleClose, CloseBold, Connection, Edit, Plus, Refresh, Right, Search, Upload, VideoPlay } from '@element-plus/icons-vue';
import { workflowApi } from '../api/modules';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';
const context = useContextStore();
const tab = ref('templates');
const templateDialog = ref(false);
const startDialog = ref(false);
const handleDialog = ref(false);
const selectedTemplate = ref(null);
const publishVersionId = ref();
const handleTaskId = ref(null);
const loading = reactive({ templates: false, tasks: false });
const templateQuery = reactive({ moduleId: undefined });
const taskQuery = reactive({ moduleId: undefined, status: '' });
const templates = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const tasks = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const nodes = ref([
    { id: 'start', type: 'START', name: '开始' },
    { id: 'approve-1', type: 'APPROVE', name: '部门审批' },
    { id: 'end', type: 'END', name: '结束' }
]);
const versionForm = reactive({ versionNo: undefined, settingJson: '{ "timeoutHours": 24 }' });
const templateForm = reactive({ systemId: undefined, tenantId: undefined, moduleId: undefined, templateName: '', status: 'DRAFT' });
const startForm = reactive({ systemId: undefined, tenantId: undefined, moduleId: undefined, recordId: undefined });
const handleForm = reactive({ action: 'APPROVE', comment: '', transferTo: undefined });
const templateBlockReason = computed(() => {
    if (!context.hasSystemContext)
        return 'Enter system context before loading workflow templates.';
    if (!templateQuery.moduleId)
        return 'Select moduleId before loading workflow templates.';
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
function addNode(type) {
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
function selectTemplate(row) {
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
    }
    finally {
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
    }
    finally {
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
    if (!publishVersionId.value)
        return;
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
function openHandle(row, action) {
    handleTaskId.value = Number(row.taskId ?? row.id);
    Object.assign(handleForm, { action, comment: '', transferTo: undefined });
    handleDialog.value = true;
}
async function handleTask() {
    if (!handleTaskId.value)
        return;
    await workflowApi.handleTask(handleTaskId.value, { ...handleForm });
    ElMessage.success('任务已处理');
    handleDialog.value = false;
    loadTasks();
}
onMounted(() => {
    loadTemplates();
    loadTasks();
});
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
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "header-actions" },
});
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
    onClick: (__VLS_ctx.openTemplate)
};
__VLS_3.slots.default;
var __VLS_3;
const __VLS_8 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.VideoPlay),
    type: "primary",
}));
const __VLS_10 = __VLS_9({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.VideoPlay),
    type: "primary",
}, ...__VLS_functionalComponentArgsRest(__VLS_9));
let __VLS_12;
let __VLS_13;
let __VLS_14;
const __VLS_15 = {
    onClick: (...[$event]) => {
        __VLS_ctx.startDialog = true;
    }
};
__VLS_11.slots.default;
var __VLS_11;
const __VLS_16 = {}.ElTabs;
/** @type {[typeof __VLS_components.ElTabs, typeof __VLS_components.elTabs, typeof __VLS_components.ElTabs, typeof __VLS_components.elTabs, ]} */ ;
// @ts-ignore
const __VLS_17 = __VLS_asFunctionalComponent(__VLS_16, new __VLS_16({
    ...{ 'onTabChange': {} },
    modelValue: (__VLS_ctx.tab),
}));
const __VLS_18 = __VLS_17({
    ...{ 'onTabChange': {} },
    modelValue: (__VLS_ctx.tab),
}, ...__VLS_functionalComponentArgsRest(__VLS_17));
let __VLS_20;
let __VLS_21;
let __VLS_22;
const __VLS_23 = {
    onTabChange: (...[$event]) => {
        __VLS_ctx.tab === 'tasks' ? __VLS_ctx.loadTasks() : __VLS_ctx.loadTemplates();
    }
};
__VLS_19.slots.default;
const __VLS_24 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent(__VLS_24, new __VLS_24({
    label: "流程模板",
    name: "templates",
}));
const __VLS_26 = __VLS_25({
    label: "流程模板",
    name: "templates",
}, ...__VLS_functionalComponentArgsRest(__VLS_25));
const __VLS_28 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_29 = __VLS_asFunctionalComponent(__VLS_28, new __VLS_28({
    label: "待办任务",
    name: "tasks",
}));
const __VLS_30 = __VLS_29({
    label: "待办任务",
    name: "tasks",
}, ...__VLS_functionalComponentArgsRest(__VLS_29));
var __VLS_19;
if (__VLS_ctx.tab === 'templates') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "split-grid" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "toolbar" },
    });
    const __VLS_32 = {}.ElInputNumber;
    /** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
    // @ts-ignore
    const __VLS_33 = __VLS_asFunctionalComponent(__VLS_32, new __VLS_32({
        modelValue: (__VLS_ctx.templateQuery.moduleId),
        placeholder: "moduleId",
        min: (1),
        controlsPosition: "right",
    }));
    const __VLS_34 = __VLS_33({
        modelValue: (__VLS_ctx.templateQuery.moduleId),
        placeholder: "moduleId",
        min: (1),
        controlsPosition: "right",
    }, ...__VLS_functionalComponentArgsRest(__VLS_33));
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
        onClick: (__VLS_ctx.loadTemplates)
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
        onClick: (__VLS_ctx.loadTemplates)
    };
    var __VLS_47;
    const __VLS_52 = {}.ElTable;
    /** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
    // @ts-ignore
    const __VLS_53 = __VLS_asFunctionalComponent(__VLS_52, new __VLS_52({
        ...{ 'onCurrentChange': {} },
        data: (__VLS_ctx.templates.records),
        border: true,
        height: "560",
        emptyText: (__VLS_ctx.templateEmptyText),
        highlightCurrentRow: true,
    }));
    const __VLS_54 = __VLS_53({
        ...{ 'onCurrentChange': {} },
        data: (__VLS_ctx.templates.records),
        border: true,
        height: "560",
        emptyText: (__VLS_ctx.templateEmptyText),
        highlightCurrentRow: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_53));
    let __VLS_56;
    let __VLS_57;
    let __VLS_58;
    const __VLS_59 = {
        onCurrentChange: (__VLS_ctx.selectTemplate)
    };
    __VLS_asFunctionalDirective(__VLS_directives.vLoading)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.loading.templates) }, null, null);
    __VLS_55.slots.default;
    const __VLS_60 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_61 = __VLS_asFunctionalComponent(__VLS_60, new __VLS_60({
        prop: "id",
        label: "ID",
        width: "80",
    }));
    const __VLS_62 = __VLS_61({
        prop: "id",
        label: "ID",
        width: "80",
    }, ...__VLS_functionalComponentArgsRest(__VLS_61));
    const __VLS_64 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_65 = __VLS_asFunctionalComponent(__VLS_64, new __VLS_64({
        prop: "templateName",
        label: "模板名称",
        minWidth: "160",
    }));
    const __VLS_66 = __VLS_65({
        prop: "templateName",
        label: "模板名称",
        minWidth: "160",
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
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_73 = __VLS_asFunctionalComponent(__VLS_72, new __VLS_72({
        prop: "currentVersion",
        label: "版本",
        width: "100",
    }));
    const __VLS_74 = __VLS_73({
        prop: "currentVersion",
        label: "版本",
        width: "100",
    }, ...__VLS_functionalComponentArgsRest(__VLS_73));
    const __VLS_76 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_77 = __VLS_asFunctionalComponent(__VLS_76, new __VLS_76({
        prop: "status",
        label: "状态",
        width: "110",
    }));
    const __VLS_78 = __VLS_77({
        prop: "status",
        label: "状态",
        width: "110",
    }, ...__VLS_functionalComponentArgsRest(__VLS_77));
    __VLS_79.slots.default;
    {
        const { default: __VLS_thisSlot } = __VLS_79.slots;
        const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
        /** @type {[typeof StatusTag, ]} */ ;
        // @ts-ignore
        const __VLS_80 = __VLS_asFunctionalComponent(StatusTag, new StatusTag({
            value: (row.status),
        }));
        const __VLS_81 = __VLS_80({
            value: (row.status),
        }, ...__VLS_functionalComponentArgsRest(__VLS_80));
    }
    var __VLS_79;
    const __VLS_83 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_84 = __VLS_asFunctionalComponent(__VLS_83, new __VLS_83({
        label: "操作",
        width: "150",
    }));
    const __VLS_85 = __VLS_84({
        label: "操作",
        width: "150",
    }, ...__VLS_functionalComponentArgsRest(__VLS_84));
    __VLS_86.slots.default;
    {
        const { default: __VLS_thisSlot } = __VLS_86.slots;
        const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
        const __VLS_87 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_88 = __VLS_asFunctionalComponent(__VLS_87, new __VLS_87({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Edit),
            size: "small",
        }));
        const __VLS_89 = __VLS_88({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Edit),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_88));
        let __VLS_91;
        let __VLS_92;
        let __VLS_93;
        const __VLS_94 = {
            onClick: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'templates'))
                    return;
                __VLS_ctx.selectTemplate(row);
            }
        };
        __VLS_90.slots.default;
        var __VLS_90;
    }
    var __VLS_86;
    var __VLS_55;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "page-title" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h1, __VLS_intrinsicElements.h1)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
    (__VLS_ctx.selectedTemplate ? `模板 ID ${__VLS_ctx.selectedTemplate.id ?? __VLS_ctx.selectedTemplate.templateId}` : '选择模板后创建版本');
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "toolbar" },
    });
    const __VLS_95 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_96 = __VLS_asFunctionalComponent(__VLS_95, new __VLS_95({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Plus),
    }));
    const __VLS_97 = __VLS_96({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Plus),
    }, ...__VLS_functionalComponentArgsRest(__VLS_96));
    let __VLS_99;
    let __VLS_100;
    let __VLS_101;
    const __VLS_102 = {
        onClick: (...[$event]) => {
            if (!(__VLS_ctx.tab === 'templates'))
                return;
            __VLS_ctx.addNode('APPROVE');
        }
    };
    __VLS_98.slots.default;
    var __VLS_98;
    const __VLS_103 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_104 = __VLS_asFunctionalComponent(__VLS_103, new __VLS_103({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Plus),
    }));
    const __VLS_105 = __VLS_104({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Plus),
    }, ...__VLS_functionalComponentArgsRest(__VLS_104));
    let __VLS_107;
    let __VLS_108;
    let __VLS_109;
    const __VLS_110 = {
        onClick: (...[$event]) => {
            if (!(__VLS_ctx.tab === 'templates'))
                return;
            __VLS_ctx.addNode('CONDITION');
        }
    };
    __VLS_106.slots.default;
    var __VLS_106;
    const __VLS_111 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_112 = __VLS_asFunctionalComponent(__VLS_111, new __VLS_111({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Plus),
    }));
    const __VLS_113 = __VLS_112({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Plus),
    }, ...__VLS_functionalComponentArgsRest(__VLS_112));
    let __VLS_115;
    let __VLS_116;
    let __VLS_117;
    const __VLS_118 = {
        onClick: (...[$event]) => {
            if (!(__VLS_ctx.tab === 'templates'))
                return;
            __VLS_ctx.addNode('CC');
        }
    };
    __VLS_114.slots.default;
    var __VLS_114;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "workflow-canvas" },
    });
    for (const [node, index] of __VLS_getVForSourceType((__VLS_ctx.nodes))) {
        (node.id);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "workflow-node" },
        });
        const __VLS_119 = {}.ElIcon;
        /** @type {[typeof __VLS_components.ElIcon, typeof __VLS_components.elIcon, typeof __VLS_components.ElIcon, typeof __VLS_components.elIcon, ]} */ ;
        // @ts-ignore
        const __VLS_120 = __VLS_asFunctionalComponent(__VLS_119, new __VLS_119({}));
        const __VLS_121 = __VLS_120({}, ...__VLS_functionalComponentArgsRest(__VLS_120));
        __VLS_122.slots.default;
        const __VLS_123 = {}.Connection;
        /** @type {[typeof __VLS_components.Connection, ]} */ ;
        // @ts-ignore
        const __VLS_124 = __VLS_asFunctionalComponent(__VLS_123, new __VLS_123({}));
        const __VLS_125 = __VLS_124({}, ...__VLS_functionalComponentArgsRest(__VLS_124));
        var __VLS_122;
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        (node.name);
        if (index < __VLS_ctx.nodes.length - 1) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
                ...{ class: "workflow-edge" },
            });
        }
    }
    const __VLS_127 = {}.ElForm;
    /** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
    // @ts-ignore
    const __VLS_128 = __VLS_asFunctionalComponent(__VLS_127, new __VLS_127({
        model: (__VLS_ctx.versionForm),
        labelWidth: "90px",
        ...{ class: "designer-form" },
    }));
    const __VLS_129 = __VLS_128({
        model: (__VLS_ctx.versionForm),
        labelWidth: "90px",
        ...{ class: "designer-form" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_128));
    __VLS_130.slots.default;
    const __VLS_131 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_132 = __VLS_asFunctionalComponent(__VLS_131, new __VLS_131({
        label: "版本号",
    }));
    const __VLS_133 = __VLS_132({
        label: "版本号",
    }, ...__VLS_functionalComponentArgsRest(__VLS_132));
    __VLS_134.slots.default;
    const __VLS_135 = {}.ElInputNumber;
    /** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
    // @ts-ignore
    const __VLS_136 = __VLS_asFunctionalComponent(__VLS_135, new __VLS_135({
        modelValue: (__VLS_ctx.versionForm.versionNo),
        min: (1),
        controlsPosition: "right",
        placeholder: "空值由后端生成",
    }));
    const __VLS_137 = __VLS_136({
        modelValue: (__VLS_ctx.versionForm.versionNo),
        min: (1),
        controlsPosition: "right",
        placeholder: "空值由后端生成",
    }, ...__VLS_functionalComponentArgsRest(__VLS_136));
    var __VLS_134;
    const __VLS_139 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_140 = __VLS_asFunctionalComponent(__VLS_139, new __VLS_139({
        label: "设置 JSON",
    }));
    const __VLS_141 = __VLS_140({
        label: "设置 JSON",
    }, ...__VLS_functionalComponentArgsRest(__VLS_140));
    __VLS_142.slots.default;
    const __VLS_143 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_144 = __VLS_asFunctionalComponent(__VLS_143, new __VLS_143({
        modelValue: (__VLS_ctx.versionForm.settingJson),
        type: "textarea",
        rows: (4),
        ...{ class: "json-box" },
    }));
    const __VLS_145 = __VLS_144({
        modelValue: (__VLS_ctx.versionForm.settingJson),
        type: "textarea",
        rows: (4),
        ...{ class: "json-box" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_144));
    var __VLS_142;
    var __VLS_130;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "toolbar" },
    });
    const __VLS_147 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_148 = __VLS_asFunctionalComponent(__VLS_147, new __VLS_147({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
        disabled: (!__VLS_ctx.selectedTemplate),
    }));
    const __VLS_149 = __VLS_148({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
        disabled: (!__VLS_ctx.selectedTemplate),
    }, ...__VLS_functionalComponentArgsRest(__VLS_148));
    let __VLS_151;
    let __VLS_152;
    let __VLS_153;
    const __VLS_154 = {
        onClick: (__VLS_ctx.saveVersion)
    };
    __VLS_150.slots.default;
    var __VLS_150;
    const __VLS_155 = {}.ElInputNumber;
    /** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
    // @ts-ignore
    const __VLS_156 = __VLS_asFunctionalComponent(__VLS_155, new __VLS_155({
        modelValue: (__VLS_ctx.publishVersionId),
        placeholder: "versionId",
        min: (1),
        controlsPosition: "right",
    }));
    const __VLS_157 = __VLS_156({
        modelValue: (__VLS_ctx.publishVersionId),
        placeholder: "versionId",
        min: (1),
        controlsPosition: "right",
    }, ...__VLS_functionalComponentArgsRest(__VLS_156));
    const __VLS_159 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_160 = __VLS_asFunctionalComponent(__VLS_159, new __VLS_159({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Upload),
    }));
    const __VLS_161 = __VLS_160({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Upload),
    }, ...__VLS_functionalComponentArgsRest(__VLS_160));
    let __VLS_163;
    let __VLS_164;
    let __VLS_165;
    const __VLS_166 = {
        onClick: (__VLS_ctx.publishVersion)
    };
    __VLS_162.slots.default;
    var __VLS_162;
}
else {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "toolbar" },
    });
    const __VLS_167 = {}.ElInputNumber;
    /** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
    // @ts-ignore
    const __VLS_168 = __VLS_asFunctionalComponent(__VLS_167, new __VLS_167({
        modelValue: (__VLS_ctx.taskQuery.moduleId),
        placeholder: "moduleId",
        min: (1),
        controlsPosition: "right",
    }));
    const __VLS_169 = __VLS_168({
        modelValue: (__VLS_ctx.taskQuery.moduleId),
        placeholder: "moduleId",
        min: (1),
        controlsPosition: "right",
    }, ...__VLS_functionalComponentArgsRest(__VLS_168));
    const __VLS_171 = {}.ElSelect;
    /** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
    // @ts-ignore
    const __VLS_172 = __VLS_asFunctionalComponent(__VLS_171, new __VLS_171({
        modelValue: (__VLS_ctx.taskQuery.status),
        clearable: true,
        placeholder: "任务状态",
        ...{ style: {} },
    }));
    const __VLS_173 = __VLS_172({
        modelValue: (__VLS_ctx.taskQuery.status),
        clearable: true,
        placeholder: "任务状态",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_172));
    __VLS_174.slots.default;
    const __VLS_175 = {}.ElOption;
    /** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
    // @ts-ignore
    const __VLS_176 = __VLS_asFunctionalComponent(__VLS_175, new __VLS_175({
        label: "待处理",
        value: "PENDING",
    }));
    const __VLS_177 = __VLS_176({
        label: "待处理",
        value: "PENDING",
    }, ...__VLS_functionalComponentArgsRest(__VLS_176));
    const __VLS_179 = {}.ElOption;
    /** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
    // @ts-ignore
    const __VLS_180 = __VLS_asFunctionalComponent(__VLS_179, new __VLS_179({
        label: "已同意",
        value: "APPROVED",
    }));
    const __VLS_181 = __VLS_180({
        label: "已同意",
        value: "APPROVED",
    }, ...__VLS_functionalComponentArgsRest(__VLS_180));
    const __VLS_183 = {}.ElOption;
    /** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
    // @ts-ignore
    const __VLS_184 = __VLS_asFunctionalComponent(__VLS_183, new __VLS_183({
        label: "已拒绝",
        value: "REJECTED",
    }));
    const __VLS_185 = __VLS_184({
        label: "已拒绝",
        value: "REJECTED",
    }, ...__VLS_functionalComponentArgsRest(__VLS_184));
    var __VLS_174;
    const __VLS_187 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_188 = __VLS_asFunctionalComponent(__VLS_187, new __VLS_187({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Search),
    }));
    const __VLS_189 = __VLS_188({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Search),
    }, ...__VLS_functionalComponentArgsRest(__VLS_188));
    let __VLS_191;
    let __VLS_192;
    let __VLS_193;
    const __VLS_194 = {
        onClick: (__VLS_ctx.loadTasks)
    };
    __VLS_190.slots.default;
    var __VLS_190;
    const __VLS_195 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_196 = __VLS_asFunctionalComponent(__VLS_195, new __VLS_195({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Refresh),
    }));
    const __VLS_197 = __VLS_196({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Refresh),
    }, ...__VLS_functionalComponentArgsRest(__VLS_196));
    let __VLS_199;
    let __VLS_200;
    let __VLS_201;
    const __VLS_202 = {
        onClick: (__VLS_ctx.loadTasks)
    };
    var __VLS_198;
    const __VLS_203 = {}.ElTable;
    /** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
    // @ts-ignore
    const __VLS_204 = __VLS_asFunctionalComponent(__VLS_203, new __VLS_203({
        data: (__VLS_ctx.tasks.records),
        border: true,
        height: "560",
        emptyText: (__VLS_ctx.taskEmptyText),
    }));
    const __VLS_205 = __VLS_204({
        data: (__VLS_ctx.tasks.records),
        border: true,
        height: "560",
        emptyText: (__VLS_ctx.taskEmptyText),
    }, ...__VLS_functionalComponentArgsRest(__VLS_204));
    __VLS_asFunctionalDirective(__VLS_directives.vLoading)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.loading.tasks) }, null, null);
    __VLS_206.slots.default;
    const __VLS_207 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_208 = __VLS_asFunctionalComponent(__VLS_207, new __VLS_207({
        prop: "id",
        label: "ID",
        width: "80",
    }));
    const __VLS_209 = __VLS_208({
        prop: "id",
        label: "ID",
        width: "80",
    }, ...__VLS_functionalComponentArgsRest(__VLS_208));
    const __VLS_211 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_212 = __VLS_asFunctionalComponent(__VLS_211, new __VLS_211({
        prop: "instanceId",
        label: "实例 ID",
        width: "110",
    }));
    const __VLS_213 = __VLS_212({
        prop: "instanceId",
        label: "实例 ID",
        width: "110",
    }, ...__VLS_functionalComponentArgsRest(__VLS_212));
    const __VLS_215 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_216 = __VLS_asFunctionalComponent(__VLS_215, new __VLS_215({
        prop: "moduleId",
        label: "moduleId",
        width: "110",
    }));
    const __VLS_217 = __VLS_216({
        prop: "moduleId",
        label: "moduleId",
        width: "110",
    }, ...__VLS_functionalComponentArgsRest(__VLS_216));
    const __VLS_219 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_220 = __VLS_asFunctionalComponent(__VLS_219, new __VLS_219({
        prop: "taskName",
        label: "任务名称",
        minWidth: "160",
    }));
    const __VLS_221 = __VLS_220({
        prop: "taskName",
        label: "任务名称",
        minWidth: "160",
    }, ...__VLS_functionalComponentArgsRest(__VLS_220));
    const __VLS_223 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_224 = __VLS_asFunctionalComponent(__VLS_223, new __VLS_223({
        prop: "status",
        label: "状态",
        width: "120",
    }));
    const __VLS_225 = __VLS_224({
        prop: "status",
        label: "状态",
        width: "120",
    }, ...__VLS_functionalComponentArgsRest(__VLS_224));
    __VLS_226.slots.default;
    {
        const { default: __VLS_thisSlot } = __VLS_226.slots;
        const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
        /** @type {[typeof StatusTag, ]} */ ;
        // @ts-ignore
        const __VLS_227 = __VLS_asFunctionalComponent(StatusTag, new StatusTag({
            value: (row.status),
        }));
        const __VLS_228 = __VLS_227({
            value: (row.status),
        }, ...__VLS_functionalComponentArgsRest(__VLS_227));
    }
    var __VLS_226;
    const __VLS_230 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_231 = __VLS_asFunctionalComponent(__VLS_230, new __VLS_230({
        prop: "createdAt",
        label: "创建时间",
        minWidth: "160",
    }));
    const __VLS_232 = __VLS_231({
        prop: "createdAt",
        label: "创建时间",
        minWidth: "160",
    }, ...__VLS_functionalComponentArgsRest(__VLS_231));
    const __VLS_234 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_235 = __VLS_asFunctionalComponent(__VLS_234, new __VLS_234({
        label: "处理",
        width: "310",
        fixed: "right",
    }));
    const __VLS_236 = __VLS_235({
        label: "处理",
        width: "310",
        fixed: "right",
    }, ...__VLS_functionalComponentArgsRest(__VLS_235));
    __VLS_237.slots.default;
    {
        const { default: __VLS_thisSlot } = __VLS_237.slots;
        const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
        const __VLS_238 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_239 = __VLS_asFunctionalComponent(__VLS_238, new __VLS_238({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.CircleCheck),
            size: "small",
        }));
        const __VLS_240 = __VLS_239({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.CircleCheck),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_239));
        let __VLS_242;
        let __VLS_243;
        let __VLS_244;
        const __VLS_245 = {
            onClick: (...[$event]) => {
                if (!!(__VLS_ctx.tab === 'templates'))
                    return;
                __VLS_ctx.openHandle(row, 'APPROVE');
            }
        };
        __VLS_241.slots.default;
        var __VLS_241;
        const __VLS_246 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_247 = __VLS_asFunctionalComponent(__VLS_246, new __VLS_246({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.CircleClose),
            size: "small",
        }));
        const __VLS_248 = __VLS_247({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.CircleClose),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_247));
        let __VLS_250;
        let __VLS_251;
        let __VLS_252;
        const __VLS_253 = {
            onClick: (...[$event]) => {
                if (!!(__VLS_ctx.tab === 'templates'))
                    return;
                __VLS_ctx.openHandle(row, 'REJECT');
            }
        };
        __VLS_249.slots.default;
        var __VLS_249;
        const __VLS_254 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_255 = __VLS_asFunctionalComponent(__VLS_254, new __VLS_254({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Right),
            size: "small",
        }));
        const __VLS_256 = __VLS_255({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Right),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_255));
        let __VLS_258;
        let __VLS_259;
        let __VLS_260;
        const __VLS_261 = {
            onClick: (...[$event]) => {
                if (!!(__VLS_ctx.tab === 'templates'))
                    return;
                __VLS_ctx.openHandle(row, 'TRANSFER');
            }
        };
        __VLS_257.slots.default;
        var __VLS_257;
        const __VLS_262 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_263 = __VLS_asFunctionalComponent(__VLS_262, new __VLS_262({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.CloseBold),
            size: "small",
            type: "danger",
        }));
        const __VLS_264 = __VLS_263({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.CloseBold),
            size: "small",
            type: "danger",
        }, ...__VLS_functionalComponentArgsRest(__VLS_263));
        let __VLS_266;
        let __VLS_267;
        let __VLS_268;
        const __VLS_269 = {
            onClick: (...[$event]) => {
                if (!!(__VLS_ctx.tab === 'templates'))
                    return;
                __VLS_ctx.openHandle(row, 'TERMINATE');
            }
        };
        __VLS_265.slots.default;
        var __VLS_265;
    }
    var __VLS_237;
    var __VLS_206;
}
const __VLS_270 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_271 = __VLS_asFunctionalComponent(__VLS_270, new __VLS_270({
    modelValue: (__VLS_ctx.templateDialog),
    title: "新建流程模板",
    width: "560px",
}));
const __VLS_272 = __VLS_271({
    modelValue: (__VLS_ctx.templateDialog),
    title: "新建流程模板",
    width: "560px",
}, ...__VLS_functionalComponentArgsRest(__VLS_271));
__VLS_273.slots.default;
const __VLS_274 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_275 = __VLS_asFunctionalComponent(__VLS_274, new __VLS_274({
    model: (__VLS_ctx.templateForm),
    labelWidth: "108px",
}));
const __VLS_276 = __VLS_275({
    model: (__VLS_ctx.templateForm),
    labelWidth: "108px",
}, ...__VLS_functionalComponentArgsRest(__VLS_275));
__VLS_277.slots.default;
const __VLS_278 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_279 = __VLS_asFunctionalComponent(__VLS_278, new __VLS_278({
    label: "系统 ID",
    required: true,
}));
const __VLS_280 = __VLS_279({
    label: "系统 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_279));
__VLS_281.slots.default;
const __VLS_282 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_283 = __VLS_asFunctionalComponent(__VLS_282, new __VLS_282({
    modelValue: (__VLS_ctx.templateForm.systemId),
    min: (1),
}));
const __VLS_284 = __VLS_283({
    modelValue: (__VLS_ctx.templateForm.systemId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_283));
var __VLS_281;
const __VLS_286 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_287 = __VLS_asFunctionalComponent(__VLS_286, new __VLS_286({
    label: "租户 ID",
    required: true,
}));
const __VLS_288 = __VLS_287({
    label: "租户 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_287));
__VLS_289.slots.default;
const __VLS_290 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_291 = __VLS_asFunctionalComponent(__VLS_290, new __VLS_290({
    modelValue: (__VLS_ctx.templateForm.tenantId),
    min: (1),
}));
const __VLS_292 = __VLS_291({
    modelValue: (__VLS_ctx.templateForm.tenantId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_291));
var __VLS_289;
const __VLS_294 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_295 = __VLS_asFunctionalComponent(__VLS_294, new __VLS_294({
    label: "模块 ID",
    required: true,
}));
const __VLS_296 = __VLS_295({
    label: "模块 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_295));
__VLS_297.slots.default;
const __VLS_298 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_299 = __VLS_asFunctionalComponent(__VLS_298, new __VLS_298({
    modelValue: (__VLS_ctx.templateForm.moduleId),
    min: (1),
}));
const __VLS_300 = __VLS_299({
    modelValue: (__VLS_ctx.templateForm.moduleId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_299));
var __VLS_297;
const __VLS_302 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_303 = __VLS_asFunctionalComponent(__VLS_302, new __VLS_302({
    label: "模板名称",
    required: true,
}));
const __VLS_304 = __VLS_303({
    label: "模板名称",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_303));
__VLS_305.slots.default;
const __VLS_306 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_307 = __VLS_asFunctionalComponent(__VLS_306, new __VLS_306({
    modelValue: (__VLS_ctx.templateForm.templateName),
}));
const __VLS_308 = __VLS_307({
    modelValue: (__VLS_ctx.templateForm.templateName),
}, ...__VLS_functionalComponentArgsRest(__VLS_307));
var __VLS_305;
const __VLS_310 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_311 = __VLS_asFunctionalComponent(__VLS_310, new __VLS_310({
    label: "状态",
}));
const __VLS_312 = __VLS_311({
    label: "状态",
}, ...__VLS_functionalComponentArgsRest(__VLS_311));
__VLS_313.slots.default;
const __VLS_314 = {}.ElSelect;
/** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
// @ts-ignore
const __VLS_315 = __VLS_asFunctionalComponent(__VLS_314, new __VLS_314({
    modelValue: (__VLS_ctx.templateForm.status),
}));
const __VLS_316 = __VLS_315({
    modelValue: (__VLS_ctx.templateForm.status),
}, ...__VLS_functionalComponentArgsRest(__VLS_315));
__VLS_317.slots.default;
const __VLS_318 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_319 = __VLS_asFunctionalComponent(__VLS_318, new __VLS_318({
    label: "草稿",
    value: "DRAFT",
}));
const __VLS_320 = __VLS_319({
    label: "草稿",
    value: "DRAFT",
}, ...__VLS_functionalComponentArgsRest(__VLS_319));
const __VLS_322 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_323 = __VLS_asFunctionalComponent(__VLS_322, new __VLS_322({
    label: "发布",
    value: "PUBLISHED",
}));
const __VLS_324 = __VLS_323({
    label: "发布",
    value: "PUBLISHED",
}, ...__VLS_functionalComponentArgsRest(__VLS_323));
var __VLS_317;
var __VLS_313;
var __VLS_277;
{
    const { footer: __VLS_thisSlot } = __VLS_273.slots;
    const __VLS_326 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_327 = __VLS_asFunctionalComponent(__VLS_326, new __VLS_326({
        ...{ 'onClick': {} },
    }));
    const __VLS_328 = __VLS_327({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_327));
    let __VLS_330;
    let __VLS_331;
    let __VLS_332;
    const __VLS_333 = {
        onClick: (...[$event]) => {
            __VLS_ctx.templateDialog = false;
        }
    };
    __VLS_329.slots.default;
    var __VLS_329;
    const __VLS_334 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_335 = __VLS_asFunctionalComponent(__VLS_334, new __VLS_334({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_336 = __VLS_335({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_335));
    let __VLS_338;
    let __VLS_339;
    let __VLS_340;
    const __VLS_341 = {
        onClick: (__VLS_ctx.saveTemplate)
    };
    __VLS_337.slots.default;
    var __VLS_337;
}
var __VLS_273;
const __VLS_342 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_343 = __VLS_asFunctionalComponent(__VLS_342, new __VLS_342({
    modelValue: (__VLS_ctx.startDialog),
    title: "发起流程",
    width: "560px",
}));
const __VLS_344 = __VLS_343({
    modelValue: (__VLS_ctx.startDialog),
    title: "发起流程",
    width: "560px",
}, ...__VLS_functionalComponentArgsRest(__VLS_343));
__VLS_345.slots.default;
const __VLS_346 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_347 = __VLS_asFunctionalComponent(__VLS_346, new __VLS_346({
    model: (__VLS_ctx.startForm),
    labelWidth: "108px",
}));
const __VLS_348 = __VLS_347({
    model: (__VLS_ctx.startForm),
    labelWidth: "108px",
}, ...__VLS_functionalComponentArgsRest(__VLS_347));
__VLS_349.slots.default;
const __VLS_350 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_351 = __VLS_asFunctionalComponent(__VLS_350, new __VLS_350({
    label: "系统 ID",
    required: true,
}));
const __VLS_352 = __VLS_351({
    label: "系统 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_351));
__VLS_353.slots.default;
const __VLS_354 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_355 = __VLS_asFunctionalComponent(__VLS_354, new __VLS_354({
    modelValue: (__VLS_ctx.startForm.systemId),
    min: (1),
}));
const __VLS_356 = __VLS_355({
    modelValue: (__VLS_ctx.startForm.systemId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_355));
var __VLS_353;
const __VLS_358 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_359 = __VLS_asFunctionalComponent(__VLS_358, new __VLS_358({
    label: "租户 ID",
    required: true,
}));
const __VLS_360 = __VLS_359({
    label: "租户 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_359));
__VLS_361.slots.default;
const __VLS_362 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_363 = __VLS_asFunctionalComponent(__VLS_362, new __VLS_362({
    modelValue: (__VLS_ctx.startForm.tenantId),
    min: (1),
}));
const __VLS_364 = __VLS_363({
    modelValue: (__VLS_ctx.startForm.tenantId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_363));
var __VLS_361;
const __VLS_366 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_367 = __VLS_asFunctionalComponent(__VLS_366, new __VLS_366({
    label: "模块 ID",
    required: true,
}));
const __VLS_368 = __VLS_367({
    label: "模块 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_367));
__VLS_369.slots.default;
const __VLS_370 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_371 = __VLS_asFunctionalComponent(__VLS_370, new __VLS_370({
    modelValue: (__VLS_ctx.startForm.moduleId),
    min: (1),
}));
const __VLS_372 = __VLS_371({
    modelValue: (__VLS_ctx.startForm.moduleId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_371));
var __VLS_369;
const __VLS_374 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_375 = __VLS_asFunctionalComponent(__VLS_374, new __VLS_374({
    label: "记录 ID",
    required: true,
}));
const __VLS_376 = __VLS_375({
    label: "记录 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_375));
__VLS_377.slots.default;
const __VLS_378 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_379 = __VLS_asFunctionalComponent(__VLS_378, new __VLS_378({
    modelValue: (__VLS_ctx.startForm.recordId),
    min: (1),
}));
const __VLS_380 = __VLS_379({
    modelValue: (__VLS_ctx.startForm.recordId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_379));
var __VLS_377;
var __VLS_349;
{
    const { footer: __VLS_thisSlot } = __VLS_345.slots;
    const __VLS_382 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_383 = __VLS_asFunctionalComponent(__VLS_382, new __VLS_382({
        ...{ 'onClick': {} },
    }));
    const __VLS_384 = __VLS_383({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_383));
    let __VLS_386;
    let __VLS_387;
    let __VLS_388;
    const __VLS_389 = {
        onClick: (...[$event]) => {
            __VLS_ctx.startDialog = false;
        }
    };
    __VLS_385.slots.default;
    var __VLS_385;
    const __VLS_390 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_391 = __VLS_asFunctionalComponent(__VLS_390, new __VLS_390({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.VideoPlay),
    }));
    const __VLS_392 = __VLS_391({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.VideoPlay),
    }, ...__VLS_functionalComponentArgsRest(__VLS_391));
    let __VLS_394;
    let __VLS_395;
    let __VLS_396;
    const __VLS_397 = {
        onClick: (__VLS_ctx.startWorkflow)
    };
    __VLS_393.slots.default;
    var __VLS_393;
}
var __VLS_345;
const __VLS_398 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_399 = __VLS_asFunctionalComponent(__VLS_398, new __VLS_398({
    modelValue: (__VLS_ctx.handleDialog),
    title: (`任务处理：${__VLS_ctx.handleForm.action}`),
    width: "540px",
}));
const __VLS_400 = __VLS_399({
    modelValue: (__VLS_ctx.handleDialog),
    title: (`任务处理：${__VLS_ctx.handleForm.action}`),
    width: "540px",
}, ...__VLS_functionalComponentArgsRest(__VLS_399));
__VLS_401.slots.default;
const __VLS_402 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_403 = __VLS_asFunctionalComponent(__VLS_402, new __VLS_402({
    model: (__VLS_ctx.handleForm),
    labelWidth: "96px",
}));
const __VLS_404 = __VLS_403({
    model: (__VLS_ctx.handleForm),
    labelWidth: "96px",
}, ...__VLS_functionalComponentArgsRest(__VLS_403));
__VLS_405.slots.default;
const __VLS_406 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_407 = __VLS_asFunctionalComponent(__VLS_406, new __VLS_406({
    label: "处理意见",
}));
const __VLS_408 = __VLS_407({
    label: "处理意见",
}, ...__VLS_functionalComponentArgsRest(__VLS_407));
__VLS_409.slots.default;
const __VLS_410 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_411 = __VLS_asFunctionalComponent(__VLS_410, new __VLS_410({
    modelValue: (__VLS_ctx.handleForm.comment),
    type: "textarea",
    rows: (4),
}));
const __VLS_412 = __VLS_411({
    modelValue: (__VLS_ctx.handleForm.comment),
    type: "textarea",
    rows: (4),
}, ...__VLS_functionalComponentArgsRest(__VLS_411));
var __VLS_409;
if (__VLS_ctx.handleForm.action === 'TRANSFER') {
    const __VLS_414 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_415 = __VLS_asFunctionalComponent(__VLS_414, new __VLS_414({
        label: "转交给",
    }));
    const __VLS_416 = __VLS_415({
        label: "转交给",
    }, ...__VLS_functionalComponentArgsRest(__VLS_415));
    __VLS_417.slots.default;
    const __VLS_418 = {}.ElInputNumber;
    /** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
    // @ts-ignore
    const __VLS_419 = __VLS_asFunctionalComponent(__VLS_418, new __VLS_418({
        modelValue: (__VLS_ctx.handleForm.transferTo),
        min: (1),
    }));
    const __VLS_420 = __VLS_419({
        modelValue: (__VLS_ctx.handleForm.transferTo),
        min: (1),
    }, ...__VLS_functionalComponentArgsRest(__VLS_419));
    var __VLS_417;
}
var __VLS_405;
{
    const { footer: __VLS_thisSlot } = __VLS_401.slots;
    const __VLS_422 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_423 = __VLS_asFunctionalComponent(__VLS_422, new __VLS_422({
        ...{ 'onClick': {} },
    }));
    const __VLS_424 = __VLS_423({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_423));
    let __VLS_426;
    let __VLS_427;
    let __VLS_428;
    const __VLS_429 = {
        onClick: (...[$event]) => {
            __VLS_ctx.handleDialog = false;
        }
    };
    __VLS_425.slots.default;
    var __VLS_425;
    const __VLS_430 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_431 = __VLS_asFunctionalComponent(__VLS_430, new __VLS_430({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_432 = __VLS_431({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_431));
    let __VLS_434;
    let __VLS_435;
    let __VLS_436;
    const __VLS_437 = {
        onClick: (__VLS_ctx.handleTask)
    };
    __VLS_433.slots.default;
    var __VLS_433;
}
var __VLS_401;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['header-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['split-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['workflow-canvas']} */ ;
/** @type {__VLS_StyleScopedClasses['workflow-node']} */ ;
/** @type {__VLS_StyleScopedClasses['workflow-edge']} */ ;
/** @type {__VLS_StyleScopedClasses['designer-form']} */ ;
/** @type {__VLS_StyleScopedClasses['json-box']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            Check: Check,
            CircleCheck: CircleCheck,
            CircleClose: CircleClose,
            CloseBold: CloseBold,
            Connection: Connection,
            Edit: Edit,
            Plus: Plus,
            Refresh: Refresh,
            Right: Right,
            Search: Search,
            Upload: Upload,
            VideoPlay: VideoPlay,
            StatusTag: StatusTag,
            tab: tab,
            templateDialog: templateDialog,
            startDialog: startDialog,
            handleDialog: handleDialog,
            selectedTemplate: selectedTemplate,
            publishVersionId: publishVersionId,
            loading: loading,
            templateQuery: templateQuery,
            taskQuery: taskQuery,
            templates: templates,
            tasks: tasks,
            nodes: nodes,
            versionForm: versionForm,
            templateForm: templateForm,
            startForm: startForm,
            handleForm: handleForm,
            templateEmptyText: templateEmptyText,
            taskEmptyText: taskEmptyText,
            addNode: addNode,
            openTemplate: openTemplate,
            selectTemplate: selectTemplate,
            loadTemplates: loadTemplates,
            loadTasks: loadTasks,
            saveTemplate: saveTemplate,
            saveVersion: saveVersion,
            publishVersion: publishVersion,
            startWorkflow: startWorkflow,
            openHandle: openHandle,
            handleTask: handleTask,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
