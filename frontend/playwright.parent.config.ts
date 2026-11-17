import { defineConfig } from "@playwright/test";
import base from "./playwright.ai.config";
export default defineConfig({ ...base, testMatch: ["parent-portal.spec.ts", "accounts-phone.spec.ts", "fees.spec.ts"] });
