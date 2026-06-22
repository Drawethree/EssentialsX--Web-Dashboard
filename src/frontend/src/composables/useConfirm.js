import { useConfirmStore } from '../stores/confirm'

/**
 * Imperative confirm dialog.
 *   const confirm = useConfirm()
 *   if (await confirm({ title: 'Delete kit?', message: '…', danger: true })) { … }
 */
export function useConfirm() {
  const store = useConfirmStore()
  return opts => store.ask(typeof opts === 'string' ? { message: opts } : opts)
}
