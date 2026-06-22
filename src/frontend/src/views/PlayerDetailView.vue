<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-5">
    <router-link to="/players" class="text-sm text-muted hover:text-primary">← Back to players</router-link>

    <div v-if="player" class="space-y-5">
      <!-- Identity header -->
      <div class="card flex flex-wrap items-center gap-4">
        <Avatar :uuid="player.uuid" :name="player.name" :size="64" />
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2 flex-wrap">
            <h1 class="truncate">{{ player.name }}</h1>
            <span :class="player.online ? 'badge-online' : 'badge-offline'">{{ player.online ? 'Online' : 'Offline' }}</span>
            <span v-if="player.banned" class="badge bg-red-500/15 text-red-500">Banned</span>
            <span v-if="player.muted" class="badge bg-amber-500/15 text-amber-500">Muted</span>
            <span v-if="player.vanished" class="badge bg-violet-500/15 text-violet-500">Vanished</span>
            <span v-if="player.op" class="badge bg-brand-subtle text-brand">OP</span>
          </div>
          <button class="text-xs text-muted font-mono mt-1 hover:text-primary inline-flex items-center gap-1" title="Copy UUID" @click="copy(player.uuid, 'UUID copied')">
            {{ player.uuid }} <ClipboardIcon class="w-3 h-3" />
          </button>
        </div>
        <div class="text-right">
          <p class="text-xs text-muted uppercase">Balance</p>
          <p class="text-2xl font-bold text-brand">{{ formatMoney(player.money, player.symbol) }}</p>
        </div>
      </div>

      <TabsRoot v-model="tab" class="space-y-4">
        <TabsList class="flex gap-1 border-b border-edge overflow-x-auto">
          <TabsTrigger
            v-for="t in visibleTabs" :key="t.value" :value="t.value"
            class="px-3 py-2 text-sm whitespace-nowrap border-b-2 border-transparent text-muted hover:text-primary data-[state=active]:border-brand data-[state=active]:text-brand transition-colors outline-none"
          >{{ t.label }}</TabsTrigger>
        </TabsList>

        <!-- Overview -->
        <TabsContent value="overview" class="outline-none">
          <div class="grid sm:grid-cols-2 gap-5">
            <div class="card space-y-2">
              <h3>Details</h3>
              <dl class="text-sm space-y-1.5">
                <div class="flex justify-between"><dt class="text-muted">Nickname</dt><dd class="text-primary">{{ player.nickname || '—' }}</dd></div>
                <div class="flex justify-between"><dt class="text-muted">Last login</dt><dd class="text-primary">{{ player.lastLogin ? timeAgo(player.lastLogin) : '—' }}</dd></div>
                <div v-if="player.online" class="flex justify-between"><dt class="text-muted">World</dt><dd class="text-primary">{{ player.world }}</dd></div>
                <div v-if="player.online" class="flex justify-between"><dt class="text-muted">Gamemode</dt><dd class="text-primary">{{ player.gamemode }}</dd></div>
                <div class="flex justify-between items-center">
                  <dt class="text-muted">IP</dt>
                  <dd v-if="geo?.ip" class="text-primary font-mono text-xs">
                    <button class="hover:text-brand" @click="copy(geo.ip, 'IP copied')">{{ geo.ip }}</button>
                  </dd>
                  <dd v-else class="text-muted">—</dd>
                </div>
                <div v-if="geo?.country" class="flex justify-between"><dt class="text-muted">Location</dt><dd class="text-primary">📍 {{ geo.city ? geo.city + ', ' : '' }}{{ geo.country }}</dd></div>
              </dl>
            </div>

            <div v-if="can('PLAYERS_MANAGE')" class="card space-y-3">
              <h3>Nickname</h3>
              <div class="flex gap-2">
                <input v-model="nickname" class="input" placeholder="Leave blank to clear" />
                <Button variant="subtle" :loading="busy.nickname" @click="applyNickname">Save</Button>
              </div>
            </div>

            <!-- Staff notes -->
            <div class="card space-y-3 sm:col-span-2">
              <h3>Staff Notes <span class="text-xs text-faint font-normal">({{ notes.length }})</span></h3>
              <div v-if="can('PLAYERS_MANAGE')" class="flex gap-2">
                <input v-model="noteText" class="input" placeholder="Add a note about this player…" @keyup.enter="addNote" />
                <Button variant="subtle" @click="addNote">Add</Button>
              </div>
              <div v-if="notes.length" class="space-y-2">
                <div v-for="n in notes" :key="n.id" class="flex items-start gap-2 px-3 py-2 rounded-lg bg-elevated text-sm">
                  <PencilSquareIcon class="w-4 h-4 text-brand flex-shrink-0 mt-0.5" />
                  <div class="min-w-0 flex-1">
                    <p class="text-primary break-words">{{ n.note }}</p>
                    <p class="text-xs text-muted mt-0.5">{{ n.staff }} · {{ timeAgo(n.createdAt) }}</p>
                  </div>
                  <button v-if="can('PLAYERS_MANAGE')" class="text-red-500 hover:text-red-600 text-xs flex-shrink-0" @click="removeNote(n.id)">Delete</button>
                </div>
              </div>
              <EmptyState v-else :icon="PencilSquareIcon" title="No notes yet" hint="Pin context about this player for your staff team." />
            </div>
          </div>
        </TabsContent>

        <!-- Economy -->
        <TabsContent v-if="canEconomy" value="economy" class="outline-none">
          <div class="card space-y-3 max-w-md">
            <h3>Adjust Balance</h3>
            <p class="text-sm text-muted">Current: <span class="text-brand font-medium">{{ formatMoney(player.money, player.symbol) }}</span></p>
            <div class="flex gap-2">
              <input v-model.number="moneyAmount" type="number" min="0" class="input" placeholder="Amount" />
              <Select v-model="moneyAction" :options="moneyActions" class="w-32" aria-label="Action" />
              <Button :loading="busy.money" @click="applyMoney">Apply</Button>
            </div>
          </div>
        </TabsContent>

        <!-- Homes & Mail -->
        <TabsContent value="homesmail" class="outline-none">
          <div class="grid lg:grid-cols-2 gap-5">
            <div class="card space-y-3">
              <h3>Homes <span class="text-xs text-faint font-normal">({{ player.homes?.length || 0 }})</span></h3>
              <div v-if="player.homes?.length" class="space-y-2">
                <div v-for="h in player.homes" :key="h.name" class="flex items-center gap-2 px-3 py-2 rounded-lg bg-elevated text-sm">
                  <MapPinIcon class="w-4 h-4 text-brand flex-shrink-0" />
                  <span class="font-medium text-primary">{{ h.name }}</span>
                  <span class="text-muted text-xs truncate">{{ h.world }} · {{ h.x }}, {{ h.y }}, {{ h.z }}</span>
                  <button v-if="can('PLAYERS_MANAGE')" class="ml-auto text-red-500 hover:text-red-600 text-xs" @click="deleteHome(h.name)">Delete</button>
                </div>
              </div>
              <EmptyState v-else :icon="MapPinIcon" title="No homes set" />
            </div>

            <div class="card space-y-3">
              <div class="flex items-center justify-between">
                <h3>Mail <span class="text-xs text-faint font-normal">({{ mail.length }})</span></h3>
                <button v-if="can('PLAYERS_MANAGE') && mail.length" class="text-xs text-red-500" @click="clearMail">Clear all</button>
              </div>
              <div v-if="mail.length" class="space-y-1 max-h-48 overflow-auto">
                <p v-for="(m, i) in mail" :key="i" class="text-sm text-secondary px-2 py-1 rounded bg-elevated">{{ m }}</p>
              </div>
              <EmptyState v-else :icon="EnvelopeIcon" title="No mail" />
              <div v-if="can('PLAYERS_MANAGE')" class="flex gap-2">
                <input v-model="mailText" class="input" placeholder="Send mail…" @keyup.enter="sendMail" />
                <Button variant="subtle" @click="sendMail">Send</Button>
              </div>
            </div>
          </div>
        </TabsContent>

        <!-- Inventory -->
        <TabsContent v-if="can('INVENTORY_VIEW')" value="inventory" class="outline-none">
          <div class="card space-y-3">
            <div class="flex items-center justify-between">
              <h3 class="flex items-center gap-2">
                Inventory & Ender Chest
                <span v-if="inv?.readOnly" class="badge bg-amber-500/15 text-amber-500">Offline snapshot · read-only</span>
              </h3>
              <Button variant="ghost" class="text-xs" @click="loadInventory">Refresh</Button>
            </div>
            <InventoryGrid v-if="inv" :inv="inv" :editable="!inv.readOnly && can('INVENTORY_MANAGE')" @edit="openSlot" />
            <EmptyState v-else :icon="ArchiveBoxIcon" title="No inventory data"
                        hint="This player has no saved inventory yet." />
          </div>
        </TabsContent>

        <!-- Moderation -->
        <TabsContent v-if="can('BANS_MANAGE')" value="moderation" class="outline-none">
          <div class="card space-y-3">
            <h3>Moderation</h3>
            <div class="flex flex-wrap gap-2">
              <Button variant="subtle" @click="openWarn">Warn</Button>
              <Button v-if="!player.muted" variant="subtle" @click="muteModal = true">Mute</Button>
              <Button v-else variant="subtle" @click="doUnmute">Unmute</Button>
              <Button v-if="!player.banned" variant="danger" @click="banModal = true">Ban</Button>
              <Button v-else variant="subtle" @click="doUnban">Unban</Button>
              <Button variant="subtle" :disabled="!player.online" @click="msgModal = true">Message</Button>
              <Button variant="subtle" :disabled="!player.online" @click="kickModal = true">Kick</Button>
            </div>
            <p class="text-xs text-muted">Warnings auto-escalate to a mute or ban at configured thresholds.</p>
          </div>
        </TabsContent>

        <!-- Punishment history -->
        <TabsContent value="history" class="outline-none">
          <div class="card space-y-3">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <h3>Punishment History <span class="text-xs text-faint font-normal">({{ punishTotal }})</span></h3>
              <Button variant="ghost" class="text-xs" @click="loadPunishments">Refresh</Button>
            </div>
            <div v-if="punishments.length" class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="text-left text-muted border-b border-edge">
                    <th class="py-2 pr-3 font-medium">Type</th>
                    <th class="py-2 pr-3 font-medium">Reason</th>
                    <th class="py-2 pr-3 font-medium">Duration</th>
                    <th class="py-2 pr-3 font-medium">Staff</th>
                    <th class="py-2 font-medium">When</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in punishments" :key="p.id" class="border-b border-edge/50">
                    <td class="py-2 pr-3"><span class="badge" :class="punishBadge(p.type)">{{ p.type }}</span></td>
                    <td class="py-2 pr-3 text-secondary">{{ p.reason || '—' }}</td>
                    <td class="py-2 pr-3 text-secondary">{{ p.durationMs > 0 ? formatDuration(p.durationMs) : 'Permanent' }}</td>
                    <td class="py-2 pr-3 text-secondary">{{ p.staff }}</td>
                    <td class="py-2 text-muted whitespace-nowrap">{{ timeAgo(p.createdAt) }}</td>
                  </tr>
                </tbody>
              </table>
              <Pagination v-if="punishTotal > punishSize" :page="punishPage" :size="punishSize" :total="punishTotal" class="mt-3" @update:page="goPunishPage" />
            </div>
            <EmptyState v-else :icon="ShieldExclamationIcon" title="No punishments on record" hint="Bans, mutes and kicks issued from the dashboard appear here." />
          </div>
        </TabsContent>

        <!-- Timeline -->
        <TabsContent value="timeline" class="outline-none">
          <div class="space-y-5">
            <!-- Linked accounts (alts) -->
            <div v-if="can('BANS_VIEW')" class="card space-y-3">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <h3>Linked Accounts <span class="text-xs text-faint font-normal">({{ alts.length }})</span></h3>
                <Button variant="ghost" class="text-xs" @click="loadAlts">Refresh</Button>
              </div>
              <div v-if="alts.length" class="space-y-2">
                <router-link
                  v-for="a in alts" :key="a.uuid" :to="`/players/${a.uuid}`"
                  class="flex items-center gap-2 px-3 py-2 rounded-lg bg-elevated text-sm hover:bg-elevated/70 transition-colors"
                >
                  <Avatar :uuid="a.uuid" :name="a.name" :size="24" />
                  <span class="font-medium text-primary truncate">{{ a.name || a.uuid }}</span>
                  <span v-if="a.banned" class="badge bg-red-500/15 text-red-500">Banned</span>
                  <span class="text-muted text-xs font-mono truncate ml-1">{{ a.ips }}</span>
                  <span class="ml-auto text-muted text-xs whitespace-nowrap">{{ timeAgo(a.lastSeen) }}</span>
                </router-link>
              </div>
              <EmptyState v-else :icon="UsersIcon" title="No linked accounts" hint="Accounts that share an IP with this player appear here." />
            </div>

            <!-- Activity feed -->
            <div class="card space-y-3">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <h3>Activity Timeline</h3>
                <Button variant="ghost" class="text-xs" @click="loadTimeline">Refresh</Button>
              </div>
              <div v-if="timeline.length" class="space-y-0.5">
                <div v-for="(e, i) in timeline" :key="i" class="flex items-start gap-3 px-3 py-2 rounded-lg hover:bg-elevated">
                  <component :is="tlMeta(e.type).icon" class="w-4 h-4 mt-0.5 flex-shrink-0" :class="tlMeta(e.type).color" />
                  <div class="min-w-0 flex-1">
                    <p class="text-sm">
                      <span class="font-medium text-primary">{{ e.label }}</span>
                      <span v-if="e.detail" class="text-secondary"> — {{ e.detail }}</span>
                    </p>
                    <p class="text-xs text-muted" :title="formatDateTime(e.ts)">{{ timeAgo(e.ts) }}</p>
                  </div>
                </div>
              </div>
              <EmptyState v-else :icon="ClockIcon" title="No activity yet" hint="Logins, chat, punishments, notes and balance changes appear here." />
            </div>
          </div>
        </TabsContent>

        <!-- Powers -->
        <TabsContent v-if="can('PLAYERS_MANAGE')" value="powers" class="outline-none">
          <div class="card space-y-4">
            <div>
              <h3 class="mb-2">Powers <span v-if="!player.online" class="text-xs text-faint font-normal">(most need the player online)</span></h3>
              <div class="flex flex-wrap gap-2">
                <Button variant="subtle" :disabled="!player.online" @click="power('heal')">Heal</Button>
                <Button variant="subtle" :disabled="!player.online" @click="power('feed')">Feed</Button>
                <Button variant="subtle" :disabled="!player.online" @click="power('fly')">Fly</Button>
                <Button variant="subtle" :disabled="!player.online" @click="power('god')">God</Button>
                <Button variant="subtle" :disabled="!player.online" @click="power('vanish')">{{ player.vanished ? 'Unvanish' : 'Vanish' }}</Button>
                <Button variant="subtle" :disabled="!player.online" @click="power('clearinv')">Clear Inv</Button>
                <Button variant="subtle" :disabled="!player.online" @click="power('spawn')">To Spawn</Button>
                <Button variant="subtle" :disabled="!player.online" @click="giveModal = true">Give Item</Button>
                <Button v-if="auth.isAdmin" variant="subtle" @click="power(player.op ? 'deop' : 'op')">{{ player.op ? 'De-op' : 'Op' }}</Button>
              </div>
            </div>
            <div v-if="player.online" class="flex items-center gap-2">
              <span class="text-sm text-muted">Gamemode</span>
              <Select :model-value="player.gamemode" :options="gamemodes" class="w-40" aria-label="Gamemode"
                      @update:model-value="setGamemode" />
            </div>
          </div>
        </TabsContent>
      </TabsRoot>
    </div>

    <div v-else-if="loading" class="space-y-4">
      <Skeleton height="5rem" rounded="rounded-xl" />
      <Skeleton height="12rem" rounded="rounded-xl" />
    </div>
    <p v-else class="text-sm text-danger">Player not found.</p>

    <!-- Modals -->
    <Modal :open="msgModal" title="Message Player" @close="msgModal = false">
      <input v-model="msgText" class="input" placeholder="Message…" />
      <template #footer>
        <Button variant="ghost" @click="msgModal = false">Cancel</Button>
        <Button @click="sendMessage">Send</Button>
      </template>
    </Modal>

    <Modal :open="kickModal" title="Kick Player" @close="kickModal = false">
      <input v-model="kickReason" class="input" placeholder="Reason (optional)" />
      <template #footer>
        <Button variant="ghost" @click="kickModal = false">Cancel</Button>
        <Button variant="danger" @click="doKick">Kick</Button>
      </template>
    </Modal>

    <Modal :open="warnModal" title="Warn Player" @close="warnModal = false">
      <label class="label">Template <span class="text-faint">(optional)</span></label>
      <PunishmentTemplatePicker class="mb-3" @apply="applyTemplateToWarn" />
      <label class="label">Reason</label>
      <input v-model="warnReason" class="input" placeholder="Reason shown to the player" @keyup.enter="doWarn" />
      <template #footer>
        <Button variant="ghost" @click="warnModal = false">Cancel</Button>
        <Button @click="doWarn">Warn</Button>
      </template>
    </Modal>

    <Modal :open="muteModal" title="Mute Player" @close="muteModal = false">
      <label class="label">Template <span class="text-faint">(optional)</span></label>
      <PunishmentTemplatePicker class="mb-3" @apply="applyTemplate('mute', $event)" />
      <label class="label">Duration (minutes, 0 = permanent)</label>
      <input v-model.number="muteMinutes" type="number" min="0" class="input" />
      <template #footer>
        <Button variant="ghost" @click="muteModal = false">Cancel</Button>
        <Button @click="doMute">Mute</Button>
      </template>
    </Modal>

    <Modal :open="banModal" title="Ban Player" @close="banModal = false">
      <label class="label">Template <span class="text-faint">(optional)</span></label>
      <PunishmentTemplatePicker class="mb-3" @apply="applyTemplate('ban', $event)" />
      <label class="label">Reason</label>
      <input v-model="banReason" class="input mb-3" placeholder="Reason" />
      <label class="label">Duration (minutes, 0 = permanent)</label>
      <input v-model.number="banMinutes" type="number" min="0" class="input" />
      <template #footer>
        <Button variant="ghost" @click="banModal = false">Cancel</Button>
        <Button variant="danger" @click="doBan">Ban</Button>
      </template>
    </Modal>

    <Modal :open="giveModal" title="Give Item" @close="giveModal = false">
      <label class="label">Item</label>
      <MaterialInput v-model="giveForm.material" class="mb-3" />
      <label class="label">Amount</label>
      <input v-model.number="giveForm.amount" type="number" min="1" class="input" />
      <template #footer>
        <Button variant="ghost" @click="giveModal = false">Cancel</Button>
        <Button @click="doGive">Give</Button>
      </template>
    </Modal>

    <Modal :open="slotModal" :title="`Edit slot ${slotForm.id}`" @close="slotModal = false">
      <label class="label">Item — blank to clear</label>
      <MaterialInput v-model="slotForm.material" placeholder="golden_apple" class="mb-3" />
      <label class="label">Amount</label>
      <input v-model.number="slotForm.amount" type="number" min="1" class="input" />
      <template #footer>
        <Button variant="ghost" @click="slotModal = false">Cancel</Button>
        <Button variant="danger" class="mr-auto" @click="clearSlotItem">Clear</Button>
        <Button @click="saveSlot">Save</Button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { TabsRoot, TabsList, TabsTrigger, TabsContent } from 'reka-ui'
