import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // During the Maven build, BUILD_OUTPUT_DIR is set to target/classes/web.
    // During local dev builds, defaults to dist/.
    outDir: process.env.BUILD_OUTPUT_DIR || 'dist',
    emptyOutDir: true,
  },
  server: {
    // Proxy API calls to the Javalin backend during local development.
    proxy: {
      '/api': 'http://localhost:8095',
      '/health': 'http://localhost:8095',
    },
  },
})
