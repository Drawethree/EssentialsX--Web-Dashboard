import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '../api'

const DEFAULT_NAME = 'EssentialsX Dashboard'
const DEFAULT_ACCENT = '#e13c43'

/** White-label branding — panel name, accent colour, and custom logo, applied app-wide. */
export const useBrandingStore = defineStore('branding', () => {
  const serverName = ref(DEFAULT_NAME)
  const accentColor = ref(DEFAULT_ACCENT)
  const hasLogo = ref(false)
  const logoVersion = ref(0) // bumped to bust the logo cache after an upload

  const logoUrl = computed(() => hasLogo.value ? `/api/branding/logo?v=${logoVersion.value}` : null)

  async function fetch() {
    try {
      const { data } = await api.getBranding()
      apply(data)
    } catch { /* fall back to defaults */ }
  }

  function apply(data) {
    serverName.value = data.serverName || DEFAULT_NAME
    accentColor.value = data.accentColor || DEFAULT_ACCENT
    hasLogo.value = !!data.hasLogo
    logoVersion.value++
    document.title = serverName.value
    applyAccent(accentColor.value)
  }

  function applyAccent(hex) {
    const rgb = hexToRgb(hex)
    if (!rgb) return
    const root = document.documentElement.style
    root.setProperty('--color-brand-rgb', `${rgb.r} ${rgb.g} ${rgb.b}`)
    root.setProperty('--color-brand-hover', darken(rgb, 0.12))
    root.setProperty('--color-brand-subtle', `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, 0.12)`)
  }

  function hexToRgb(hex) {
    const m = /^#?([0-9a-f]{6})$/i.exec(hex || '')
    if (!m) return null
    const n = parseInt(m[1], 16)
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
  }

  function darken({ r, g, b }, amount) {
    const d = c => Math.max(0, Math.round(c * (1 - amount)))
    const hex = c => d(c).toString(16).padStart(2, '0')
    return `#${hex(r)}${hex(g)}${hex(b)}`
  }

  return { serverName, accentColor, hasLogo, logoUrl, fetch, apply }
})
