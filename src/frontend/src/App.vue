<template>
  <div v-if="isLoginPage" class="min-h-screen">
    <router-view />
  </div>
  <div v-else class="flex min-h-screen">
    <!-- Mobile overlay -->
    <div v-if="sidebarOpen" class="fixed inset-0 bg-black/50 z-40 lg:hidden" @click="sidebarOpen = false"></div>

    <!-- Sidebar -->
    <aside :class="[
      'fixed lg:static inset-y-0 left-0 z-50 bg-surface border-r border-edge flex flex-col flex-shrink-0 transition-all duration-200 w-60',
      collapsed ? 'lg:w-[4.75rem]' : 'lg:w-60',
      sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
    ]">
      <div class="p-4 border-b border-edge flex items-center gap-3" :class="collapsed ? 'lg:justify-center' : ''">
        <img :src="branding.logoUrl || logo" alt="" class="w-10 h-10 rounded-lg flex-shrink-0 object-contain" />
        <div :class="collapsed ? 'lg:hidden' : ''" class="min-w-0">
          <div class="text-sm font-bold text-primary leading-tight truncate max-w-[10rem]">{{ branding.serverName }}</div>
        </div>
      </div>

      <div v-if="auth.isDemo && !collapsed" class="mx-3 mt-3 px-3 py-2 rounded-lg callout-warn text-xs text-center">
        <span class="font-semibold">{{ t('demo.badge') }}</span> — {{ t('demo.readonly') }}
      </div>

      <nav class="flex-1 p-3 overflow-y-auto">
        <div v-for="group in visibleGroups" :key="group.label" class="mb-4 last:mb-0">
          <p v-if="group.label" class="px-3 mb-1 text-[0.65rem] font-semibold uppercase tracking-wider text-faint" :class="collapsed ? 'lg:hidden' : ''">
            {{ t(group.label) }}
          </p>
          <div class="space-y-0.5">
            <router-link
              v-for="item in group.items"
              :key="item.to"
              :to="item.to"
              class="nav-link"
              :class="[isActive(item) ? 'nav-link-active' : '', item.danger ? 'hover:text-red-600 dark:hover:text-red-400 hover:bg-red-500/10' : '', collapsed ? 'lg:justify-center' : '']"
              :title="collapsed ? t(item.label) : ''"
            >
              <component :is="item.icon" class="w-4 h-4 flex-shrink-0" />
              <span :class="collapsed ? 'lg:hidden' : ''">{{ t(item.label) }}</span>
            </router-link>
          </div>
        </div>
      </nav>

      <div class="p-3 border-t border-edge flex items-center justify-between">
        <span class="text-xs text-faint" :class="collapsed ? 'lg:hidden' : ''">© 2026 Drawethree</span>
        <button class="hidden lg:flex p-1.5 rounded-md text-muted hover:text-primary hover:bg-elevated transition-colors" :aria-label="collapsed ? 'Expand sidebar' : 'Collapse sidebar'" @click="toggleCollapsed">
          <ChevronDoubleLeftIcon v-if="!collapsed" class="w-4 h-4" />
          <ChevronDoubleRightIcon v-else class="w-4 h-4" />
        </button>
      </div>
    </aside>

    <!-- Main -->
    <main class="flex-1 flex flex-col min-w-0">
      <header class="flex items-center gap-3 px-4 py-2.5 bg-surface border-b border-edge sticky top-0 z-30">
        <button class="lg:hidden p-1.5 rounded-md hover:bg-elevated" @click="sidebarOpen = !sidebarOpen" aria-label="Toggle navigation">
          <Bars3Icon class="w-5 h-5 text-secondary" />
        </button>

        <!-- Search / command palette trigger -->
        <button
          class="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-edge bg-elevated/60 text-muted hover:text-primary hover:border-edge2 transition-colors text-sm w-full max-w-xs"
          @click="openPalette"
        >
          <MagnifyingGlassIcon class="w-4 h-4" />
          <span class="hidden sm:inline">{{ t('header.search') }}</span>
          <kbd class="ml-auto text-[0.6rem] border border-edge rounded px-1 py-0.5 hidden sm:inline">⌘K</kbd>
        </button>

        <div class="flex-1" />

        <!-- Notifications -->
        <div v-if="canViewNotifications" class="relative">
          <div v-if="notifOpen" class="fixed inset-0 z-40" @click="notifOpen = false"></div>
          <button class="relative z-50 p-2 rounded-md text-muted hover:text-primary hover:bg-elevated transition-colors" @click="toggleNotif" aria-label="Notifications">
            <BellIcon class="w-5 h-5" />
            <span v-if="unreadCount" class="absolute top-0.5 right-0.5 min-w-[1rem] h-4 px-1 rounded-full bg-red-500 text-white text-[0.6rem] font-bold flex items-center justify-center">{{ unreadCount > 9 ? '9+' : unreadCount }}</span>
          </button>
          <div v-if="notifOpen" class="absolute right-0 mt-1 w-80 max-w-[calc(100vw-2rem)] bg-overlay border border-edge rounded-lg shadow-lg z-50 overflow-hidden">
            <div class="px-3 py-2 border-b border-edge flex items-center justify-between">
              <p class="text-sm font-medium text-primary">{{ t('header.notifications') }}</p>
              <router-link to="/audit-log" class="text-xs text-brand hover:underline" @click="notifOpen = false">{{ t('header.viewAll') }}</router-link>
            </div>
            <div class="max-h-96 overflow-y-auto">
              <button
                v-for="(n, i) in notifications"
                :key="i"
                class="w-full text-left px-3 py-2 hover:bg-elevated flex items-start gap-2 border-b border-edge last:border-0"
                @click="openNotification(n)"
              >
                <span class="badge mt-0.5 flex-shrink-0" :class="auditBadgeClass(n.action)">{{ auditLabel(n.action) }}</span>
                <div class="min-w-0 flex-1">
                  <p class="text-xs text-secondary truncate">{{ n.details || '—' }}</p>
                  <p class="text-[0.65rem] text-faint">{{ n.user }} · {{ timeAgo(n.timestamp) }}</p>
                </div>
              </button>
              <p v-if="!notifications.length" class="text-sm text-muted text-center py-6">{{ t('header.noNotifications') }}</p>
            </div>
          </div>
        </div>

        <button class="p-2 rounded-md text-muted hover:text-primary hover:bg-elevated transition-colors" @click="theme.toggle()" :aria-label="t('header.toggleTheme')">
          <SunIcon v-if="theme.isDark" class="w-5 h-5" />
          <MoonIcon v-else class="w-5 h-5" />
        </button>

        <div class="relative">
          <!-- click-outside backdrop (closes the menu without racing the item click) -->
          <div v-if="menuOpen" class="fixed inset-0 z-40" @click="menuOpen = false"></div>
          <button class="relative z-50 flex items-center gap-2 pl-1 pr-2 py-1 rounded-md hover:bg-elevated" @click="menuOpen = !menuOpen">
            <span class="w-7 h-7 rounded-full bg-brand-subtle text-brand flex items-center justify-center text-xs font-semibold uppercase">
              {{ (auth.username || '?').charAt(0) }}
            </span>
            <span class="text-sm text-primary hidden sm:block max-w-[10rem] truncate">{{ auth.username }}</span>
            <ChevronDownIcon class="w-4 h-4 text-muted" />
          </button>
          <div v-if="menuOpen" class="absolute right-0 mt-1 w-52 bg-overlay border border-edge rounded-lg shadow-lg py-1 z-50">
            <div class="px-3 py-1.5 border-b border-edge">
              <p class="text-xs text-muted">{{ t('header.signedInAs') }}</p>
              <p class="text-sm font-medium text-primary truncate">{{ auth.username }}</p>
            </div>
            <button v-if="!auth.isDemo" class="w-full text-left px-3 py-2 text-sm text-secondary hover:bg-elevated flex items-center gap-2" @click="openCredentials">
              <KeyIcon class="w-4 h-4" /> {{ t('header.changeCredentials') }}
            </button>
            <router-link v-if="!auth.isDemo" to="/security" class="w-full text-left px-3 py-2 text-sm text-secondary hover:bg-elevated flex items-center gap-2">
              <LockClosedIcon class="w-4 h-4" /> {{ t('header.security') }}
            </router-link>
            <div class="px-3 py-2 border-t border-edge">
              <p class="text-xs text-muted mb-1 flex items-center gap-2"><LanguageIcon class="w-4 h-4" /> {{ t('header.language') }}</p>
              <div class="flex flex-wrap gap-1">
                <button
                  v-for="l in locales" :key="l.code"
                  class="px-2 py-1 rounded text-xs"
                  :class="locale === l.code ? 'bg-brand-subtle text-brand font-medium' : 'text-secondary hover:bg-elevated'"
                  @click="changeLocale(l.code)"
                >{{ l.label }}</button>
              </div>
            </div>
            <button class="w-full text-left px-3 py-2 text-sm text-red-500 hover:bg-red-500/10 flex items-center gap-2 border-t border-edge" @click="auth.logout()">
              <ArrowRightOnRectangleIcon class="w-4 h-4" /> {{ t('header.logout') }}
            </button>
          </div>
        </div>
      </header>

      <!-- Demo nudge: read-only notice + links to docs/community, dismissible. -->
      <div v-if="auth.isDemo && !demoBannerDismissed" class="flex items-center gap-2 px-4 py-2 bg-amber-500/10 border-b border-amber-500/30 text-amber-700 dark:text-amber-300 text-sm">
        <span class="flex-1 min-w-0">
          You're exploring a read-only demo — most actions are disabled. Read the
          <a :href="WIKI_URL" target="_blank" rel="noopener noreferrer" class="font-semibold underline underline-offset-2 hover:text-amber-900 dark:hover:text-amber-200">Wiki</a>
          or join our
          <a :href="DISCORD_URL" target="_blank" rel="noopener noreferrer" class="font-semibold underline underline-offset-2 hover:text-amber-900 dark:hover:text-amber-200">Discord</a>.
        </span>
        <button class="p-1 rounded hover:bg-amber-500/20 flex-shrink-0" aria-label="Dismiss" @click="dismissDemoBanner">
          <XMarkIcon class="w-4 h-4" />
        </button>
      </div>

      <div class="flex-1 overflow-auto">
        <router-view />
      </div>
    </main>
  </div>

  <ToastContainer />
  <ConfirmDialog />
  <CommandPalette />

  <!-- Forced password change: blocks the panel until the default password is replaced. -->
  <div v-if="auth.mustChangePassword && !isLoginPage" class="fixed inset-0 z-[100] bg-black/70 flex items-center justify-center p-4">
    <div class="bg-overlay border border-edge rounded-xl shadow-2xl w-full max-w-md p-6 space-y-4">
      <div class="flex items-center gap-3">
        <span class="w-10 h-10 rounded-lg bg-red-500/15 text-red-500 flex items-center justify-center flex-shrink-0"><KeyIcon class="w-5 h-5" /></span>
        <div>
          <h2 class="text-lg font-bold text-primary leading-tight">Change your password</h2>
          <p class="text-xs text-muted">You're still using the default password. Set a new one to continue.</p>
        </div>
      </div>
      <form class="space-y-3" @submit.prevent="submitForced">
        <div>
          <label class="label">Current Password</label>
          <input v-model="forcedForm.currentPassword" type="password" class="input" autocomplete="current-password" />
        </div>
        <div>
          <label class="label">New Password <span class="text-faint">(min 6)</span></label>
          <input v-model="forcedForm.newPassword" type="password" class="input" autocomplete="new-password" />
        </div>
        <div>
          <label class="label">Confirm New Password</label>
          <input v-model="forcedForm.confirm" type="password" class="input" autocomplete="new-password" />
        </div>
        <p v-if="forcedError" class="text-sm text-danger">{{ forcedError }}</p>
        <div class="flex justify-end gap-2 pt-1">
          <button type="button" class="btn-ghost" @click="auth.logout()">Log out</button>
          <button type="submit" class="btn-primary" :disabled="forcedSaving">Set password</button>
        </div>
      </form>
    </div>
  </div>

  <Modal :open="credModal" title="Change Credentials" @close="credModal = false">
    <form id="cred-form" class="space-y-3" @submit.prevent="submitCredentials">
      <div>
        <label class="label">Current Password</label>
        <input v-model="credForm.currentPassword" type="password" class="input" autocomplete="current-password" />
      </div>
      <div>
        <label class="label">New Username <span class="text-faint">(optional)</span></label>
        <input v-model="credForm.newUsername" class="input" maxlength="32" autocomplete="username" />
      </div>
      <div>
        <label class="label">New Password <span class="text-faint">(optional, min 6)</span></label>
        <input v-model="credForm.newPassword" type="password" class="input" autocomplete="new-password" />
      </div>
      <p v-if="credError" class="text-sm text-danger">{{ credError }}</p>
    </form>
    <template #footer>
      <button class="btn-ghost" @click="credModal = false">Cancel</button>
      <button type="submit" form="cred-form" class="btn-primary" :disabled="credSaving">Save</button>
    </template>
  </Modal>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { LOCALES, setLocale } from './i18n'
