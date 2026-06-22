<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-5">
    <div>
      <h1>Bans &amp; Mutes</h1>
      <p class="text-sm text-muted">Active enforcement across the server. Manage individuals from their player page.</p>
    </div>

    <div class="card p-0 overflow-x-auto">
      <div class="flex items-center justify-between px-4 py-3 border-b border-edge">
        <h3>Active Bans <span class="text-xs text-faint font-normal">({{ bansTotal.toLocaleString() }})</span></h3>
        <button class="btn-ghost text-xs" :disabled="!bans.length" @click="exportBans">Export CSV</button>
      </div>
      <table class="w-full text-sm">
        <thead class="bg-elevated text-muted text-xs uppercase">
          <tr><th class="text-left px-4 py-2">Player</th><th class="text-left px-4 py-2">Reason</th><th class="text-left px-4 py-2 hidden sm:table-cell">Expires</th><th class="px-4 py-2"></th></tr>
        </thead>
        <tbody>
          <tr v-for="b in bans" :key="b.target" class="border-t border-edge">
            <td class="px-4 py-2 font-medium text-primary">{{ b.target }}</td>
            <td class="px-4 py-2 text-secondary">{{ b.reason }}</td>
            <td class="px-4 py-2 text-muted hidden sm:table-cell">{{ b.expires ? new Date(b.expires).toLocaleString() : 'Permanent' }}</td>
            <td class="px-4 py-2 text-right">
              <router-link v-if="b.uuid" :to="`/players/${b.uuid}`" class="btn-subtle py-1">Manage</router-link>
            </td>
          </tr>
          <tr v-if="!bans.length"><td colspan="4" class="px-4 py-6 text-center text-muted">No active bans.</td></tr>
        </tbody>
      </table>
      <div class="px-4 pb-3"><Pagination :page="bansPage" :size="pageSize" :total="bansTotal" @update:page="p => { bansPage = p; loadBans() }" /></div>
    </div>

    <div class="card p-0 overflow-x-auto">
      <h3 class="px-4 py-3 border-b border-edge">Active Mutes <span class="text-xs text-faint font-normal">({{ mutesTotal.toLocaleString() }})</span></h3>
      <table class="w-full text-sm">
        <thead class="bg-elevated text-muted text-xs uppercase">
          <tr><th class="text-left px-4 py-2">Player</th><th class="text-left px-4 py-2">Expires</th><th class="px-4 py-2"></th></tr>
        </thead>
        <tbody>
          <tr v-for="m in mutes" :key="m.uuid" class="border-t border-edge">
            <td class="px-4 py-2 font-medium text-primary">{{ m.name }}</td>
            <td class="px-4 py-2 text-muted">{{ m.expires ? new Date(m.expires).toLocaleString() : 'Permanent' }}</td>
            <td class="px-4 py-2 text-right"><router-link :to="`/players/${m.uuid}`" class="btn-subtle py-1">Manage</router-link></td>
          </tr>
          <tr v-if="!mutes.length"><td colspan="3" class="px-4 py-6 text-center text-muted">{{ loading ? 'Loading…' : 'No active mutes.' }}</td></tr>
        </tbody>
      </table>
      <div class="px-4 pb-3"><Pagination :page="mutesPage" :size="pageSize" :total="mutesTotal" @update:page="p => { mutesPage = p; loadMutes() }" /></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { exportCsv } from '../utils/csv'
import Pagination from '../components/Pagination.vue'

const pageSize = 20
const bans = ref([])
const bansPage = ref(0)
const bansTotal = ref(0)
const mutes = ref([])
const mutesPage = ref(0)
const mutesTotal = ref(0)
const loading = ref(true)

async function loadBans() {
  try {
    const { data } = await api.getBans(bansPage.value, pageSize)
    bans.value = data.bans
    bansTotal.value = data.total
  } catch { /* ignore */ }
}

async function loadMutes() {
  try {
    const { data } = await api.getMutes(mutesPage.value, pageSize)
    mutes.value = data.mutes
    mutesTotal.value = data.total
  } catch { /* ignore */ } finally { loading.value = false }
}

function exportBans() {
  exportCsv('bans.csv', bans.value.map(b => ({
    target: b.target,
    reason: b.reason,
    expires: b.expires ? new Date(b.expires).toISOString() : 'Permanent',
  })), [
    { key: 'target', label: 'Player' },
    { key: 'reason', label: 'Reason' },
    { key: 'expires', label: 'Expires' },
  ])
}

onMounted(() => { loadBans(); loadMutes() })
</script>
