import { defineStore } from 'pinia';
import { ElMessage } from 'element-plus';
import { platformApi } from '../api/modules';
import { useAuthStore } from './auth';
const CONTEXT_KEY = 'unique.examine.context';
function readContext() {
    const text = localStorage.getItem(CONTEXT_KEY);
    if (!text)
        return {};
    try {
        return JSON.parse(text);
    }
    catch {
        return {};
    }
}
export const useContextStore = defineStore('context', {
    state: () => ({
        current: readContext()
    }),
    getters: {
        systemId: (state) => state.current.systemId,
        tenantId: (state) => state.current.tenantId,
        hasSystemContext: (state) => Boolean(state.current.systemId && state.current.tenantId)
    },
    actions: {
        setContext(context) {
            this.current = context;
            localStorage.setItem(CONTEXT_KEY, JSON.stringify(context));
        },
        clear() {
            this.current = {};
            localStorage.removeItem(CONTEXT_KEY);
        },
        async enterSystem(row) {
            const systemId = Number(row.systemId ?? row.id);
            const tenantId = Number(row.tenantId);
            const result = await platformApi.enterSystem({ systemId, tenantId });
            useAuthStore().applyToken(result);
            this.setContext({
                systemId,
                tenantId,
                systemName: String(row.systemName ?? row.name ?? `系统 ${systemId}`),
                tenantName: String(row.tenantName ?? `租户 ${tenantId}`)
            });
            ElMessage.success('已进入系统上下文');
        },
        enrichPayload(payload) {
            return {
                ...payload,
                systemId: payload.systemId ?? this.current.systemId,
                tenantId: payload.tenantId ?? this.current.tenantId
            };
        }
    }
});
