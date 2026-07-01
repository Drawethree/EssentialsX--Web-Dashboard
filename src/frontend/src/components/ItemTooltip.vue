<template>
  <div class="mc-tooltip">
    <!-- Name / material -->
    <div class="mc-name">
      <McText v-if="item.name" :text="item.name" default-color="#ffffff" />
      <span v-else>{{ prettyMaterial }}</span>
      <span v-if="item.amount > 1" class="mc-dim"> ×{{ item.amount }}</span>
    </div>
    <div class="mc-id">minecraft:{{ materialId }}</div>

    <!-- Enchantments -->
    <template v-if="item.enchantments?.length">
      <div v-for="(e, i) in item.enchantments" :key="'en' + i" class="mc-line mc-ench">
        {{ e.name }}{{ levelSuffix(e.level) }}
      </div>
    </template>

    <!-- Potion effects -->
    <template v-if="item.potion">
      <div v-if="item.potion.base" class="mc-line mc-effect">{{ item.potion.base }}</div>
      <div v-for="(ef, i) in item.potion.effects || []" :key="'pe' + i" class="mc-line mc-effect">
        {{ ef.name }} <span v-if="ef.amplifier > 0">{{ roman(ef.amplifier + 1) }}</span>
        <span class="mc-dim"> ({{ formatTicks(ef.duration) }})</span>
      </div>
    </template>

    <!-- Armor trim -->
    <div v-if="item.trim" class="mc-line mc-dim">Trim: {{ item.trim.pattern }} · {{ item.trim.material }}</div>

    <!-- Food -->
    <template v-if="item.food">
      <div class="mc-line mc-blue">When eaten: +{{ item.food.nutrition }} hunger</div>
      <div class="mc-line mc-blue">Saturation: {{ item.food.saturation }}</div>
      <div v-if="item.food.canAlwaysEat" class="mc-line mc-dim">Always edible</div>
    </template>

    <!-- Attribute modifiers -->
    <template v-if="item.attributes?.length">
      <div class="mc-spacer"></div>
      <div v-for="(a, i) in item.attributes" :key="'at' + i" class="mc-line mc-blue">
        {{ signed(a.amount) }} {{ a.attribute }}<span v-if="a.slot" class="mc-dim"> ({{ a.slot }})</span>
      </div>
    </template>

    <!-- Lore -->
    <template v-if="item.lore?.length">
      <div class="mc-spacer"></div>
      <div v-for="(l, i) in item.lore" :key="'lo' + i" class="mc-line">
        <McText v-if="l !== '' && l != null" :text="l" default-color="#aa55aa" :italic-by-default="true" />
        <span v-else>&nbsp;</span>
      </div>
    </template>

    <!-- Status flags -->
    <div class="mc-spacer"></div>
    <div v-if="hasDurability" class="mc-line mc-dim">Durability: {{ item.durability }} / {{ item.maxDurability }}</div>
    <div v-if="item.unbreakable" class="mc-line mc-blue">Unbreakable</div>
    <div v-if="item.skullOwner" class="mc-line mc-dim">Head of {{ item.skullOwner }}</div>
    <div v-if="item.customModelData != null" class="mc-line mc-dim">Model data: {{ item.customModelData }}</div>
    <div v-if="item.flags?.length" class="mc-line mc-dim2">{{ item.flags.map(prettyFlag).join(', ') }}</div>

    <!-- Raw NBT / data components -->
    <template v-if="item.nbt">
      <div class="mc-spacer"></div>
      <div class="mc-nbt-label">NBT</div>
      <div class="mc-nbt">{{ item.nbt }}</div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import McText from './McText.vue'

const props = defineProps({
  item: { type: Object, required: true },
})

const materialId = computed(() => String(props.item.material || '').toLowerCase())
const prettyMaterial = computed(() => materialId.value.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()))
const hasDurability = computed(() => props.item.maxDurability && props.item.durability != null && props.item.damage > 0)

// Vanilla shows a level numeral for every enchant except level-1 single-level enchants;
// keeping it simple, we always show the numeral (with a leading space) so it's never glued on.
function levelSuffix(level) { return ' ' + roman(level) }
function roman(n) {
  if (n == null) return ''
  if (n < 1 || n > 3999) return String(n)
  const map = [[1000,'M'],[900,'CM'],[500,'D'],[400,'CD'],[100,'C'],[90,'XC'],[50,'L'],[40,'XL'],[10,'X'],[9,'IX'],[5,'V'],[4,'IV'],[1,'I']]
  let out = ''
  for (const [v, s] of map) { while (n >= v) { out += s; n -= v } }
  return out
}
function signed(n) { return (n > 0 ? '+' : '') + (Math.round(n * 100) / 100) }
function formatTicks(ticks) {
  const secs = Math.round((ticks || 0) / 20)
  const m = Math.floor(secs / 60), s = secs % 60
  return `${m}:${String(s).padStart(2, '0')}`
}
function prettyFlag(f) {
  return String(f).replace(/^HIDE_/, 'Hide ').replace(/_/g, ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase())
}
</script>

<style scoped>
.mc-tooltip {
  font-family: 'Minecraft', ui-monospace, 'Cascadia Code', monospace;
  background: rgba(16, 0, 16, 0.94);
  border: 2px solid;
  border-image: linear-gradient(180deg, rgba(80, 0, 255, 0.55), rgba(40, 0, 130, 0.55)) 1;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.55);
  border-radius: 2px;
  padding: 6px 9px;
  min-width: 8rem;
  max-width: 22rem;
  line-height: 1.35;
  font-size: 0.78rem;
  color: #f4f4f4;
  pointer-events: none;
}
.mc-name { font-weight: 600; color: #fff; }
.mc-id { font-size: 0.66rem; color: #4a8aff; margin-bottom: 2px; opacity: 0.9; }
.mc-line { font-size: 0.74rem; }
.mc-ench { color: #aaaaaa; }
.mc-effect { color: #5577ff; }
.mc-blue { color: #5577ff; }
.mc-dim { color: #aaaaaa; }
.mc-dim2 { color: #555555; }
.mc-spacer { height: 5px; }
.mc-nbt-label { font-size: 0.62rem; color: #4a8aff; letter-spacing: 0.04em; }
.mc-nbt {
  font-size: 0.62rem;
  color: #8a8a8a;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 7rem;       /* tooltips can't scroll — keep long NBT from dominating */
  overflow: hidden;
  -webkit-mask-image: linear-gradient(180deg, #000 75%, transparent);
          mask-image: linear-gradient(180deg, #000 75%, transparent);
}
</style>
