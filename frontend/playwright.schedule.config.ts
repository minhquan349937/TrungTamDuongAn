import { defineConfig } from "@playwright/test";
import base from "./playwright.ai.config";
export default defineConfig({ ...base, testMatch: "schedule-database.spec.ts" });
