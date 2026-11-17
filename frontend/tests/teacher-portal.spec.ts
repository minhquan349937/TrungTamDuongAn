import { test, expect, type Page } from "@playwright/test";
import type { TeacherPortal } from "../src/services/teacherPortalApi";
import { createSeed } from "../src/mocks/seed";

async function mockTeacher(page: Page) {
  const portal: TeacherPortal = {
    linkStatus: "READY", teacher: { id: "NV-actual", fullName: "Giáo viên đăng nhập" },
    students: [{ id: "HS-assigned", code: "HS901", fullName: "Học sinh được giao", birthDate: "2021-01-01", admittedOn: "2025-01-01", status: "ACTIVE" }],
    classes: [{ id: "LH-group", code: "LH901", name: "Nhóm được phân công", type: "GROUP", teacherId: "NV-actual", capacity: 8, status: "ACTIVE" },
      { id: "LH-empty", code: "LH902", name: "Nhóm chưa có học sinh", type: "GROUP", teacherId: "NV-actual", capacity: 8, status: "ACTIVE" }],
    memberships: [{ classId: "LH-group", code: "LH901", name: "Nhóm được phân công", type: "GROUP", teacherId: "NV-actual", capacity: 8, status: "ACTIVE", studentId: "HS-assigned", startedOn: "2025-01-01", endedOn: null }],
    sessions: [], attendance: [], notices: [],
  };
  const state = { portal, fail: false };
  await page.addInitScript(seed => { localStorage.setItem("duongan.demo.v1", JSON.stringify(seed)); }, createSeed());
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/csrf") return route.fulfill({ json: { token: "csrf" } });
    if (path === "/api/auth/me") return route.fulfill({ json: { id: "TK-actual", displayName: "Giáo viên đăng nhập", role: "TEACHER", email: "teacher@example.com" } });
    if (path === "/api/teacher/portal") return route.fulfill(state.fail ? { status: 503, json: { message: "Không thể kết nối dữ liệu phân công." } } : { json: state.portal });
    return route.fulfill({ json: [] });
  });
  return state;
}

test("teacher dashboard, student list and group rosters use actual account assignments", async ({ page }) => {
  await mockTeacher(page);
  await page.goto("/#/dashboard");
  await expect(page.getByRole("heading", { name: /Chào Giáo viên đăng nhập/ })).toBeVisible();
  await expect(page.getByText("Học sinh được giao", { exact: true })).toBeVisible();
  await expect(page.getByText("Thu Hà", { exact: true })).toHaveCount(0);
  await page.goto("/#/students");
  await expect(page.getByRole("heading", { name: "Học sinh được phân công", exact: true }).first()).toBeVisible();
  await expect(page.getByText("HS901", { exact: false }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Thêm học sinh", exact: true })).toHaveCount(0);
  await page.goto("/#/groups");
  await expect(page.getByRole("heading", { name: "Nhóm được phân công", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Nhóm chưa có học sinh", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Xem chi tiết", exact: true }).first().click();
  await expect(page.getByRole("dialog").getByText("Học sinh được giao", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Thêm lớp học", exact: true })).toHaveCount(0);
});

test("unlinked teacher sees no demo data", async ({ page }) => {
  const state = await mockTeacher(page);
  state.portal = { ...state.portal, linkStatus: "NO_ACTIVE_STAFF_PROFILE", teacher: null, students: [], classes: [], memberships: [] };
  await page.goto("/#/students");
  await expect(page.getByText(/Tài khoản chưa có hồ sơ giáo viên đang hoạt động/)).toBeVisible();
  await expect(page.getByText("Học sinh được giao", { exact: true })).toHaveCount(0);
  await page.goto("/#/groups");
  await expect(page.getByText("Chưa có lớp học phù hợp", { exact: true })).toBeVisible();
});

test("errors never fall back to local data and retry reloads assignments", async ({ page }) => {
  const state = await mockTeacher(page);state.fail = true;
  await page.goto("/#/students");
  await expect(page.getByText("Không thể tải danh sách phụ trách", { exact: true })).toBeVisible();
  await expect(page.getByText("Học sinh được giao", { exact: true })).toHaveCount(0);
  state.fail = false;
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(page.getByText("Học sinh được giao", { exact: true })).toBeVisible();
});

test("refocusing refreshes a revoked assignment", async ({ page }) => {
  const state = await mockTeacher(page);
  await page.goto("/#/groups");
  await expect(page.getByRole("heading", { name: "Nhóm được phân công", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Xem chi tiết", exact: true }).first().click();
  await expect(page.getByRole("dialog").getByText("Học sinh được giao", { exact: true })).toBeVisible();
  state.portal = { ...state.portal, students: [], classes: [], memberships: [] };
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(page.getByRole("heading", { name: "Nhóm được phân công", exact: true })).toHaveCount(0);
  await expect(page.getByText("Chưa có lớp học phù hợp", { exact: true })).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("admin creates a persisted group and its assigned teacher sees the same roster", async ({ page }) => {
  const state = await mockTeacher(page);
  const students = state.portal.students;
  let role = "ADMIN";
  const saved: { id: string; name: string; type: "GROUP"; teacherId: string; capacity: number; status: "ACTIVE"; studentIds: string[] }[] = [];
  state.portal = { ...state.portal, students: [], classes: [], memberships: [] };
  await page.route("**/api/auth/me", route => route.fulfill({ json: { id: "actor", role, displayName: "Giáo viên đăng nhập", email: "actor@example.com" } }));
  await page.route("**/api/student-profiles", route => route.fulfill({ json: students.map(s => ({ ...s, notes: null, guardian: null })) }));
  await page.route("**/api/admin/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/teachers")) return route.fulfill({ json: [{ id: "NV-actual", code: "NV901", fullName: "Giáo viên đăng nhập" }] });
    if (path === "/api/admin/classes" && route.request().method() === "POST") {
      expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
      const row = { ...route.request().postDataJSON(), id: "LH-persisted" };saved.push(row);
      state.portal.students = students;
      state.portal.classes = [row];
      state.portal.memberships = row.studentIds.map((studentId: string) => ({ classId: row.id, code: "LH901", name: row.name, type: row.type, teacherId: row.teacherId, capacity: row.capacity, status: row.status, studentId, startedOn: "2025-01-01", endedOn: null }));
      return route.fulfill({ json: row });
    }
    if (path === "/api/admin/classes") return route.fulfill({ json: saved });
    return route.fulfill({ json: [] });
  });
  await page.goto("/#/groups");
  await page.getByRole("button", { name: "Thêm lớp học", exact: true }).click();
  await page.getByLabel(/Tên lớp học$/).fill("Lớp nhóm lưu trên hệ thống");
  await page.getByRole("combobox", { name: /Giáo viên phụ trách$/ }).press("Enter");
  await page.getByTitle("NV901 · Giáo viên đăng nhập", { exact: true }).click();
  await page.getByRole("combobox", { name: /Học sinh tham gia$/ }).press("Enter");
  await page.getByTitle("HS901 · Học sinh được giao", { exact: true }).click();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Tạo mới", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Lớp nhóm lưu trên hệ thống", exact: true })).toBeVisible();
  expect(saved[0].studentIds).toEqual(["HS-assigned"]);
  role = "TEACHER";
  await page.reload();
  await expect(page.getByRole("heading", { name: "Lớp nhóm lưu trên hệ thống", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Xem chi tiết", exact: true }).click();
  await expect(page.getByRole("dialog").getByText("Học sinh được giao", { exact: true })).toBeVisible();
});
