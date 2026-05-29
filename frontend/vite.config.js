import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  esbuild: {
    drop: mode === 'production' ? ['console', 'debugger'] : [],
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    rollupOptions: {
      output: {
        // 手动分包优化
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router'],
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          markdown: ['marked', 'dompurify', 'highlight.js'],
          viz: ['echarts', 'mermaid', 'katex'],
        }
      }
    },
    // 块大小警告限制
    chunkSizeWarningLimit: 1000,
    // 使用内置 esbuild 压缩（无需 terser 依赖，与 deploy 脚本 npm run build 一致）
    minify: 'esbuild',
    // CSS 代码分割
    cssCodeSplit: true,
    // Source map
    sourcemap: false
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  },
  optimizeDeps: {
    include: ['vue', 'vue-router', 'element-plus', 'marked', 'dompurify']
  }
}))
