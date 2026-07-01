<template>
  <Modal :open="open" :title="modalTitle" size="xl" @close="$emit('close')">
    <div class="grid gap-5 md:grid-cols-[minmax(0,15rem)_1fr]">
      <!-- Preview pane -->
      <div class="space-y-3">
        <p class="label">Preview</p>
        <div class="flex items-center justify-center rounded-lg border border-edge bg-elevated p-4">
          <span class="relative w-16 h-16 flex items-center justify-center rounded border border-edge bg-surface"
                :class="hasEnchants ? 'border-fuchsia-500/60 shadow-[0_0_8px_-1px_rgba(217,70,239,0.5)]' : ''">
            <ItemIcon v-if="form.material" :material="form.material" :size="48" />
            <span v-if="form.amount > 1" class="absolute bottom-0 right-0.5 text-xs font-bold text-white" style="text-shadow:0 1px 2px #000">{{ form.amount }}</span>
          </span>
        </div>
        <div v-if="form.material" class="flex justify-center">
          <ItemTooltip :item="previewItem" />
        </div>

        <div v-if="source.nbt">
          <p class="label">Current NBT</p>
          <pre class="text-[0.62rem] leading-snug font-mono bg-elevated border border-edge rounded p-2 max-h-48 overflow-auto whitespace-pre-wrap break-all text-muted select-text">{{ source.nbt }}</pre>
        </div>
      </div>

      <!-- Form pane -->
      <div class="space-y-4 min-w-0">
        <div class="grid grid-cols-[1fr_auto] gap-3 items-end">
          <div>
            <label class="label">Item — clear field to empty the slot</label>
            <MaterialInput v-model="form.material" placeholder="diamond_sword" />
          </div>
          <div class="w-24">
            <label class="label">Amount</label>
            <input v-model.number="form.amount" type="number" min="1" :max="maxStack" class="input" />
          </div>
        </div>

        <div>
          <label class="label">Display name <span class="text-muted">— supports &amp; colour codes</span></label>
          <input v-model="form.name" class="input" placeholder="&6Legendary Blade" />
          <p v-if="form.name" class="mt-1 text-sm"><McText :text="form.name" default-color="#ffffff" /></p>
        </div>

        <div>
          <label class="label">Lore <span class="text-muted">— one line per row, &amp; colour codes</span></label>
          <textarea v-model="form.lore" rows="3" class="input font-mono text-sm" placeholder="&7A blade of legend&#10;&8Forged in fire"></textarea>
        </div>

        <!-- Enchantments -->
        <div>
          <div class="flex items-center justify-between">
            <label class="label">Enchantments</label>
            <Button variant="ghost" class="text-xs" @click="addEnchant">+ Add</Button>
          </div>
          <div v-if="form.enchantments.length" class="space-y-2">
            <div v-for="(en, i) in form.enchantments" :key="i" class="flex gap-2 items-center">
              <Select v-model="en.id" :options="enchantOptions" placeholder="Enchantment" trigger-class="flex-1" class="flex-1" />
              <input v-model.number="en.level" type="number" min="1" max="255" class="input w-20" aria-label="Level" />
              <button type="button" class="text-muted hover:text-danger px-1" aria-label="Remove" @click="form.enchantments.splice(i, 1)">✕</button>
            </div>
          </div>
          <p v-else class="text-xs text-muted">None.</p>
        </div>

        <!-- Durability -->
        <div v-if="maxDurability">
          <label class="label">Durability — {{ maxDurability - form.damage }} / {{ maxDurability }}</label>
          <input v-model.number="form.damage" type="range" min="0" :max="maxDurability" class="w-full accent-brand" />
        </div>

        <!-- Toggles + numeric meta -->
        <div class="grid grid-cols-2 gap-3 items-end">
          <label class="flex items-center gap-2 text-sm cursor-pointer select-none">
            <input v-model="form.unbreakable" type="checkbox" class="accent-brand" /> Unbreakable
          </label>
          <div>
            <label class="label">Custom model data</label>
            <input v-model.number="form.customModelData" type="number" min="0" class="input" placeholder="—" />
          </div>
        </div>

        <!-- Skull owner -->
        <div v-if="isHead">
          <label class="label">Skull owner <span class="text-muted">— player name</span></label>
          <input v-model="form.skullOwner" class="input" placeholder="Notch" />
        </div>

        <!-- Item flags -->
        <div>
          <label class="label">Hide flags</label>
          <div class="grid grid-cols-2 gap-1.5">
            <label v-for="f in FLAGS" :key="f" class="flex items-center gap-2 text-sm cursor-pointer select-none">
              <input type="checkbox" class="accent-brand" :value="f" v-model="form.flags" /> {{ prettyFlag(f) }}
            </label>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <Button variant="ghost" @click="$emit('close')">Cancel</Button>
      <Button v-if="showClear" variant="danger" class="mr-auto" @click="$emit('clear')">Clear slot</Button>
      <Button @click="onSave">{{ saveLabel }}</Button>
    </template>
  </Modal>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import Modal from './Modal.vue'
