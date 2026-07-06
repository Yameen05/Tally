import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // Static assets are precached; API responses are never cached (financial
      // data must always be fresh), which is the plugin default for non-assets.
      manifest: {
        name: 'Tally — Smart Finance',
        short_name: 'Tally',
        description: 'Personal finance dashboard: transactions, budgets, bank sync, and insights.',
        theme_color: '#070714',
        background_color: '#070714',
        display: 'standalone',
        start_url: '/',
        icons: [
          {
            src: '/icon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'any maskable',
          },
        ],
      },
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          charts: ['recharts'],
          integrations: ['axios', 'react-plaid-link'],
          icons: ['lucide-react']
        }
      }
    }
  }
})
