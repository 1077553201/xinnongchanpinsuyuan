import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './styles/base.css'
import './styles/glass.css'
import './styles/management.css'
import './styles/themes.css'
import { initializeTheme } from './composables/useTheme'

initializeTheme()
createApp(App).use(createPinia()).use(router).mount('#app')
