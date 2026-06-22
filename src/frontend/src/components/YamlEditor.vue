<template>
  <div>
    <div ref="el" class="rounded-md border overflow-hidden" :class="valid ? 'border-edge2' : 'border-red-500/60'"></div>
    <p class="text-xs mt-1 flex items-center gap-1" :class="valid ? 'text-green-600 dark:text-green-500' : 'text-danger'">
      <span>{{ valid ? '✓ Valid YAML' : '✗ ' + error }}</span>
    </p>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { EditorView, basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { yaml } from '@codemirror/lang-yaml'
import { oneDark } from '@codemirror/theme-one-dark'
import jsyaml from 'js-yaml'
import { useThemeStore } from '../stores/theme'

const theme = useThemeStore()

const props = defineProps({
  modelValue: { type: String, default: '' },
  readonly: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'update:valid'])

const el = ref(null)
const valid = ref(true)
const error = ref('')
let view

function validate(text) {
  try {
    jsyaml.load(text)
    valid.value = true
    error.value = ''
  } catch (e) {
    valid.value = false
    error.value = (e.message || 'Invalid YAML').split('\n')[0]
  }
  emit('update:valid', valid.value)
}

onMounted(() => {
  const listener = EditorView.updateListener.of(v => {
    if (v.docChanged) {
      const text = v.state.doc.toString()
      emit('update:modelValue', text)
      validate(text)
    }
  })
  const extensions = [basicSetup, yaml(), listener, EditorView.lineWrapping]
  if (theme.isDark) extensions.push(oneDark)
  if (props.readonly) extensions.push(EditorState.readOnly.of(true))
  view = new EditorView({ doc: props.modelValue || '', extensions, parent: el.value })
  validate(props.modelValue || '')
})

// Keep the editor in sync when the parent loads/replaces content.
watch(() => props.modelValue, val => {
  if (view && val !== view.state.doc.toString()) {
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: val || '' } })
    validate(val || '')
  }
})

onBeforeUnmount(() => view && view.destroy())
</script>

<style>
.cm-editor { max-height: 60vh; font-size: 12px; }
.cm-editor .cm-scroller { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
</style>
