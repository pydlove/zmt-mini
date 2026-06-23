import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueJsx()],
  server: {
    port: 35173,
    proxy: {
      '/api': {
        target: 'http://localhost:38080',
        changeOrigin: true,
      },
      '/preview.html': {
        target: 'http://localhost:38080',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:38080',
        changeOrigin: true,
      },
    },
  },
})
