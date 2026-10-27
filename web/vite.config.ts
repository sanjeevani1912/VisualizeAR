import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In development the API runs on :4000; proxying keeps the browser on a single origin, so the
// session cookie is first-party exactly as it is in production.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:4000' },
  },
})
