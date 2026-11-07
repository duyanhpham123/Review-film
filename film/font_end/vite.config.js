import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// Khi dev: mọi request /api được chuyển tới Spring Boot, nên frontend và API cùng origin
// -> cookie refresh token (HttpOnly) hoạt động mà không vướng CORS.
// Đổi địa chỉ backend bằng VITE_API_TARGET trong file .env.local (mặc định http://localhost:8081).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_API_TARGET || 'http://localhost:8081',
          changeOrigin: false,
        },
      },
    },
  };
});
