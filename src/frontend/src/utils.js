export function formatMoney(value, symbol = '$') {
  if (value === null || value === undefined) return '—'
  const num = typeof value === 'number' ? value : Number(value)
  if (Number.isNaN(num)) return String(value)
  return symbol + num.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

export function timeAgo(millis) {
  if (!millis) return 'never'
  const diff = Date.now() - millis
  if (diff < 0) return 'just now'
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}d ago`
  return new Date(millis).toLocaleDateString()
}

export function formatDateTime(millis) {
  if (!millis) return '—'
  return new Date(millis).toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

export function formatDuration(millis) {
  if (!millis || millis <= 0) return '—'
  const remaining = millis - Date.now()
  if (remaining <= 0) return 'expired'
  const mins = Math.floor(remaining / 60000)
  if (mins < 60) return `${mins}m`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ${mins % 60}m`
  const days = Math.floor(hours / 24)
  return `${days}d ${hours % 24}h`
}

// Minecraft legacy colour codes, keyed by the character that follows & or §.
const MC_COLORS = {
  '0': '#000000', '1': '#0000aa', '2': '#00aa00', '3': '#00aaaa',
  '4': '#aa0000', '5': '#aa00aa', '6': '#ffaa00', '7': '#aaaaaa',
  '8': '#555555', '9': '#5555ff', a: '#55ff55', b: '#55ffff',
  c: '#ff5555', d: '#ff55ff', e: '#ffff55', f: '#ffffff',
}

/**
 * Parse a string with Minecraft legacy colour/format codes (& or §) into styled runs.
 * Returns an array of { text, color, bold, italic, underline, strike, obfuscated }.
 * Mirrors vanilla behaviour: a colour code resets formatting; `r` resets everything.
 */
export function parseMcColors(input) {
  const text = String(input ?? '')
  const segments = []
  let cur = { text: '', color: null, bold: false, italic: false, underline: false, strike: false, obfuscated: false }
  const push = () => { if (cur.text) segments.push({ ...cur }); cur = { ...cur, text: '' } }

  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if ((ch === '&' || ch === '§') && i + 1 < text.length) {
      const code = text[i + 1].toLowerCase()
      if (MC_COLORS[code]) {
        push()
        cur = { text: '', color: MC_COLORS[code], bold: false, italic: false, underline: false, strike: false, obfuscated: false }
        i++; continue
      }
      if ('klmnor'.includes(code)) {
        push()
        if (code === 'l') cur.bold = true
        else if (code === 'o') cur.italic = true
        else if (code === 'n') cur.underline = true
        else if (code === 'm') cur.strike = true
        else if (code === 'k') cur.obfuscated = true
        else if (code === 'r') cur = { text: '', color: null, bold: false, italic: false, underline: false, strike: false, obfuscated: false }
        i++; continue
      }
    }
    cur.text += ch
  }
  push()
  return segments
}

export function uptime(millis) {
  const secs = Math.floor(millis / 1000)
  const d = Math.floor(secs / 86400)
  const h = Math.floor((secs % 86400) / 3600)
  const m = Math.floor((secs % 3600) / 60)
  if (d > 0) return `${d}d ${h}h`
  if (h > 0) return `${h}h ${m}m`
  return `${m}m`
}
