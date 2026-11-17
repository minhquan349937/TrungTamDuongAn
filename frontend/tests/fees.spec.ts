import { test, expect, type BrowserContext, type Page } from "@playwright/test";
import type { FeeRecord } from "../src/services/feeApi";
import { emptyParentPortal } from "./parent-portal-fixture";

const child = { id: "HS-fees-1", code: "HS-FAKE-1", fullName: "Học sinh học phí giả lập" };
const another = { id: "HS-fees-2", code: "HS-FAKE-2", fullName: "Học sinh khác giả lập" };
function fee(id = "HD-fees-1", student = child): FeeRecord {
  return { id, code: id, studentId: student.id, studentName: student.fullName, title: "Học phí kiểm thử", month: "2026-11",
    amount: 2500000, paidAmount: 0, outstandingAmount: 2500000, dueOn: "2026-11-30", paidAt: null };
}
type State = { rows: FeeRecord[]; fail: boolean; creates: number; payments: number };
async function api(context: BrowserContext, role: "ADMIN" | "PARENT", state: State) {
  await context.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    if (path === "/api/auth/csrf") return route.fulfill({ json: { token: "csrf" } });
    if (path === "/api/parent/portal") return route.fulfill({ json: emptyParentPortal() });
    if (path === "/api/auth/me") return route.fulfill({ json: { id: `test-${role}`, displayName: "Tài khoản học phí giả lập", email: null, phone: "0912000000", role } });
    if (path === "/api/fees/students") return route.fulfill({ json: role === "ADMIN" ? [child, another] : [child] });
    if (path === "/api/fees") {
      if (method === "POST") {
        expect(role).toBe("ADMIN");
        expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
        if (state.fail) return route.fulfill({ status: 503, json: { message: "Chưa lưu được khoản thu. Vui lòng thử lại." } });
        const input = route.request().postDataJSON();
        expect(input).not.toHaveProperty("createdBy");
        const row = { ...fee(`HD-created-${++state.creates}`), ...input };
        state.rows.unshift(row);
        return route.fulfill({ json: row });
      }
      return route.fulfill({ json: state.rows.filter(row => role === "ADMIN" || row.studentId === child.id) });
    }
    if (path.endsWith("/payment")) {
      expect(role).toBe("ADMIN"); expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
      state.payments++;
      const row = state.rows.find(row => row.id === path.split("/")[3])!;
      row.paidAmount = row.amount; row.outstandingAmount = 0; row.paidAt = "2026-11-12T17:30:00";
      return route.fulfill({ json: row });
    }
    return route.fulfill({ json: [] });
  });
}
async function fillFee(page: Page) {
  await page.getByRole("button", { name: "Tạo khoản thu", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("combobox", { name: /Học sinh$/ }).click();
  await page.getByTitle(`${child.fullName} · ${child.code}`, { exact: true }).click();
  await dialog.getByRole("textbox", { name: /Nội dung khoản thu$/ }).fill("Học phí kiểm thử");
  await dialog.getByRole("textbox", { name: /Tháng học phí$/ }).fill("2026-11");
  await dialog.getByRole("spinbutton", { name: /Số tiền/ }).fill("2500000");
  await dialog.getByRole("button", { name: "Lưu khoản thu", exact: true }).click();
}

test("admin saves a fee and linked parent sees it after reload without another child's fees", async ({ browser }) => {
  const state: State = { rows: [fee("HD-other", another)], fail: false, creates: 0, payments: 0 };
  const admin = await browser.newContext(), parent = await browser.newContext();
  try {
    await api(admin, "ADMIN", state); await api(parent, "PARENT", state);
    const page = await admin.newPage(); await page.goto("http://127.0.0.1:5174/#/fees");
    await fillFee(page);
    await expect(page.getByRole("dialog")).toHaveCount(0);
    expect(state.creates).toBe(1);
    const familyPage = await parent.newPage(); await familyPage.goto("http://127.0.0.1:5174/#/fees");
    await expect(familyPage.getByRole("cell", { name: child.fullName, exact: true })).toBeVisible();
    await expect(familyPage.getByText(another.fullName, { exact: true })).toHaveCount(0);
    await expect(familyPage.getByRole("button", { name: "Tạo khoản thu", exact: true })).toHaveCount(0);
    await familyPage.reload();
    await expect(familyPage.getByRole("cell", { name: child.fullName, exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Ghi nhận thu", exact: true }).first().click();
    await page.getByRole("button", { name: "Xác nhận đã thu", exact: true }).click();
    await expect.poll(() => state.payments).toBe(1);
    await familyPage.evaluate(() => window.dispatchEvent(new Event("focus")));
    await expect(familyPage.getByRole("cell", { name: "Đã thanh toán", exact: true })).toBeVisible();
    await expect(familyPage.getByRole("cell", { name: "13/11/2026", exact: true })).toBeVisible();
  } finally { await admin.close(); await parent.close(); }
});

test("open parent fee page refreshes after 15 seconds and dashboard totals use the same API", async ({ page, context }) => {
  const state: State = { rows: [], fail: false, creates: 0, payments: 0 };
  await api(context, "PARENT", state); await page.clock.install();
  await page.goto("/#/fees");
  await expect(page.getByRole("heading", { name: "Học phí & khoản thu" })).toBeVisible();
  state.rows.push(fee()); await page.clock.fastForward(16000);
  await expect(page.getByRole("cell", { name: child.fullName, exact: true })).toBeVisible();
  await page.goto("/#/dashboard");
  const card = page.locator(".stat-card").filter({ hasText: "Khoản thu chưa thanh toán" });
  await expect(card).toContainText("2.500.000");
});

test("failed creation retains the form and does not add a local fee", async ({ page, context }) => {
  const state: State = { rows: [], fail: true, creates: 0, payments: 0 };
  await api(context, "ADMIN", state); await page.goto("/#/fees"); await fillFee(page);
  await expect(page.getByText("Chưa lưu được khoản thu. Vui lòng thử lại.", { exact: true })).toBeVisible();
  await expect(page.getByRole("dialog").getByRole("textbox", { name: /Nội dung khoản thu$/ })).toHaveValue("Học phí kiểm thử");
  expect(state.rows).toHaveLength(0); expect(state.creates).toBe(0);
});
