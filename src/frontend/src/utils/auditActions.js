// Severity-based styling for audit-log action types. Mirrors the colour scheme used by the
// backend webhook embeds (notify/NotificationService.java) and the punishment badges.

const RED = 'bg-red-500/15 text-red-500'
const AMBER = 'bg-amber-500/15 text-amber-500'
const GREEN = 'bg-green-500/15 text-green-500'
const NEUTRAL = 'bg-brand-subtle text-brand'

// Destructive or security-negative events.
const RED_ACTIONS = new Set([
  'BAN', 'KICK', 'SERVER_STOP', 'LOGIN_FAIL', 'LOGIN_2FA_FAIL', 'CONSOLE', 'CONSOLE_BLOCKED',
  '2FA_DISABLE', 'STAFF_DELETE', 'SESSION_REVOKE_ADMIN',
])

// Caution: config edits and access-control / privilege changes.
const AMBER_ACTIONS = new Set([
  'MUTE', 'ECONOMY_BULK', 'CONFIG_SAVE', 'CHAT_FORMAT_SAVE', 'PROTECT_SAVE', 'DISCORD_CONFIG_SAVE',
  'WHITELIST_TOGGLE', 'WHITELIST_ADD', 'WHITELIST_REMOVE', 'STAFF_CREATE', 'STAFF_UPDATE',
  'ACCOUNT_UPDATE', 'SESSION_REVOKE', 'SESSION_REVOKE_OTHERS', '2FA_RECOVERY_REGEN',
  'SET_MONEY', 'GAMEMODE', 'JAIL_PLAYER',
])

// Positive / reversal / successful-access events.
const GREEN_ACTIONS = new Set([
  'UNBAN', 'UNMUTE', 'LOGIN', 'DEMO_LOGIN', '2FA_ENABLE', 'UNJAIL_PLAYER', 'SAVE_ALL',
])

// Human-readable overrides where the generic underscore→Title-Case rule reads awkwardly.
const LABEL_OVERRIDES = {
  LOGIN_FAIL: 'Login Failed',
  LOGIN_2FA_FAIL: '2FA Failed',
  DEMO_LOGIN: 'Demo Login',
  '2FA_ENABLE': '2FA Enabled',
  '2FA_DISABLE': '2FA Disabled',
  '2FA_RECOVERY_REGEN': '2FA Recovery Codes',
  SESSION_REVOKE: 'Session Revoked',
  SESSION_REVOKE_OTHERS: 'Sessions Revoked (others)',
  SESSION_REVOKE_ADMIN: 'Session Revoked (admin)',
  MAIL_ALL: 'Mail All',
  ECONOMY_BULK: 'Bulk Economy',
  CONFIG_SAVE: 'Config Saved',
  CHAT_FORMAT_SAVE: 'Chat Format Saved',
  PROTECT_SAVE: 'Protect Config Saved',
  DISCORD_CONFIG_SAVE: 'Discord Config Saved',
  INV_SET: 'Inventory Set',
  INV_CLEAR: 'Inventory Cleared',
  SET_MONEY: 'Set Balance',
  SET_NICKNAME: 'Set Nickname',
  SET_HOME: 'Set Home',
  SET_SPAWN: 'Set Spawn',
  SAVE_ALL: 'Save All',
  ACCOUNT_UPDATE: 'Account Updated',
  CONSOLE_BLOCKED: 'Blocked Command',
}

// Actions worth surfacing in the in-app notification bell (security/moderation-relevant).
const NOTABLE_ACTIONS = new Set([
  'BAN', 'UNBAN', 'KICK', 'MUTE', 'UNMUTE', 'SERVER_STOP', 'LOGIN_FAIL', 'LOGIN_2FA_FAIL',
  'CONSOLE_BLOCKED', '2FA_DISABLE', 'STAFF_CREATE', 'STAFF_DELETE', 'SESSION_REVOKE_ADMIN',
])

/** Whether an audit action should appear in the notification centre. */
export function isNotableAction(action) {
  return !!action && NOTABLE_ACTIONS.has(action.toUpperCase())
}

/** Tailwind badge classes for a given audit action. */
export function auditBadgeClass(action) {
  if (!action) return NEUTRAL
  const a = action.toUpperCase()
  if (RED_ACTIONS.has(a) || a.startsWith('DELETE')) return RED
  if (AMBER_ACTIONS.has(a) || a.startsWith('CLEAR')) return AMBER
  if (GREEN_ACTIONS.has(a)) return GREEN
  return NEUTRAL
}

/** Human-friendly label for an action constant, e.g. SET_MONEY → "Set Balance". */
export function auditLabel(action) {
  if (!action) return ''
  const a = action.toUpperCase()
  if (LABEL_OVERRIDES[a]) return LABEL_OVERRIDES[a]
  return action.toLowerCase().split('_').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
}
