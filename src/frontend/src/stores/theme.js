import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(localStorage.getItem('essdash_theme') !== 'light')

  function apply() {
    document.documentElement.classList.toggle('dark', isDark.value)
    localStorage.setItem('essdash_theme', isDark.value ? 'dark' : 'light')
  }

  function toggle() {
    isDark.value = !isDark.value
    apply()
  }

  return { isDark, toggle, apply }
})
