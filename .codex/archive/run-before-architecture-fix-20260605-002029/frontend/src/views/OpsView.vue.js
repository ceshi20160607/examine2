import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { opsApi } from '../api/modules';
import StatusTag from '../components/StatusTag.vue';
const tab = ref('health');
const configDialog = ref(false);
const health = ref({});
const loading = reactive({ logs: false, configs: false });
const logQuery = reactive({ account: '', action: '' });
const configQuery = reactive({ configKey: '' });
const logs = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const configs = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const configForm = reactive({ configKey: '', configValue: '', description: '', status: 'ENABLED' });
const healthItems = computed(() => [
    { label: '服务', value: String(health.value.serviceStatus || '') },
    { label: '数据库', value: String(health.value.databaseStatus || '') },
    { label: 'Redis', value: String(health.value.redisStatus || '') },
    { label: '文件存储', value: String(health.value.storageStatus || '') },
    { label: '脚本版本', value: String(health.value.scriptVersionStatus || '') }
]);
function isOk(value) {
    return ['UP', 'OK', 'NORMAL', 'SUCCESS'].includes(value.toUpperCase());
}
function maskValue(key, value) {
    const name = String(key || '').toLowerCase();
    if (/(password|secret|token|key|credential|sk)/.test(name))
        return '******';
    return value ?? '-';
}
function openConfig() {
    Object.assign(configForm, { configKey: '', configValue: '', description: '', status: 'ENABLED' });
    configDialog.value = true;
}
async function loadHealth() {
    health.value = await opsApi.health();
}
async function loadLogs() {
    loading.logs = true;
    try {
        Object.assign(logs, await opsApi.auditLogs(logQuery));
    }
    finally {
        loading.logs = false;
    }
}
async function loadConfigs() {
    loading.configs = true;
    try {
        Object.assign(configs, await opsApi.configs(configQuery));
    }
    finally {
        loading.configs = false;
    }
}
async function saveConfig() {
    await opsApi.createConfig({ ...configForm });
    ElMessage.success('配置已保存');
    configDialog.value = false;
    loadConfigs();
}
function tabChanged() {
    if (tab.value === 'health')
        loadHealth();
    if (tab.value === 'logs')
        loadLogs();
    if (tab.value === 'configs')
        loadConfigs();
}
function refreshAll() {
    tabChanged();
}
onMounted(() => {
    loadHealth();
    loadLogs();
    loadConfigs();
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
    icon: (__VLS_ctx.Refresh),
}));
const __VLS_2 = __VLS_1({
    ...{ 'onClick': {} },
    icon: (__VLS_ctx.Refresh),
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
let __VLS_4;
let __VLS_5;
let __VLS_6;
const __VLS_7 = {
    onClick: (__VLS_ctx.refreshAll)
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
    onClick: (__VLS_ctx.openConfig)
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
    onTabChange: (__VLS_ctx.tabChanged)
};
__VLS_19.slots.default;
const __VLS_24 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent(__VLS_24, new __VLS_24({
    label: "健康检查",
    name: "health",
}));
const __VLS_26 = __VLS_25({
    label: "健康检查",
    name: "health",
}, ...__VLS_functionalComponentArgsRest(__VLS_25));
const __VLS_28 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_29 = __VLS_asFunctionalComponent(__VLS_28, new __VLS_28({
    label: "审计日志",
    name: "logs",
}));
const __VLS_30 = __VLS_29({
    label: "审计日志",
    name: "logs",
}, ...__VLS_functionalComponentArgsRest(__VLS_29));
const __VLS_32 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_33 = __VLS_asFunctionalComponent(__VLS_32, new __VLS_32({
    label: "全局配置",
    name: "configs",
}));
const __VLS_34 = __VLS_33({
    label: "全局配置",
    name: "configs",
}, ...__VLS_functionalComponentArgsRest(__VLS_33));
var __VLS_19;
if (__VLS_ctx.tab === 'health') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "metric-grid" },
    });
    for (const [item] of __VLS_getVForSourceType((__VLS_ctx.healthItems))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            key: (item.label),
            ...{ class: "metric" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        (item.label);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({
            ...{ class: (__VLS_ctx.isOk(item.value) ? 'status-ok' : 'status-bad') },
        });
        (item.value || '-');
    }
    const __VLS_36 = {}.ElDescriptions;
    /** @type {[typeof __VLS_components.ElDescriptions, typeof __VLS_components.elDescriptions, typeof __VLS_components.ElDescriptions, typeof __VLS_components.elDescriptions, ]} */ ;
    // @ts-ignore
    const __VLS_37 = __VLS_asFunctionalComponent(__VLS_36, new __VLS_36({
        column: (2),
        border: true,
    }));
    const __VLS_38 = __VLS_37({
        column: (2),
        border: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_37));
    __VLS_39.slots.default;
    for (const [value, key] of __VLS_getVForSourceType((__VLS_ctx.health))) {
        const __VLS_40 = {}.ElDescriptionsItem;
        /** @type {[typeof __VLS_components.ElDescriptionsItem, typeof __VLS_components.elDescriptionsItem, typeof __VLS_components.ElDescriptionsItem, typeof __VLS_components.elDescriptionsItem, ]} */ ;
        // @ts-ignore
        const __VLS_41 = __VLS_asFunctionalComponent(__VLS_40, new __VLS_40({
            key: (String(key)),
            label: (String(key)),
        }));
        const __VLS_42 = __VLS_41({
            key: (String(key)),
            label: (String(key)),
        }, ...__VLS_functionalComponentArgsRest(__VLS_41));
        __VLS_43.slots.default;
        (value ?? '-');
        var __VLS_43;
    }
    var __VLS_39;
}
else if (__VLS_ctx.tab === 'logs') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "toolbar" },
    });
    const __VLS_44 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_45 = __VLS_asFunctionalComponent(__VLS_44, new __VLS_44({
        modelValue: (__VLS_ctx.logQuery.account),
        clearable: true,
        placeholder: "账号",
        ...{ style: {} },
    }));
    const __VLS_46 = __VLS_45({
        modelValue: (__VLS_ctx.logQuery.account),
        clearable: true,
        placeholder: "账号",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_45));
    const __VLS_48 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_49 = __VLS_asFunctionalComponent(__VLS_48, new __VLS_48({
        modelValue: (__VLS_ctx.logQuery.action),
        clearable: true,
        placeholder: "操作类型",
        ...{ style: {} },
    }));
    const __VLS_50 = __VLS_49({
        modelValue: (__VLS_ctx.logQuery.action),
        clearable: true,
        placeholder: "操作类型",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_49));
    const __VLS_52 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_53 = __VLS_asFunctionalComponent(__VLS_52, new __VLS_52({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Search),
    }));
    const __VLS_54 = __VLS_53({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Search),
    }, ...__VLS_functionalComponentArgsRest(__VLS_53));
    let __VLS_56;
    let __VLS_57;
    let __VLS_58;
    const __VLS_59 = {
        onClick: (__VLS_ctx.loadLogs)
    };
    __VLS_55.slots.default;
    var __VLS_55;
    const __VLS_60 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_61 = __VLS_asFunctionalComponent(__VLS_60, new __VLS_60({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Refresh),
    }));
    const __VLS_62 = __VLS_61({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Refresh),
    }, ...__VLS_functionalComponentArgsRest(__VLS_61));
    let __VLS_64;
    let __VLS_65;
    let __VLS_66;
    const __VLS_67 = {
        onClick: (__VLS_ctx.loadLogs)
    };
    var __VLS_63;
    const __VLS_68 = {}.ElTable;
    /** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
    // @ts-ignore
    const __VLS_69 = __VLS_asFunctionalComponent(__VLS_68, new __VLS_68({
        data: (__VLS_ctx.logs.records),
        border: true,
        height: "560",
    }));
    const __VLS_70 = __VLS_69({
        data: (__VLS_ctx.logs.records),
        border: true,
        height: "560",
    }, ...__VLS_functionalComponentArgsRest(__VLS_69));
    __VLS_asFunctionalDirective(__VLS_directives.vLoading)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.loading.logs) }, null, null);
    __VLS_71.slots.default;
    const __VLS_72 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_73 = __VLS_asFunctionalComponent(__VLS_72, new __VLS_72({
        prop: "id",
        label: "ID",
        width: "80",
    }));
    const __VLS_74 = __VLS_73({
        prop: "id",
        label: "ID",
        width: "80",
    }, ...__VLS_functionalComponentArgsRest(__VLS_73));
    const __VLS_76 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_77 = __VLS_asFunctionalComponent(__VLS_76, new __VLS_76({
        prop: "account",
        label: "账号",
        width: "140",
    }));
    const __VLS_78 = __VLS_77({
        prop: "account",
        label: "账号",
        width: "140",
    }, ...__VLS_functionalComponentArgsRest(__VLS_77));
    const __VLS_80 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_81 = __VLS_asFunctionalComponent(__VLS_80, new __VLS_80({
        prop: "action",
        label: "操作",
        minWidth: "150",
    }));
    const __VLS_82 = __VLS_81({
        prop: "action",
        label: "操作",
        minWidth: "150",
    }, ...__VLS_functionalComponentArgsRest(__VLS_81));
    const __VLS_84 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_85 = __VLS_asFunctionalComponent(__VLS_84, new __VLS_84({
        prop: "result",
        label: "结果",
        width: "110",
    }));
    const __VLS_86 = __VLS_85({
        prop: "result",
        label: "结果",
        width: "110",
    }, ...__VLS_functionalComponentArgsRest(__VLS_85));
    __VLS_87.slots.default;
    {
        const { default: __VLS_thisSlot } = __VLS_87.slots;
        const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
        /** @type {[typeof StatusTag, ]} */ ;
        // @ts-ignore
        const __VLS_88 = __VLS_asFunctionalComponent(StatusTag, new StatusTag({
            value: (row.result),
        }));
        const __VLS_89 = __VLS_88({
            value: (row.result),
        }, ...__VLS_functionalComponentArgsRest(__VLS_88));
    }
    var __VLS_87;
    const __VLS_91 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_92 = __VLS_asFunctionalComponent(__VLS_91, new __VLS_91({
        prop: "traceId",
        label: "traceId",
        minWidth: "180",
        showOverflowTooltip: true,
    }));
    const __VLS_93 = __VLS_92({
        prop: "traceId",
        label: "traceId",
        minWidth: "180",
        showOverflowTooltip: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_92));
    const __VLS_95 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_96 = __VLS_asFunctionalComponent(__VLS_95, new __VLS_95({
        prop: "errorMessage",
        label: "错误摘要",
        minWidth: "220",
        showOverflowTooltip: true,
    }));
    const __VLS_97 = __VLS_96({
        prop: "errorMessage",
        label: "错误摘要",
        minWidth: "220",
        showOverflowTooltip: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_96));
    const __VLS_99 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_100 = __VLS_asFunctionalComponent(__VLS_99, new __VLS_99({
        prop: "createdAt",
        label: "时间",
        minWidth: "160",
    }));
    const __VLS_101 = __VLS_100({
        prop: "createdAt",
        label: "时间",
        minWidth: "160",
    }, ...__VLS_functionalComponentArgsRest(__VLS_100));
    var __VLS_71;
}
else {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "toolbar" },
    });
    const __VLS_103 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_104 = __VLS_asFunctionalComponent(__VLS_103, new __VLS_103({
        modelValue: (__VLS_ctx.configQuery.configKey),
        clearable: true,
        placeholder: "配置键",
        ...{ style: {} },
    }));
    const __VLS_105 = __VLS_104({
        modelValue: (__VLS_ctx.configQuery.configKey),
        clearable: true,
        placeholder: "配置键",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_104));
    const __VLS_107 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_108 = __VLS_asFunctionalComponent(__VLS_107, new __VLS_107({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Search),
    }));
    const __VLS_109 = __VLS_108({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Search),
    }, ...__VLS_functionalComponentArgsRest(__VLS_108));
    let __VLS_111;
    let __VLS_112;
    let __VLS_113;
    const __VLS_114 = {
        onClick: (__VLS_ctx.loadConfigs)
    };
    __VLS_110.slots.default;
    var __VLS_110;
    const __VLS_115 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_116 = __VLS_asFunctionalComponent(__VLS_115, new __VLS_115({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Refresh),
    }));
    const __VLS_117 = __VLS_116({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Refresh),
    }, ...__VLS_functionalComponentArgsRest(__VLS_116));
    let __VLS_119;
    let __VLS_120;
    let __VLS_121;
    const __VLS_122 = {
        onClick: (__VLS_ctx.loadConfigs)
    };
    var __VLS_118;
    const __VLS_123 = {}.ElTable;
    /** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
    // @ts-ignore
    const __VLS_124 = __VLS_asFunctionalComponent(__VLS_123, new __VLS_123({
        data: (__VLS_ctx.configs.records),
        border: true,
        height: "560",
    }));
    const __VLS_125 = __VLS_124({
        data: (__VLS_ctx.configs.records),
        border: true,
        height: "560",
    }, ...__VLS_functionalComponentArgsRest(__VLS_124));
    __VLS_asFunctionalDirective(__VLS_directives.vLoading)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.loading.configs) }, null, null);
    __VLS_126.slots.default;
    const __VLS_127 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_128 = __VLS_asFunctionalComponent(__VLS_127, new __VLS_127({
        prop: "id",
        label: "ID",
        width: "80",
    }));
    const __VLS_129 = __VLS_128({
        prop: "id",
        label: "ID",
        width: "80",
    }, ...__VLS_functionalComponentArgsRest(__VLS_128));
    const __VLS_131 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_132 = __VLS_asFunctionalComponent(__VLS_131, new __VLS_131({
        prop: "configKey",
        label: "配置键",
        minWidth: "180",
    }));
    const __VLS_133 = __VLS_132({
        prop: "configKey",
        label: "配置键",
        minWidth: "180",
    }, ...__VLS_functionalComponentArgsRest(__VLS_132));
    const __VLS_135 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_136 = __VLS_asFunctionalComponent(__VLS_135, new __VLS_135({
        prop: "configValue",
        label: "配置值",
        minWidth: "220",
    }));
    const __VLS_137 = __VLS_136({
        prop: "configValue",
        label: "配置值",
        minWidth: "220",
    }, ...__VLS_functionalComponentArgsRest(__VLS_136));
    __VLS_138.slots.default;
    {
        const { default: __VLS_thisSlot } = __VLS_138.slots;
        const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
        (__VLS_ctx.maskValue(row.configKey, row.configValue));
    }
    var __VLS_138;
    const __VLS_139 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_140 = __VLS_asFunctionalComponent(__VLS_139, new __VLS_139({
        prop: "description",
        label: "说明",
        minWidth: "220",
    }));
    const __VLS_141 = __VLS_140({
        prop: "description",
        label: "说明",
        minWidth: "220",
    }, ...__VLS_functionalComponentArgsRest(__VLS_140));
    const __VLS_143 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_144 = __VLS_asFunctionalComponent(__VLS_143, new __VLS_143({
        prop: "status",
        label: "状态",
        width: "110",
    }));
    const __VLS_145 = __VLS_144({
        prop: "status",
        label: "状态",
        width: "110",
    }, ...__VLS_functionalComponentArgsRest(__VLS_144));
    __VLS_146.slots.default;
    {
        const { default: __VLS_thisSlot } = __VLS_146.slots;
        const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
        /** @type {[typeof StatusTag, ]} */ ;
        // @ts-ignore
        const __VLS_147 = __VLS_asFunctionalComponent(StatusTag, new StatusTag({
            value: (row.status),
        }));
        const __VLS_148 = __VLS_147({
            value: (row.status),
        }, ...__VLS_functionalComponentArgsRest(__VLS_147));
    }
    var __VLS_146;
    var __VLS_126;
}
const __VLS_150 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_151 = __VLS_asFunctionalComponent(__VLS_150, new __VLS_150({
    modelValue: (__VLS_ctx.configDialog),
    title: "新建全局配置",
    width: "620px",
}));
const __VLS_152 = __VLS_151({
    modelValue: (__VLS_ctx.configDialog),
    title: "新建全局配置",
    width: "620px",
}, ...__VLS_functionalComponentArgsRest(__VLS_151));
__VLS_153.slots.default;
const __VLS_154 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_155 = __VLS_asFunctionalComponent(__VLS_154, new __VLS_154({
    model: (__VLS_ctx.configForm),
    labelWidth: "108px",
}));
const __VLS_156 = __VLS_155({
    model: (__VLS_ctx.configForm),
    labelWidth: "108px",
}, ...__VLS_functionalComponentArgsRest(__VLS_155));
__VLS_157.slots.default;
const __VLS_158 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_159 = __VLS_asFunctionalComponent(__VLS_158, new __VLS_158({
    label: "配置键",
    required: true,
}));
const __VLS_160 = __VLS_159({
    label: "配置键",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_159));