import { useAuthStore } from './stores/auth'
import { useThemeStore } from './stores/theme'
import { useToastStore } from './stores/toast'
import { useBrandingStore } from './stores/branding'
import { api } from './api'
import ToastContainer from './components/ToastContainer.vue'
import ConfirmDialog from './components/ui/ConfirmDialog.vue'
import CommandPalette from './components/CommandPalette.vue'
import Modal from './components/Modal.vue'
import logo from './assets/logo.png'
import { isNotableAction, auditLabel, auditBadgeClass } from './utils/auditActions'
import { timeAgo } from './utils'
import {
  HomeIcon, UsersIcon, BanknotesIcon, NoSymbolIcon, GiftIcon, MapPinIcon,
  CommandLineIcon, Cog6ToothIcon, ShieldCheckIcon, ClipboardDocumentListIcon,
  MegaphoneIcon, SunIcon, MoonIcon, Bars3Icon, ChevronDownIcon, KeyIcon,
  ArrowRightOnRectangleIcon, ServerStackIcon, PuzzlePieceIcon,
  ChevronDoubleLeftIcon, ChevronDoubleRightIcon, MagnifyingGlassIcon,
  ClockIcon, LockClosedIcon, SwatchIcon, ChartBarIcon, BellIcon,
  ChatBubbleLeftRightIcon, ShieldExclamationIcon, LanguageIcon,
  QuestionMarkCircleIcon, XMarkIcon,
} from '@heroicons/vue/24/outline'
import { WIKI_URL, DISCORD_URL } from './support'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const locales = LOCALES
function changeLocale(code) { setLocale(code); menuOpen.value = false }
const auth = useAuthStore()
const theme = useThemeStore()
const toast = useToastStore()
const branding = useBrandingStore()

