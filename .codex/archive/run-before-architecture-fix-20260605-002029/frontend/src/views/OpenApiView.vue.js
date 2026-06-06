import axios from 'axios';
import { hmacSha256Base64Url, sha256Hex } from '../api/openapi-sign';
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Check, Connection, Edit, Key, Location, Lock, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { openApiManageApi } from '../api/modules';
import { useContextStore } from '../stores/context';
import StatusTag from '../components/StatusTag.vue';
import SecurityForm from '../components/SecurityForm.vue';
const context = useContextStore();
const tab = ref('clients');
const loading = ref(false);
const clientDialog = ref(false);
const clientQuery = reactive({ keyword: '' });
const clients = reactive({ pageNo: 1, pageSize: 20, total: 0, records: [] });
const clientForm = reactive({
    id: undefined,
    clientId: '',
    clientName: '',
    status: 'ENABLED',
    rateLimitRule: '{ "qps": 10 }'
});
const credentialForm = reactive({ clientPk: undefined, keyVersion: undefined, expiresAt: '' });
const scopeForm = reactive({ clientPk: undefined, scopeType: 'SYSTEM', scopeValue: '' });
const ipForm = reactive({ clientPk: undefined, ipList: '' });
const scopeTypes = ['SYSTEM', 'TENANT', 'APP', 'MODULE', 'ACTION', 'FIELD'];
const consoleResult = ref('');
const consoleForm = reactive({
    clientId: '',
    keyVersion: 1,
    secret: '',
    method: 'GET',
    moduleId: undefined,
    recordId: undefined,
    body: '{\n  "systemId": 1,\n  "tenantId": 1,\n  "appId": 1,\n  "moduleId": 1,\n  "values": []\n}'
});
const contextBlockReason = computed(() => (!context.hasSystemContext ? 'Enter system context before loading OpenAPI clients.' : ''));
const clientEmptyText = computed(() => contextBlockReason.value || 'No data');
function clearClients() {
    Object.assign(clients, { pageNo: 1, pageSize: 20, total: 0, records: [] });
}
function openClient(row) {
    if (!context.hasSystemContext) {
        ElMessage.warning('Enter system context before creating OpenAPI clients.');
        return;
    }
    Object.assign(clientForm, {
        id: row ? Number(row.id ?? row.clientPk) || undefined : undefined,
        clientId: String(row?.clientId ?? ''),
        clientName: String(row?.clientName ?? ''),
        status: String(row?.status ?? 'ENABLED'),
        rateLimitRule: String(row?.rateLimitRule ?? '{ "qps": 10 }')
    });
    clientDialog.value = true;
}
function openCredential(row) {
    tab.value = 'security';
    credentialForm.clientPk = Number(row.clientPk ?? row.id);
}
function openScope(row) {
    tab.value = 'security';
    scopeForm.clientPk = Number(row.clientPk ?? row.id);
}
function openIp(row) {
    tab.value = 'security';
    ipForm.clientPk = Number(row.clientPk ?? row.id);
}
async function loadClients() {
    if (contextBlockReason.value) {
        clearClients();
        ElMessage.warning(contextBlockReason.value);
        return;
    }
    loading.value = true;
    try {
        Object.assign(clients, await openApiManageApi.clients(clientQuery));
    }
    finally {
        loading.value = false;
    }
}
async function saveClient() {
    if (!clientForm.clientId || !clientForm.clientName) {
        ElMessage.warning('clientId and clientName are required.');
        return;
    }
    const payload = {
        ...(clientForm.id ? { id: clientForm.id } : {}),
        clientId: clientForm.clientId,
        clientName: clientForm.clientName,
        status: clientForm.status,
        rateLimitRule: clientForm.rateLimitRule
    };
    await openApiManageApi.createClient(payload);
    ElMessage.success(clientForm.id ? '外部应用已保存' : '外部应用已创建');
    clientDialog.value = false;
    loadClients();
}
async function saveCredential() {
    if (!credentialForm.clientPk) {
        ElMessage.warning('clientPk is required.');
        return;
    }
    const result = await openApiManageApi.createCredential({
        clientPk: credentialForm.clientPk,
        ...(credentialForm.keyVersion ? { keyVersion: credentialForm.keyVersion } : {}),
        ...(credentialForm.expiresAt ? { expiresAt: credentialForm.expiresAt } : {})
    });
    const secretOnce = String(result.secretOnce || '');
    await ElMessageBox.alert(secretOnce || '后端未返回 secretOnce', '一次性密钥', {
        confirmButtonText: '我已记录',
        type: 'warning'
    });
}
async function saveScope() {
    if (!scopeForm.clientPk || !scopeForm.scopeType || !scopeForm.scopeValue) {
        ElMessage.warning('clientPk, scopeType and scopeValue are required.');
        return;
    }
    await openApiManageApi.saveScope({ ...scopeForm });
    ElMessage.success('授权范围已保存');
}
async function saveIp() {
    if (!ipForm.clientPk || !ipForm.ipList.trim()) {
        ElMessage.warning('clientPk and IP whitelist are required.');
        return;
    }
    const ipList = Array.from(new Set(ipForm.ipList.split(/\r?\n/).map((item) => item.trim()).filter(Boolean)));
    await openApiManageApi.saveIpWhitelist({ clientPk: ipForm.clientPk, ipList });
    ElMessage.success('IP 白名单已保存');
}
async function callOpenApi() {
    const method = consoleForm.method;
    if (!consoleForm.clientId || !consoleForm.keyVersion || !consoleForm.secret) {
        ElMessage.warning('clientId, keyVersion and secret are required.');
        return;
    }
    if (method === 'GET' && (!consoleForm.moduleId || !consoleForm.recordId)) {
        ElMessage.warning('moduleId and recordId are required for OpenAPI GET.');
        return;
    }
    const body = method === 'POST' ? consoleForm.body || '{}' : '';
    if (method === 'POST') {
        const payload = JSON.parse(body);
        if (!payload.systemId || !payload.tenantId || !payload.appId || !payload.moduleId) {
            ElMessage.warning('POST body must include systemId, tenantId, appId and moduleId.');
            return;
        }
    }
    const uri = method === 'GET'
        ? `/api/v1/open/records/${consoleForm.moduleId}/${consoleForm.recordId}`
        : '/api/v1/open/records';
    const timestamp = String(Math.floor(Date.now() / 1000));
    const nonce = crypto.randomUUID();
    const bodyHash = await sha256Hex(body);
    const canonical = `${method}\n${uri}\n${timestamp}\n${nonce}\n${bodyHash}`;
    const signature = await hmacSha256Base64Url(consoleForm.secret, canonical);
    const response = await axios.request({
        method,
        url: uri,
        baseURL: import.meta.env.VITE_API_BASE_URL || '',
        data: method === 'POST' ? JSON.parse(body) : undefined,
        headers: {
            'X-Open-Client-Id': consoleForm.clientId,
            'X-Open-Key-Version': String(consoleForm.keyVersion),
            'X-Open-Timestamp': timestamp,
            'X-Open-Nonce': nonce,
            'X-Open-Signature': signature,
            'Idempotency-Key': method === 'POST' ? crypto.randomUUID() : undefined
        }
    });
    consoleResult.value = JSON.stringify(response.data, null, 2);
}
onMounted(loadClients);
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
        __VLS_ctx.openClient();
    }
};
__VLS_3.slots.default;
var __VLS_3;
const __VLS_8 = {}.ElTabs;
/** @type {[typeof __VLS_components.ElTabs, typeof __VLS_components.elTabs, typeof __VLS_components.ElTabs, typeof __VLS_components.elTabs, ]} */ ;
// @ts-ignore
const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
    ...{ 'onTabChange': {} },
    modelValue: (__VLS_ctx.tab),
}));
const __VLS_10 = __VLS_9({
    ...{ 'onTabChange': {} },
    modelValue: (__VLS_ctx.tab),
}, ...__VLS_functionalComponentArgsRest(__VLS_9));
let __VLS_12;
let __VLS_13;
let __VLS_14;
const __VLS_15 = {
    onTabChange: (...[$event]) => {
        __VLS_ctx.tab === 'clients' && __VLS_ctx.loadClients();
    }
};
__VLS_11.slots.default;
const __VLS_16 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_17 = __VLS_asFunctionalComponent(__VLS_16, new __VLS_16({
    label: "外部应用",
    name: "clients",
}));
const __VLS_18 = __VLS_17({
    label: "外部应用",
    name: "clients",
}, ...__VLS_functionalComponentArgsRest(__VLS_17));
const __VLS_20 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_21 = __VLS_asFunctionalComponent(__VLS_20, new __VLS_20({
    label: "凭证/授权",
    name: "security",
}));
const __VLS_22 = __VLS_21({
    label: "凭证/授权",
    name: "security",
}, ...__VLS_functionalComponentArgsRest(__VLS_21));
const __VLS_24 = {}.ElTabPane;
/** @type {[typeof __VLS_components.ElTabPane, typeof __VLS_components.elTabPane, ]} */ ;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent(__VLS_24, new __VLS_24({
    label: "HMAC 调用台",
    name: "console",
}));
const __VLS_26 = __VLS_25({
    label: "HMAC 调用台",
    name: "console",
}, ...__VLS_functionalComponentArgsRest(__VLS_25));
var __VLS_11;
if (__VLS_ctx.tab === 'clients') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "toolbar" },
    });
    const __VLS_28 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_29 = __VLS_asFunctionalComponent(__VLS_28, new __VLS_28({
        modelValue: (__VLS_ctx.clientQuery.keyword),
        clearable: true,
        placeholder: "应用名称/clientId",
        ...{ style: {} },
    }));
    const __VLS_30 = __VLS_29({
        modelValue: (__VLS_ctx.clientQuery.keyword),
        clearable: true,
        placeholder: "应用名称/clientId",
        ...{ style: {} },
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
        onClick: (__VLS_ctx.loadClients)
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
        onClick: (__VLS_ctx.loadClients)
    };
    var __VLS_43;
    const __VLS_48 = {}.ElTable;
    /** @type {[typeof __VLS_components.ElTable, typeof __VLS_components.elTable, typeof __VLS_components.ElTable, typeof __VLS_components.elTable, ]} */ ;
    // @ts-ignore
    const __VLS_49 = __VLS_asFunctionalComponent(__VLS_48, new __VLS_48({
        data: (__VLS_ctx.clients.records),
        border: true,
        height: "560",
        emptyText: (__VLS_ctx.clientEmptyText),
    }));
    const __VLS_50 = __VLS_49({
        data: (__VLS_ctx.clients.records),
        border: true,
        height: "560",
        emptyText: (__VLS_ctx.clientEmptyText),
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
    const __VLS_56 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_57 = __VLS_asFunctionalComponent(__VLS_56, new __VLS_56({
        prop: "clientId",
        label: "clientId",
        minWidth: "170",
    }));
    const __VLS_58 = __VLS_57({
        prop: "clientId",
        label: "clientId",
        minWidth: "170",
    }, ...__VLS_functionalComponentArgsRest(__VLS_57));
    const __VLS_60 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_61 = __VLS_asFunctionalComponent(__VLS_60, new __VLS_60({
        prop: "clientName",
        label: "应用名称",
        minWidth: "170",
    }));
    const __VLS_62 = __VLS_61({
        prop: "clientName",
        label: "应用名称",
        minWidth: "170",
    }, ...__VLS_functionalComponentArgsRest(__VLS_61));
    const __VLS_64 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_65 = __VLS_asFunctionalComponent(__VLS_64, new __VLS_64({
        prop: "status",
        label: "状态",
        width: "120",
    }));
    const __VLS_66 = __VLS_65({
        prop: "status",
        label: "状态",
        width: "120",
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
    const __VLS_71 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_72 = __VLS_asFunctionalComponent(__VLS_71, new __VLS_71({
        prop: "lastCallAt",
        label: "最近调用",
        minWidth: "160",
    }));
    const __VLS_73 = __VLS_72({
        prop: "lastCallAt",
        label: "最近调用",
        minWidth: "160",
    }, ...__VLS_functionalComponentArgsRest(__VLS_72));
    const __VLS_75 = {}.ElTableColumn;
    /** @type {[typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, typeof __VLS_components.ElTableColumn, typeof __VLS_components.elTableColumn, ]} */ ;
    // @ts-ignore
    const __VLS_76 = __VLS_asFunctionalComponent(__VLS_75, new __VLS_75({
        label: "操作",
        width: "280",
        fixed: "right",
    }));
    const __VLS_77 = __VLS_76({
        label: "操作",
        width: "280",
        fixed: "right",
    }, ...__VLS_functionalComponentArgsRest(__VLS_76));
    __VLS_78.slots.default;
    {
        const { default: __VLS_thisSlot } = __VLS_78.slots;
        const [{ row }] = __VLS_getSlotParams(__VLS_thisSlot);
        const __VLS_79 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_80 = __VLS_asFunctionalComponent(__VLS_79, new __VLS_79({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Edit),
            size: "small",
        }));
        const __VLS_81 = __VLS_80({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Edit),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_80));
        let __VLS_83;
        let __VLS_84;
        let __VLS_85;
        const __VLS_86 = {
            onClick: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'clients'))
                    return;
                __VLS_ctx.openClient(row);
            }
        };
        __VLS_82.slots.default;
        var __VLS_82;
        const __VLS_87 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_88 = __VLS_asFunctionalComponent(__VLS_87, new __VLS_87({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Key),
            size: "small",
        }));
        const __VLS_89 = __VLS_88({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Key),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_88));
        let __VLS_91;
        let __VLS_92;
        let __VLS_93;
        const __VLS_94 = {
            onClick: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'clients'))
                    return;
                __VLS_ctx.openCredential(row);
            }
        };
        __VLS_90.slots.default;
        var __VLS_90;
        const __VLS_95 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_96 = __VLS_asFunctionalComponent(__VLS_95, new __VLS_95({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Lock),
            size: "small",
        }));
        const __VLS_97 = __VLS_96({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Lock),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_96));
        let __VLS_99;
        let __VLS_100;
        let __VLS_101;
        const __VLS_102 = {
            onClick: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'clients'))
                    return;
                __VLS_ctx.openScope(row);
            }
        };
        __VLS_98.slots.default;
        var __VLS_98;
        const __VLS_103 = {}.ElButton;
        /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
        // @ts-ignore
        const __VLS_104 = __VLS_asFunctionalComponent(__VLS_103, new __VLS_103({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Location),
            size: "small",
        }));
        const __VLS_105 = __VLS_104({
            ...{ 'onClick': {} },
            icon: (__VLS_ctx.Location),
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_104));
        let __VLS_107;
        let __VLS_108;
        let __VLS_109;
        const __VLS_110 = {
            onClick: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'clients'))
                    return;
                __VLS_ctx.openIp(row);
            }
        };
        __VLS_106.slots.default;
        var __VLS_106;
    }
    var __VLS_78;
    var __VLS_51;
}
else if (__VLS_ctx.tab === 'security') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "split-grid" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "page-title" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h1, __VLS_intrinsicElements.h1)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
    /** @type {[typeof SecurityForm, ]} */ ;
    // @ts-ignore
    const __VLS_111 = __VLS_asFunctionalComponent(SecurityForm, new SecurityForm({
        ...{ 'onSubmit': {} },
        mode: "credential",
        model: (__VLS_ctx.credentialForm),
    }));
    const __VLS_112 = __VLS_111({
        ...{ 'onSubmit': {} },
        mode: "credential",
        model: (__VLS_ctx.credentialForm),
    }, ...__VLS_functionalComponentArgsRest(__VLS_111));
    let __VLS_114;
    let __VLS_115;
    let __VLS_116;
    const __VLS_117 = {
        onSubmit: (__VLS_ctx.saveCredential)
    };
    var __VLS_113;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "page-title" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h1, __VLS_intrinsicElements.h1)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
    const __VLS_118 = {}.ElForm;
    /** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
    // @ts-ignore
    const __VLS_119 = __VLS_asFunctionalComponent(__VLS_118, new __VLS_118({
        model: (__VLS_ctx.scopeForm),
        labelWidth: "108px",
    }));
    const __VLS_120 = __VLS_119({
        model: (__VLS_ctx.scopeForm),
        labelWidth: "108px",
    }, ...__VLS_functionalComponentArgsRest(__VLS_119));
    __VLS_121.slots.default;
    const __VLS_122 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_123 = __VLS_asFunctionalComponent(__VLS_122, new __VLS_122({
        label: "clientPk",
    }));
    const __VLS_124 = __VLS_123({
        label: "clientPk",
    }, ...__VLS_functionalComponentArgsRest(__VLS_123));
    __VLS_125.slots.default;
    const __VLS_126 = {}.ElInputNumber;
    /** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
    // @ts-ignore
    const __VLS_127 = __VLS_asFunctionalComponent(__VLS_126, new __VLS_126({
        modelValue: (__VLS_ctx.scopeForm.clientPk),
        min: (1),
    }));
    const __VLS_128 = __VLS_127({
        modelValue: (__VLS_ctx.scopeForm.clientPk),
        min: (1),
    }, ...__VLS_functionalComponentArgsRest(__VLS_127));
    var __VLS_125;
    const __VLS_130 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_131 = __VLS_asFunctionalComponent(__VLS_130, new __VLS_130({
        label: "授权类型",
    }));
    const __VLS_132 = __VLS_131({
        label: "授权类型",
    }, ...__VLS_functionalComponentArgsRest(__VLS_131));
    __VLS_133.slots.default;
    const __VLS_134 = {}.ElSelect;
    /** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
    // @ts-ignore
    const __VLS_135 = __VLS_asFunctionalComponent(__VLS_134, new __VLS_134({
        modelValue: (__VLS_ctx.scopeForm.scopeType),
    }));
    const __VLS_136 = __VLS_135({
        modelValue: (__VLS_ctx.scopeForm.scopeType),
    }, ...__VLS_functionalComponentArgsRest(__VLS_135));
    __VLS_137.slots.default;
    for (const [item] of __VLS_getVForSourceType((__VLS_ctx.scopeTypes))) {
        const __VLS_138 = {}.ElOption;
        /** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
        // @ts-ignore
        const __VLS_139 = __VLS_asFunctionalComponent(__VLS_138, new __VLS_138({
            key: (item),
            label: (item),
            value: (item),
        }));
        const __VLS_140 = __VLS_139({
            key: (item),
            label: (item),
            value: (item),
        }, ...__VLS_functionalComponentArgsRest(__VLS_139));
    }
    var __VLS_137;
    var __VLS_133;
    const __VLS_142 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_143 = __VLS_asFunctionalComponent(__VLS_142, new __VLS_142({
        label: "授权值",
    }));
    const __VLS_144 = __VLS_143({
        label: "授权值",
    }, ...__VLS_functionalComponentArgsRest(__VLS_143));
    __VLS_145.slots.default;
    const __VLS_146 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_147 = __VLS_asFunctionalComponent(__VLS_146, new __VLS_146({
        modelValue: (__VLS_ctx.scopeForm.scopeValue),
    }));
    const __VLS_148 = __VLS_147({
        modelValue: (__VLS_ctx.scopeForm.scopeValue),
    }, ...__VLS_functionalComponentArgsRest(__VLS_147));
    var __VLS_145;
    const __VLS_150 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_151 = __VLS_asFunctionalComponent(__VLS_150, new __VLS_150({}));
    const __VLS_152 = __VLS_151({}, ...__VLS_functionalComponentArgsRest(__VLS_151));
    __VLS_153.slots.default;
    const __VLS_154 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_155 = __VLS_asFunctionalComponent(__VLS_154, new __VLS_154({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_156 = __VLS_155({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_155));
    let __VLS_158;
    let __VLS_159;
    let __VLS_160;
    const __VLS_161 = {
        onClick: (__VLS_ctx.saveScope)
    };
    __VLS_157.slots.default;
    var __VLS_157;
    var __VLS_153;
    const __VLS_162 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_163 = __VLS_asFunctionalComponent(__VLS_162, new __VLS_162({
        label: "IP 白名单",
    }));
    const __VLS_164 = __VLS_163({
        label: "IP 白名单",
    }, ...__VLS_functionalComponentArgsRest(__VLS_163));
    __VLS_165.slots.default;
    const __VLS_166 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_167 = __VLS_asFunctionalComponent(__VLS_166, new __VLS_166({
        modelValue: (__VLS_ctx.ipForm.ipList),
        type: "textarea",
        rows: (5),
        placeholder: "每行一个 IP 或 CIDR",
    }));
    const __VLS_168 = __VLS_167({
        modelValue: (__VLS_ctx.ipForm.ipList),
        type: "textarea",
        rows: (5),
        placeholder: "每行一个 IP 或 CIDR",
    }, ...__VLS_functionalComponentArgsRest(__VLS_167));
    var __VLS_165;
    const __VLS_170 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_171 = __VLS_asFunctionalComponent(__VLS_170, new __VLS_170({}));
    const __VLS_172 = __VLS_171({}, ...__VLS_functionalComponentArgsRest(__VLS_171));
    __VLS_173.slots.default;
    const __VLS_174 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_175 = __VLS_asFunctionalComponent(__VLS_174, new __VLS_174({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_176 = __VLS_175({
        ...{ 'onClick': {} },
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_175));
    let __VLS_178;
    let __VLS_179;
    let __VLS_180;
    const __VLS_181 = {
        onClick: (__VLS_ctx.saveIp)
    };
    __VLS_177.slots.default;
    var __VLS_177;
    var __VLS_173;
    var __VLS_121;
}
else {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "split-grid" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "page-title" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h1, __VLS_intrinsicElements.h1)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
    const __VLS_182 = {}.ElForm;
    /** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
    // @ts-ignore
    const __VLS_183 = __VLS_asFunctionalComponent(__VLS_182, new __VLS_182({
        model: (__VLS_ctx.consoleForm),
        labelWidth: "124px",
    }));
    const __VLS_184 = __VLS_183({
        model: (__VLS_ctx.consoleForm),
        labelWidth: "124px",
    }, ...__VLS_functionalComponentArgsRest(__VLS_183));
    __VLS_185.slots.default;
    const __VLS_186 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_187 = __VLS_asFunctionalComponent(__VLS_186, new __VLS_186({
        label: "clientId",
    }));
    const __VLS_188 = __VLS_187({
        label: "clientId",
    }, ...__VLS_functionalComponentArgsRest(__VLS_187));
    __VLS_189.slots.default;
    const __VLS_190 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_191 = __VLS_asFunctionalComponent(__VLS_190, new __VLS_190({
        modelValue: (__VLS_ctx.consoleForm.clientId),
    }));
    const __VLS_192 = __VLS_191({
        modelValue: (__VLS_ctx.consoleForm.clientId),
    }, ...__VLS_functionalComponentArgsRest(__VLS_191));
    var __VLS_189;
    const __VLS_194 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_195 = __VLS_asFunctionalComponent(__VLS_194, new __VLS_194({
        label: "keyVersion",
    }));
    const __VLS_196 = __VLS_195({
        label: "keyVersion",
    }, ...__VLS_functionalComponentArgsRest(__VLS_195));
    __VLS_197.slots.default;
    const __VLS_198 = {}.ElInputNumber;
    /** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
    // @ts-ignore
    const __VLS_199 = __VLS_asFunctionalComponent(__VLS_198, new __VLS_198({
        modelValue: (__VLS_ctx.consoleForm.keyVersion),
        min: (1),
        controlsPosition: "right",
    }));
    const __VLS_200 = __VLS_199({
        modelValue: (__VLS_ctx.consoleForm.keyVersion),
        min: (1),
        controlsPosition: "right",
    }, ...__VLS_functionalComponentArgsRest(__VLS_199));
    var __VLS_197;
    const __VLS_202 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_203 = __VLS_asFunctionalComponent(__VLS_202, new __VLS_202({
        label: "secret",
    }));
    const __VLS_204 = __VLS_203({
        label: "secret",
    }, ...__VLS_functionalComponentArgsRest(__VLS_203));
    __VLS_205.slots.default;
    const __VLS_206 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_207 = __VLS_asFunctionalComponent(__VLS_206, new __VLS_206({
        modelValue: (__VLS_ctx.consoleForm.secret),
        type: "password",
        showPassword: true,
    }));
    const __VLS_208 = __VLS_207({
        modelValue: (__VLS_ctx.consoleForm.secret),
        type: "password",
        showPassword: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_207));
    var __VLS_205;
    const __VLS_210 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_211 = __VLS_asFunctionalComponent(__VLS_210, new __VLS_210({
        label: "方法",
    }));
    const __VLS_212 = __VLS_211({
        label: "方法",
    }, ...__VLS_functionalComponentArgsRest(__VLS_211));
    __VLS_213.slots.default;
    const __VLS_214 = {}.ElSelect;
    /** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
    // @ts-ignore
    const __VLS_215 = __VLS_asFunctionalComponent(__VLS_214, new __VLS_214({
        modelValue: (__VLS_ctx.consoleForm.method),
    }));
    const __VLS_216 = __VLS_215({
        modelValue: (__VLS_ctx.consoleForm.method),
    }, ...__VLS_functionalComponentArgsRest(__VLS_215));
    __VLS_217.slots.default;
    const __VLS_218 = {}.ElOption;
    /** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
    // @ts-ignore
    const __VLS_219 = __VLS_asFunctionalComponent(__VLS_218, new __VLS_218({
        label: "GET",
        value: "GET",
    }));
    const __VLS_220 = __VLS_219({
        label: "GET",
        value: "GET",
    }, ...__VLS_functionalComponentArgsRest(__VLS_219));
    const __VLS_222 = {}.ElOption;
    /** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
    // @ts-ignore
    const __VLS_223 = __VLS_asFunctionalComponent(__VLS_222, new __VLS_222({
        label: "POST",
        value: "POST",
    }));
    const __VLS_224 = __VLS_223({
        label: "POST",
        value: "POST",
    }, ...__VLS_functionalComponentArgsRest(__VLS_223));
    var __VLS_217;
    var __VLS_213;
    const __VLS_226 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_227 = __VLS_asFunctionalComponent(__VLS_226, new __VLS_226({
        label: "moduleId",
    }));
    const __VLS_228 = __VLS_227({
        label: "moduleId",
    }, ...__VLS_functionalComponentArgsRest(__VLS_227));
    __VLS_229.slots.default;
    const __VLS_230 = {}.ElInputNumber;
    /** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
    // @ts-ignore
    const __VLS_231 = __VLS_asFunctionalComponent(__VLS_230, new __VLS_230({
        modelValue: (__VLS_ctx.consoleForm.moduleId),
        min: (1),
    }));
    const __VLS_232 = __VLS_231({
        modelValue: (__VLS_ctx.consoleForm.moduleId),
        min: (1),
    }, ...__VLS_functionalComponentArgsRest(__VLS_231));
    var __VLS_229;
    const __VLS_234 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_235 = __VLS_asFunctionalComponent(__VLS_234, new __VLS_234({
        label: "recordId",
    }));
    const __VLS_236 = __VLS_235({
        label: "recordId",
    }, ...__VLS_functionalComponentArgsRest(__VLS_235));
    __VLS_237.slots.default;
    const __VLS_238 = {}.ElInputNumber;
    /** @type {[typeof __VLS_components.ElInputNumber, typeof __VLS_components.elInputNumber, ]} */ ;
    // @ts-ignore
    const __VLS_239 = __VLS_asFunctionalComponent(__VLS_238, new __VLS_238({
        modelValue: (__VLS_ctx.consoleForm.recordId),
        min: (1),
    }));
    const __VLS_240 = __VLS_239({
        modelValue: (__VLS_ctx.consoleForm.recordId),
        min: (1),
    }, ...__VLS_functionalComponentArgsRest(__VLS_239));
    var __VLS_237;
    const __VLS_242 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_243 = __VLS_asFunctionalComponent(__VLS_242, new __VLS_242({
        label: "请求体 JSON",
    }));
    const __VLS_244 = __VLS_243({
        label: "请求体 JSON",
    }, ...__VLS_functionalComponentArgsRest(__VLS_243));
    __VLS_245.slots.default;
    const __VLS_246 = {}.ElInput;
    /** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
    // @ts-ignore
    const __VLS_247 = __VLS_asFunctionalComponent(__VLS_246, new __VLS_246({
        modelValue: (__VLS_ctx.consoleForm.body),
        type: "textarea",
        rows: (6),
        ...{ class: "json-box" },
    }));
    const __VLS_248 = __VLS_247({
        modelValue: (__VLS_ctx.consoleForm.body),
        type: "textarea",
        rows: (6),
        ...{ class: "json-box" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_247));
    var __VLS_245;
    const __VLS_250 = {}.ElFormItem;
    /** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
    // @ts-ignore
    const __VLS_251 = __VLS_asFunctionalComponent(__VLS_250, new __VLS_250({}));
    const __VLS_252 = __VLS_251({}, ...__VLS_functionalComponentArgsRest(__VLS_251));
    __VLS_253.slots.default;
    const __VLS_254 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_255 = __VLS_asFunctionalComponent(__VLS_254, new __VLS_254({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Connection),
    }));
    const __VLS_256 = __VLS_255({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Connection),
    }, ...__VLS_functionalComponentArgsRest(__VLS_255));
    let __VLS_258;
    let __VLS_259;
    let __VLS_260;
    const __VLS_261 = {
        onClick: (__VLS_ctx.callOpenApi)
    };
    __VLS_257.slots.default;
    var __VLS_257;
    var __VLS_253;
    var __VLS_185;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "content-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "page-title" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h1, __VLS_intrinsicElements.h1)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.pre, __VLS_intrinsicElements.pre)({
        ...{ class: "response-box" },
    });
    (__VLS_ctx.consoleResult);
}
const __VLS_262 = {}.ElDialog;
/** @type {[typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, typeof __VLS_components.ElDialog, typeof __VLS_components.elDialog, ]} */ ;
// @ts-ignore
const __VLS_263 = __VLS_asFunctionalComponent(__VLS_262, new __VLS_262({
    modelValue: (__VLS_ctx.clientDialog),
    title: (__VLS_ctx.clientForm.id ? '编辑 OpenAPI 应用' : '新建 OpenAPI 应用'),
    width: "620px",
}));
const __VLS_264 = __VLS_263({
    modelValue: (__VLS_ctx.clientDialog),
    title: (__VLS_ctx.clientForm.id ? '编辑 OpenAPI 应用' : '新建 OpenAPI 应用'),
    width: "620px",
}, ...__VLS_functionalComponentArgsRest(__VLS_263));
__VLS_265.slots.default;
const __VLS_266 = {}.ElForm;
/** @type {[typeof __VLS_components.ElForm, typeof __VLS_components.elForm, typeof __VLS_components.ElForm, typeof __VLS_components.elForm, ]} */ ;
// @ts-ignore
const __VLS_267 = __VLS_asFunctionalComponent(__VLS_266, new __VLS_266({
    model: (__VLS_ctx.clientForm),
    labelWidth: "118px",
}));
const __VLS_268 = __VLS_267({
    model: (__VLS_ctx.clientForm),
    labelWidth: "118px",
}, ...__VLS_functionalComponentArgsRest(__VLS_267));
__VLS_269.slots.default;
const __VLS_270 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_271 = __VLS_asFunctionalComponent(__VLS_270, new __VLS_270({
    label: "clientId",
    required: true,
}));
const __VLS_272 = __VLS_271({
    label: "clientId",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_271));
