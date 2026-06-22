<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-5">
    <div>
      <h1>Server Controls</h1>
      <p class="text-sm text-muted">Whitelist, worlds, spawn, jails and server lifecycle.</p>
    </div>

    <!-- Whitelist -->
    <div class="card space-y-3">
      <div class="flex items-center justify-between">
        <h3>Whitelist</h3>
        <label class="flex items-center gap-2 text-sm">
          <input type="checkbox" :checked="whitelist.enabled" @change="toggleWhitelist($event.target.checked)" />
          Enabled
        </label>
      </div>
      <div class="flex gap-2">
        <input v-model="newWhitelist" class="input max-w-xs" placeholder="Player name" @keyup.enter="addWhitelist" />
        <button class="btn-subtle" @click="addWhitelist">Add</button>
      </div>
      <div v-if="whitelist.players.length" class="flex flex-wrap gap-2">
        <span v-for="p in whitelist.players" :key="p" class="badge bg-elevated text-secondary flex items-center gap-1">
          {{ p }}
          <button class="text-red-500 hover:text-red-600" @click="removeWhitelist(p)">✕</button>
        </span>
      </div>
      <p v-else class="text-sm text-muted">No whitelisted players.</p>
    </div>

    <!-- Worlds -->
    <div class="card space-y-3">
      <h3>Worlds</h3>
      <div v-for="w in worlds" :key="w.name" class="flex flex-wrap items-center gap-2 py-2 border-b border-edge/50 last:border-0">
        <div class="flex-1 min-w-[8rem]">
          <p class="text-sm font-medium text-primary">{{ w.name }}</p>
          <p class="text-xs text-muted">{{ w.environment }} · {{ w.players }} online</p>
        </div>
        <Select :model-value="''" :options="timeOptions" placeholder="Set time…" class="w-32" aria-label="Set time" @update:model-value="v => setTime(w.name, v)" />
        <Select :model-value="''" :options="weatherOptions" placeholder="Weather…" class="w-32" aria-label="Set weather" @update:model-value="v => setWeather(w.name, v)" />
      </div>
    </div>

    <!-- Spawn -->
    <div class="card space-y-3">
      <h3>Spawn <span v-if="!spawn.installed" class="text-xs text-faint font-normal">(EssentialsXSpawn not installed)</span></h3>
      <template v-if="spawn.installed">
        <p v-if="spawn.world" class="text-sm text-muted">Current: {{ spawn.world }} · {{ spawn.x }}, {{ spawn.y }}, {{ spawn.z }}</p>
        <div class="grid grid-cols-1 sm:grid-cols-5 gap-2">
          <input v-model="spawnForm.world" class="input" placeholder="world" />
          <input v-model.number="spawnForm.x" type="number" class="input" placeholder="X" />
          <input v-model.number="spawnForm.y" type="number" class="input" placeholder="Y" />
          <input v-model.number="spawnForm.z" type="number" class="input" placeholder="Z" />
          <button class="btn-subtle" @click="saveSpawn">Set Spawn</button>
        </div>
      </template>
    </div>

    <!-- Jails -->
    <div class="card space-y-3">
      <div class="flex items-center justify-between">
        <h3>Jails</h3>
        <button class="btn-subtle" @click="jailModal = true">New Jail</button>
      </div>
      <div v-if="jails.length" class="space-y-1">
        <div v-for="j in jails" :key="j.name" class="flex items-center gap-2 px-3 py-2 rounded-lg bg-elevated text-sm">
          <span class="font-medium text-primary">{{ j.name }}</span>
          <span class="text-muted text-xs">{{ j.world }} · {{ j.x }}, {{ j.y }}, {{ j.z }}</span>
          <button class="ml-auto text-red-500 text-xs" @click="removeJail(j.name)">Delete</button>
        </div>
      </div>
      <p v-else class="text-sm text-muted">No jails defined.</p>
      <div class="flex flex-wrap gap-2 pt-1">
        <input v-model="jailPlayerForm.player" class="input max-w-[10rem]" placeholder="Player" />
        <Select v-model="jailPlayerForm.jail" :options="jails.map(j => j.name)" placeholder="Jail…" class="w-32" aria-label="Jail" />
        <input v-model.number="jailPlayerForm.minutes" type="number" min="0" class="input w-24" placeholder="Mins" />
        <button class="btn-subtle" @click="doJail">Jail</button>
        <button class="btn-ghost" @click="doUnjail">Unjail</button>
      </div>
    </div>

    <!-- Lifecycle -->
    <div class="card space-y-3 border-red-500/30">
      <h3>Server</h3>
      <div class="flex flex-wrap gap-2">
        <button class="btn-subtle" @click="saveAll">Save All</button>
        <button class="btn-danger" @click="stopServer">Stop Server</button>
      </div>
    </div>

    <Modal :open="jailModal" title="New Jail" @close="jailModal = false">
      <div class="space-y-3">
        <div><label class="label">Name</label><input v-model="jailForm.name" class="input" /></div>
        <div><label class="label">World</label><input v-model="jailForm.world" class="input" placeholder="world" /></div>
        <div class="grid grid-cols-3 gap-2">
          <div><label class="label">X</label><input v-model.number="jailForm.x" type="number" class="input" /></div>
          <div><label class="label">Y</label><input v-model.number="jailForm.y" type="number" class="input" /></div>
          <div><label class="label">Z</label><input v-model.number="jailForm.z" type="number" class="input" /></div>
        </div>
      </div>
      <template #footer>
        <button class="btn-ghost" @click="jailModal = false">Cancel</button>
        <button class="btn-primary" @click="createJail">Create</button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { useToastStore } from '../stores/toast'