import MaterialInput from './MaterialInput.vue'
import ItemIcon from './ItemIcon.vue'
import ItemTooltip from './ItemTooltip.vue'
import McText from './McText.vue'
import Button from './ui/Button.vue'
import Select from './ui/Select.vue'
import { useEnchantments } from '../stores/enchantments'

const FLAGS = [
  'HIDE_ENCHANTS', 'HIDE_ATTRIBUTES', 'HIDE_UNBREAKABLE', 'HIDE_DESTROYS',
  'HIDE_PLACED_ON', 'HIDE_DYE', 'HIDE_ARMOR_TRIM', 'HIDE_ADDITIONAL_TOOLTIP',
]

const props = defineProps({
  open: { type: Boolean, default: false },
  slotId: { type: String, default: '' },
  source: { type: Object, default: () => ({}) },
  title: { type: String, default: '' },
  saveLabel: { type: String, default: 'Save' },
  showClear: { type: Boolean, default: true },
  amountMax: { type: Number, default: 0 }, // overrides the per-material stack cap when > 0 (give mode)
})
const emit = defineEmits(['close', 'save', 'clear'])

const modalTitle = computed(() => props.title || `Edit ${props.slotId}`)

const { list: enchantList } = useEnchantments()
const enchantOptions = computed(() => enchantList.value.map(e => ({ value: e.id, label: e.name })))
const enchName = id => enchantList.value.find(e => e.id === id)?.name || id

const form = ref(blank())

function blank() {
  return { material: '', amount: 1, name: '', lore: '', enchantments: [], damage: 0,
    unbreakable: false, customModelData: null, flags: [], skullOwner: '' }
}

// Prefill from the clicked slot every time the modal opens, so existing meta is preserved.
watch(() => props.open, open => {
  if (!open) return
  const s = props.source || {}
  form.value = {
    material: s.material ? s.material.toLowerCase() : '',
    amount: s.amount || 1,
    name: toAmp(s.name || ''),
    lore: (s.lore || []).map(toAmp).join('\n'),
    enchantments: (s.enchantments || []).map(e => ({ id: e.id || e.name, level: e.level || 1 })),
    damage: s.damage || 0,
    unbreakable: !!s.unbreakable,
    customModelData: s.customModelData ?? null,
    flags: [...(s.flags || [])],
    skullOwner: s.skullOwner || '',
  }
})

// Durability/max-stack are only known for the item that was already in the slot.
const sameMaterial = computed(() => props.source?.material && form.value.material === props.source.material.toLowerCase())
const maxDurability = computed(() => (sameMaterial.value ? props.source.maxDurability : 0) || 0)
const maxStack = computed(() => props.amountMax > 0 ? props.amountMax : ((sameMaterial.value ? props.source.maxStack : 64) || 64))
const isHead = computed(() => form.value.material === 'player_head')
const hasEnchants = computed(() => form.value.enchantments.some(e => e.id))

const loreLines = computed(() => form.value.lore.split('\n').filter((l, i, a) => l !== '' || i < a.length - 1))

const previewItem = computed(() => ({
  material: form.value.material,
  amount: form.value.amount,
  name: form.value.name || undefined,
  lore: loreLines.value.length ? loreLines.value : undefined,
  enchantments: form.value.enchantments.filter(e => e.id).map(e => ({ name: enchName(e.id), level: e.level })),
  maxDurability: maxDurability.value || undefined,
  damage: form.value.damage,
  durability: maxDurability.value ? maxDurability.value - form.value.damage : undefined,
  unbreakable: form.value.unbreakable,
  customModelData: form.value.customModelData ?? undefined,
  flags: form.value.flags,
  skullOwner: isHead.value ? (form.value.skullOwner || undefined) : undefined,
}))

function addEnchant() {
  const first = enchantList.value[0]?.id || ''
  form.value.enchantments.push({ id: first, level: 1 })
}

function onSave() {
  const f = form.value
  if (!f.material.trim()) { if (props.showClear) emit('clear'); return }
  emit('save', {
    material: f.material.trim(),
    amount: f.amount || 1,
    name: f.name,
    lore: loreLines.value,
    enchantments: f.enchantments.filter(e => e.id).map(e => ({ id: e.id, level: e.level || 1 })),
    damage: maxDurability.value ? f.damage : null,
    unbreakable: f.unbreakable,
    customModelData: (f.customModelData === null || f.customModelData === '') ? -1 : f.customModelData,
    flags: f.flags,
    skullOwner: isHead.value ? f.skullOwner : null,
  })
}

// Convert stored § colour codes back to & so they're editable in plain inputs.
function toAmp(str) { return String(str || '').replace(/§/g, '&') }
function prettyFlag(f) {
  return String(f).replace(/^HIDE_/, 'Hide ').replace(/_/g, ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase())
}
</script>
