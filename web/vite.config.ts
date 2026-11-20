import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// In development the API runs on :4000; proxying keeps the browser on a single origin, so the
// session cookie is first-party exactly as it is in production.
// The map server (through Nginx) is proxied under /map the same way, so VITE_MAP_STYLE_URL=/map/style.json
// works in development exactly as it does behind Nginx. Override the target with DEV_MAP_PROXY (env or .env.local).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': 'http://localhost:4000',
        '/map': {
          target: env.DEV_MAP_PROXY || 'http://localhost:8000',
          // Nginx writes the address the browser used into style.json (from the Host header), so pass the
          // browser's own Host through; otherwise the style would point past the dashboard's address.
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq, req) => {
              if (req.headers.host) proxyReq.setHeader('Host', req.headers.host)
            })
          },
        },
      },
    },
  }
})
