<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-5">
    <div class="flex items-center justify-between">
      <div>
        <h1>Kits</h1>
        <p class="text-sm text-muted">Create and edit EssentialsX kits. Saved kits reload instantly.</p>
      </div>
      <button v-if="can('KITS_MANAGE')" class="btn-primary" @click="openNew">New Kit</button>
    </div>

    <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
      <div v-for="k in kits" :key="k.name" class="card space-y-2">
        <div class="flex items-center justify-between">
          <h3 class="capitalize">{{ k.name }}</h3>
          <span class="badge bg-elevated text-muted">{{ k.delay }}s</span>
        </div>
        <ul class="text-xs text-secondary space-y-1 max-h-32 overflow-auto">
          <li v-for="(item, i) in k.items" :key="i" class="flex items-center gap-1.5">
            <ItemIcon v-if="!item.command" :material="item.material || ''" :size="20" :title="item.raw" />
            <span v-else class="w-5 text-center text-brand font-bold">/</span>
            <span class="font-mono truncate" :class="item.command ? 'text-brand' : ''">{{ item.raw }}</span>
          </li>
        </ul>
        <div v-if="can('KITS_MANAGE')" class="flex gap-2 pt-1">
          <button class="btn-subtle py-1 flex-1" @click="openEdit(k)">Edit</button>
          <button class="btn-ghost py-1 text-red-500" @click="remove(k)">Delete</button>
        </div>
      </div>
      <p v-if="!kits.length" class="text-sm text-muted col-span-full text-center py-8">{{ loading ? 'Loading…' : 'No kits defined yet.' }}</p>
    </div>

    <Modal :open="modal" :title="editing.name ? `Edit kit: ${editing.name}` : 'New Kit'" @close="modal = false">
      <div class="space-y-3">
        <div v-if="!editing.existing">
          <label class="label">Kit name</label>
          <input v-model="editing.name" class="input" placeholder="starter" />
        </div>
        <div>
          <label class="label">Cooldown (seconds)</label>
          <input v-model.number="editing.delay" type="number" min="0" class="input" />
        </div>
        <div>
          <label class="label">Items <span class="text-faint">(one per line, EssentialsX format)</span></label>
          <textarea v-model="editing.itemsText" rows="6" class="input font-mono text-xs" placeholder="diamond_sword 1&#10;cooked_beef 16&#10;golden_apple 2"></textarea>
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
import ItemIcon from '../components/ItemIcon.vue'
import { useConfirm } from '../composables/useConfirm'

const confirm = useConfirm()

const auth = useAuthStore()
const toast = useToastStore()
const can = p => auth.hasPermission(p)

const kits = ref([])
const loading = ref(true)
const modal = ref(false)
const editing = ref({ name: '', delay: 0, itemsText: '', existing: false })

async function load() {
  loading.value = true
  try { kits.value = (await api.getKits()).data.kits } catch { /* ignore */ } finally { loading.value = false }
}

function openNew() {
  editing.value = { name: '', delay: 0, itemsText: '', existing: false }
  modal.value = true
}
function openEdit(k) {
  editing.value = { name: k.name, delay: k.delay, itemsText: k.items.map(i => i.raw).join('\n'), existing: true }
  modal.value = true
}

async function save() {
  const e = editing.value
  if (!e.name?.trim()) return toast.error('Kit name is required')
  const items = e.itemsText.split('\n').map(s => s.trim()).filter(Boolean)
  if (!items.length) return toast.error('Add at least one item')
  try {
    await api.saveKit(e.name.trim().toLowerCase(), e.delay || 0, items)
    toast.success('Kit saved')
    modal.value = false
    load()
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to save kit')
  }
}

async function remove(k) {
  if (!(await confirm({ title: 'Delete kit', message: `Delete kit "${k.name}"?`, confirmText: 'Delete', danger: true }))) return
  try {
    await api.deleteKit(k.name)
    load()
    toast.success(`Kit "${k.name}" deleted`, { action: { label: 'Undo', onClick: () => restore(k) } })
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to delete')
  }
}

async function restore(k) {
  try {
    await api.saveKit(k.name, k.delay || 0, k.items.map(i => i.raw))
    toast.success(`Kit "${k.name}" restored`)
    load()
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to restore kit')
  }
}

onMounted(load)
</script>