import { ClipboardIcon, MapPinIcon, EnvelopeIcon, ArchiveBoxIcon, PencilSquareIcon, ShieldExclamationIcon,
  UsersIcon, ClockIcon, ChatBubbleLeftRightIcon, ArrowRightOnRectangleIcon, BanknotesIcon } from '@heroicons/vue/24/outline'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { formatMoney, timeAgo, formatDateTime } from '../utils'
import { useClipboard } from '../composables/useClipboard'
import Modal from '../components/Modal.vue'
import Avatar from '../components/Avatar.vue'
import MaterialInput from '../components/MaterialInput.vue'
import PunishmentTemplatePicker from '../components/PunishmentTemplatePicker.vue'
import InventoryGrid from '../components/InventoryGrid.vue'
import Pagination from '../components/Pagination.vue'
import Button from '../components/ui/Button.vue'
import Select from '../components/ui/Select.vue'
import Skeleton from '../components/ui/Skeleton.vue'
import EmptyState from '../components/ui/EmptyState.vue'

const route = useRoute()
const auth = useAuthStore()
const toast = useToastStore()
const copy = useClipboard()
const uuid = route.params.uuid

const player = ref(null)
const loading = ref(true)
const mail = ref([])
const geo = ref(null)
const inv = ref(null)
const tab = ref('overview')
const busy = ref({ nickname: false, money: false })

