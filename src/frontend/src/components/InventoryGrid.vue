<template>
  <div class="space-y-5">
    <div class="flex flex-wrap gap-5">
      <!-- Armor + off-hand -->
      <div class="flex gap-3">
        <div class="flex flex-col gap-1">
          <p class="label">Armor</p>
          <InventorySlot
            v-for="s in armorOrdered" :key="'a' + s.index"
            class="w-11 h-11" :slot-data="s" container="armor" :editable="editable" @edit="$emit('edit', $event)"
          />
        </div>
        <div class="flex flex-col gap-1 justify-end">
          <p class="label">Off-hand</p>
          <InventorySlot
            v-for="s in inv.offhand" :key="'o' + s.index"
            class="w-11 h-11" :slot-data="s" container="offhand" :editable="editable" @edit="$emit('edit', $event)"
          />
        </div>
      </div>

      <!-- Main inventory + hotbar -->
      <div class="flex-1 min-w-0 overflow-x-auto">
        <p class="label">Inventory</p>
        <div class="grid grid-cols-9 gap-1 min-w-[18rem]">
          <InventorySlot
            v-for="s in mainRows" :key="'m' + s.index"
            :slot-data="s" container="inv" :editable="editable" @edit="$emit('edit', $event)"
          />
        </div>
        <div class="grid grid-cols-9 gap-1 mt-2 pt-2 border-t border-edge min-w-[18rem]">
          <InventorySlot
            v-for="s in hotbar" :key="'h' + s.index"
            :slot-data="s" container="inv" :editable="editable" @edit="$emit('edit', $event)"
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
          :slot-data="s" container="ender" :editable="editable" @edit="$emit('edit', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import InventorySlot from './InventorySlot.vue'

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
</script>
