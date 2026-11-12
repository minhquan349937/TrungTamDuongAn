import { defineConfig } from "@playwright/test";
import base from "./playwright.config";

// These UI tests mock every API; no backend or real student database is needed.
export default defineConfig({
  ...base,
  testMatch: "ai-summary.spec.ts",
  webServer: [{
    command: "node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5174 --strictPort",
    url: "http://127.0.0.1:5174",
    reuseExistingServer: true,
    timeout: 30000,
  }],
});
