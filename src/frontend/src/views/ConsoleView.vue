<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-4 h-full flex flex-col">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1>Live Console</h1>
        <p class="text-sm text-muted">Real-time server output.</p>
      </div>
      <div class="flex items-center gap-3">
        <label class="text-xs text-muted flex items-center gap-1.5 cursor-pointer">
          <input type="checkbox" v-model="showChat" /> Chat
        </label>
        <label class="text-xs text-muted flex items-center gap-1.5 cursor-pointer">
          <input type="checkbox" v-model="showTimestamps" /> Timestamps
        </label>
        <button class="btn-ghost text-xs" :disabled="!lines.length" @click="clearConsole">Clear</button>
        <span class="flex items-center gap-1.5 text-xs" :class="connected ? 'text-emerald-400' : 'text-amber-400'">
          <span class="w-2 h-2 rounded-full" :class="connected ? 'bg-emerald-500' : 'bg-amber-500 animate-pulse'"></span>
          {{ connected ? 'Connected' : 'Connecting…' }}
        </span>
      </div>
    </div>

    <!-- Terminal — intentionally dark in both themes (PebbleHost-style). -->
    <div class="relative flex-1 min-h-[320px]">
      <div
        ref="logEl"
        class="absolute inset-0 bg-[#0b0e14] rounded-xl p-3 font-mono text-xs leading-relaxed overflow-auto border border-slate-800 shadow-inner"
        @scroll="onScroll"
      >
        <div v-for="(line, i) in lines" :key="i" class="whitespace-pre-wrap break-words flex gap-2">
          <span v-if="showTimestamps" class="text-slate-600 flex-shrink-0 select-none tabular-nums">{{ fmtTime(line.timestamp) }}</span>
          <span v-if="line.type === 'chat'" class="text-emerald-400">&lt;{{ line.name }}&gt; {{ line.message }}</span>
          <span v-else :class="levelColor(line.level)">{{ line.message }}</span>
        </div>
        <p v-if="!lines.length" class="text-slate-600">Waiting for output…</p>
      </div>

      <!-- Jump to bottom (shown when auto-scroll is paused) -->
      <button
        v-if="!autoScroll"
        class="absolute bottom-3 right-3 flex items-center gap-1 px-2.5 py-1 rounded-full bg-slate-800/90 text-slate-200 text-xs shadow-lg border border-slate-700 hover:bg-slate-700 transition-colors"
        @click="jumpToBottom"
      >
        <ArrowDownIcon class="w-3.5 h-3.5" /> Jump to latest
      </button>
    </div>

    <form v-if="can('CONSOLE_EXECUTE')" class="flex gap-2" @submit.prevent="run">
      <span class="flex items-center text-muted font-mono px-1">/</span>
      <input v-model="command" class="input font-mono" placeholder="say Hello world" autocomplete="off"
             @keydown.up.prevent="historyUp" @keydown.down.prevent="historyDown" />
      <button type="submit" class="btn-primary">Run</button>
    </form>
    <p v-else class="text-xs text-faint">You have read-only console access.</p>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { ArrowDownIcon } from '@heroicons/vue/24/solid'
import { api, openEventStream } from '../api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'

const auth = useAuthStore()
const toast = useToastStore()
const can = p => auth.hasPermission(p)

const lines = ref([])
const command = ref('')
const connected = ref(false)
const showChat = ref(true)
const showTimestamps = ref(false)
const autoScroll = ref(true)
const logEl = ref(null)
const history = []
let historyIdx = -1
let stream = null

function levelColor(level) {
  if (level === 'ERROR' || level === 'FATAL') return 'text-red-400'
  if (level === 'WARN') return 'text-amber-300'
  if (level === 'DEBUG' || level === 'TRACE') return 'text-slate-500'
  return 'text-slate-200'
}

function fmtTime(ts) {
  const d = ts ? new Date(ts) : new Date()
  return d.toLocaleTimeString([], { hour12: false })
}

function push(line) {
  lines.value.push(line)
  if (lines.value.length > 1000) lines.value.splice(0, lines.value.length - 1000)
  if (autoScroll.value) scroll()
}

function scroll() {
  nextTick(() => { if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight })
}

// Pause auto-scroll the moment the user scrolls up; resume once they return to the bottom.
function onScroll() {
  const el = logEl.value
  if (!el) return
  autoScroll.value = el.scrollHeight - el.scrollTop - el.clientHeight < 40
}

function jumpToBottom() {
  autoScroll.value = true
  scroll()
}

function clearConsole() {
  lines.value = []
  autoScroll.value = true
}

async function run() {
  if (!command.value.trim()) return
  const cmd = command.value
  history.unshift(cmd)
  historyIdx = -1
  command.value = ''
  try { await api.runCommand(cmd) }
  catch (err) { toast.error(err.response?.data?.error ?? 'Command failed') }
}

function historyUp() {
  if (historyIdx < history.length - 1) { historyIdx++; command.value = history[historyIdx] }
}
function historyDown() {
  if (historyIdx > 0) { historyIdx--; command.value = history[historyIdx] }
  else { historyIdx = -1; command.value = '' }
}

onMounted(() => {
  stream = openEventStream()
  stream.onopen = () => { connected.value = true }
  stream.onerror = () => { connected.value = false }
  stream.addEventListener('console-line', e => push({ type: 'log', ...JSON.parse(e.data) }))
  stream.addEventListener('chat', e => { if (showChat.value) push({ type: 'chat', ...JSON.parse(e.data) }) })
})

onUnmounted(() => { if (stream) stream.close() })
</script>