__VLS_273.slots.default;
const __VLS_274 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_275 = __VLS_asFunctionalComponent(__VLS_274, new __VLS_274({
    modelValue: (__VLS_ctx.clientForm.clientId),
}));
const __VLS_276 = __VLS_275({
    modelValue: (__VLS_ctx.clientForm.clientId),
}, ...__VLS_functionalComponentArgsRest(__VLS_275));
var __VLS_273;
const __VLS_278 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_279 = __VLS_asFunctionalComponent(__VLS_278, new __VLS_278({
    label: "应用名称",
    required: true,
}));
const __VLS_280 = __VLS_279({
    label: "应用名称",
    required: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_279));
__VLS_281.slots.default;
const __VLS_282 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_283 = __VLS_asFunctionalComponent(__VLS_282, new __VLS_282({
    modelValue: (__VLS_ctx.clientForm.clientName),
}));
const __VLS_284 = __VLS_283({
    modelValue: (__VLS_ctx.clientForm.clientName),
}, ...__VLS_functionalComponentArgsRest(__VLS_283));
var __VLS_281;
const __VLS_286 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_287 = __VLS_asFunctionalComponent(__VLS_286, new __VLS_286({
    label: "状态",
}));
const __VLS_288 = __VLS_287({
    label: "状态",
}, ...__VLS_functionalComponentArgsRest(__VLS_287));
__VLS_289.slots.default;
const __VLS_290 = {}.ElSelect;
/** @type {[typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, typeof __VLS_components.ElSelect, typeof __VLS_components.elSelect, ]} */ ;
// @ts-ignore
const __VLS_291 = __VLS_asFunctionalComponent(__VLS_290, new __VLS_290({
    modelValue: (__VLS_ctx.clientForm.status),
}));
const __VLS_292 = __VLS_291({
    modelValue: (__VLS_ctx.clientForm.status),
}, ...__VLS_functionalComponentArgsRest(__VLS_291));
__VLS_293.slots.default;
const __VLS_294 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_295 = __VLS_asFunctionalComponent(__VLS_294, new __VLS_294({
    label: "启用",
    value: "ENABLED",
}));
const __VLS_296 = __VLS_295({
    label: "启用",
    value: "ENABLED",
}, ...__VLS_functionalComponentArgsRest(__VLS_295));
const __VLS_298 = {}.ElOption;
/** @type {[typeof __VLS_components.ElOption, typeof __VLS_components.elOption, ]} */ ;
// @ts-ignore
const __VLS_299 = __VLS_asFunctionalComponent(__VLS_298, new __VLS_298({
    label: "停用",
    value: "DISABLED",
}));
const __VLS_300 = __VLS_299({
    label: "停用",
    value: "DISABLED",
}, ...__VLS_functionalComponentArgsRest(__VLS_299));
var __VLS_293;
var __VLS_289;
const __VLS_302 = {}.ElFormItem;
/** @type {[typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, typeof __VLS_components.ElFormItem, typeof __VLS_components.elFormItem, ]} */ ;
// @ts-ignore
const __VLS_303 = __VLS_asFunctionalComponent(__VLS_302, new __VLS_302({
    label: "限流规则 JSON",
}));
const __VLS_304 = __VLS_303({
    label: "限流规则 JSON",
}, ...__VLS_functionalComponentArgsRest(__VLS_303));
__VLS_305.slots.default;
const __VLS_306 = {}.ElInput;
/** @type {[typeof __VLS_components.ElInput, typeof __VLS_components.elInput, ]} */ ;
// @ts-ignore
const __VLS_307 = __VLS_asFunctionalComponent(__VLS_306, new __VLS_306({
    modelValue: (__VLS_ctx.clientForm.rateLimitRule),
    type: "textarea",
    rows: (5),
    ...{ class: "json-box" },
}));
const __VLS_308 = __VLS_307({
    modelValue: (__VLS_ctx.clientForm.rateLimitRule),
    type: "textarea",
    rows: (5),
    ...{ class: "json-box" },
}, ...__VLS_functionalComponentArgsRest(__VLS_307));
var __VLS_305;
var __VLS_269;
{
    const { footer: __VLS_thisSlot } = __VLS_265.slots;
    const __VLS_310 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_311 = __VLS_asFunctionalComponent(__VLS_310, new __VLS_310({
        ...{ 'onClick': {} },
    }));
    const __VLS_312 = __VLS_311({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_311));
    let __VLS_314;
    let __VLS_315;
    let __VLS_316;
    const __VLS_317 = {
        onClick: (...[$event]) => {
            __VLS_ctx.clientDialog = false;
        }
    };
    __VLS_313.slots.default;
    var __VLS_313;
    const __VLS_318 = {}.ElButton;
    /** @type {[typeof __VLS_components.ElButton, typeof __VLS_components.elButton, typeof __VLS_components.ElButton, typeof __VLS_components.elButton, ]} */ ;
    // @ts-ignore
    const __VLS_319 = __VLS_asFunctionalComponent(__VLS_318, new __VLS_318({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }));
    const __VLS_320 = __VLS_319({
        ...{ 'onClick': {} },
        type: "primary",
        icon: (__VLS_ctx.Check),
    }, ...__VLS_functionalComponentArgsRest(__VLS_319));
    let __VLS_322;
    let __VLS_323;
    let __VLS_324;
    const __VLS_325 = {
        onClick: (__VLS_ctx.saveClient)
    };
    __VLS_321.slots.default;
    var __VLS_321;
}
var __VLS_265;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['split-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['split-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['json-box']} */ ;
/** @type {__VLS_StyleScopedClasses['content-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['page-title']} */ ;
/** @type {__VLS_StyleScopedClasses['response-box']} */ ;
/** @type {__VLS_StyleScopedClasses['json-box']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            Check: Check,
            Connection: Connection,
            Edit: Edit,
            Key: Key,
            Location: Location,
            Lock: Lock,
            Plus: Plus,
            Refresh: Refresh,
            Search: Search,
            StatusTag: StatusTag,
            SecurityForm: SecurityForm,
            tab: tab,
            loading: loading,
            clientDialog: clientDialog,
            clientQuery: clientQuery,
            clients: clients,
            clientForm: clientForm,
            credentialForm: credentialForm,
            scopeForm: scopeForm,
            ipForm: ipForm,
            scopeTypes: scopeTypes,
            consoleResult: consoleResult,
            consoleForm: consoleForm,
            clientEmptyText: clientEmptyText,
            openClient: openClient,
            openCredential: openCredential,
            openScope: openScope,
            openIp: openIp,
            loadClients: loadClients,
            saveClient: saveClient,
            saveCredential: saveCredential,
            saveScope: saveScope,
            saveIp: saveIp,
            callOpenApi: callOpenApi,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
