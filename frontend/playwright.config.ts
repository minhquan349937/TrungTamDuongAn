import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  timeout: 45000,
  fullyParallel: false,
  workers: 1,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:5174",
    headless: true,
    launchOptions: {
      executablePath: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    },
    screenshot: "only-on-failure",
  },
  webServer: [
    {
      command: process.platform === "win32" ? ".\\mvnw.cmd spring-boot:run" : "./mvnw spring-boot:run",
      cwd: "..",
      url: "http://127.0.0.1:8081/api/auth/csrf",
      reuseExistingServer: true,
      timeout: 60000,
    },
    {
      command: "node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5174 --strictPort",
      url: "http://127.0.0.1:5174",
      reuseExistingServer: true,
      timeout: 30000,
    },
  ],
});
