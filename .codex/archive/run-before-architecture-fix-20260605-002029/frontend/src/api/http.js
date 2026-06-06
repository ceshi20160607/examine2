import axios from 'axios';
import { ElMessage } from 'element-plus';
import router from '../router';
const TOKEN_KEY = 'unique.examine.accessToken';
export const http = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '',
    timeout: 20000
});
http.interceptors.request.use((config) => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});
http.interceptors.response.use((response) => {
    const body = response.data;
    if (body && typeof body === 'object' && 'code' in body) {
        if (body.code === 0) {
            return body.data;
        }
        const message = body.message || '请求失败';
        ElMessage.error(message);
        return Promise.reject(new Error(message));
    }
    return body;
}, (error) => {
    const status = error.response?.status;
    const message = error.response?.data?.message || error.message || '网络请求失败';
    if (status === 401) {
        localStorage.removeItem(TOKEN_KEY);
        if (router.currentRoute.value.path !== '/login') {
            router.push('/login');
        }
    }
    ElMessage.error(message);
    return Promise.reject(error);
});
export function request(config) {
    return http.request(config);
}
export { TOKEN_KEY };