const isLoginPage = computed(() => route.path === '/login')
const sidebarOpen = ref(false)
const menuOpen = ref(false)
const collapsed = ref(localStorage.getItem('essdash_sidebar_collapsed') === '1')

function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem('essdash_sidebar_collapsed', collapsed.value ? '1' : '0')
}

const demoBannerDismissed = ref(localStorage.getItem('essdash_demo_banner_dismissed') === '1')
function dismissDemoBanner() {
  demoBannerDismissed.value = true
  localStorage.setItem('essdash_demo_banner_dismissed', '1')
}

function openPalette() {
  window.dispatchEvent(new Event('essdash:open-palette'))
}

// `label` values are i18n keys, resolved with t() at render time.
const NAV_GROUPS = [
  { label: '', items: [
    { to: '/', label: 'nav.overview', icon: HomeIcon, exact: true },
    { to: '/analytics', label: 'nav.analytics', icon: ChartBarIcon },
    { to: '/help', label: 'nav.help', icon: QuestionMarkCircleIcon },
  ] },
  {
    label: 'nav.management',
    items: [
      { to: '/players', label: 'nav.players', icon: UsersIcon, perm: 'PLAYERS_VIEW' },
      { to: '/economy', label: 'nav.economy', icon: BanknotesIcon, perm: 'ECONOMY_VIEW' },
      { to: '/bans', label: 'nav.bansMutes', icon: NoSymbolIcon, perm: 'BANS_VIEW' },
      { to: '/kits', label: 'nav.kits', icon: GiftIcon, perm: 'KITS_VIEW' },
      { to: '/warps', label: 'nav.warps', icon: MapPinIcon, perm: 'WARPS_VIEW' },
    ],
  },
  {
    label: 'nav.server',
    items: [
      { to: '/console', label: 'nav.console', icon: CommandLineIcon, perm: 'CONSOLE_VIEW' },
      { to: '/chat', label: 'nav.chat', icon: ChatBubbleLeftRightIcon, perm: 'CHAT_VIEW' },
      { to: '/server', label: 'nav.serverControls', icon: ServerStackIcon, perm: 'SERVER_MANAGE' },
      { to: '/modules', label: 'nav.modules', icon: PuzzlePieceIcon, perm: 'MODULES_VIEW' },
      { to: '/scheduler', label: 'nav.scheduler', icon: ClockIcon, perm: 'SCHEDULER_VIEW' },
      { to: '/config', label: 'nav.config', icon: Cog6ToothIcon, perm: 'CONFIG_VIEW' },
      { to: '/tools', label: 'nav.tools', icon: MegaphoneIcon, anyPerm: ['BROADCAST', 'MAIL_MANAGE'] },
    ],
  },
  {
    label: 'nav.admin',
    items: [
      { to: '/staff', label: 'nav.staff', icon: ShieldCheckIcon, admin: true },
      { to: '/moderation', label: 'nav.moderation', icon: ShieldExclamationIcon, admin: true },
      { to: '/branding', label: 'nav.branding', icon: SwatchIcon, admin: true },
      { to: '/audit-log', label: 'nav.auditLog', icon: ClipboardDocumentListIcon, perm: 'AUDIT_LOG' },
    ],
  },
]

