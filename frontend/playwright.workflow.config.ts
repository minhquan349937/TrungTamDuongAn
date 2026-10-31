import { defineConfig } from "@playwright/test";
import base from "./playwright.config";
// API responses are intercepted by these browser tests; no backend or live DB is started.
export default defineConfig({
  ...base,
  testMatch: ["plan-selection.spec.ts", "intervention.spec.ts", "assignments.spec.ts", "new-plan-history.spec.ts", "plan-reports.spec.ts"],
  webServer: {
    command: "node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5174 --strictPort",
    url: "http://127.0.0.1:5174", reuseExistingServer: true, timeout: 30000,
  },
});
