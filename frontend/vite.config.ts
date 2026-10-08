import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// Dev: the browser talks to :5173; API calls and the session cookie are proxied to Spring Boot on :8080.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/logout': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
})
