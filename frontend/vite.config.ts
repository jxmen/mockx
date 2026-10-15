import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 백엔드(./gradlew bootRun)로 API 요청을 넘긴다. 같은 출처로 보이므로 CORS 설정이 필요 없다.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
})
