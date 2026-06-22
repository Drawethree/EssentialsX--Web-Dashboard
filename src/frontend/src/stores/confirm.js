import { defineStore } from 'pinia'
import { ref } from 'vue'

/** A single global confirm dialog driven imperatively via useConfirm(). */
export const useConfirmStore = defineStore('confirm', () => {
  const open = ref(false)
  const options = ref({})
  let resolver = null

  function ask(opts) {
    options.value = {
      title: 'Are you sure?',
      message: '',
      confirmText: 'Confirm',
      cancelText: 'Cancel',
      danger: false,
      ...opts,
    }
    open.value = true
    return new Promise(resolve => { resolver = resolve })
  }

  function resolve(value) {
    open.value = false
    if (resolver) { resolver(value); resolver = null }
  }

  return { open, options, ask, resolve }
})
