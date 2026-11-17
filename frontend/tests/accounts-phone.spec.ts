import { test, expect, type Page } from "@playwright/test";
import type { Account } from "../src/services/accountApi";
import { createSeed } from "../src/mocks/seed";
import { emptyParentPortal } from "./parent-portal-fixture";

test("parent dashboard handles stored students without a linked parent profile", async ({ page }) => {
  const db = createSeed();
  db.parents = [];
  await page.addInitScript(data => {
    localStorage.setItem("duongan.demo.cleared.v1", "1");
    localStorage.setItem("duongan.demo.v1", JSON.stringify(data));
  }, db);
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/csrf") return route.fulfill({ json: { token: "csrf" } });
    if (path === "/api/parent/portal") return route.fulfill({ json: emptyParentPortal() });
    if (path === "/api/auth/me") return route.fulfill({ json: {
      id: "TK-parent-new", email: null, phone: "0912000004", displayName: "Phụ huynh giả lập", role: "PARENT",
    } });
    return route.fulfill({ json: [] });
  });
  await page.goto("/#/dashboard");
  await expect(page.getByRole("heading", { name: "Cùng con lớn lên mỗi ngày" })).toBeVisible();
  await expect(page.getByText("Chưa có hồ sơ con được liên kết.", { exact: true })).toBeVisible();
  expect(errors).toEqual([]);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Cùng con lớn lên mỗi ngày" })).toBeVisible();
  expect(errors).toEqual([]);
});

async function mockAdmin(page: Page) {
  const accounts: Account[] = [{ id: "TK000001", displayName: "Giáo viên giả lập", email: "teacher@test.invalid", phone: "0912000001", role: "TEACHER", status: "ACTIVE" }];
  const parents: { id: string; code: string; fullName: string; phone: string; email: string | null; address: string | null }[] = [];
  const state = { accounts, parents, failUpdate: false, created: 0, createdStudent: false };
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    if (path === "/api/auth/csrf") return route.fulfill({ json: { token: "csrf" } });
    if (path === "/api/auth/me") return route.fulfill({ json: { id: "admin", email: "admin@test.invalid", displayName: "Test admin", role: "ADMIN" } });
    if (path === "/api/admin/accounts") return route.fulfill({ json: accounts });
    if (path === `/api/admin/accounts/${accounts[0].id}`) {
      expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
      if (state.failUpdate) return route.fulfill({ status: 409, json: { message: "Số điện thoại hoặc email đã được tài khoản khác sử dụng." } });
      Object.assign(accounts[0], route.request().postDataJSON());
      return route.fulfill({ json: accounts[0] });
    }
    if (path === "/api/guardians") {
      if (method === "POST") {
        state.created++;
        expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
        const input = route.request().postDataJSON();
        expect(input.email).toBeNull();
        expect(input).not.toHaveProperty("password");expect(input).not.toHaveProperty("role");
        const parent = { ...input, id: "PH000001", code: "PH001" }; parents.push(parent);
        accounts.push({ id: "TK000002", displayName: input.fullName, phone: input.phone, email: null, role: "PARENT", status: "ACTIVE" });
        return route.fulfill({ json: parent });
      }
      return route.fulfill({ json: parents });
    }
    if (path === "/api/student-profiles") {
      if (method === "POST") {
        expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
        const input = route.request().postDataJSON();
        expect(input.guardian.phone).toBe("0912000005");expect(input.guardian.email).toBeNull();
        state.createdStudent = true;
        return route.fulfill({ json: { ...input, id: "HS000001", code: "HS001", status: "ACTIVE" } });
      }
      return route.fulfill({ json: [] });
    }
    return route.fulfill({ json: [] });
  });
  return state;
}

test("real accounts list displays phone, search and contact updates while keeping failed edits", async ({ page }) => {
  const state = await mockAdmin(page);
  await page.goto("/#/accounts");
  await expect(page.getByRole("columnheader", { name: "Số điện thoại", exact: true })).toBeVisible();
  await expect(page.getByRole("cell", { name: "0912000001", exact: true })).toBeVisible();
  await page.getByRole("searchbox", { name: "Tìm kiếm tài khoản" }).fill("0912000001");
  await page.getByRole("button", { name: "Chỉnh sửa", exact: true }).click();
  const phone = page.getByRole("textbox", { name: "Số điện thoại", exact: true });
  await phone.fill("0912000002");state.failUpdate = true;
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page.getByText("Số điện thoại hoặc email đã được tài khoản khác sử dụng.", { exact: true })).toBeVisible();
  await expect(phone).toHaveValue("0912000002");
  state.failUpdate = false;
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(phone).toHaveCount(0);
  await page.getByRole("searchbox", { name: "Tìm kiếm tài khoản" }).fill("0912000002");
  await expect(page.getByRole("cell", { name: "0912000002", exact: true })).toBeVisible();
});

