import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 部署在 Spring 静态资源 /ui/ 下；开发态代理 /api 到集成层
export default defineConfig({
  base: '/ui/',
  plugins: [vue()],
  build: {
    outDir: '../monitor-integration/target/classes/static/ui',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/health': 'http://localhost:8080',
    },
  },
});
