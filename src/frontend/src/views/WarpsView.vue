<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-5">
    <div class="flex items-center justify-between">
      <div>
        <h1>Warps</h1>
        <p class="text-sm text-muted">Manage server warp points.</p>
      </div>
      <button v-if="can('WARPS_MANAGE')" class="btn-primary" @click="openNew">New Warp</button>
    </div>

    <div class="card p-0 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-elevated text-muted text-xs uppercase">
          <tr><th class="text-left px-4 py-2">Name</th><th class="text-left px-4 py-2">Location</th><th class="px-4 py-2"></th></tr>
        </thead>
        <tbody>
          <tr v-for="w in warps" :key="w.name" class="border-t border-edge hover:bg-elevated/50">
            <td class="px-4 py-2 font-medium text-primary">{{ w.name }}</td>
            <td class="px-4 py-2 text-muted">{{ w.world }} · {{ w.x }}, {{ w.y }}, {{ w.z }}</td>
            <td class="px-4 py-2 text-right space-x-1">
              <button v-if="can('WARPS_MANAGE')" class="btn-subtle py-1" @click="openEdit(w)">Edit</button>
              <button v-if="can('WARPS_MANAGE')" class="btn-ghost py-1 text-red-500" @click="remove(w)">Delete</button>
            </td>
          </tr>
          <tr v-if="!warps.length"><td colspan="3" class="px-4 py-8 text-center text-muted">{{ loading ? 'Loading…' : 'No warps defined.' }}</td></tr>
        </tbody>
      </table>
    </div>

    <Modal :open="modal" :title="editing.existing ? `Edit warp: ${editing.name}` : 'New Warp'" @close="modal = false">
      <div class="space-y-3">
        <div v-if="!editing.existing">
          <label class="label">Name</label>
          <input v-model="editing.name" class="input" placeholder="spawn" />
        </div>
        <div>
          <label class="label">World</label>
          <input v-model="editing.world" class="input" placeholder="world" />
        </div>
        <div class="grid grid-cols-3 gap-2">
          <div><label class="label">X</label><input v-model.number="editing.x" type="number" step="0.5" class="input" /></div>
          <div><label class="label">Y</label><input v-model.number="editing.y" type="number" step="0.5" class="input" /></div>
          <div><label class="label">Z</label><input v-model.number="editing.z" type="number" step="0.5" class="input" /></div>
        </div>
        <div class="grid grid-cols-2 gap-2">
          <div><label class="label">Yaw</label><input v-model.number="editing.yaw" type="number" class="input" /></div>
          <div><label class="label">Pitch</label><input v-model.number="editing.pitch" type="number" class="input" /></div>
        </div>
      </div>
      <template #footer>
        <button class="btn-ghost" @click="modal = false">Cancel</button>
        <button class="btn-primary" @click="save">Save</button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import Modal from '../components/Modal.vue'
import { useConfirm } from '../composables/useConfirm'

const confirm = useConfirm()

const auth = useAuthStore()
const toast = useToastStore()
const can = p => auth.hasPermission(p)

const warps = ref([])
const loading = ref(true)
const modal = ref(false)
const editing = ref({})

async function load() {
  loading.value = true
  try { warps.value = (await api.getWarps()).data.warps } catch { /* ignore */ } finally { loading.value = false }
}

function openNew() {
  editing.value = { name: '', world: 'world', x: 0, y: 64, z: 0, yaw: 0, pitch: 0, existing: false }
  modal.value = true
}
function openEdit(w) {
  editing.value = { ...w, existing: true }
  modal.value = true
}

async function save() {
  const e = editing.value
  if (!e.name?.trim()) return toast.error('Warp name is required')
  try {
    await api.saveWarp(e.name.trim(), { world: e.world, x: e.x, y: e.y, z: e.z, yaw: e.yaw || 0, pitch: e.pitch || 0 })
    toast.success('Warp saved')
    modal.value = false
    load()
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to save warp')
  }
}

async function remove(w) {
  if (!(await confirm({ title: 'Delete warp', message: `Delete warp "${w.name}"?`, confirmText: 'Delete', danger: true }))) return
  try {
    await api.deleteWarp(w.name)
    load()
    toast.success(`Warp "${w.name}" deleted`, { action: { label: 'Undo', onClick: () => restore(w) } })
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to delete')
  }
}

async function restore(w) {
  try {
    await api.saveWarp(w.name, { world: w.world, x: w.x, y: w.y, z: w.z, yaw: w.yaw || 0, pitch: w.pitch || 0 })
    toast.success(`Warp "${w.name}" restored`)
    load()
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to restore warp')
  }
}

onMounted(load)
</script>
