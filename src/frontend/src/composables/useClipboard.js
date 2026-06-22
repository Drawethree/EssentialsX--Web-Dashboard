import { useToastStore } from '../stores/toast'

/** Copy text to the clipboard with a toast confirmation, falling back for non-HTTPS contexts. */
export function useClipboard() {
  const toast = useToastStore()
  return async (text, label = 'Copied to clipboard') => {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text)
      } else {
        const ta = document.createElement('textarea')
        ta.value = text
        ta.style.position = 'fixed'
        ta.style.opacity = '0'
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
      }
      toast.success(label)
    } catch {
      toast.error('Could not copy')
    }
  }
}
