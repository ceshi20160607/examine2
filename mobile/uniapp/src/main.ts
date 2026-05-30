import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { setUnauthorizedHandler } from './api/http'
import { useSessionStore } from './stores/session'

export function createApp() {
  const app = createSSRApp(App)
  const pinia = createPinia()
  app.use(pinia)
  setUnauthorizedHandler(() => {
    const session = useSessionStore()
    session.setToken(null)
    session.setPayload(null)
    uni.reLaunch({ url: '/pages/auth/login' })
  })
  return {
    app,
  }
}
