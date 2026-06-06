import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Check, Delete, Link, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { fileApi } from '../api/modules';
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
const files = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const tasks = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const fileForm = reactive({ systemId: undefined, tenantId: undefined, storagePath: '', fileName: '', fileSize: 0, contentType: '' });
const relationForm = reactive({ fileId: undefined, objectType: 'RECORD', objectId: undefined });
const taskForm = reactive({ systemId: undefined, tenantId: undefined, appId: undefined, moduleId: undefined, taskType: 'EXPORT', templateId: undefined });
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
function openRelation(row) {
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
    }
    finally {
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
    }
    finally {
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
async function removeFile(row) {
    await ElMessageBox.confirm('确认删除文件元数据？', '删除文件', { type: 'warning' });
    await fileApi.remove(Number(row.fileId ?? row.id));
    ElMessage.success('文件元数据已删除');
    loadFiles();
}
onMounted(() => {
    loadFiles();
    loadTasks();
});
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
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
    onClick: (__VLS_ctx.openFile)
};
__VLS_3.slots.default;
var __VLS_3;
const __VLS_8 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Plus),
    type: "primary",
}));
const __VLS_10 = __VLS_9({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Plus),
    type: "primary",
}, ...__VLS_functionalComponentArgsRest(__VLS_9));
let __VLS_12;
let __VLS_13;
let __VLS_14;
const __VLS_15 = {
    onClick: (__VLS_ctx.openTask)
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
        __VLS_ctx.tab === 'files' ? __VLS_ctx.loadFiles() : __VLS_ctx.loadTasks();
    }
};
__VLS_19.slots.default;
const __VLS_24 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent(__VLS_24, new __VLS_24({
    label: "文件元数据",
    name: "files",
}));
const __VLS_26 = __VLS_25({
    label: "文件元数据",
    name: "files",
}, ...__VLS_functionalComponentArgsRest(__VLS_25));
const __VLS_28 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_29 = __VLS_asFunctionalComponent(__VLS_28, new __VLS_28({
    label: "导入导出任务",
    name: "tasks",
}));
const __VLS_30 = __VLS_29({
    label: "导入导出任务",
    name: "tasks",
}, ...__VLS_functionalComponentArgsRest(__VLS_29));
var __VLS_19;
if (__VLS_ctx.tab === 'files') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "toolbar" },
    });
    const __VLS_32 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_33 = __VLS_asFunctionalComponent(__VLS_32, new __VLS_32({
        modelValue: (__VLS_ctx.fileQuery.fileName),
        clearable: true,
        placeholder: "文件名",
        ...{ style: {} },
    }));
    const __VLS_34 = __VLS_33({
        modelValue: (__VLS_ctx.fileQuery.fileName),
        clearable: true,
        placeholder: "文件名",
        ...{ style: {} },
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
        onClick: (__VLS_ctx.loadFiles)
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
        onClick: (__VLS_ctx.loadFiles)
    };
    var __VLS_47;
    const __VLS_52 = {}.ElTable;
    /** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
    // @ts-ignore
    const __VLS_53 = __VLS_asFunctionalComponent(__VLS_52, new __VLS_52({
        data: (__VLS_ctx.files.records),
        border: true,
        height: "560",
        emptyText: (__VLS_ctx.fileEmptyText),
    }));
    const __VLS_54 = __VLS_53({
        data: (__VLS_ctx.files.records),
        border: true,
        height: "560",
        emptyText: (__VLS_ctx.fileEmptyText),
    }, ...__VLS_functionalComponentArgsRest(__VLS_53));
    __VLS_asFunctionalDirective(__VLS_directives.vLoading)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.loading.files) }, null, null);
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
        prop: "fileName",
        label: "文件名",
        minWidth: "180",
    }));
    const __VLS_62 = __VLS_61({
        prop: "fileName",
        label: "文件名",
        minWidth: "180",
    }, ...__VLS_functionalComponentArgsRest(__VLS_61));
    const __VLS_64 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_65 = __VLS_asFunctionalComponent(__VLS_64, new __VLS_64({
        prop: "contentType",
        label: "类型",
        minWidth: "150",
    }));
    const __VLS_66 = __VLS_65({
        prop: "contentType",
        label: "类型",
        minWidth: "150",
    }, ...__VLS_functionalComponentArgsRest(__VLS_65));
    const __VLS_68 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_69 = __VLS_asFunctionalComponent(__VLS_68, new __VLS_68({
        prop: "fileSize",
        label: "大小",
        width: "110",
    }));
    const __VLS_70 = __VLS_69({
        prop: "fileSize",
        label: "大小",
        width: "110",
    }, ...__VLS_functionalComponentArgsRest(__VLS_69));
    const __VLS_72 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_73 = __VLS_asFunctionalComponent(__VLS_72, new __VLS_72({
        prop: "storagePath",
        label: "存储路径",
        minWidth: "220",
        showOverflowTooltip: true,
    }));
    const __VLS_74 = __VLS_73({
        prop: "storagePath",
        label: "存储路径",
        minWidth: "220",
        showOverflowTooltip: true,
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
        width: "210",
        fixed: "right",
    }));
    const __VLS_85 = __VLS_84({
        label: "操作",
        width: "210",
        fixed: "right",
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
            icon: (__VLS_ctx.Link),
            size: "small",
        }));
        const __VLS_89 = __VLS_88({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Link),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_88));
        let __VLS_91;
        let __VLS_92;
        let __VLS_93;
        const __VLS_94 = {
            onClick: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'files'))
                    return;
                __VLS_ctx.openRelation(row);
            }
        };
        __VLS_90.slots.default;
        var __VLS_90;
        const __VLS_95 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_96 = __VLS_asFunctionalComponent(__VLS_95, new __VLS_95({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Delete),
            size: "small",
            type: "danger",
        }));
        const __VLS_97 = __VLS_96({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Delete),
            size: "small",
            type: "danger",
        }, ...__VLS_functionalComponentArgsRest(__VLS_96));
        let __VLS_99;
        let __VLS_100;
        let __VLS_101;
        const __VLS_102 = {
            onClick: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'files'))
                    return;
                __VLS_ctx.removeFile(row);
            }
        };
        __VLS_98.slots.default;
        var __VLS_98;
    }
    var __VLS_86;
    var __VLS_55;
}
else {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "toolbar" },
    });
    const __VLS_103 = {}.ElSelect;
    /** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
    // @ts-ignore
    const __VLS_104 = __VLS_asFunctionalComponent(__VLS_103, new __VLS_103({
        modelValue: (__VLS_ctx.taskQuery.taskType),
        clearable: true,
        placeholder: "任务类型",
        ...{ style: {} },
    }));
    const __VLS_105 = __VLS_104({
        modelValue: (__VLS_ctx.taskQuery.taskType),
        clearable: true,
        placeholder: "任务类型",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_104));
    __VLS_106.slots.default;
    const __VLS_107 = {}.ElOption;
    /** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
    // @ts-ignore
    const __VLS_108 = __VLS_asFunctionalComponent(__VLS_107, new __VLS_107({
        label: "导入",
        value: "IMPORT",
    }));
    const __VLS_109 = __VLS_108({
        label: "导入",
        value: "IMPORT",
    }, ...__VLS_functionalComponentArgsRest(__VLS_108));
    const __VLS_111 = {}.ElOption;
    /** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
    // @ts-ignore
    const __VLS_112 = __VLS_asFunctionalComponent(__VLS_111, new __VLS_111({
        label: "导出",
        value: "EXPORT",
    }));
    const __VLS_113 = __VLS_112({
        label: "导出",
        value: "EXPORT",
    }, ...__VLS_functionalComponentArgsRest(__VLS_112));
    var __VLS_106;
    const __VLS_115 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_116 = __VLS_asFunctionalComponent(__VLS_115, new __VLS_115({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Search),
    }));
    const __VLS_117 = __VLS_116({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Search),
    }, ...__VLS_functionalComponentArgsRest(__VLS_116));
    let __VLS_119;
    let __VLS_120;
    let __VLS_121;
    const __VLS_122 = {
        onClick: (__VLS_ctx.loadTasks)
    };
    __VLS_118.slots.default;
    var __VLS_118;
    const __VLS_123 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_124 = __VLS_asFunctionalComponent(__VLS_123, new __VLS_123({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Refresh),
    }));
    const __VLS_125 = __VLS_124({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Refresh),
    }, ...__VLS_functionalComponentArgsRest(__VLS_124));
    let __VLS_127;
    let __VLS_128;
    let __VLS_129;
    const __VLS_130 = {
        onClick: (__VLS_ctx.loadTasks)
    };
    var __VLS_126;
    const __VLS_131 = {}.ElTable;
    /** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
    // @ts-ignore
    const __VLS_132 = __VLS_asFunctionalComponent(__VLS_131, new __VLS_131({
        data: (__VLS_ctx.tasks.records),
        border: true,
        height: "560",
        emptyText: (__VLS_ctx.taskEmptyText),
    }));
    const __VLS_133 = __VLS_132({
        data: (__VLS_ctx.tasks.records),
        border: true,
        height: "560",
        emptyText: (__VLS_ctx.taskEmptyText),
    }, ...__VLS_functionalComponentArgsRest(__VLS_132));
    __VLS_asFunctionalDirective(__VLS_directives.vLoading)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.loading.tasks) }, null, null);
    __VLS_134.slots.default;
    const __VLS_135 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_136 = __VLS_asFunctionalComponent(__VLS_135, new __VLS_135({
        prop: "id",
        label: "ID",
        width: "80",
    }));
    const __VLS_137 = __VLS_136({
        prop: "id",
        label: "ID",
        width: "80",
    }, ...__VLS_functionalComponentArgsRest(__VLS_136));
    const __VLS_139 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_140 = __VLS_asFunctionalComponent(__VLS_139, new __VLS_139({
        prop: "taskType",
        label: "类型",
        width: "110",
    }));
    const __VLS_141 = __VLS_140({
        prop: "taskType",
        label: "类型",
        width: "110",
    }, ...__VLS_functionalComponentArgsRest(__VLS_140));
    const __VLS_143 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_144 = __VLS_asFunctionalComponent(__VLS_143, new __VLS_143({
        prop: "templateId",
        label: "模板 ID",
        width: "110",
    }));
    const __VLS_145 = __VLS_144({
        prop: "templateId",
        label: "模板 ID",
        width: "110",
    }, ...__VLS_functionalComponentArgsRest(__VLS_144));
    const __VLS_147 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_148 = __VLS_asFunctionalComponent(__VLS_147, new __VLS_147({
        prop: "moduleId",
        label: "moduleId",
        width: "110",
    }));
    const __VLS_149 = __VLS_148({
        prop: "moduleId",
        label: "moduleId",
        width: "110",
    }, ...__VLS_functionalComponentArgsRest(__VLS_148));
    const __VLS_151 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_152 = __VLS_asFunctionalComponent(__VLS_151, new __VLS_151({
        prop: "status",
        label: "状态",
        width: "120",
    }));
    const __VLS_153 = __VLS_152({
        prop: "status",
        label: "状态",
        width: "120",
    }, ...__VLS_functionalComponentArgsRest(__VLS_152));
    __VLS_154.slots.default;
    {
        const { default: __VLS_thisSlot } = __VLS_154.slots;
        const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
        /** @type {[typeof StatusTag, ]} */ ;
        // @ts-ignore
        const __VLS_155 = __VLS_asFunctionalComponent(StatusTag, new StatusTag({
            value: (row.status),
        }));
        const __VLS_156 = __VLS_155({
            value: (row.status),
        }, ...__VLS_functionalComponentArgsRest(__VLS_155));
    }
    var __VLS_154;
    const __VLS_158 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_159 = __VLS_asFunctionalComponent(__VLS_158, new __VLS_158({
        prop: "failureReason",
        label: "失败原因",
        minWidth: "180",
        showOverflowTooltip: true,
    }));
    const __VLS_160 = __VLS_159({
        prop: "failureReason",
        label: "失败原因",
        minWidth: "180",
        showOverflowTooltip: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_159));
    const __VLS_162 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_163 = __VLS_asFunctionalComponent(__VLS_162, new __VLS_162({
        prop: "resultFileId",
        label: "结果文件",
        width: "110",
    }));
    const __VLS_164 = __VLS_163({
        prop: "resultFileId",
        label: "结果文件",
        width: "110",
    }, ...__VLS_functionalComponentArgsRest(__VLS_163));
    const __VLS_166 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_167 = __VLS_asFunctionalComponent(__VLS_166, new __VLS_166({
        prop: "createdAt",
        label: "创建时间",
        minWidth: "160",
    }));
    const __VLS_168 = __VLS_167({
        prop: "createdAt",
        label: "创建时间",
        minWidth: "160",
    }, ...__VLS_functionalComponentArgsRest(__VLS_167));
    var __VLS_134;
}
const __VLS_170 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_171 = __VLS_asFunctionalComponent(__VLS_170, new __VLS_170({
    modelValue: (__VLS_ctx.fileDialog),
    title: "新建文件元数据",
    width: "620px",
}));
const __VLS_172 = __VLS_171({
    modelValue: (__VLS_ctx.fileDialog),
    title: "新建文件元数据",
    width: "620px",
}, ...__VLS_functionalComponentArgsRest(__VLS_171));
__VLS_173.slots.default;
const __VLS_174 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_175 = __VLS_asFunctionalComponent(__VLS_174, new __VLS_174({
    model: (__VLS_ctx.fileForm),
    labelWidth: "108px",
}));
const __VLS_176 = __VLS_175({
    model: (__VLS_ctx.fileForm),
    labelWidth: "108px",
}, ...__VLS_functionalComponentArgsRest(__VLS_175));
__VLS_177.slots.default;
const __VLS_178 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_179 = __VLS_asFunctionalComponent(__VLS_178, new __VLS_178({
    label: "系统 ID",
    required: true,
}));
const __VLS_180 = __VLS_179({
    label: "系统 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_179));
__VLS_181.slots.default;
const __VLS_182 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_183 = __VLS_asFunctionalComponent(__VLS_182, new __VLS_182({
    modelValue: (__VLS_ctx.fileForm.systemId),
    min: (1),
}));
const __VLS_184 = __VLS_183({
    modelValue: (__VLS_ctx.fileForm.systemId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_183));
var __VLS_181;
const __VLS_186 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_187 = __VLS_asFunctionalComponent(__VLS_186, new __VLS_186({
    label: "租户 ID",
    required: true,
}));
const __VLS_188 = __VLS_187({
    label: "租户 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_187));
__VLS_189.slots.default;
const __VLS_190 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_191 = __VLS_asFunctionalComponent(__VLS_190, new __VLS_190({
    modelValue: (__VLS_ctx.fileForm.tenantId),
    min: (1),
}));
const __VLS_192 = __VLS_191({
    modelValue: (__VLS_ctx.fileForm.tenantId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_191));
var __VLS_189;
const __VLS_194 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_195 = __VLS_asFunctionalComponent(__VLS_194, new __VLS_194({
    label: "文件名",
    required: true,
}));
const __VLS_196 = __VLS_195({
    label: "文件名",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_195));
__VLS_197.slots.default;
const __VLS_198 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_199 = __VLS_asFunctionalComponent(__VLS_198, new __VLS_198({
    modelValue: (__VLS_ctx.fileForm.fileName),
}));
const __VLS_200 = __VLS_199({
    modelValue: (__VLS_ctx.fileForm.fileName),
}, ...__VLS_functionalComponentArgsRest(__VLS_199));
var __VLS_197;
const __VLS_202 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_203 = __VLS_asFunctionalComponent(__VLS_202, new __VLS_202({
    label: "存储路径",
    required: true,
}));
const __VLS_204 = __VLS_203({
    label: "存储路径",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_203));
__VLS_205.slots.default;
const __VLS_206 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_207 = __VLS_asFunctionalComponent(__VLS_206, new __VLS_206({
    modelValue: (__VLS_ctx.fileForm.storagePath),
}));
const __VLS_208 = __VLS_207({
    modelValue: (__VLS_ctx.fileForm.storagePath),
}, ...__VLS_functionalComponentArgsRest(__VLS_207));
var __VLS_205;
const __VLS_210 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_211 = __VLS_asFunctionalComponent(__VLS_210, new __VLS_210({
    label: "文件大小",
}));
const __VLS_212 = __VLS_211({
    label: "文件大小",
}, ...__VLS_functionalComponentArgsRest(__VLS_211));
__VLS_213.slots.default;
const __VLS_214 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_215 = __VLS_asFunctionalComponent(__VLS_214, new __VLS_214({
    modelValue: (__VLS_ctx.fileForm.fileSize),
    min: (0),
}));
const __VLS_216 = __VLS_215({
    modelValue: (__VLS_ctx.fileForm.fileSize),
    min: (0),
}, ...__VLS_functionalComponentArgsRest(__VLS_215));
var __VLS_213;
const __VLS_218 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_219 = __VLS_asFunctionalComponent(__VLS_218, new __VLS_218({
    label: "MIME 类型",
}));
const __VLS_220 = __VLS_219({
    label: "MIME 类型",
}, ...__VLS_functionalComponentArgsRest(__VLS_219));
__VLS_221.slots.default;
const __VLS_222 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_223 = __VLS_asFunctionalComponent(__VLS_222, new __VLS_222({
    modelValue: (__VLS_ctx.fileForm.contentType),
}));
const __VLS_224 = __VLS_223({
    modelValue: (__VLS_ctx.fileForm.contentType),
}, ...__VLS_functionalComponentArgsRest(__VLS_223));
var __VLS_221;
var __VLS_177;
{
    const { footer: __VLS_thisSlot } = __VLS_173.slots;
    const __VLS_226 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_227 = __VLS_asFunctionalComponent(__VLS_226, new __VLS_226({
        ...{ 'onClick': {} },
    }));
    const __VLS_228 = __VLS_227({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_227));
    let __VLS_230;
    let __VLS_231;
    let __VLS_232;
    const __VLS_233 = {
        onClick: (...[$event]) => {
            __VLS_ctx.fileDialog = false;
        }
    };
    __VLS_229.slots.default;
    var __VLS_229;
    const __VLS_234 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_235 = __VLS_asFunctionalComponent(__VLS_234, new __VLS_234({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_236 = __VLS_235({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_235));
    let __VLS_238;
    let __VLS_239;
    let __VLS_240;
    const __VLS_241 = {
        onClick: (__VLS_ctx.saveFile)
    };
    __VLS_237.slots.default;
    var __VLS_237;
}
var __VLS_173;
const __VLS_242 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_243 = __VLS_asFunctionalComponent(__VLS_242, new __VLS_242({
    modelValue: (__VLS_ctx.relationDialog),
    title: "关联文件",
    width: "560px",
}));
const __VLS_244 = __VLS_243({
    modelValue: (__VLS_ctx.relationDialog),
    title: "关联文件",
    width: "560px",
}, ...__VLS_functionalComponentArgsRest(__VLS_243));
__VLS_245.slots.default;
const __VLS_246 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_247 = __VLS_asFunctionalComponent(__VLS_246, new __VLS_246({
    model: (__VLS_ctx.relationForm),
    labelWidth: "108px",
}));
const __VLS_248 = __VLS_247({
    model: (__VLS_ctx.relationForm),
    labelWidth: "108px",
}, ...__VLS_functionalComponentArgsRest(__VLS_247));
__VLS_249.slots.default;
const __VLS_250 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_251 = __VLS_asFunctionalComponent(__VLS_250, new __VLS_250({
    label: "文件 ID",
}));
const __VLS_252 = __VLS_251({
    label: "文件 ID",
}, ...__VLS_functionalComponentArgsRest(__VLS_251));
__VLS_253.slots.default;
const __VLS_254 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_255 = __VLS_asFunctionalComponent(__VLS_254, new __VLS_254({
    modelValue: (__VLS_ctx.relationForm.fileId),
    min: (1),
}));
const __VLS_256 = __VLS_255({
    modelValue: (__VLS_ctx.relationForm.fileId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_255));
var __VLS_253;
const __VLS_258 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_259 = __VLS_asFunctionalComponent(__VLS_258, new __VLS_258({
    label: "对象类型",
}));
const __VLS_260 = __VLS_259({
    label: "对象类型",
}, ...__VLS_functionalComponentArgsRest(__VLS_259));
__VLS_261.slots.default;
const __VLS_262 = {}.ElSelect;
/** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
// @ts-ignore
const __VLS_263 = __VLS_asFunctionalComponent(__VLS_262, new __VLS_262({
    modelValue: (__VLS_ctx.relationForm.objectType),
}));
const __VLS_264 = __VLS_263({
    modelValue: (__VLS_ctx.relationForm.objectType),
}, ...__VLS_functionalComponentArgsRest(__VLS_263));
__VLS_265.slots.default;
const __VLS_266 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_267 = __VLS_asFunctionalComponent(__VLS_266, new __VLS_266({
    label: "记录",
    value: "RECORD",
}));
const __VLS_268 = __VLS_267({
    label: "记录",
    value: "RECORD",
}, ...__VLS_functionalComponentArgsRest(__VLS_267));
const __VLS_270 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_271 = __VLS_asFunctionalComponent(__VLS_270, new __VLS_270({
    label: "任务",
    value: "TASK",
}));
const __VLS_272 = __VLS_271({
    label: "任务",
    value: "TASK",
}, ...__VLS_functionalComponentArgsRest(__VLS_271));
const __VLS_274 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_275 = __VLS_asFunctionalComponent(__VLS_274, new __VLS_274({
    label: "导入导出",
    value: "IMPORT_EXPORT",
}));
const __VLS_276 = __VLS_275({
    label: "导入导出",
    value: "IMPORT_EXPORT",
}, ...__VLS_functionalComponentArgsRest(__VLS_275));
var __VLS_265;
var __VLS_261;
const __VLS_278 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_279 = __VLS_asFunctionalComponent(__VLS_278, new __VLS_278({
    label: "对象 ID",
}));
const __VLS_280 = __VLS_279({
    label: "对象 ID",
}, ...__VLS_functionalComponentArgsRest(__VLS_279));
__VLS_281.slots.default;
const __VLS_282 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_283 = __VLS_asFunctionalComponent(__VLS_282, new __VLS_282({
    modelValue: (__VLS_ctx.relationForm.objectId),
    min: (1),
}));
const __VLS_284 = __VLS_283({
    modelValue: (__VLS_ctx.relationForm.objectId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_283));
var __VLS_281;
var __VLS_249;
{
    const { footer: __VLS_thisSlot } = __VLS_245.slots;
    const __VLS_286 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_287 = __VLS_asFunctionalComponent(__VLS_286, new __VLS_286({
        ...{ 'onClick': {} },
    }));
    const __VLS_288 = __VLS_287({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_287));
    let __VLS_290;
    let __VLS_291;
    let __VLS_292;
    const __VLS_293 = {
        onClick: (...[$event]) => {
            __VLS_ctx.relationDialog = false;
        }
    };
    __VLS_289.slots.default;
    var __VLS_289;
    const __VLS_294 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_295 = __VLS_asFunctionalComponent(__VLS_294, new __VLS_294({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_296 = __VLS_295({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_295));
    let __VLS_298;
    let __VLS_299;
    let __VLS_300;
    const __VLS_301 = {
        onClick: (__VLS_ctx.saveRelation)
    };
    __VLS_297.slots.default;
    var __VLS_297;
}
var __VLS_245;
const __VLS_302 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_303 = __VLS_asFunctionalComponent(__VLS_302, new __VLS_302({
    modelValue: (__VLS_ctx.taskDialog),
    title: "新建导入导出任务",
    width: "620px",
}));
const __VLS_304 = __VLS_303({
    modelValue: (__VLS_ctx.taskDialog),
    title: "新建导入导出任务",
    width: "620px",
}, ...__VLS_functionalComponentArgsRest(__VLS_303));
__VLS_305.slots.default;
const __VLS_306 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_307 = __VLS_asFunctionalComponent(__VLS_306, new __VLS_306({
    model: (__VLS_ctx.taskForm),
    labelWidth: "118px",
}));
const __VLS_308 = __VLS_307({
    model: (__VLS_ctx.taskForm),
    labelWidth: "118px",
}, ...__VLS_functionalComponentArgsRest(__VLS_307));
__VLS_309.slots.default;
const __VLS_310 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_311 = __VLS_asFunctionalComponent(__VLS_310, new __VLS_310({
    label: "系统 ID",
    required: true,
}));
const __VLS_312 = __VLS_311({
    label: "系统 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_311));
__VLS_313.slots.default;
const __VLS_314 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_315 = __VLS_asFunctionalComponent(__VLS_314, new __VLS_314({
    modelValue: (__VLS_ctx.taskForm.systemId),
    min: (1),
}));
const __VLS_316 = __VLS_315({
    modelValue: (__VLS_ctx.taskForm.systemId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_315));
var __VLS_313;
const __VLS_318 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_319 = __VLS_asFunctionalComponent(__VLS_318, new __VLS_318({
    label: "租户 ID",
    required: true,
}));
const __VLS_320 = __VLS_319({
    label: "租户 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_319));
__VLS_321.slots.default;
const __VLS_322 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_323 = __VLS_asFunctionalComponent(__VLS_322, new __VLS_322({
    modelValue: (__VLS_ctx.taskForm.tenantId),
    min: (1),
}));
const __VLS_324 = __VLS_323({
    modelValue: (__VLS_ctx.taskForm.tenantId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_323));
var __VLS_321;
const __VLS_326 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_327 = __VLS_asFunctionalComponent(__VLS_326, new __VLS_326({
    label: "应用 ID",
}));
const __VLS_328 = __VLS_327({
    label: "应用 ID",
}, ...__VLS_functionalComponentArgsRest(__VLS_327));
__VLS_329.slots.default;
const __VLS_330 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_331 = __VLS_asFunctionalComponent(__VLS_330, new __VLS_330({
    modelValue: (__VLS_ctx.taskForm.appId),
    min: (1),
}));
const __VLS_332 = __VLS_331({
    modelValue: (__VLS_ctx.taskForm.appId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_331));
var __VLS_329;
const __VLS_334 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_335 = __VLS_asFunctionalComponent(__VLS_334, new __VLS_334({
    label: "模块 ID",
    required: true,
}));
const __VLS_336 = __VLS_335({
    label: "模块 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_335));
__VLS_337.slots.default;
const __VLS_338 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_339 = __VLS_asFunctionalComponent(__VLS_338, new __VLS_338({
    modelValue: (__VLS_ctx.taskForm.moduleId),
    min: (1),
}));
const __VLS_340 = __VLS_339({
    modelValue: (__VLS_ctx.taskForm.moduleId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_339));
var __VLS_337;
const __VLS_342 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_343 = __VLS_asFunctionalComponent(__VLS_342, new __VLS_342({
    label: "任务类型",
}));
const __VLS_344 = __VLS_343({
    label: "任务类型",
}, ...__VLS_functionalComponentArgsRest(__VLS_343));
__VLS_345.slots.default;
const __VLS_346 = {}.ElSelect;
/** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
// @ts-ignore
const __VLS_347 = __VLS_asFunctionalComponent(__VLS_346, new __VLS_346({
    modelValue: (__VLS_ctx.taskForm.taskType),
}));
const __VLS_348 = __VLS_347({
    modelValue: (__VLS_ctx.taskForm.taskType),
}, ...__VLS_functionalComponentArgsRest(__VLS_347));
__VLS_349.slots.default;
const __VLS_350 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_351 = __VLS_asFunctionalComponent(__VLS_350, new __VLS_350({
    label: "导入",
    value: "IMPORT",
}));
const __VLS_352 = __VLS_351({
    label: "导入",
    value: "IMPORT",
}, ...__VLS_functionalComponentArgsRest(__VLS_351));
const __VLS_354 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_355 = __VLS_asFunctionalComponent(__VLS_354, new __VLS_354({
    label: "导出",
    value: "EXPORT",
}));
const __VLS_356 = __VLS_355({
    label: "导出",
    value: "EXPORT",
}, ...__VLS_functionalComponentArgsRest(__VLS_355));
var __VLS_349;
var __VLS_345;
const __VLS_358 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_359 = __VLS_asFunctionalComponent(__VLS_358, new __VLS_358({
    label: "模板 ID",
}));
const __VLS_360 = __VLS_359({
    label: "模板 ID",
}, ...__VLS_functionalComponentArgsRest(__VLS_359));
__VLS_361.slots.default;
const __VLS_362 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_363 = __VLS_asFunctionalComponent(__VLS_362, new __VLS_362({
    modelValue: (__VLS_ctx.taskForm.templateId),
    min: (1),
}));
const __VLS_364 = __VLS_363({
    modelValue: (__VLS_ctx.taskForm.templateId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_363));
var __VLS_361;
var __VLS_309;
{
    const { footer: __VLS_thisSlot } = __VLS_305.slots;
    const __VLS_366 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_367 = __VLS_asFunctionalComponent(__VLS_366, new __VLS_366({
        ...{ 'onClick': {} },
    }));
    const __VLS_368 = __VLS_367({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_367));
    let __VLS_370;
    let __VLS_371;
    let __VLS_372;
    const __VLS_373 = {
        onClick: (...[$event]) => {
            __VLS_ctx.taskDialog = false;
        }
    };
    __VLS_369.slots.default;
    var __VLS_369;
    const __VLS_374 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_375 = __VLS_asFunctionalComponent(__VLS_374, new __VLS_374({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_376 = __VLS_375({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_375));
    let __VLS_378;
    let __VLS_379;
    let __VLS_380;
    const __VLS_381 = {
        onClick: (__VLS_ctx.saveTask)
    };
    __VLS_377.slots.default;
    var __VLS_377;
}
var __VLS_305;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['header-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            Check: Check,
            Delete: Delete,
            Link: Link,
            Plus: Plus,
            Refresh: Refresh,
            Search: Search,
            StatusTag: StatusTag,
            tab: tab,
            fileDialog: fileDialog,
            relationDialog: relationDialog,
            taskDialog: taskDialog,
            loading: loading,
            fileQuery: fileQuery,
            taskQuery: taskQuery,
            files: files,
            tasks: tasks,
            fileForm: fileForm,
            relationForm: relationForm,
            taskForm: taskForm,
            fileEmptyText: fileEmptyText,
            taskEmptyText: taskEmptyText,
            openFile: openFile,
            openTask: openTask,
            openRelation: openRelation,
            loadFiles: loadFiles,
            loadTasks: loadTasks,
            saveFile: saveFile,
            saveRelation: saveRelation,
            saveTask: saveTask,
            removeFile: removeFile,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
