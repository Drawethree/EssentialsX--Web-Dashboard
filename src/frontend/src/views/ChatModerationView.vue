<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-4 h-full flex flex-col">
    <div class="flex items-center justify-between gap-3">
      <div>
        <h1>Chat Moderation</h1>
        <p class="text-sm text-muted">Live chat with moderation tools. {{ connected ? 'Connected.' : 'Connecting…' }}</p>
      </div>
      <span class="w-2.5 h-2.5 rounded-full flex-shrink-0" :class="connected ? 'bg-green-500' : 'bg-amber-500'"></span>
    </div>

    <div class="relative">
      <MagnifyingGlassIcon class="w-4 h-4 text-muted absolute left-3 top-1/2 -translate-y-1/2" />
      <input v-model="q" class="input pl-9" placeholder="Search chat history by player or message…" @input="onSearch" />
    </div>

    <div class="card p-0 flex-1 min-h-[300px] overflow-auto">
      <ul class="divide-y divide-edge">
        <li v-for="m in messages" :key="m.key" class="flex flex-wrap items-start gap-x-3 gap-y-1 px-3 py-2 hover:bg-elevated/50 group">
          <span class="text-xs text-muted whitespace-nowrap mt-0.5 w-14 flex-shrink-0" :title="formatDateTime(m.ts)">{{ shortTime(m.ts) }}</span>
          <div class="min-w-0 flex-1">
            <p class="text-sm break-words">
              <router-link :to="`/players/${m.uuid}`" class="font-medium text-brand hover:underline">{{ m.name }}</router-link>
              <span v-if="isFlagged(m.message)" class="badge bg-red-500/15 text-red-500 ml-1.5 align-middle">flagged</span>
              <span class="text-secondary"> {{ m.message }}</span>
            </p>
          </div>
          <div v-if="canModerate" class="flex items-center gap-1 opacity-100 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity flex-shrink-0 ml-auto">
            <button class="btn-ghost py-0.5 px-2 text-xs" title="Warn" @click="warn(m)">Warn</button>
            <button class="btn-ghost py-0.5 px-2 text-xs" title="Mute 60m" @click="mute(m)">Mute</button>
            <button class="btn-ghost py-0.5 px-2 text-xs text-red-500" title="Ban" @click="ban(m)">Ban</button>
            <button v-if="m.id" class="btn-ghost py-0.5 px-2 text-xs text-muted" title="Delete line" @click="del(m)">✕</button>
          </div>
        </li>
      </ul>
      <p v-if="!messages.length" class="text-sm text-muted text-center py-10">
        {{ loading ? 'Loading…' : 'No chat yet. New messages appear here live.' }}
      </p>
    </div>

    <Pagination v-if="!isLive && total > size" :page="page" :size="size" :total="total" @update:page="goPage" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api, openEventStream } from '../api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { useConfirm } from '../composables/useConfirm'
import { formatDateTime } from '../utils'
import Pagination from '../components/Pagination.vue'
import { MagnifyingGlassIcon } from '@heroicons/vue/24/outline'

const auth = useAuthStore()
const toast = useToastStore()
const confirm = useConfirm()
const canModerate = computed(() => auth.hasPermission('CHAT_MODERATE'))

// Starter profanity/keyword flag list — purely a client-side highlight aid.
const FLAG_WORDS = ['kys', 'noob', 'hack', 'cheat', 'scam', 'slur']

const messages = ref([])
const q = ref('')
const page = ref(0)
const size = 50
const total = ref(0)
const loading = ref(true)
const connected = ref(false)
let stream = null
let searchTimer = null
let liveKey = 0

// In live mode (page 0, no search) new SSE messages prepend; paging/searching shows static history.
const isLive = computed(() => page.value === 0 && !q.value.trim())

function shortTime(ts) {
  return new Date(ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}
function isFlagged(msg) {
  const lower = (msg || '').toLowerCase()
  return FLAG_WORDS.some(w => lower.includes(w))
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.chatHistory(page.value, size, q.value.trim())
    messages.value = data.entries.map(e => ({ ...e, key: `h${e.id}` }))
    total.value = data.total
  } catch { /* ignore */ } finally { loading.value = false }
}

function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 0; load() }, 250)
}
function goPage(p) { page.value = p; load() }

function onLiveChat(e) {
  if (!isLive.value) return
  const d = JSON.parse(e.data)
  messages.value.unshift({ id: null, uuid: d.uuid, name: d.name, message: d.message, ts: d.timestamp, key: `l${++liveKey}` })
  if (messages.value.length > 200) messages.value.pop()
}

async function warn(m) {
  try {
    const { data } = await api.warnPlayer(m.uuid, 'Inappropriate chat')
    toast.success(data.escalated ? `${m.name} warned → auto ${data.action}` : `${m.name} warned (${data.warnings})`)
  } catch (err) { toast.error(err.response?.data?.error ?? 'Failed to warn') }
}
async function mute(m) {
  try { await api.mute(m.uuid, 60); toast.success(`${m.name} muted for 60m`) }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed to mute') }
}
async function ban(m) {
  if (!(await confirm({ title: 'Ban player', message: `Ban ${m.name} for chat violation?`, confirmText: 'Ban', danger: true }))) return
  try { await api.ban(m.uuid, 'Chat violation', 0); toast.success(`${m.name} banned`) }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed to ban') }
}
async function del(m) {
  try {
    await api.deleteChat(m.id)
    messages.value = messages.value.filter(x => x.key !== m.key)
    toast.success('Message removed')
  } catch (err) { toast.error(err.response?.data?.error ?? 'Failed to delete') }
}

onMounted(() => {
  load()
  stream = openEventStream()
  stream.onopen = () => { connected.value = true }
  stream.onerror = () => { connected.value = false }
  stream.addEventListener('chat', onLiveChat)
})
onUnmounted(() => { if (stream) stream.close() })
</script>
