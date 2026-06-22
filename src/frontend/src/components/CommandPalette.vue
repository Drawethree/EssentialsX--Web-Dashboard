<template>
  <teleport to="body">
    <transition name="fade">
      <div
        v-if="open"
        class="fixed inset-0 z-[95] flex items-start justify-center pt-[14vh] px-4"
        @keydown.down.prevent="move(1)"
        @keydown.up.prevent="move(-1)"
        @keydown.enter.prevent="choose"
        @keydown.esc="close"
      >
        <div class="absolute inset-0 bg-black/50" @click="close"></div>
        <div class="relative w-full max-w-lg bg-overlay border border-edge rounded-xl shadow-lg overflow-hidden animate-slide-up-fade">
          <div class="flex items-center gap-2 px-4 py-3 border-b border-edge">
            <MagnifyingGlassIcon class="w-5 h-5 text-muted flex-shrink-0" />
            <input
              ref="inputEl"
              v-model="query"
              class="flex-1 bg-transparent outline-none text-sm text-primary"
              placeholder="Jump to a page or search players…"
              @input="onInput"
            />
            <kbd class="text-[0.6rem] text-faint border border-edge rounded px-1 py-0.5">ESC</kbd>
          </div>

          <div ref="listEl" class="max-h-80 overflow-auto py-1">
            <template v-for="group in groups" :key="group.label">
              <p v-if="group.items.length" class="px-3 pt-2 pb-1 text-[0.6rem] uppercase tracking-wider text-faint">{{ group.label }}</p>
              <button
                v-for="item in group.items"
                :key="item.key"
                :data-idx="item.index"
                class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-left outline-none"
                :class="item.index === active ? 'bg-brand-subtle text-brand' : 'text-secondary hover:bg-elevated'"
                @click="select(item)"
                @mousemove="active = item.index"
              >
                <component :is="item.icon" v-if="item.icon" class="w-4 h-4 flex-shrink-0" />
                <Avatar v-else-if="item.uuid" :uuid="item.uuid" :name="item.label" :size="20" />
                <span class="truncate">{{ item.label }}</span>
              </button>
            </template>
            <p v-if="!flat.length" class="px-3 py-6 text-center text-sm text-muted">No results</p>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { api } from '../api'
import Avatar from './Avatar.vue'
import {
  MagnifyingGlassIcon, HomeIcon, UsersIcon, BanknotesIcon, NoSymbolIcon, GiftIcon,
  MapPinIcon, CommandLineIcon, ServerStackIcon, PuzzlePieceIcon, Cog6ToothIcon,
  MegaphoneIcon, ShieldCheckIcon, ClipboardDocumentListIcon, ClockIcon, LockClosedIcon, SwatchIcon,
} from '@heroicons/vue/24/outline'

const router = useRouter()
const auth = useAuthStore()

const PAGES = [
  { to: '/', label: 'Overview', icon: HomeIcon },
  { to: '/players', label: 'Players', icon: UsersIcon, perm: 'PLAYERS_VIEW' },
  { to: '/economy', label: 'Economy', icon: BanknotesIcon, perm: 'ECONOMY_VIEW' },
  { to: '/bans', label: 'Bans & Mutes', icon: NoSymbolIcon, perm: 'BANS_VIEW' },
  { to: '/kits', label: 'Kits', icon: GiftIcon, perm: 'KITS_VIEW' },
  { to: '/warps', label: 'Warps', icon: MapPinIcon, perm: 'WARPS_VIEW' },
  { to: '/console', label: 'Live Console', icon: CommandLineIcon, perm: 'CONSOLE_VIEW' },
  { to: '/server', label: 'Server Controls', icon: ServerStackIcon, perm: 'SERVER_MANAGE' },
  { to: '/modules', label: 'Modules', icon: PuzzlePieceIcon, perm: 'MODULES_VIEW' },
  { to: '/scheduler', label: 'Scheduler', icon: ClockIcon, perm: 'SCHEDULER_VIEW' },
  { to: '/config', label: 'Config Editor', icon: Cog6ToothIcon, perm: 'CONFIG_VIEW' },
  { to: '/tools', label: 'Tools', icon: MegaphoneIcon, anyPerm: ['BROADCAST', 'MAIL_MANAGE'] },
  { to: '/staff', label: 'Staff', icon: ShieldCheckIcon, admin: true },
  { to: '/branding', label: 'Branding', icon: SwatchIcon, admin: true },
  { to: '/security', label: 'Security', icon: LockClosedIcon },
  { to: '/audit-log', label: 'Audit Log', icon: ClipboardDocumentListIcon, perm: 'AUDIT_LOG' },
]

const open = ref(false)
const query = ref('')
const active = ref(0)
const players = ref([])
const inputEl = ref(null)
const listEl = ref(null)
let searchTimer = null

const visiblePages = computed(() => PAGES.filter(p => {
  if (p.admin) return auth.isAdmin
  if (p.anyPerm) return p.anyPerm.some(x => auth.hasPermission(x))
  if (p.perm) return auth.hasPermission(p.perm)
  return true
}).filter(p => p.label.toLowerCase().includes(query.value.toLowerCase())))

const groups = computed(() => {
  let i = 0
  const pageItems = visiblePages.value.map(p => ({ ...p, key: 'p' + p.to, index: i++ }))
  const playerItems = players.value.map(pl => ({
    key: 'u' + pl.uuid, label: pl.name, uuid: pl.uuid, to: `/players/${pl.uuid}`, index: i++,
  }))
  return [
    { label: 'Pages', items: pageItems },
    { label: 'Players', items: playerItems },
  ]
})

const flat = computed(() => groups.value.flatMap(g => g.items))

function onInput() {
  active.value = 0
  clearTimeout(searchTimer)
  const q = query.value.trim()
  if (!q || !auth.hasPermission('PLAYERS_VIEW')) { players.value = []; return }
  searchTimer = setTimeout(async () => {
    try { players.value = (await api.searchPlayers(q, 0, 6)).data.players } catch { players.value = [] }
  }, 200)
}

function move(dir) {
  const n = flat.value.length
  if (!n) return
  active.value = (active.value + dir + n) % n
  nextTick(() => {
    const el = listEl.value?.querySelector(`[data-idx="${active.value}"]`)
    el?.scrollIntoView({ block: 'nearest' })
  })
}

function choose() {
  const item = flat.value.find(i => i.index === active.value)
  if (item) select(item)
}

function select(item) {
  close()
  router.push(item.to)
}

function openPalette() {
  open.value = true
  query.value = ''
  players.value = []
  active.value = 0
  nextTick(() => inputEl.value?.focus())
}

function close() { open.value = false }

function onKey(e) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    open.value ? close() : openPalette()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  window.addEventListener('essdash:open-palette', openPalette)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('essdash:open-palette', openPalette)
})
</script>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
