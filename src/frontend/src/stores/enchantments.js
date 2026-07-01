import { ref } from 'vue'
import { api } from '../api'

// Module-level cache so the enchantment list is fetched only once per session.
const list = ref([]) // [{ id, name, maxLevel }]
let loaded = false

export function useEnchantments() {
  if (!loaded) {
    loaded = true
    api.enchantments().then(r => { list.value = r.data }).catch(() => { loaded = false })
  }
  return { list }
}
