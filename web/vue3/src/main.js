import { createApp } from 'vue'
import './style.css'
import './views/admin-shared.css'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'

setUnauthorizedHandler((target) => {
  if (router.currentRoute.value.name === 'login') return
  router.replace(target).catch(() => {})
})

createApp(App).use(router).mount('#app')
