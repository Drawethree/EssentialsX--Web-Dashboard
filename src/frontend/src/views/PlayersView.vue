<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-4">
    <div>
      <h1>Players</h1>
      <p class="text-sm text-muted">Search and manage any player — online or offline.</p>
    </div>

    <div class="flex items-center gap-2 flex-wrap">
      <input v-model="q" class="input max-w-sm" placeholder="Search by name…" @input="debouncedSearch" />
      <button class="btn-ghost text-xs" :disabled="!players.length" @click="exportPage">Export CSV</button>
    </div>

    <div class="card p-0 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-elevated text-muted text-xs uppercase">
          <tr>
            <th class="text-left px-4 py-2 font-medium">Player</th>
            <th class="text-left px-4 py-2 font-medium hidden sm:table-cell">Last Seen</th>
            <th class="text-left px-4 py-2 font-medium">Status</th>
            <th class="px-4 py-2"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in players" :key="p.uuid" class="border-t border-edge hover:bg-elevated/50">
            <td class="px-4 py-2">
              <div class="flex items-center gap-2">
                <Avatar :uuid="p.uuid" :name="p.name" :size="28" />
                <span class="font-medium text-primary">{{ p.name }}</span>
              </div>
            </td>
            <td class="px-4 py-2 text-muted hidden sm:table-cell">{{ timeAgo(p.lastSeen) }}</td>
            <td class="px-4 py-2">
              <span :class="p.online ? 'badge-online' : 'badge-offline'">{{ p.online ? 'Online' : 'Offline' }}</span>
            </td>
            <td class="px-4 py-2 text-right">
              <router-link :to="`/players/${p.uuid}`" class="btn-subtle py-1">Manage</router-link>
            </td>
          </tr>
          <tr v-if="!players.length">
            <td colspan="4" class="px-4 py-8 text-center text-muted">{{ loading ? 'Loading…' : 'No players found.' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination :page="page" :size="size" :total="total" @update:page="changePage" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { timeAgo } from '../utils'
import { exportCsv } from '../utils/csv'
import Avatar from '../components/Avatar.vue'
import Pagination from '../components/Pagination.vue'

const q = ref('')
const players = ref([])
const total = ref(0)
const page = ref(0)
const size = 20
const loading = ref(false)
let timer = null

async function search() {
  loading.value = true
  try {
    const { data } = await api.searchPlayers(q.value, page.value, size)
    players.value = data.players
    total.value = data.total
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

function debouncedSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => { page.value = 0; search() }, 300)
}

function changePage(p) {
  page.value = p
  search()
}

function exportPage() {
  exportCsv('players.csv', players.value.map(p => ({
    name: p.name,
    uuid: p.uuid,
    online: p.online ? 'yes' : 'no',
    lastSeen: p.lastSeen ? new Date(p.lastSeen).toISOString() : '',
  })), [
    { key: 'name', label: 'Player' },
    { key: 'uuid', label: 'UUID' },
    { key: 'online', label: 'Online' },
    { key: 'lastSeen', label: 'Last Seen' },
  ])
}

onMounted(search)
</script>
