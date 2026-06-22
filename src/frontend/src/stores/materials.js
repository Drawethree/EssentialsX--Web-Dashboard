import { ref } from 'vue'
import { api } from '../api'

// Module-level cache so the (large) material list is fetched only once per session.
const list = ref([])
let loaded = false

export function useMaterials() {
  if (!loaded) {
    loaded = true
    api.materials().then(r => { list.value = r.data }).catch(() => { loaded = false })
  }
  return { list }
}
