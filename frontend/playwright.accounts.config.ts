import { defineConfig } from "@playwright/test";
import base from "./playwright.ai.config";
export default defineConfig({ ...base, testMatch: "accounts-phone.spec.ts" });
