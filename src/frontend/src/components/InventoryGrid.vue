<template>
  <div class="space-y-5">
    <div class="flex flex-wrap gap-5">
      <!-- Armor + off-hand -->
      <div class="flex gap-3">
        <div class="flex flex-col gap-1">
          <p class="label">Armor</p>
          <InventorySlot
            v-for="s in armorOrdered" :key="'a' + s.index"
            class="w-11 h-11" :slot-data="s" container="armor" :editable="editable"
            @edit="$emit('edit', $event)" @hover="onHover" @move="onMove" @leave="onLeave"
          />
        </div>
        <div class="flex flex-col gap-1 justify-end">
          <p class="label">Off-hand</p>
          <InventorySlot
            v-for="s in inv.offhand" :key="'o' + s.index"
            class="w-11 h-11" :slot-data="s" container="offhand" :editable="editable"
            @edit="$emit('edit', $event)" @hover="onHover" @move="onMove" @leave="onLeave"
          />
        </div>
      </div>

      <!-- Main inventory + hotbar -->
      <div class="flex-1 min-w-0 overflow-x-auto">
        <p class="label">Inventory</p>
        <div class="grid grid-cols-9 gap-1 min-w-[18rem]">
          <InventorySlot
            v-for="s in mainRows" :key="'m' + s.index"
            :slot-data="s" container="inv" :editable="editable"
            @edit="$emit('edit', $event)" @hover="onHover" @move="onMove" @leave="onLeave"
          />
        </div>
        <div class="grid grid-cols-9 gap-1 mt-2 pt-2 border-t border-edge min-w-[18rem]">
          <InventorySlot
            v-for="s in hotbar" :key="'h' + s.index"
            :slot-data="s" container="inv" :editable="editable"
            @edit="$emit('edit', $event)" @hover="onHover" @move="onMove" @leave="onLeave"
          />
        </div>
      </div>
    </div>

    <!-- Ender chest -->
    <div class="overflow-x-auto">
      <p class="label">Ender Chest</p>
      <div class="grid grid-cols-9 gap-1 min-w-[18rem]">
        <InventorySlot
          v-for="s in inv.ender" :key="'e' + s.index"
          :slot-data="s" container="ender" :editable="editable"
          @edit="$emit('edit', $event)" @hover="onHover" @move="onMove" @leave="onLeave"
        />
      </div>
    </div>

    <!-- Shared floating item tooltip -->
    <teleport to="body">
      <div
        v-if="hover.item"
        ref="tipEl"
        class="fixed z-[100]"
        :style="{ left: pos.left + 'px', top: pos.top + 'px' }"
      >
        <ItemTooltip :item="hover.item" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import { computed, reactive, ref, nextTick } from 'vue'
import InventorySlot from './InventorySlot.vue'
import ItemTooltip from './ItemTooltip.vue'

const props = defineProps({
  inv: { type: Object, required: true },
  editable: { type: Boolean, default: false },
})
defineEmits(['edit'])

// Vanilla layout: slots 0–8 are the hotbar, 9–35 the main three rows.
const mainRows = computed(() => props.inv.inv.filter(s => s.index >= 9 && s.index <= 35))
const hotbar = computed(() => props.inv.inv.filter(s => s.index <= 8))
// getArmorContents() is [boots, leggings, chestplate, helmet]; show helmet→boots.
const armorOrdered = computed(() => [...props.inv.armor].reverse())

// ── Floating tooltip ─────────────────────────────────────────────────────────
const hover = reactive({ item: null })
const tipEl = ref(null)
const pos = reactive({ left: 0, top: 0 })
let lastMouse = { x: 0, y: 0 }

function place() {
  const pad = 14
  const w = tipEl.value?.offsetWidth || 240
  const h = tipEl.value?.offsetHeight || 160
  let left = lastMouse.x + pad
  let top = lastMouse.y + pad
  if (left + w > window.innerWidth - 8) left = lastMouse.x - w - pad
  if (top + h > window.innerHeight - 8) top = Math.max(8, window.innerHeight - h - 8)
  if (left < 8) left = 8
  pos.left = left
  pos.top = top
}

function onHover({ item, x, y }) {
  hover.item = item
  lastMouse = { x, y }
  nextTick(place)
}
function onMove({ x, y }) {
  lastMouse = { x, y }
  if (hover.item) place()
}
function onLeave() { hover.item = null }
</script>
