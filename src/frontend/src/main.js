import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { i18n } from './i18n'

// Self-hosted Inter (no runtime CDN — the dashboard may run on a LAN/offline box).
import '@fontsource/inter/latin-400.css'
import '@fontsource/inter/latin-500.css'
import '@fontsource/inter/latin-600.css'
import '@fontsource/inter/latin-700.css'

import './style.css'
import logo from './assets/logo.png'

// Apply saved theme before first render to avoid a flash.
if (localStorage.getItem('essdash_theme') !== 'light') {
  document.documentElement.classList.add('dark')
}

// Favicon — uses the bundled, hashed asset URL (served under /assets/*).
const favicon = document.createElement('link')
favicon.rel = 'icon'
favicon.type = 'image/png'
favicon.href = logo
document.head.appendChild(favicon)

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.mount('#app')
