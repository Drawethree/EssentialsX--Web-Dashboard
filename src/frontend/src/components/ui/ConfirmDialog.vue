<template>
  <Modal :open="store.open" :title="store.options.title" size="sm" @close="store.resolve(false)">
    <p class="text-sm text-secondary whitespace-pre-line">{{ store.options.message }}</p>
    <template #footer>
      <Button variant="ghost" @click="store.resolve(false)">{{ store.options.cancelText }}</Button>
      <Button :variant="store.options.danger ? 'danger' : 'primary'" @click="store.resolve(true)">
        {{ store.options.confirmText }}
      </Button>
    </template>
  </Modal>
</template>

<script setup>
import Modal from '../Modal.vue'
import Button from './Button.vue'
import { useConfirmStore } from '../../stores/confirm'

const store = useConfirmStore()
</script>
