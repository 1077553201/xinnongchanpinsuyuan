import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  // 后端默认 8080；mock 模式下指向预览数据服务（本机 8080 常被系统保留，故用 8090）。
  const env = loadEnv(mode, process.cwd(), '')
  const fallback = mode === 'mock' ? 'http://127.0.0.1:8090' : 'http://127.0.0.1:8080'
  const proxyTarget = env.VITE_PROXY_TARGET || fallback

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      strictPort: true,
      // 允许通过 cloudflared 隧道域名访问。cloudflared 回源时会保留原始 Host 头，
      // 所以这里必须显式放行隧道域名（Vite 5.4.12+ 默认只放行 localhost/IP）。
      allowedHosts: ['.trycloudflare.com', '.mhjy.cc.cd'],
      proxy: {
        // 后端未配置 CORS，开发期统一走 dev 代理，前端零跨域
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
    },
    preview: { host: '0.0.0.0', port: 4173, strictPort: true },
  }
})
