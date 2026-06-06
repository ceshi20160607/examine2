import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, CircleCheck, CircleClose, Plus, Refresh, Search, SwitchButton } from '@element-plus/icons-vue';
import { platformApi } from '../api/modules';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';
const context = useContextStore();
const tab = ref('systems');
const tenantDialog = ref(false);
const systemDialog = ref(false);
const tenantQuery = reactive({ keyword: '' });
const systemQuery = reactive({ keyword: '' });
const loading = reactive({ tenants: false, systems: false });
const tenants = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const systems = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const tenantForm = reactive({ tenantName: '', tenantCode: '', ownerAccountId: undefined, status: 'ENABLED' });
const systemForm = reactive({
    systemName: '',
    systemCode: '',
    tenantId: undefined,
    ownerAccountId: undefined,
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
    }
    finally {
        loading.tenants = false;
    }
}
async function loadSystems() {
    loading.systems = true;
    try {
        Object.assign(systems, await platformApi.systems({ keyword: systemQuery.keyword }));
    }
    finally {
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
async function changeStatus(row, status) {
    await platformApi.updateSystemStatus(Number(row.systemId ?? row.id), status);
    ElMessage.success('状态已更新');
    loadSystems();
}
onMounted(() => {
    loadTenants();
    loadSystems();
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
    onClick: (__VLS_ctx.openTenant)
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
    onClick: (__VLS_ctx.openSystem)
};
__VLS_11.slots.default;
var __VLS_11;
const __VLS_16 = {}.ElTabs;
/** @type {[typeof __VLS_components.ElTabs, typeof __VLS_components.elTabs, typeof __VLS_components.ElTabs, typeof __VLS_components.elTabs, ]} */ ;
// @ts-ignore
const __VLS_17 = __VLS_asFunctionalComponent(__VLS_16, new __VLS_16({
    modelValue: (__VLS_ctx.tab),
}));
const __VLS_18 = __VLS_17({
    modelValue: (__VLS_ctx.tab),
}, ...__VLS_functionalComponentArgsRest(__VLS_17));
__VLS_19.slots.default;
const __VLS_20 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_21 = __VLS_asFunctionalComponent(__VLS_20, new __VLS_20({
    label: "租户",
    name: "tenants",
}));
const __VLS_22 = __VLS_21({
    label: "租户",
    name: "tenants",
}, ...__VLS_functionalComponentArgsRest(__VLS_21));
__VLS_23.slots.default;
__VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
    ...{ class: "content-panel" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "toolbar" },
});
const __VLS_24 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent(__VLS_24, new __VLS_24({
    modelValue: (__VLS_ctx.tenantQuery.keyword),
    clearable: true,
    placeholder: "租户名称/编码",
    ...{ style: {} },
}));
const __VLS_26 = __VLS_25({
    modelValue: (__VLS_ctx.tenantQuery.keyword),
    clearable: true,
    placeholder: "租户名称/编码",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_25));
const __VLS_28 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_29 = __VLS_asFunctionalComponent(__VLS_28, new __VLS_28({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Search),
}));
const __VLS_30 = __VLS_29({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Search),
}, ...__VLS_functionalComponentArgsRest(__VLS_29));
let __VLS_32;
let __VLS_33;
let __VLS_34;
const __VLS_35 = {
    onClick: (__VLS_ctx.loadTenants)
};
__VLS_31.slots.default;
var __VLS_31;
const __VLS_36 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_37 = __VLS_asFunctionalComponent(__VLS_36, new __VLS_36({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Refresh),
}));
const __VLS_38 = __VLS_37({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Refresh),
}, ...__VLS_functionalComponentArgsRest(__VLS_37));
let __VLS_40;
let __VLS_41;
let __VLS_42;
const __VLS_43 = {
    onClick: (__VLS_ctx.loadTenants)
};
var __VLS_39;
const __VLS_44 = {}.ElTable;
/** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
// @ts-ignore
const __VLS_45 = __VLS_asFunctionalComponent(__VLS_44, new __VLS_44({
    data: (__VLS_ctx.tenants.records),
    border: true,
    height: "520",
}));
const __VLS_46 = __VLS_45({
    data: (__VLS_ctx.tenants.records),
    border: true,
    height: "520",
}, ...__VLS_functionalComponentArgsRest(__VLS_45));
__VLS_asFunctionalDirective(__VLS_directives.vLoading)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.loading.tenants) }, null, null);
__VLS_47.slots.default;
const __VLS_48 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_49 = __VLS_asFunctionalComponent(__VLS_48, new __VLS_48({
    prop: "id",
    label: "ID",
    width: "90",
}));
const __VLS_50 = __VLS_49({
    prop: "id",
    label: "ID",
    width: "90",
}, ...__VLS_functionalComponentArgsRest(__VLS_49));
const __VLS_52 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_53 = __VLS_asFunctionalComponent(__VLS_52, new __VLS_52({
    prop: "tenantName",
    label: "租户名称",
    minWidth: "160",
}));
const __VLS_54 = __VLS_53({
    prop: "tenantName",
    label: "租户名称",
    minWidth: "160",
}, ...__VLS_functionalComponentArgsRest(__VLS_53));
const __VLS_56 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_57 = __VLS_asFunctionalComponent(__VLS_56, new __VLS_56({
    prop: "tenantCode",
    label: "编码",
    minWidth: "140",
}));
const __VLS_58 = __VLS_57({
    prop: "tenantCode",
    label: "编码",
    minWidth: "140",
}, ...__VLS_functionalComponentArgsRest(__VLS_57));
const __VLS_60 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_61 = __VLS_asFunctionalComponent(__VLS_60, new __VLS_60({
    prop: "ownerAccountId",
    label: "负责人账号",
    width: "130",
}));
const __VLS_62 = __VLS_61({
    prop: "ownerAccountId",
    label: "负责人账号",
    width: "130",
}, ...__VLS_functionalComponentArgsRest(__VLS_61));
const __VLS_64 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_65 = __VLS_asFunctionalComponent(__VLS_64, new __VLS_64({
    prop: "status",
    label: "状态",
    width: "110",
}));
const __VLS_66 = __VLS_65({
    prop: "status",
    label: "状态",
    width: "110",
}, ...__VLS_functionalComponentArgsRest(__VLS_65));
__VLS_67.slots.default;
{
    const { default: __VLS_thisSlot } = __VLS_67.slots;
    const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
    /** @type {[typeof StatusTag, ]} */ ;
    // @ts-ignore
    const __VLS_68 = __VLS_asFunctionalComponent(StatusTag, new StatusTag({
        value: (row.status),
    }));
    const __VLS_69 = __VLS_68({
        value: (row.status),
    }, ...__VLS_functionalComponentArgsRest(__VLS_68));
}
var __VLS_67;
var __VLS_47;
var __VLS_23;
const __VLS_71 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_72 = __VLS_asFunctionalComponent(__VLS_71, new __VLS_71({
    label: "系统",
    name: "systems",
}));
const __VLS_73 = __VLS_72({
    label: "系统",
    name: "systems",
}, ...__VLS_functionalComponentArgsRest(__VLS_72));
__VLS_74.slots.default;
__VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
    ...{ class: "content-panel" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "toolbar" },
});
const __VLS_75 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_76 = __VLS_asFunctionalComponent(__VLS_75, new __VLS_75({
    modelValue: (__VLS_ctx.systemQuery.keyword),
    clearable: true,
    placeholder: "系统名称/编码",
    ...{ style: {} },
}));
const __VLS_77 = __VLS_76({
    modelValue: (__VLS_ctx.systemQuery.keyword),
    clearable: true,
    placeholder: "系统名称/编码",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_76));
