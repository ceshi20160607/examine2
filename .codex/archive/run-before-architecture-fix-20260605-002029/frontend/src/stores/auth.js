import { defineStore } from 'pinia';
import { authApi } from '../api/modules';
import { TOKEN_KEY } from '../api/http';
const USER_KEY = 'unique.examine.user';
function readUser() {
    const text = localStorage.getItem(USER_KEY);
    if (!text)
        return null;
    try {
        return JSON.parse(text);
    }
    catch {
        return null;
    }
}
export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: localStorage.getItem(TOKEN_KEY) || '',
        user: readUser(),
        loading: false
    }),
    getters: {
        isAuthenticated: (state) => Boolean(state.token)
    },
    actions: {
        applyToken(result) {
            this.token = result.accessToken;
            localStorage.setItem(TOKEN_KEY, result.accessToken);
            if (result.user) {
                this.user = result.user;
                localStorage.setItem(USER_KEY, JSON.stringify(result.user));
            }
        },
        async login(account, password) {
            this.loading = true;
            try {
                const result = await authApi.login({ account, password });
                this.applyToken(result);
                return result;
            }
            finally {
                this.loading = false;
            }
        },
        async register(data) {
            this.loading = true;
            try {
                return await authApi.register(data);
            }
            finally {
                this.loading = false;
            }
        },
        async loadMe() {
            if (!this.token)
                return null;
            const user = await authApi.me();
            this.user = user;
            localStorage.setItem(USER_KEY, JSON.stringify(user));
            return user;
        },
        async refresh() {
            const result = await authApi.refresh();
            this.applyToken(result);
            return result;
        },
        async logout() {
            if (this.token) {
                await authApi.logout().catch(() => undefined);
            }
            this.token = '';
            this.user = null;
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(USER_KEY);
        }
    }
});
