import fs from 'node:fs';
import path from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { viteSingleFile } from 'vite-plugin-singlefile';

const isOfflineSingleFile = process.env.VITE_OFFLINE_SINGLEFILE === 'true';

/**
 * Copies the compiled single-file index.html to offline/ExamSet-Standalone.html
 * cleanly so offline browsers can launch it directly via double-click or local HTTP.
 */
function renameOfflineStandaloneHtml() {
  return {
    name: 'examset-offline-standalone-output',
    closeBundle() {
      if (!isOfflineSingleFile) return;
      const outDir = path.resolve(__dirname, 'offline');
      const builtIndex = path.join(outDir, 'index.html');
      const targetHtml = path.join(outDir, 'ExamSet-Standalone.html');
      if (fs.existsSync(builtIndex)) {
        const html = fs
          .readFileSync(builtIndex, 'utf8')
          .replace('<script type="module" crossorigin>', '<script type="module">');
        fs.writeFileSync(targetHtml, html, 'utf8');
        fs.unlinkSync(builtIndex);
      }
    },
  };
}

export default defineConfig({
  base: isOfflineSingleFile ? './' : '/',
  plugins: [
    react(),
    ...(isOfflineSingleFile
      ? [
          viteSingleFile({ removeViteModuleLoader: true }),
          renameOfflineStandaloneHtml(),
        ]
      : []),
  ],
  build: {
    outDir: isOfflineSingleFile ? 'offline' : 'dist',
    emptyOutDir: !isOfflineSingleFile,
    assetsInlineLimit: isOfflineSingleFile ? 100_000_000 : 4096,
    cssCodeSplit: !isOfflineSingleFile,
    sourcemap: false,
    rollupOptions: isOfflineSingleFile
      ? {
          output: {
            inlineDynamicImports: true,
          },
        }
      : {
          output: {
            manualChunks: {
              'react-vendor': ['react', 'react-dom'],
              icons: ['lucide-react'],
            },
          },
        },
  },
  server: {
    host: true,
    port: 5173,
  },
  preview: {
    host: true,
    port: 4173,
  },
});
