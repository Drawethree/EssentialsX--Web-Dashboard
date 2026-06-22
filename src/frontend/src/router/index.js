import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import OverviewView from '../views/OverviewView.vue'
import AnalyticsView from '../views/AnalyticsView.vue'
import PlayersView from '../views/PlayersView.vue'
import PlayerDetailView from '../views/PlayerDetailView.vue'
import EconomyView from '../views/EconomyView.vue'
import BansView from '../views/BansView.vue'
import KitsView from '../views/KitsView.vue'
import WarpsView from '../views/WarpsView.vue'
import ConsoleView from '../views/ConsoleView.vue'
import ChatModerationView from '../views/ChatModerationView.vue'
import ConfigView from '../views/ConfigView.vue'
import ServerView from '../views/ServerView.vue'
import ModulesView from '../views/ModulesView.vue'
import StaffView from '../views/StaffView.vue'
import AuditLogView from '../views/AuditLogView.vue'
import ToolsView from '../views/ToolsView.vue'
import SchedulerView from '../views/SchedulerView.vue'
import SecurityView from '../views/SecurityView.vue'
import BrandingView from '../views/BrandingView.vue'
import ModerationSettingsView from '../views/ModerationSettingsView.vue'
import HelpView from '../views/HelpView.vue'

const routes = [
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/', component: OverviewView },
  { path: '/analytics', component: AnalyticsView },
  { path: '/players', component: PlayersView, meta: { permission: 'PLAYERS_VIEW' } },
  { path: '/players/:uuid', component: PlayerDetailView, meta: { permission: 'PLAYERS_VIEW' } },
  { path: '/economy', component: EconomyView, meta: { permission: 'ECONOMY_VIEW' } },
  { path: '/bans', component: BansView, meta: { permission: 'BANS_VIEW' } },
  { path: '/kits', component: KitsView, meta: { permission: 'KITS_VIEW' } },
  { path: '/warps', component: WarpsView, meta: { permission: 'WARPS_VIEW' } },
  { path: '/console', component: ConsoleView, meta: { permission: 'CONSOLE_VIEW' } },
  { path: '/chat', component: ChatModerationView, meta: { permission: 'CHAT_VIEW' } },
  { path: '/config', component: ConfigView, meta: { permission: 'CONFIG_VIEW' } },
  { path: '/server', component: ServerView, meta: { permission: 'SERVER_MANAGE' } },
  { path: '/modules', component: ModulesView, meta: { permission: 'MODULES_VIEW' } },
  { path: '/scheduler', component: SchedulerView, meta: { permission: 'SCHEDULER_VIEW' } },
  { path: '/security', component: SecurityView },
  { path: '/staff', component: StaffView, meta: { adminOnly: true } },
  { path: '/branding', component: BrandingView, meta: { adminOnly: true } },
  { path: '/moderation', component: ModerationSettingsView, meta: { adminOnly: true } },
  { path: '/audit-log', component: AuditLogView, meta: { permission: 'AUDIT_LOG' } },
  { path: '/tools', component: ToolsView, meta: { anyPermission: ['BROADCAST', 'MAIL_MANAGE'] } },
  { path: '/help', component: HelpView },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(to => {
  const token = localStorage.getItem('essdash_token')
  if (!to.meta.public && !token) return '/login'

  const role = localStorage.getItem('essdash_role')
  // Admin-only pages are also viewable by the read-only DEMO account (for the showcase); all
  // mutations on these pages stay blocked server-side. Regular STAFF remain restricted.
  if (to.meta.adminOnly && role !== 'ADMIN' && role !== 'DEMO') return '/'

  if (to.meta.permission && role !== 'ADMIN') {
    const perms = JSON.parse(localStorage.getItem('essdash_permissions') ?? '[]')
    if (!perms.includes(to.meta.permission)) return '/'
  }

  if (to.meta.anyPermission && role !== 'ADMIN') {
    const perms = JSON.parse(localStorage.getItem('essdash_permissions') ?? '[]')
    if (!to.meta.anyPermission.some(p => perms.includes(p))) return '/'
  }
})

export default router
