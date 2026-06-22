/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: 'rgb(var(--color-brand-rgb) / <alpha-value>)',
          hover: 'var(--color-brand-hover)',
          subtle: 'var(--color-brand-subtle)',
        },
        page:      'var(--color-bg-page)',
        surface:   'var(--color-bg-surface)',
        elevated:  'rgb(var(--color-elevated-rgb) / <alpha-value>)',
        overlay:   'var(--color-bg-overlay)',
        primary:   'var(--color-text-primary)',
        secondary: 'var(--color-text-secondary)',
        muted:     'var(--color-text-muted)',
        faint:     'var(--color-text-faint)',
        edge:      'rgb(var(--color-edge-rgb) / <alpha-value>)',
        edge2:     'var(--color-border-subtle)',
        track:     'var(--color-progress-track)',
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      boxShadow: {
        sm: 'var(--shadow-sm)',
        md: 'var(--shadow-md)',
        lg: 'var(--shadow-lg)',
      },
      keyframes: {
        'fade-in': { '0%': { opacity: '0' }, '100%': { opacity: '1' } },
        'slide-up-fade': {
          '0%': { opacity: '0', transform: 'translateY(4px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        'fade-in': 'fade-in 150ms ease-out',
        'slide-up-fade': 'slide-up-fade 160ms cubic-bezier(0.16, 1, 0.3, 1)',
      },
    },
  },
  plugins: [],
}