function itemVisible(item) {
  // Admin-only nav items are also shown to the read-only DEMO account so evaluators can browse
  // them (writes stay blocked server-side).
  if (item.admin) return auth.isAdmin || auth.isDemo
  if (item.anyPerm) return item.anyPerm.some(p => auth.hasPermission(p))
  if (item.perm) return auth.hasPermission(item.perm)
  return true
}

const visibleGroups = computed(() =>
  NAV_GROUPS
    .map(g => ({ label: g.label, items: g.items.filter(itemVisible) }))
    .filter(g => g.items.length),
)

function isActive(item) {
  return item.exact ? route.path === item.to : route.path.startsWith(item.to)
}

// Change credentials
const credModal = ref(false)
const credSaving = ref(false)
const credError = ref('')
const credForm = ref({ currentPassword: '', newUsername: '', newPassword: '' })

function openCredentials() {
  menuOpen.value = false
  credForm.value = { currentPassword: '', newUsername: auth.username, newPassword: '' }
  credError.value = ''
  credModal.value = true
}

async function submitCredentials() {
  credError.value = ''
  credSaving.value = true
  try {
    const { data } = await api.changeAccount(
      credForm.value.currentPassword,
      credForm.value.newUsername || auth.username,
      credForm.value.newPassword || null,
    )
    auth.updateToken(data.token, data.username)
    toast.success('Credentials updated.')
    credModal.value = false
  } catch (err) {
    credError.value = err.response?.data?.error ?? 'Failed to update credentials'
  } finally {
    credSaving.value = false
  }
}

