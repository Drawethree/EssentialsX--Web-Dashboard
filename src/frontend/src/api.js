import axios from 'axios'
import { useToastStore } from './stores/toast'

const http = axios.create({ baseURL: '/' })

http.interceptors.request.use(config => {
  const token = localStorage.getItem('essdash_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  r => r,
  err => {
    if (err.response?.status === 401 && window.location.pathname !== '/login') {
      localStorage.removeItem('essdash_token')
      window.location.href = '/login'
      return Promise.reject(err)
    }
    if (err.response?.status === 403 && err.response?.data?.error === 'Demo accounts are read-only') {
      useToastStore().info('This action is restricted in the Demo Account.')
      return Promise.reject(err)
    }
    return Promise.reject(err)
  },
)

export const api = {
  // Auth
  login: (username, password) => http.post('/api/auth/login', { username, password }),
  completeTotp: (preAuthToken, code) => http.post('/api/auth/login/totp', { preAuthToken, code }),
  logout: () => http.post('/api/auth/logout'),
  demoAvailable: () => http.get('/api/auth/demo-available'),
  demoLogin: () => http.post('/api/auth/demo'),
  changeAccount: (currentPassword, newUsername, newPassword) =>
    http.put('/api/auth/account', { currentPassword, newUsername, newPassword }),

  // Two-factor authentication
  get2fa: () => http.get('/api/auth/2fa'),
  setup2fa: password => http.post('/api/auth/2fa/setup', { password }),
  enable2fa: code => http.post('/api/auth/2fa/enable', { code }),
  disable2fa: password => http.post('/api/auth/2fa/disable', { password }),
  regenerateRecoveryCodes: password => http.post('/api/auth/2fa/recovery-codes', { password }),

  // Active sessions
  getSessions: () => http.get('/api/auth/sessions'),
  revokeSession: jti => http.delete(`/api/auth/sessions/${encodeURIComponent(jti)}`),
  revokeOtherSessions: () => http.post('/api/auth/sessions/revoke-others'),
  getAllSessions: () => http.get('/api/admin/sessions'),
  revokeAnySession: jti => http.delete(`/api/admin/sessions/${encodeURIComponent(jti)}`),

  // Scheduled tasks
  getTasks: () => http.get('/api/scheduler/tasks'),
  createTask: data => http.post('/api/scheduler/tasks', data),
  updateTask: (id, data) => http.put(`/api/scheduler/tasks/${id}`, data),
  deleteTask: id => http.delete(`/api/scheduler/tasks/${id}`),
  toggleTask: id => http.post(`/api/scheduler/tasks/${id}/toggle`),
  runTask: id => http.post(`/api/scheduler/tasks/${id}/run`),

  // Branding (white-label)
  getBranding: () => http.get('/api/branding'),
  saveBranding: data => http.put('/api/branding', data),
  uploadLogo: formData => http.post('/api/branding/logo', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  deleteLogo: () => http.delete('/api/branding/logo'),

  // Server
  overview: () => http.get('/api/server/overview'),
  materials: () => http.get('/api/meta/materials'),

  // Players
  searchPlayers: (q = '', page = 0, size = 20) => http.get('/api/players', { params: { q, page, size } }),
  getPlayer: uuid => http.get(`/api/players/${uuid}`),
  setMoney: (uuid, action, amount) => http.put(`/api/players/${uuid}/money`, { action, amount }),
  setNickname: (uuid, nickname) => http.put(`/api/players/${uuid}/nickname`, { nickname }),
  getHomes: uuid => http.get(`/api/players/${uuid}/homes`),
  deleteHome: (uuid, name) => http.delete(`/api/players/${uuid}/homes/${encodeURIComponent(name)}`),
  getMail: uuid => http.get(`/api/players/${uuid}/mail`),
  sendMail: (uuid, message) => http.post(`/api/players/${uuid}/mail`, { message }),
  clearMail: uuid => http.delete(`/api/players/${uuid}/mail`),
  mute: (uuid, durationMinutes) => http.post(`/api/players/${uuid}/mute`, { durationMinutes }),
  unmute: uuid => http.delete(`/api/players/${uuid}/mute`),
  ban: (uuid, reason, durationMinutes) => http.post(`/api/players/${uuid}/ban`, { reason, durationMinutes }),
  unban: uuid => http.delete(`/api/players/${uuid}/ban`),
  kick: (uuid, reason) => http.post(`/api/players/${uuid}/kick`, { reason }),
  message: (uuid, message) => http.post(`/api/players/${uuid}/message`, { message }),
  setGamemode: (uuid, gamemode) => http.put(`/api/players/${uuid}/gamemode`, { gamemode }),
  playerAction: (uuid, action) => http.post(`/api/players/${uuid}/action`, { action }),
  giveItem: (uuid, material, amount) => http.post(`/api/players/${uuid}/give`, { material, amount }),
  teleportToPlayer: (uuid, targetUuid) => http.post(`/api/players/${uuid}/teleport`, { targetUuid }),
  setHome: (uuid, data) => http.put(`/api/players/${uuid}/homes`, data),
  getInventory: uuid => http.get(`/api/players/${uuid}/inventory`),
  setSlot: (uuid, slot, material, amount) => http.put(`/api/players/${uuid}/inventory/${slot}`, { material, amount }),
  clearSlot: (uuid, slot) => http.delete(`/api/players/${uuid}/inventory/${slot}`),
  getGeo: uuid => http.get(`/api/players/${uuid}/geo`),
  getPunishments: (uuid, page = 0, size = 20) => http.get(`/api/players/${uuid}/punishments`, { params: { page, size } }),
  getNotes: uuid => http.get(`/api/players/${uuid}/notes`),
  addNote: (uuid, note) => http.post(`/api/players/${uuid}/notes`, { note }),
  deleteNote: (uuid, id) => http.delete(`/api/players/${uuid}/notes/${id}`),
  playerTimeline: uuid => http.get(`/api/players/${uuid}/timeline`),
  playerAlts: uuid => http.get(`/api/players/${uuid}/alts`),
  warnPlayer: (uuid, reason) => http.post(`/api/players/${uuid}/warn`, { reason }),

  // Analytics
  analyticsHistory: (range = '24h') => http.get('/api/analytics/history', { params: { range } }),

  // Economy
  baltop: (page = 0, size = 20) => http.get('/api/economy/baltop', { params: { page, size } }),
  economyStats: () => http.get('/api/economy/stats'),
  economyBulk: (action, amount, target) => http.post('/api/economy/bulk', { action, amount, target }),
  economyDebts: () => http.get('/api/economy/debts'),
  economyResetDebts: () => http.post('/api/economy/reset-debts'),

  // Bans & mutes
  getBans: (page = 0, size = 20) => http.get('/api/bans', { params: { page, size } }),
  getMutes: (page = 0, size = 20) => http.get('/api/bans/mutes', { params: { page, size } }),

  // Kits
  getKits: () => http.get('/api/kits'),
  saveKit: (name, delay, items) => http.put(`/api/kits/${encodeURIComponent(name)}`, { delay, items }),
  deleteKit: name => http.delete(`/api/kits/${encodeURIComponent(name)}`),

  // Warps
  getWarps: () => http.get('/api/warps'),
  saveWarp: (name, data) => http.put(`/api/warps/${encodeURIComponent(name)}`, data),
  deleteWarp: name => http.delete(`/api/warps/${encodeURIComponent(name)}`),
  teleportWarp: (name, uuid) => http.post(`/api/warps/${encodeURIComponent(name)}/teleport`, { uuid }),

  // Console
  runCommand: command => http.post('/api/console/execute', { command }),

  // Chat moderation
  chatHistory: (page = 0, size = 50, q = '', uuid = '') =>
    http.get('/api/chat', { params: { page, size, q, uuid } }),
  deleteChat: id => http.delete(`/api/chat/${id}`),

  // Moderation templates + escalation
  getTemplates: () => http.get('/api/moderation/templates'),
  saveTemplates: templates => http.put('/api/moderation/templates', { templates }),
  getEscalation: () => http.get('/api/moderation/escalation'),
  saveEscalation: rules => http.put('/api/moderation/escalation', { rules }),

  // Economy intelligence
  economyTransactions: (page = 0, size = 50, uuid = '') =>
    http.get('/api/economy/transactions', { params: { page, size, uuid } }),
  economyInsights: (range = '7d') => http.get('/api/economy/insights', { params: { range } }),

  // Config
  getConfig: () => http.get('/api/config'),
  saveConfig: content => http.put('/api/config', { content }),

  // Staff
  getStaff: () => http.get('/api/staff'),
  createStaff: data => http.post('/api/staff', data),
  updateStaff: (username, data) => http.put(`/api/staff/${encodeURIComponent(username)}`, data),
  deleteStaff: username => http.delete(`/api/staff/${encodeURIComponent(username)}`),
  resetStaff2fa: username => http.delete(`/api/staff/${encodeURIComponent(username)}/2fa`),

  // Admin
  broadcast: message => http.post('/api/admin/broadcast', { message }),
  mailAll: message => http.post('/api/admin/mail-all', { message }),
  auditLog: (page = 0, size = 50, q = '', action = '') =>
    http.get('/api/admin/audit-log', { params: { page, size, q, action } }),

  // Server controls
  getWhitelist: () => http.get('/api/admin/whitelist'),
  setWhitelistEnabled: enabled => http.put('/api/admin/whitelist', { enabled }),
  addWhitelist: name => http.post('/api/admin/whitelist', { name }),
  removeWhitelist: name => http.delete(`/api/admin/whitelist/${encodeURIComponent(name)}`),
  getWorlds: () => http.get('/api/admin/worlds'),
  updateWorld: (world, data) => http.post(`/api/admin/worlds/${encodeURIComponent(world)}`, data),
  saveAll: () => http.post('/api/admin/save-all'),
  stopServer: () => http.post('/api/admin/stop', { confirm: true }),
  getSpawn: () => http.get('/api/admin/spawn'),
  setSpawn: data => http.post('/api/admin/spawn', data),
  getJails: () => http.get('/api/admin/jails'),
  createJail: data => http.post('/api/admin/jails', data),
  deleteJail: name => http.delete(`/api/admin/jails/${encodeURIComponent(name)}`),
  jailPlayer: (player, jail, minutes) => http.post('/api/admin/jails/jail', { player, jail, minutes }),
  unjailPlayer: player => http.post('/api/admin/jails/unjail', { player }),

  // EssentialsX modules
  getModules: () => http.get('/api/modules'),
  getChatModule: () => http.get('/api/modules/chat'),
  saveChatModule: (format, groupFormats) => http.put('/api/modules/chat', { format, groupFormats }),
  getProtectModule: () => http.get('/api/modules/protect'),
  saveProtectModule: content => http.put('/api/modules/protect', { content }),
  getDiscordModule: () => http.get('/api/modules/discord'),
  saveDiscordModule: content => http.put('/api/modules/discord', { content }),
}

/** Opens an SSE stream to the live event channel. Returns the EventSource. */
export function openEventStream() {
  const token = localStorage.getItem('essdash_token')
  return new EventSource(`/api/events/stream?token=${encodeURIComponent(token)}`)
}
