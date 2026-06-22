import { createI18n } from 'vue-i18n'
import en from './en.json'
import es from './es.json'
import de from './de.json'

// Available UI languages. Add a locale by dropping a JSON file alongside this one,
// importing it here, and adding an entry to LOCALES.
export const LOCALES = [
  { code: 'en', label: 'English' },
  { code: 'es', label: 'Español' },
  { code: 'de', label: 'Deutsch' },
]

function initialLocale() {
  const saved = localStorage.getItem('essdash_locale')
  if (saved && LOCALES.some(l => l.code === saved)) return saved
  const browser = (navigator.language || 'en').slice(0, 2)
  return LOCALES.some(l => l.code === browser) ? browser : 'en'
}

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: 'en',
  // Missing keys fall back to English silently rather than logging warnings.
  missingWarn: false,
  fallbackWarn: false,
  messages: { en, es, de },
})

export function setLocale(code) {
  i18n.global.locale.value = code
  localStorage.setItem('essdash_locale', code)
  document.documentElement.setAttribute('lang', code)
}
