import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '../api'
import router from '../router'

export const useAuthStore = defineStore('auth', () => {
  const username = ref(null)
  const role = ref(null)
  const permissions = ref([])
  const mustChangePassword = ref(false)

  function init() {
    const token = localStorage.getItem('essdash_token')
    const user = localStorage.getItem('essdash_user')
    if (token && user) {
      username.value = user
      role.value = localStorage.getItem('essdash_role')
      const stored = localStorage.getItem('essdash_permissions')
      permissions.value = stored ? JSON.parse(stored) : []
      mustChangePassword.value = localStorage.getItem('essdash_must_change') === '1'
    }
  }

  async function login(user, pass) {
    const { data } = await api.login(user, pass)
    applySession(data)
  }

  function loginWithData(data) {
    applySession(data)
  }

  function applySession(data) {
    localStorage.setItem('essdash_token', data.token)
    localStorage.setItem('essdash_user', data.username)
    localStorage.setItem('essdash_role', data.role ?? 'STAFF')
    localStorage.setItem('essdash_permissions', JSON.stringify(data.permissions ?? []))
    localStorage.setItem('essdash_must_change', data.mustChangePassword ? '1' : '0')
    username.value = data.username
    role.value = data.role ?? 'STAFF'
    permissions.value = data.permissions ?? []
    mustChangePassword.value = !!data.mustChangePassword
    router.push('/')
  }

  function logout() {
    // Best-effort server-side revocation of the current session before clearing local state.
    api.logout().catch(() => {})
    ;['token', 'user', 'role', 'permissions', 'must_change'].forEach(k => localStorage.removeItem(`essdash_${k}`))
    username.value = null
    role.value = null
    permissions.value = []
    mustChangePassword.value = false
    router.push('/login')
  }

  function updateToken(newToken, newUsername) {
    localStorage.setItem('essdash_token', newToken)
    localStorage.setItem('essdash_user', newUsername)
    username.value = newUsername
  }

  function clearMustChange() {
    mustChangePassword.value = false
    localStorage.setItem('essdash_must_change', '0')
  }

  const isAdmin = computed(() => role.value === 'ADMIN')
  const isDemo = computed(() => role.value === 'DEMO')

  function hasPermission(perm) {
    if (isAdmin.value) return true
    return permissions.value.includes(perm)
  }

  return { username, role, permissions, mustChangePassword, isAdmin, isDemo, hasPermission, init, login, loginWithData, logout, updateToken, clearMustChange }
})