__VLS_161.slots.default;
const __VLS_162 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_163 = __VLS_asFunctionalComponent(__VLS_162, new __VLS_162({
    modelValue: (__VLS_ctx.configForm.configKey),
}));
const __VLS_164 = __VLS_163({
    modelValue: (__VLS_ctx.configForm.configKey),
}, ...__VLS_functionalComponentArgsRest(__VLS_163));
var __VLS_161;
const __VLS_166 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_167 = __VLS_asFunctionalComponent(__VLS_166, new __VLS_166({
    label: "配置值",
    required: true,
}));
const __VLS_168 = __VLS_167({
    label: "配置值",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_167));
__VLS_169.slots.default;
const __VLS_170 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_171 = __VLS_asFunctionalComponent(__VLS_170, new __VLS_170({
    modelValue: (__VLS_ctx.configForm.configValue),
    type: "textarea",
    rows: (4),
}));
const __VLS_172 = __VLS_171({
    modelValue: (__VLS_ctx.configForm.configValue),
    type: "textarea",
    rows: (4),
}, ...__VLS_functionalComponentArgsRest(__VLS_171));
var __VLS_169;
const __VLS_174 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_175 = __VLS_asFunctionalComponent(__VLS_174, new __VLS_174({
    label: "说明",
}));
const __VLS_176 = __VLS_175({
    label: "说明",
}, ...__VLS_functionalComponentArgsRest(__VLS_175));
__VLS_177.slots.default;
const __VLS_178 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_179 = __VLS_asFunctionalComponent(__VLS_178, new __VLS_178({
    modelValue: (__VLS_ctx.configForm.description),
    type: "textarea",
    rows: (3),
}));
const __VLS_180 = __VLS_179({
    modelValue: (__VLS_ctx.configForm.description),
    type: "textarea",
    rows: (3),
}, ...__VLS_functionalComponentArgsRest(__VLS_179));
var __VLS_177;
const __VLS_182 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_183 = __VLS_asFunctionalComponent(__VLS_182, new __VLS_182({
    label: "状态",
}));
const __VLS_184 = __VLS_183({
    label: "状态",
}, ...__VLS_functionalComponentArgsRest(__VLS_183));
__VLS_185.slots.default;
const __VLS_186 = {}.ElSelect;
/** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
// @ts-ignore
const __VLS_187 = __VLS_asFunctionalComponent(__VLS_186, new __VLS_186({
    modelValue: (__VLS_ctx.configForm.status),
}));
const __VLS_188 = __VLS_187({
    modelValue: (__VLS_ctx.configForm.status),
}, ...__VLS_functionalComponentArgsRest(__VLS_187));
__VLS_189.slots.default;
const __VLS_190 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_191 = __VLS_asFunctionalComponent(__VLS_190, new __VLS_190({
    label: "启用",
    value: "ENABLED",
}));
const __VLS_192 = __VLS_191({
    label: "启用",
    value: "ENABLED",
}, ...__VLS_functionalComponentArgsRest(__VLS_191));
const __VLS_194 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_195 = __VLS_asFunctionalComponent(__VLS_194, new __VLS_194({
    label: "停用",
    value: "DISABLED",
}));
const __VLS_196 = __VLS_195({
    label: "停用",
    value: "DISABLED",
}, ...__VLS_functionalComponentArgsRest(__VLS_195));
var __VLS_189;
var __VLS_185;
var __VLS_157;
{
    const { footer: __VLS_thisSlot } = __VLS_153.slots;
    const __VLS_198 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_199 = __VLS_asFunctionalComponent(__VLS_198, new __VLS_198({
        ...{ 'onClick': {} },
    }));
    const __VLS_200 = __VLS_199({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_199));
    let __VLS_202;
    let __VLS_203;
    let __VLS_204;
    const __VLS_205 = {
        onClick: (...[$event]) => {
            __VLS_ctx.configDialog = false;
        }
    };
    __VLS_201.slots.default;
    var __VLS_201;
    const __VLS_206 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_207 = __VLS_asFunctionalComponent(__VLS_206, new __VLS_206({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_208 = __VLS_207({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_207));
    let __VLS_210;
    let __VLS_211;
    let __VLS_212;
    const __VLS_213 = {
        onClick: (__VLS_ctx.saveConfig)
    };
    __VLS_209.slots.default;
    var __VLS_209;
}
var __VLS_153;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['header-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['metric-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['metric']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            Check: Check,
            Plus: Plus,
            Refresh: Refresh,
            Search: Search,
            StatusTag: StatusTag,
            tab: tab,
            configDialog: configDialog,
            health: health,
            loading: loading,
            logQuery: logQuery,
            configQuery: configQuery,
            logs: logs,
            configs: configs,
            configForm: configForm,
            healthItems: healthItems,
            isOk: isOk,
            maskValue: maskValue,
            openConfig: openConfig,
            loadLogs: loadLogs,
            loadConfigs: loadConfigs,
            saveConfig: saveConfig,
            tabChanged: tabChanged,
            refreshAll: refreshAll,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
