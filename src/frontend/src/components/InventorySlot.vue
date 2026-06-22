<template>
  <button
    type="button"
    class="relative aspect-square rounded border bg-elevated flex items-center justify-center p-0.5 overflow-hidden transition-colors"
    :class="[
      editable ? 'hover:border-brand cursor-pointer' : 'cursor-default',
      enchanted ? 'border-fuchsia-500/60 shadow-[0_0_6px_-1px_rgba(217,70,239,0.5)]' : 'border-edge',
    ]"
    :title="title"
    :disabled="!editable"
    @click="editable && $emit('edit', { container, index: slotData.index })"
  >
    <ItemIcon v-if="slotData.material" :material="slotData.material" :size="28" />

    <!-- stack count -->
    <span v-if="slotData.amount > 1" class="absolute bottom-0 right-0.5 text-[0.62rem] font-bold text-white" style="text-shadow:0 1px 2px #000">
      {{ slotData.amount }}
    </span>

    <!-- custom-name marker -->
    <span v-if="slotData.name" class="absolute top-0.5 left-0.5 w-1.5 h-1.5 rounded-full bg-amber-400" title="Custom name"></span>

    <!-- durability bar -->
    <span v-if="hasDurability" class="absolute bottom-0 left-0 right-0 h-1 bg-black/40">
      <span class="block h-full" :class="durabilityColor" :style="{ width: durabilityPct + '%' }"></span>
    </span>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import ItemIcon from './ItemIcon.vue'

const props = defineProps({
  slotData: { type: Object, required: true },
  container: { type: String, required: true },
  editable: { type: Boolean, default: false },
})
defineEmits(['edit'])

const s = computed(() => props.slotData)
const enchanted = computed(() => (s.value.enchantments?.length || 0) > 0)
const hasDurability = computed(() => s.value.maxDurability && s.value.durability != null && s.value.damage > 0)
const durabilityPct = computed(() => Math.round((s.value.durability / s.value.maxDurability) * 100))
const durabilityColor = computed(() => {
  const p = durabilityPct.value
  return p > 50 ? 'bg-green-500' : p > 25 ? 'bg-amber-500' : 'bg-red-500'
})

const title = computed(() => {
  const d = s.value
  if (!d.material) return `Empty slot ${d.index}`
  const lines = []
  lines.push((d.name ? stripCodes(d.name) + ' ' : '') + `(${pretty(d.material)})` + (d.amount > 1 ? ` ×${d.amount}` : ''))
  if (hasDurability.value) lines.push(`Durability: ${d.durability} / ${d.maxDurability}`)
  if (d.unbreakable) lines.push('Unbreakable')
  if (d.enchantments?.length) {
    lines.push('Enchantments:')
    d.enchantments.forEach(e => lines.push(`  • ${e.name} ${e.level}`))
  }
  if (d.skullOwner) lines.push(`Head of: ${d.skullOwner}`)
  if (d.customModelData) lines.push(`Custom model data: ${d.customModelData}`)
  if (d.lore?.length) { lines.push('Lore:'); d.lore.forEach(l => lines.push(`  ${stripCodes(l)}`)) }
  if (d.flags?.length) lines.push('Flags: ' + d.flags.join(', '))
  return lines.join('\n')
})

function pretty(mat) { return String(mat).replace(/_/g, ' ').toLowerCase() }
function stripCodes(str) { return String(str).replace(/[§&][0-9a-fk-or]/gi, '') }
</script>
