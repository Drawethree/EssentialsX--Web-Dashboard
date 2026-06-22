<template>
  <span
    class="inline-flex items-center justify-center flex-shrink-0 align-middle"
    :style="{ width: size + 'px', height: size + 'px' }"
    :title="title || pretty"
  >
    <img
      v-if="src && !failed"
      :src="src"
      :width="size"
      :height="size"
      alt=""
      class="mc-pixel"
      @error="onError"
    />
    <span v-else class="text-[0.5rem] leading-none text-muted text-center break-all px-0.5">{{ pretty }}</span>
  </span>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const MC_VERSION = '1.21'
const BASE = `https://assets.mcasset.cloud/${MC_VERSION}/assets/minecraft/textures`

const props = defineProps({
  material: { type: String, default: '' },
  size: { type: Number, default: 32 },
  title: { type: String, default: '' },
})

// Bukkit Material → vanilla texture name. Most map 1:1 once lower-cased.
const name = computed(() => (props.material || '').toLowerCase().replace(/^minecraft:/, ''))
const pretty = computed(() => name.value.replace(/_/g, ' '))

// Try item texture first, then block texture, then fall back to a text label.
const sources = computed(() => name.value ? [`${BASE}/item/${name.value}.png`, `${BASE}/block/${name.value}.png`] : [])
const idx = ref(0)
const failed = ref(false)
const src = computed(() => sources.value[idx.value] || '')

function onError() {
  if (idx.value < sources.value.length - 1) idx.value++
  else failed.value = true
}

watch(() => props.material, () => { idx.value = 0; failed.value = false })
</script>

<style scoped>
.mc-pixel {
  image-rendering: pixelated;
  image-rendering: -moz-crisp-edges;
  image-rendering: crisp-edges;
}
</style>