// Forced password change (default-password guard)
const forcedForm = ref({ currentPassword: '', newPassword: '', confirm: '' })
const forcedSaving = ref(false)
const forcedError = ref('')

async function submitForced() {
  forcedError.value = ''
  if (forcedForm.value.newPassword.length < 6) {
    forcedError.value = 'New password must be at least 6 characters.'
    return
  }
  if (forcedForm.value.newPassword !== forcedForm.value.confirm) {
    forcedError.value = 'Passwords do not match.'
    return
  }
  forcedSaving.value = true
  try {
    const { data } = await api.changeAccount(forcedForm.value.currentPassword, auth.username, forcedForm.value.newPassword)
    auth.updateToken(data.token, data.username)
    auth.clearMustChange()
    forcedForm.value = { currentPassword: '', newPassword: '', confirm: '' }
    toast.success('Password updated.')
  } catch (err) {
    forcedError.value = err.response?.data?.error ?? 'Failed to update password.'
  } finally {
    forcedSaving.value = false
  }
}

// Notification centre — surfaces notable audit events with an unread badge.
const canViewNotifications = computed(() => auth.hasPermission('AUDIT_LOG'))
const notifOpen = ref(false)
const notifications = ref([])
const lastSeen = ref(Number(localStorage.getItem('essdash_notifications_seen') || 0))
let notifPoll = null

const unreadCount = computed(() => notifications.value.filter(n => n.timestamp > lastSeen.value).length)
const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i

async function loadNotifications() {
  if (!canViewNotifications.value) return
  try {
    const { data } = await api.auditLog(0, 30)
    notifications.value = data.entries.filter(e => isNotableAction(e.action)).slice(0, 15)
  } catch { /* ignore */ }
}

function markSeen() {
  const newest = notifications.value[0]?.timestamp
  if (newest && newest > lastSeen.value) {
    lastSeen.value = newest
    localStorage.setItem('essdash_notifications_seen', String(newest))
  }
}

function toggleNotif() {
  notifOpen.value = !notifOpen.value
  if (notifOpen.value) markSeen()
}

function openNotification(n) {
  notifOpen.value = false
  const m = (n.details || '').match(UUID_RE)
  router.push(m ? `/players/${m[0]}` : '/audit-log')
}

watch(route, () => { sidebarOpen.value = false; menuOpen.value = false })
onMounted(() => {
  auth.init(); theme.apply(); branding.fetch()
  loadNotifications()
  notifPoll = setInterval(loadNotifications, 30000)
})
onUnmounted(() => { if (notifPoll) clearInterval(notifPoll) })
</script>
