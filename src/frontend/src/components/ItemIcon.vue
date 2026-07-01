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
// Primary: rendered inventory icons (incl. 3D block renders) keyed directly by
// Bukkit material name — covers every material. 404s on unknown names so the
// fallback chain below still works.
const ICONS = `https://mc.nerothe.com/img/${MC_VERSION}`
// Fallback: raw vanilla textures (only flat faces; misses many blocks).
const BASE = `https://assets.mcasset.cloud/${MC_VERSION}/assets/minecraft/textures`

const props = defineProps({
  material: { type: String, default: '' },
  size: { type: Number, default: 32 },
  title: { type: String, default: '' },
})

// Bukkit Material → vanilla texture name. Most map 1:1 once lower-cased.
const name = computed(() => (props.material || '').toLowerCase().replace(/^minecraft:/, ''))
const pretty = computed(() => name.value.replace(/_/g, ' '))

// Try the rendered-icon CDN first, then vanilla item/block textures, then a text label.
const sources = computed(() => name.value
  ? [`${ICONS}/minecraft_${name.value}.png`, `${BASE}/item/${name.value}.png`, `${BASE}/block/${name.value}.png`]
  : [])
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