const giveModal = ref(false)
const giveForm = ref({ material: '', amount: 1 })
const slotModal = ref(false)
const slotForm = ref({ id: '', container: '', index: 0, material: '', amount: 1 })

const moneyAmount = ref(null)
const moneyAction = ref('give')
const nickname = ref('')

const msgModal = ref(false), msgText = ref('')
const kickModal = ref(false), kickReason = ref('')
const muteModal = ref(false), muteMinutes = ref(0)
const banModal = ref(false), banReason = ref(''), banMinutes = ref(0)
const warnModal = ref(false), warnReason = ref('')
const mailText = ref('')

const notes = ref([])
const noteText = ref('')
const punishments = ref([])
const punishPage = ref(0)
const punishSize = 20
const punishTotal = ref(0)

const moneyActions = [{ value: 'give', label: 'Give' }, { value: 'take', label: 'Take' }, { value: 'set', label: 'Set' }]
const gamemodes = ['SURVIVAL', 'CREATIVE', 'ADVENTURE', 'SPECTATOR']

/** Human-readable fixed length (not remaining time) for a stored punishment duration. */
function formatLength(ms) {
  const mins = Math.floor(ms / 60000)
  if (mins < 60) return `${mins}m`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ${mins % 60}m`
  const days = Math.floor(hours / 24)
  return `${days}d ${hours % 24}h`
}
const formatDuration = formatLength

function punishBadge(type) {
  switch (type) {
    case 'BAN': return 'bg-red-500/15 text-red-500'
    case 'KICK':
    case 'MUTE': return 'bg-amber-500/15 text-amber-500'
    case 'UNBAN':
    case 'UNMUTE': return 'bg-green-500/15 text-green-500'
    default: return 'bg-brand-subtle text-brand'
  }
}

const can = p => auth.hasPermission(p)
const canEconomy = computed(() => can('ECONOMY_MANAGE') || can('PLAYERS_MANAGE'))

const visibleTabs = computed(() => [
  { value: 'overview', label: 'Overview', show: true },
  { value: 'economy', label: 'Economy', show: canEconomy.value },
  { value: 'homesmail', label: 'Homes & Mail', show: true },
  { value: 'inventory', label: 'Inventory', show: can('INVENTORY_VIEW') },
  { value: 'moderation', label: 'Moderation', show: can('BANS_MANAGE') },
  { value: 'history', label: 'History', show: true },
  { value: 'timeline', label: 'Timeline', show: true },
  { value: 'powers', label: 'Powers', show: can('PLAYERS_MANAGE') },
].filter(t => t.show))

const timeline = ref([])
const alts = ref([])

function tlMeta(type) {
  switch (type) {
    case 'punishment': return { icon: ShieldExclamationIcon, color: 'text-red-500' }
    case 'note': return { icon: PencilSquareIcon, color: 'text-brand' }
    case 'chat': return { icon: ChatBubbleLeftRightIcon, color: 'text-emerald-500' }
    case 'login': return { icon: ArrowRightOnRectangleIcon, color: 'text-muted' }
    case 'economy': return { icon: BanknotesIcon, color: 'text-amber-500' }
    default: return { icon: ClockIcon, color: 'text-muted' }
  }
}

async function loadTimeline() {
  try { timeline.value = (await api.playerTimeline(uuid)).data.events } catch { timeline.value = [] }
}
async function loadAlts() {
  if (!can('BANS_VIEW')) return
  try { alts.value = (await api.playerAlts(uuid)).data.alts } catch { alts.value = [] }
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.getPlayer(uuid)
    player.value = data
    nickname.value = data.nickname ?? ''
    mail.value = (await api.getMail(uuid)).data.mail
    loadGeo()
    loadNotes()
    loadPunishments()
    loadTimeline()
    loadAlts()
    if (can('INVENTORY_VIEW')) loadInventory()
  } catch {
    player.value = null
  } finally {
    loading.value = false
  }
}

async function loadGeo() {
  try { geo.value = (await api.getGeo(uuid)).data } catch { geo.value = null }
}
async function loadInventory() {
  try { inv.value = (await api.getInventory(uuid)).data } catch { inv.value = null }
}
async function loadNotes() {
  try { notes.value = (await api.getNotes(uuid)).data.notes } catch { notes.value = [] }
}
async function loadPunishments() {
  try {
    const { data } = await api.getPunishments(uuid, punishPage.value, punishSize)
    punishments.value = data.entries
    punishTotal.value = data.total
  } catch { punishments.value = [] }
}
function goPunishPage(p) { punishPage.value = p; loadPunishments() }

async function addNote() {
  if (!noteText.value.trim()) return
  try {
    await api.addNote(uuid, noteText.value.trim())
    noteText.value = ''
    await loadNotes()
  } catch (err) { toast.error(err.response?.data?.error ?? 'Failed to add note') }
}
async function removeNote(id) {
  try { await api.deleteNote(uuid, id); await loadNotes() }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed to delete note') }
}

async function guarded(fn, msg, key) {
  if (key) busy.value[key] = true
  try {
    await fn()
    if (msg) toast.success(msg)
    await load()
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Action failed')
  } finally {
    if (key) busy.value[key] = false
  }
}

async function power(action) {
  try {
    await api.playerAction(uuid, action)
    toast.success(`Done: ${action}`)
    if (['op', 'deop', 'god', 'fly', 'clearinv', 'vanish'].includes(action)) await load()
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Action failed')
  }
}

function setGamemode(v) {
  if (v) guarded(() => api.setGamemode(uuid, v), 'Gamemode changed')
}

function doGive() {
  if (!giveForm.value.material.trim()) return toast.error('Enter an item')
  guarded(() => api.giveItem(uuid, giveForm.value.material.trim(), giveForm.value.amount || 1), 'Item given')
  giveModal.value = false
}

function openSlot({ container, index }) {
  slotForm.value = { id: `${container}-${index}`, container, index, material: '', amount: 1 }
  slotModal.value = true
}
async function saveSlot() {
  if (!slotForm.value.material.trim()) return clearSlotItem()
  try {
    await api.setSlot(uuid, slotForm.value.id, slotForm.value.material.trim(), slotForm.value.amount || 1)
    toast.success('Slot updated'); slotModal.value = false; loadInventory()
  } catch (err) { toast.error(err.response?.data?.error ?? 'Failed') }
}
async function clearSlotItem() {
  try {
    await api.clearSlot(uuid, slotForm.value.id)
    toast.success('Slot cleared'); slotModal.value = false; loadInventory()
  } catch (err) { toast.error(err.response?.data?.error ?? 'Failed') }
}

const applyMoney = () => {
  if (moneyAmount.value == null || moneyAmount.value < 0) return toast.error('Enter a valid amount')
  guarded(() => api.setMoney(uuid, moneyAction.value, moneyAmount.value), 'Balance updated', 'money')
  moneyAmount.value = null
}
const applyNickname = () => guarded(() => api.setNickname(uuid, nickname.value), 'Nickname updated', 'nickname')
const deleteHome = name => guarded(() => api.deleteHome(uuid, name), 'Home deleted')
const clearMail = () => guarded(async () => { await api.clearMail(uuid); mail.value = [] }, 'Mail cleared')
const doUnmute = () => guarded(() => api.unmute(uuid), 'Unmuted')
const doUnban = () => guarded(() => api.unban(uuid), 'Unbanned')

const sendMail = () => {
  if (!mailText.value.trim()) return
  guarded(() => api.sendMail(uuid, mailText.value), 'Mail sent')
  mailText.value = ''
}
const sendMessage = () => { guarded(() => api.message(uuid, msgText.value), 'Message sent'); msgModal.value = false; msgText.value = '' }
const doKick = () => { guarded(() => api.kick(uuid, kickReason.value), 'Player kicked'); kickModal.value = false; kickReason.value = '' }
const doMute = () => { guarded(() => api.mute(uuid, muteMinutes.value), 'Player muted'); muteModal.value = false }
const doBan = () => { guarded(() => api.ban(uuid, banReason.value, banMinutes.value), 'Player banned'); banModal.value = false }

function openWarn() {
  warnReason.value = ''
  warnModal.value = true
}
function applyTemplateToWarn(t) {
  warnReason.value = t.reason || warnReason.value
}
// Prefill a mute/ban modal from a punishment template (duration is stored in ms).
function applyTemplate(kind, t) {
  const minutes = t.durationMs ? Math.round(t.durationMs / 60000) : 0
  if (kind === 'mute') {
    muteMinutes.value = minutes
  } else {
    banReason.value = t.reason || banReason.value
    banMinutes.value = minutes
  }
}
async function doWarn() {
  warnModal.value = false
  try {
    const { data } = await api.warnPlayer(uuid, warnReason.value || 'Warning')
    toast.success(data.escalated ? `Warned → auto ${data.action} (${data.warnings} warnings)` : `Warned (${data.warnings} total)`)
    await load()
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to warn')
  }
}

onMounted(load)
</script>