const __VLS_79 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_80 = __VLS_asFunctionalComponent(__VLS_79, new __VLS_79({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Search),
}));
const __VLS_81 = __VLS_80({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Search),
}, ...__VLS_functionalComponentArgsRest(__VLS_80));
let __VLS_83;
let __VLS_84;
let __VLS_85;
const __VLS_86 = {
    onClick: (__VLS_ctx.loadSystems)
};
__VLS_82.slots.default;
var __VLS_82;
const __VLS_87 = {}.ElButton;
/** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
// @ts-ignore
const __VLS_88 = __VLS_asFunctionalComponent(__VLS_87, new __VLS_87({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Refresh),
}));
const __VLS_89 = __VLS_88({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Refresh),
}, ...__VLS_functionalComponentArgsRest(__VLS_88));
let __VLS_91;
let __VLS_92;
let __VLS_93;
const __VLS_94 = {
    onClick: (__VLS_ctx.loadSystems)
};
var __VLS_90;
const __VLS_95 = {}.ElTable;
/** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
// @ts-ignore
const __VLS_96 = __VLS_asFunctionalComponent(__VLS_95, new __VLS_95({
    data: (__VLS_ctx.systems.records),
    border: true,
    height: "520",
}));
const __VLS_97 = __VLS_96({
    data: (__VLS_ctx.systems.records),
    border: true,
    height: "520",
}, ...__VLS_functionalComponentArgsRest(__VLS_96));
__VLS_asFunctionalDirective(__VLS_directives.vLoading)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.loading.systems) }, null, null);
__VLS_98.slots.default;
const __VLS_99 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_100 = __VLS_asFunctionalComponent(__VLS_99, new __VLS_99({
    prop: "id",
    label: "ID",
    width: "80",
}));
const __VLS_101 = __VLS_100({
    prop: "id",
    label: "ID",
    width: "80",
}, ...__VLS_functionalComponentArgsRest(__VLS_100));
const __VLS_103 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_104 = __VLS_asFunctionalComponent(__VLS_103, new __VLS_103({
    prop: "systemName",
    label: "系统名称",
    minWidth: "160",
}));
const __VLS_105 = __VLS_104({
    prop: "systemName",
    label: "系统名称",
    minWidth: "160",
}, ...__VLS_functionalComponentArgsRest(__VLS_104));
const __VLS_107 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_108 = __VLS_asFunctionalComponent(__VLS_107, new __VLS_107({
    prop: "systemCode",
    label: "编码",
    minWidth: "130",
}));
const __VLS_109 = __VLS_108({
    prop: "systemCode",
    label: "编码",
    minWidth: "130",
}, ...__VLS_functionalComponentArgsRest(__VLS_108));
const __VLS_111 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_112 = __VLS_asFunctionalComponent(__VLS_111, new __VLS_111({
    prop: "tenantId",
    label: "租户 ID",
    width: "100",
}));
const __VLS_113 = __VLS_112({
    prop: "tenantId",
    label: "租户 ID",
    width: "100",
}, ...__VLS_functionalComponentArgsRest(__VLS_112));
const __VLS_115 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_116 = __VLS_asFunctionalComponent(__VLS_115, new __VLS_115({
    prop: "ownerAccountId",
    label: "负责人账号",
    width: "130",
}));
const __VLS_117 = __VLS_116({
    prop: "ownerAccountId",
    label: "负责人账号",
    width: "130",
}, ...__VLS_functionalComponentArgsRest(__VLS_116));
const __VLS_119 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_120 = __VLS_asFunctionalComponent(__VLS_119, new __VLS_119({
    prop: "status",
    label: "状态",
    width: "110",
}));
const __VLS_121 = __VLS_120({
    prop: "status",
    label: "状态",
    width: "110",
}, ...__VLS_functionalComponentArgsRest(__VLS_120));
__VLS_122.slots.default;
{
    const { default: __VLS_thisSlot } = __VLS_122.slots;
    const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
    /** @type {[typeof StatusTag, ]} */ ;
    // @ts-ignore
    const __VLS_123 = __VLS_asFunctionalComponent(StatusTag, new StatusTag({
        value: (row.status),
    }));
    const __VLS_124 = __VLS_123({
        value: (row.status),
    }, ...__VLS_functionalComponentArgsRest(__VLS_123));
}
var __VLS_122;
const __VLS_126 = {}.ElTableColumn;
/** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
// @ts-ignore
const __VLS_127 = __VLS_asFunctionalComponent(__VLS_126, new __VLS_126({
    label: "操作",
    width: "250",
    fixed: "right",
}));
const __VLS_128 = __VLS_127({
    label: "操作",
    width: "250",
    fixed: "right",
}, ...__VLS_functionalComponentArgsRest(__VLS_127));
__VLS_129.slots.default;
{
    const { default: __VLS_thisSlot } = __VLS_129.slots;
    const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
    const __VLS_130 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_131 = __VLS_asFunctionalComponent(__VLS_130, new __VLS_130({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.SwitchButton),
        size: "small",
    }));
    const __VLS_132 = __VLS_131({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.SwitchButton),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_131));
    let __VLS_134;
    let __VLS_135;
    let __VLS_136;
    const __VLS_137 = {
        onClick: (...[$event]) => {
            __VLS_ctx.context.enterSystem(row);
        }
    };
    __VLS_133.slots.default;
    var __VLS_133;
    const __VLS_138 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_139 = __VLS_asFunctionalComponent(__VLS_138, new __VLS_138({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.CircleCheck),
        size: "small",
    }));
    const __VLS_140 = __VLS_139({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.CircleCheck),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_139));
    let __VLS_142;
    let __VLS_143;
    let __VLS_144;
    const __VLS_145 = {
        onClick: (...[$event]) => {
            __VLS_ctx.changeStatus(row, 'ENABLED');
        }
    };
    __VLS_141.slots.default;
    var __VLS_141;
    const __VLS_146 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_147 = __VLS_asFunctionalComponent(__VLS_146, new __VLS_146({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.CircleClose),
        size: "small",
    }));
    const __VLS_148 = __VLS_147({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.CircleClose),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_147));
    let __VLS_150;
    let __VLS_151;
    let __VLS_152;
    const __VLS_153 = {
        onClick: (...[$event]) => {
            __VLS_ctx.changeStatus(row, 'DISABLED');
        }
    };
    __VLS_149.slots.default;
    var __VLS_149;
}
var __VLS_129;
var __VLS_98;
var __VLS_74;
var __VLS_19;
const __VLS_154 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_155 = __VLS_asFunctionalComponent(__VLS_154, new __VLS_154({
    modelValue: (__VLS_ctx.tenantDialog),
    title: "新建租户",
    width: "520px",
}));
const __VLS_156 = __VLS_155({
    modelValue: (__VLS_ctx.tenantDialog),
    title: "新建租户",
    width: "520px",
}, ...__VLS_functionalComponentArgsRest(__VLS_155));
__VLS_157.slots.default;
const __VLS_158 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_159 = __VLS_asFunctionalComponent(__VLS_158, new __VLS_158({
    model: (__VLS_ctx.tenantForm),
    labelWidth: "100px",
}));
const __VLS_160 = __VLS_159({
    model: (__VLS_ctx.tenantForm),
    labelWidth: "100px",
}, ...__VLS_functionalComponentArgsRest(__VLS_159));
__VLS_161.slots.default;
const __VLS_162 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_163 = __VLS_asFunctionalComponent(__VLS_162, new __VLS_162({
    label: "租户名称",
    required: true,
}));
const __VLS_164 = __VLS_163({
    label: "租户名称",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_163));
__VLS_165.slots.default;
const __VLS_166 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_167 = __VLS_asFunctionalComponent(__VLS_166, new __VLS_166({
    modelValue: (__VLS_ctx.tenantForm.tenantName),
}));
const __VLS_168 = __VLS_167({
    modelValue: (__VLS_ctx.tenantForm.tenantName),
}, ...__VLS_functionalComponentArgsRest(__VLS_167));
var __VLS_165;
const __VLS_170 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_171 = __VLS_asFunctionalComponent(__VLS_170, new __VLS_170({
    label: "租户编码",
    required: true,
}));
const __VLS_172 = __VLS_171({
    label: "租户编码",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_171));
__VLS_173.slots.default;
const __VLS_174 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_175 = __VLS_asFunctionalComponent(__VLS_174, new __VLS_174({
    modelValue: (__VLS_ctx.tenantForm.tenantCode),
}));
const __VLS_176 = __VLS_175({
    modelValue: (__VLS_ctx.tenantForm.tenantCode),
}, ...__VLS_functionalComponentArgsRest(__VLS_175));
var __VLS_173;
const __VLS_178 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_179 = __VLS_asFunctionalComponent(__VLS_178, new __VLS_178({
    label: "负责人账号",
}));
const __VLS_180 = __VLS_179({
    label: "负责人账号",
}, ...__VLS_functionalComponentArgsRest(__VLS_179));
__VLS_181.slots.default;
const __VLS_182 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_183 = __VLS_asFunctionalComponent(__VLS_182, new __VLS_182({
    modelValue: (__VLS_ctx.tenantForm.ownerAccountId),
    min: (1),
}));
const __VLS_184 = __VLS_183({
    modelValue: (__VLS_ctx.tenantForm.ownerAccountId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_183));
var __VLS_181;
const __VLS_186 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_187 = __VLS_asFunctionalComponent(__VLS_186, new __VLS_186({
    label: "状态",
}));
const __VLS_188 = __VLS_187({
    label: "状态",
}, ...__VLS_functionalComponentArgsRest(__VLS_187));
__VLS_189.slots.default;
const __VLS_190 = {}.ElSelect;
/** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
// @ts-ignore
const __VLS_191 = __VLS_asFunctionalComponent(__VLS_190, new __VLS_190({
    modelValue: (__VLS_ctx.tenantForm.status),
}));
const __VLS_192 = __VLS_191({
    modelValue: (__VLS_ctx.tenantForm.status),
}, ...__VLS_functionalComponentArgsRest(__VLS_191));
__VLS_193.slots.default;
const __VLS_194 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_195 = __VLS_asFunctionalComponent(__VLS_194, new __VLS_194({
    label: "启用",
    value: "ENABLED",
}));
const __VLS_196 = __VLS_195({
    label: "启用",
    value: "ENABLED",
}, ...__VLS_functionalComponentArgsRest(__VLS_195));
const __VLS_198 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_199 = __VLS_asFunctionalComponent(__VLS_198, new __VLS_198({
    label: "停用",
    value: "DISABLED",
}));
const __VLS_200 = __VLS_199({
    label: "停用",
    value: "DISABLED",
}, ...__VLS_functionalComponentArgsRest(__VLS_199));
const __VLS_202 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_203 = __VLS_asFunctionalComponent(__VLS_202, new __VLS_202({
    label: "草稿",
    value: "DRAFT",
}));
const __VLS_204 = __VLS_203({
    label: "草稿",
    value: "DRAFT",
}, ...__VLS_functionalComponentArgsRest(__VLS_203));
var __VLS_193;
var __VLS_189;
var __VLS_161;
{
    const { footer: __VLS_thisSlot } = __VLS_157.slots;
    const __VLS_206 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_207 = __VLS_asFunctionalComponent(__VLS_206, new __VLS_206({
        ...{ 'onClick': {} },
    }));
    const __VLS_208 = __VLS_207({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_207));
    let __VLS_210;
    let __VLS_211;
    let __VLS_212;
    const __VLS_213 = {
        onClick: (...[$event]) => {
            __VLS_ctx.tenantDialog = false;
        }
    };
    __VLS_209.slots.default;
    var __VLS_209;
    const __VLS_214 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_215 = __VLS_asFunctionalComponent(__VLS_214, new __VLS_214({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_216 = __VLS_215({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_215));
    let __VLS_218;
    let __VLS_219;
    let __VLS_220;
    const __VLS_221 = {
        onClick: (__VLS_ctx.saveTenant)
    };
    __VLS_217.slots.default;
    var __VLS_217;
}
var __VLS_157;
const __VLS_222 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_223 = __VLS_asFunctionalComponent(__VLS_222, new __VLS_222({
    modelValue: (__VLS_ctx.systemDialog),
    title: "新建系统",
    width: "560px",
}));
const __VLS_224 = __VLS_223({
    modelValue: (__VLS_ctx.systemDialog),
    title: "新建系统",
    width: "560px",
}, ...__VLS_functionalComponentArgsRest(__VLS_223));
__VLS_225.slots.default;
const __VLS_226 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_227 = __VLS_asFunctionalComponent(__VLS_226, new __VLS_226({
    model: (__VLS_ctx.systemForm),
    labelWidth: "108px",
}));
const __VLS_228 = __VLS_227({
    model: (__VLS_ctx.systemForm),
    labelWidth: "108px",
}, ...__VLS_functionalComponentArgsRest(__VLS_227));
__VLS_229.slots.default;
const __VLS_230 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_231 = __VLS_asFunctionalComponent(__VLS_230, new __VLS_230({
    label: "系统名称",
    required: true,
}));
const __VLS_232 = __VLS_231({
    label: "系统名称",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_231));
__VLS_233.slots.default;
const __VLS_234 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_235 = __VLS_asFunctionalComponent(__VLS_234, new __VLS_234({
    modelValue: (__VLS_ctx.systemForm.systemName),
}));
const __VLS_236 = __VLS_235({
    modelValue: (__VLS_ctx.systemForm.systemName),
}, ...__VLS_functionalComponentArgsRest(__VLS_235));
var __VLS_233;
const __VLS_238 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_239 = __VLS_asFunctionalComponent(__VLS_238, new __VLS_238({
    label: "系统编码",
    required: true,
}));
const __VLS_240 = __VLS_239({
    label: "系统编码",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_239));
__VLS_241.slots.default;
const __VLS_242 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_243 = __VLS_asFunctionalComponent(__VLS_242, new __VLS_242({
    modelValue: (__VLS_ctx.systemForm.systemCode),
}));
const __VLS_244 = __VLS_243({
    modelValue: (__VLS_ctx.systemForm.systemCode),
}, ...__VLS_functionalComponentArgsRest(__VLS_243));
var __VLS_241;
const __VLS_246 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_247 = __VLS_asFunctionalComponent(__VLS_246, new __VLS_246({
    label: "租户 ID",
    required: true,
}));
const __VLS_248 = __VLS_247({
    label: "租户 ID",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_247));
__VLS_249.slots.default;
const __VLS_250 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_251 = __VLS_asFunctionalComponent(__VLS_250, new __VLS_250({
    modelValue: (__VLS_ctx.systemForm.tenantId),
    min: (1),
}));
const __VLS_252 = __VLS_251({
    modelValue: (__VLS_ctx.systemForm.tenantId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_251));
var __VLS_249;
const __VLS_254 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_255 = __VLS_asFunctionalComponent(__VLS_254, new __VLS_254({
    label: "负责人账号",
}));
const __VLS_256 = __VLS_255({
    label: "负责人账号",
}, ...__VLS_functionalComponentArgsRest(__VLS_255));
__VLS_257.slots.default;
const __VLS_258 = {}.ElInputNumber;
/** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
// @ts-ignore
const __VLS_259 = __VLS_asFunctionalComponent(__VLS_258, new __VLS_258({
    modelValue: (__VLS_ctx.systemForm.ownerAccountId),
    min: (1),
}));
const __VLS_260 = __VLS_259({
    modelValue: (__VLS_ctx.systemForm.ownerAccountId),
    min: (1),
}, ...__VLS_functionalComponentArgsRest(__VLS_259));
var __VLS_257;
const __VLS_262 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_263 = __VLS_asFunctionalComponent(__VLS_262, new __VLS_262({
    label: "状态",
}));
const __VLS_264 = __VLS_263({
    label: "状态",
}, ...__VLS_functionalComponentArgsRest(__VLS_263));
__VLS_265.slots.default;
const __VLS_266 = {}.ElSelect;
/** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
// @ts-ignore
const __VLS_267 = __VLS_asFunctionalComponent(__VLS_266, new __VLS_266({
    modelValue: (__VLS_ctx.systemForm.status),
}));
const __VLS_268 = __VLS_267({
    modelValue: (__VLS_ctx.systemForm.status),
}, ...__VLS_functionalComponentArgsRest(__VLS_267));
__VLS_269.slots.default;
const __VLS_270 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_271 = __VLS_asFunctionalComponent(__VLS_270, new __VLS_270({
    label: "启用",
    value: "ENABLED",
}));
const __VLS_272 = __VLS_271({
    label: "启用",
    value: "ENABLED",
}, ...__VLS_functionalComponentArgsRest(__VLS_271));
const __VLS_274 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_275 = __VLS_asFunctionalComponent(__VLS_274, new __VLS_274({
    label: "停用",
    value: "DISABLED",
}));
const __VLS_276 = __VLS_275({
    label: "停用",
    value: "DISABLED",
}, ...__VLS_functionalComponentArgsRest(__VLS_275));
const __VLS_278 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_279 = __VLS_asFunctionalComponent(__VLS_278, new __VLS_278({
    label: "草稿",
    value: "DRAFT",
}));
const __VLS_280 = __VLS_279({
    label: "草稿",
    value: "DRAFT",
}, ...__VLS_functionalComponentArgsRest(__VLS_279));
var __VLS_269;
var __VLS_265;
const __VLS_282 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_283 = __VLS_asFunctionalComponent(__VLS_282, new __VLS_282({
    label: "描述",
}));
const __VLS_284 = __VLS_283({
    label: "描述",
}, ...__VLS_functionalComponentArgsRest(__VLS_283));
__VLS_285.slots.default;
const __VLS_286 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_287 = __VLS_asFunctionalComponent(__VLS_286, new __VLS_286({
    modelValue: (__VLS_ctx.systemForm.description),
    type: "textarea",
}));
const __VLS_288 = __VLS_287({
    modelValue: (__VLS_ctx.systemForm.description),
    type: "textarea",
}, ...__VLS_functionalComponentArgsRest(__VLS_287));
var __VLS_285;
var __VLS_229;
{
    const { footer: __VLS_thisSlot } = __VLS_225.slots;
    const __VLS_290 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_291 = __VLS_asFunctionalComponent(__VLS_290, new __VLS_290({
        ...{ 'onClick': {} },
    }));
    const __VLS_292 = __VLS_291({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_291));
    let __VLS_294;
    let __VLS_295;
    let __VLS_296;
    const __VLS_297 = {
        onClick: (...[$event]) => {
            __VLS_ctx.systemDialog = false;
        }
    };
    __VLS_293.slots.default;
    var __VLS_293;
    const __VLS_298 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_299 = __VLS_asFunctionalComponent(__VLS_298, new __VLS_298({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_300 = __VLS_299({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_299));
    let __VLS_302;
    let __VLS_303;
    let __VLS_304;
    const __VLS_305 = {
        onClick: (__VLS_ctx.saveSystem)
    };
    __VLS_301.slots.default;
    var __VLS_301;
}
var __VLS_225;
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
            CircleCheck: CircleCheck,
            CircleClose: CircleClose,
            Plus: Plus,
            Refresh: Refresh,
            Search: Search,
            SwitchButton: SwitchButton,
            StatusTag: StatusTag,
            context: context,
            tab: tab,
            tenantDialog: tenantDialog,
            systemDialog: systemDialog,
            tenantQuery: tenantQuery,
            systemQuery: systemQuery,
            loading: loading,
            tenants: tenants,
            systems: systems,
            tenantForm: tenantForm,
            systemForm: systemForm,
            openTenant: openTenant,
            openSystem: openSystem,
            loadTenants: loadTenants,
            loadSystems: loadSystems,
            saveTenant: saveTenant,
            saveSystem: saveSystem,
            changeStatus: changeStatus,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
