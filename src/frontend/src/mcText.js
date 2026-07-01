// Minecraft chat-formatting parser. Turns a string with § / & colour & format codes into a
// list of styled segments so it can be rendered with real colours (instead of stripped).

// The 16 vanilla colour codes → hex.
const COLORS = {
  '0': '#000000', '1': '#0000aa', '2': '#00aa00', '3': '#00aaaa',
  '4': '#aa0000', '5': '#aa00aa', '6': '#ffaa00', '7': '#aaaaaa',
  '8': '#555555', '9': '#5555ff', a: '#55ff55', b: '#55ffff',
  c: '#ff5555', d: '#ff55ff', e: '#ffff55', f: '#ffffff',
}

function blankStyle() {
  return { color: null, bold: false, italic: false, underline: false, strike: false, obfuscated: false }
}

/**
 * Parse a Minecraft-formatted string.
 * @param {string} str
 * @returns {Array<{text:string} & ReturnType<typeof blankStyle>>}
 */
export function parseMcText(str) {
  const segments = []
  const text = String(str ?? '')
  let style = blankStyle()
  let buf = ''

  const flush = () => {
    if (buf) { segments.push({ text: buf, ...style }); buf = '' }
  }

  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if ((ch === '§' || ch === '&') && i + 1 < text.length) {
      const code = text[i + 1].toLowerCase()

      // Spigot/Bungee hex: §x§r§r§g§g§b§b (each hex digit prefixed with § or &) — used for gradients.
      if (code === 'x') {
        let hex = '', j = i + 2, ok = true
        for (let k = 0; k < 6; k++) {
          const sep = text[j], d = text[j + 1]
          if ((sep === '§' || sep === '&') && d && /[0-9a-f]/i.test(d)) { hex += d; j += 2 }
          else { ok = false; break }
        }
        if (ok) { flush(); style = blankStyle(); style.color = '#' + hex; i = j - 1; continue }
      }

      // Shorthand hex: §#rrggbb / &#rrggbb
      if (code === '#') {
        const hex = text.slice(i + 2, i + 8)
        if (/^[0-9a-f]{6}$/i.test(hex)) { flush(); style = blankStyle(); style.color = '#' + hex; i += 7; continue }
      }

      if (COLORS[code]) { flush(); style = blankStyle(); style.color = COLORS[code]; i++; continue }
      switch (code) {
        case 'l': flush(); style = { ...style, bold: true }; i++; continue
        case 'o': flush(); style = { ...style, italic: true }; i++; continue
        case 'n': flush(); style = { ...style, underline: true }; i++; continue
        case 'm': flush(); style = { ...style, strike: true }; i++; continue
        case 'k': flush(); style = { ...style, obfuscated: true }; i++; continue
        case 'r': flush(); style = blankStyle(); i++; continue
        default: break // not a recognised code — treat the marker as a literal char
      }
    }
    buf += ch
  }
  flush()
  if (segments.length === 0) segments.push({ text: '', ...blankStyle() })
  return segments
}

/** Plain text with all codes removed (handy for titles / aria labels). */
export function stripMcText(str) {
  return String(str ?? '')
    .replace(/[§&]x(?:[§&][0-9a-f]){6}/gi, '') // hex gradient sequences
    .replace(/[§&]#[0-9a-f]{6}/gi, '')         // shorthand hex
    .replace(/[§&][0-9a-fk-or]/gi, '')         // single colour / format codes
}
