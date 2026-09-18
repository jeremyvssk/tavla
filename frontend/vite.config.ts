/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    // `bun dev` mirrors what nginx does in the container: proxy the API on the same
    // origin, paths unrewritten, so the refresh cookie's Path=/auth scope still matches.
    proxy: {
      '^/(auth|users|products|categories|brands|search)(/|$)': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
