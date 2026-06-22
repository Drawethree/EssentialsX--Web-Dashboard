import { defineStore } from 'pinia'
import { ref } from 'vue'

let nextId = 0

export const useToastStore = defineStore('toast', () => {
  const toasts = ref([])

  // `opts` may carry an action: { label, onClick }. Toasts with an action stick around
  // longer so the user has time to click it (and the timer can be overridden via duration).
  function add(message, type = 'info', opts = {}) {
    const id = ++nextId
    const action = opts.action ?? null
    const duration = opts.duration ?? (action ? 8000 : 4000)
    toasts.value.push({ id, message, type, action })
    if (duration > 0) setTimeout(() => remove(id), duration)
    return id
  }

  function remove(id) {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx !== -1) toasts.value.splice(idx, 1)
  }

  // Run a toast's action, then dismiss it.
  function runAction(id) {
    const t = toasts.value.find(t => t.id === id)
    if (t?.action?.onClick) t.action.onClick()
    remove(id)
  }

  const success = (msg, opts) => add(msg, 'success', opts)
  const error = (msg, opts) => add(msg, 'error', opts)
  const info = (msg, opts) => add(msg, 'info', opts)

  return { toasts, add, success, error, info, remove, runAction }
})