test("account editor saves name, role, inactive status and a confirmed new password", async ({ page }) => {
  const state = await mockAdmin(page);
  await page.goto("/#/accounts");
  await page.getByRole("button", { name: "Chỉnh sửa", exact: true }).click();
  await page.getByRole("textbox", { name: /Họ tên$/ }).fill("Tên tài khoản mới");
  await page.getByRole("combobox", { name: /Vai trò$/ }).press("Enter");
  await page.getByTitle("Quản trị viên", { exact: true }).click();
  await page.getByRole("combobox", { name: /Trạng thái$/ }).press("Enter");
  await page.getByTitle("Ngừng hoạt động", { exact: true }).click();
  await page.getByLabel("Mật khẩu mới", { exact: true }).fill("new-password");
  await page.getByLabel("Xác nhận mật khẩu mới", { exact: true }).fill("wrong-password");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page.getByText("Mật khẩu xác nhận không khớp.", { exact: true })).toBeVisible();
  expect(state.accounts[0].displayName).toBe("Giáo viên giả lập");
  await page.getByLabel("Xác nhận mật khẩu mới", { exact: true }).fill("new-password");
  const saved = page.waitForRequest(request => request.method() === "PUT");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  expect((await saved).postDataJSON()).toMatchObject({ displayName: "Tên tài khoản mới", role: "ADMIN", status: "DISABLED", password: "new-password" });
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("cell", { name: "Ngừng hoạt động", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Chỉnh sửa", exact: true }).click();
  await expect(page.getByLabel("Mật khẩu mới", { exact: true })).toHaveValue("");
  await page.getByRole("combobox", { name: /Trạng thái$/ }).press("Enter");
  await page.getByTitle("Hoạt động", { exact: true }).click();
  const reactivated = page.waitForRequest(request => request.method() === "PUT");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  const body = (await reactivated).postDataJSON();
  expect(body.status).toBe("ACTIVE"); expect(body).not.toHaveProperty("password"); expect(body).not.toHaveProperty("confirmPassword");
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("linked parent account explains its role restriction", async ({ page }) => {
  const state = await mockAdmin(page);
  Object.assign(state.accounts[0], { role: "PARENT", allowedRoles: ["PARENT"] });
  await page.goto("/#/accounts");
  await page.getByRole("button", { name: "Chỉnh sửa", exact: true }).click();
  await expect(page.getByText("Vai trò phải phù hợp với hồ sơ nhân viên hoặc phụ huynh đã liên kết.", { exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: /Vai trò$/ }).press("Enter");
  await expect(page.getByTitle("Quản trị viên", { exact: true })).toHaveClass(/ant-select-item-option-disabled/);
});

test("parent can be added without email and its phone account appears in account management", async ({ page }) => {
  const state = await mockAdmin(page);
  await page.goto("/#/parents");
  await page.getByRole("button", { name: "Thêm phụ huynh", exact: true }).click();
  await expect(page.getByText(/mật khẩu ban đầu: 123456/)).toBeVisible();
  await page.getByRole("textbox", { name: /Họ và tên$/ }).fill("Phụ huynh giả lập");
  await page.getByRole("textbox", { name: /Số điện thoại$/ }).fill("0912000003");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page.getByRole("cell", { name: "0912000003", exact: true })).toBeVisible();
  expect(state.created).toBe(1);
  await page.goto("/#/accounts");
  const row = page.locator(".ant-table-row").filter({ hasText: "0912000003" });
  await expect(row).toContainText("Phụ huynh giả lập");await expect(row).toContainText("Phụ huynh");
});

test("adding a student accepts phone-only parent information", async ({ page }) => {
  const state = await mockAdmin(page);
  await page.goto("/#/students");
  await page.getByRole("button", { name: "Thêm học sinh", exact: true }).click();
  await expect(page.getByText(/Tự động tạo tài khoản phụ huynh/)).toBeVisible();
  await page.getByRole("textbox", { name: /Họ và tên$/ }).fill("Học sinh giả lập");
  await page.getByLabel(/Ngày sinh$/).fill("2022-01-01");
  await page.getByLabel(/Ngày nhập học$/).fill("2024-01-01");
  await page.getByRole("textbox", { name: /Họ và tên phụ huynh$/ }).fill("Phụ huynh giả lập");
  await page.getByRole("textbox", { name: /Số điện thoại$/ }).fill("0912000005");
  await page.getByRole("button", { name: "Tạo hồ sơ", exact: true }).click();
  await expect.poll(() => state.createdStudent).toBe(true);
});

for (const identifier of ["0912000004", "parent@test.invalid"]) {
  test(`login accepts ${identifier.includes("@") ? "email" : "phone"} without requiring an email-shaped value`, async ({ page }) => {
    let loggedIn = false;
    await page.route("**/api/**", async route => {
      const path = new URL(route.request().url()).pathname;
      if (path === "/api/auth/csrf") return route.fulfill({ json: { token: "csrf" } });
      if (path === "/api/parent/portal") return route.fulfill({ json: emptyParentPortal() });
      if (path === "/api/auth/login") {
        expect(route.request().postDataJSON()).toEqual({ login: identifier, password: "123456" });
        expect(route.request().headers()["x-csrf-token"]).toBe("csrf");loggedIn = true;
        return route.fulfill({ json: { id: "parent", email: identifier.includes("@") ? identifier : null, phone: "0912000004", displayName: "Parent", role: "PARENT" } });
      }
      if (path === "/api/auth/me" && !loggedIn) return route.fulfill({ status: 401, json: {} });
      return route.fulfill({ json: [] });
    });
    await page.goto("/#/login");
    await page.getByRole("textbox", { name: "Số điện thoại hoặc email", exact: true }).fill(identifier);
    await page.getByLabel("Mật khẩu", { exact: true }).fill("123456");
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await expect(page).toHaveURL(/#\/dashboard$/);
    await expect(page.getByRole("heading", { name: "Cùng con lớn lên mỗi ngày" })).toBeVisible();
    expect(loggedIn).toBe(true);
  });
}
