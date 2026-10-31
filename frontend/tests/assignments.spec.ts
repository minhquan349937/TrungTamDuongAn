import { test, expect } from "@playwright/test";
import type { IndividualAssignment } from "../src/services/individualAssignmentApi";

test("admin assigns a real student and changes their responsible teacher", async ({ page }) => {
  const rows: IndividualAssignment[] = [];
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    let data: unknown = {};
    if (path === "/api/auth/me") data = { id: "admin", displayName: "Admin", role: "ADMIN", email: "admin@test.invalid" };
    else if (path === "/api/auth/csrf") data = { token: "csrf" };
    else if (path === "/api/student-profiles") data = [{ id: "student", code: "HS001", fullName: "Test Student", status: "ACTIVE" }];
    else if (path.endsWith("/individual-assignments/teachers")) data = [
      { id: "teacher1", code: "GV001", fullName: "Teacher One" }, { id: "teacher2", code: "GV002", fullName: "Teacher Two" },
    ];
    else if (path === "/api/admin/individual-assignments") {
      if (method === "POST") {
        expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
        expect(route.request().postDataJSON()).toEqual({ studentId: "student", teacherId: "teacher1" });
        rows.push({ id: "class", code: "CN001", studentId: "student", studentCode: "HS001", studentName: "Test Student", teacherId: "teacher1", teacherName: "Teacher One", startsOn: "2026-10-28" });
        data = rows[0];
      } else data = rows;
    } else if (path === "/api/admin/individual-assignments/class" && method === "PUT") {
      expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
      expect(route.request().postDataJSON()).toEqual({ teacherId: "teacher2" });
      Object.assign(rows[0], { teacherId: "teacher2", teacherName: "Teacher Two" }); data = rows[0];
    } else return route.fulfill({ status: 404, body: "{}" });
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(data) });
  });
  await page.goto("/#/individual/assign");
  await page.getByRole("button", { name: "Thêm phân công", exact: true }).click();
  await page.getByRole("combobox", { name: "Học sinh", exact: true }).click();
  await page.getByText("HS001 · Test Student", { exact: true }).click();
  await page.getByRole("combobox", { name: "Giáo viên phụ trách", exact: true }).click();
  await page.getByText("GV001 · Teacher One", { exact: true }).click();
  await page.getByRole("button", { name: "Lưu phân công", exact: true }).click();
  await expect(page.locator("tbody")).toContainText("Teacher One");
  await page.getByRole("button", { name: "Đổi giáo viên", exact: true }).click();
  await page.getByRole("combobox", { name: "Giáo viên phụ trách", exact: true }).focus();
  await page.getByRole("combobox", { name: "Giáo viên phụ trách", exact: true }).press("ArrowDown");
  await page.getByText("GV002 · Teacher Two", { exact: true }).click();
  await page.getByRole("button", { name: "Lưu phân công", exact: true }).click();
  await expect(page.locator("tbody")).toContainText("Teacher Two");
});