import Modal from '../components/Modal.vue'
import Select from '../components/ui/Select.vue'
import { useConfirm } from '../composables/useConfirm'

const toast = useToastStore()
const confirm = useConfirm()

const timeOptions = [
  { value: '0', label: 'Dawn' }, { value: '6000', label: 'Noon' },
  { value: '12000', label: 'Dusk' }, { value: '18000', label: 'Midnight' },
]
const weatherOptions = [
  { value: 'clear', label: 'Clear' }, { value: 'rain', label: 'Rain' }, { value: 'thunder', label: 'Thunder' },
]

const whitelist = ref({ enabled: false, players: [] })
const newWhitelist = ref('')
const worlds = ref([])
const spawn = ref({ installed: false })
const spawnForm = ref({ world: 'world', x: 0, y: 64, z: 0 })
const jails = ref([])
const jailModal = ref(false)
const jailForm = ref({ name: '', world: 'world', x: 0, y: 64, z: 0 })
const jailPlayerForm = ref({ player: '', jail: '', minutes: 0 })

async function ok(fn, msg, reload) {
  try { await fn(); if (msg) toast.success(msg); if (reload) reload() }
  catch (err) { toast.error(err.response?.data?.error ?? 'Action failed') }
}

const loadWhitelist = async () => { whitelist.value = (await api.getWhitelist()).data }
const loadWorlds = async () => { worlds.value = (await api.getWorlds()).data.worlds }
const loadSpawn = async () => { spawn.value = (await api.getSpawn()).data }
const loadJails = async () => { jails.value = (await api.getJails()).data.jails }

const toggleWhitelist = enabled => ok(() => api.setWhitelistEnabled(enabled), 'Whitelist updated', loadWhitelist)
const addWhitelist = () => { if (newWhitelist.value.trim()) { ok(() => api.addWhitelist(newWhitelist.value.trim()), 'Added', loadWhitelist); newWhitelist.value = '' } }
const removeWhitelist = name => ok(() => api.removeWhitelist(name), 'Removed', loadWhitelist)

const setTime = (world, time) => { if (time) ok(() => api.updateWorld(world, { time: Number(time) }), 'Time set', loadWorlds) }
const setWeather = (world, weather) => { if (weather) ok(() => api.updateWorld(world, { weather }), 'Weather set', loadWorlds) }

const saveSpawn = () => ok(() => api.setSpawn(spawnForm.value), 'Spawn set', loadSpawn)

const createJail = () => { ok(() => api.createJail(jailForm.value), 'Jail created', loadJails); jailModal.value = false }
const removeJail = async name => {
  if (await confirm({ title: 'Delete jail', message: `Delete jail "${name}"?`, confirmText: 'Delete', danger: true })) {
    ok(() => api.deleteJail(name), 'Jail deleted', loadJails)
  }
}
const doJail = () => {
  if (!jailPlayerForm.value.player || !jailPlayerForm.value.jail) return toast.error('Player and jail required')
  ok(() => api.jailPlayer(jailPlayerForm.value.player, jailPlayerForm.value.jail, jailPlayerForm.value.minutes || 0), 'Player jailed')
}
const doUnjail = () => { if (jailPlayerForm.value.player) ok(() => api.unjailPlayer(jailPlayerForm.value.player), 'Player unjailed') }

const saveAll = () => ok(() => api.saveAll(), 'World saved')
const stopServer = async () => {
  if (await confirm({ title: 'Stop the server?', message: 'All players will be disconnected and the server process will shut down.', confirmText: 'Stop server', danger: true })) {
    ok(() => api.stopServer(), 'Stopping server…')
  }
}

onMounted(() => { loadWhitelist(); loadWorlds(); loadSpawn(); loadJails() })
</script>
