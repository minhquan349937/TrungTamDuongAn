import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
declare const process: { env: Record<string, string | undefined> }; // no @types/node; API_TARGET lets tests point at another backend
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    allowedHosts: [".trycloudflare.com"],
    proxy: {
      "/api": { target: process.env.API_TARGET ?? "http://127.0.0.1:8081", changeOrigin: false },
    },
  },
  build: { chunkSizeWarningLimit: 1500 },
});
