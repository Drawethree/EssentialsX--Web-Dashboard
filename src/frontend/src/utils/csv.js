// Client-side CSV export. Converts an array of objects into a downloadable CSV file
// using already-fetched data — no backend round-trip.

function escapeCell(value) {
  if (value === null || value === undefined) return ''
  const s = String(value)
  // Quote when the cell contains a comma, quote, or newline; double up inner quotes.
  if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`
  return s
}

/**
 * Trigger a CSV download.
 * @param {string} filename  e.g. "baltop.csv"
 * @param {Array<Object>} rows  array of plain objects
 * @param {Array<{key:string,label:string}>} [columns]  optional explicit columns/order
 */
export function exportCsv(filename, rows, columns) {
  if (!rows || rows.length === 0) return
  const cols = columns && columns.length
    ? columns
    : Object.keys(rows[0]).map(k => ({ key: k, label: k }))

  const header = cols.map(c => escapeCell(c.label)).join(',')
  const body = rows.map(row => cols.map(c => escapeCell(row[c.key])).join(',')).join('\r\n')
  const csv = `${header}\r\n${body}`

  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
