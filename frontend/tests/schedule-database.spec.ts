import { test, expect, type Page } from "@playwright/test";
import { today } from "../src/utils/format";
import { createSeed } from "../src/mocks/seed";

async function calendar(page: Page) {
  const data = {
    classes: [{ id: "class-real", name: "Lớp đã lưu trong CSDL", type: "INDIVIDUAL", teacherId: "teacher-real", capacity: 1, status: "ACTIVE", startsOn: "2025-01-01", endsOn: null, teacherAvailable: true }],
    staff: [{ id: "teacher-real", name: "Giáo viên phụ trách thật" }], students: [{ id: "student-real", name: "Học sinh trong lớp" }],
    sessions: [] as { id: string; classId: string; teacherId: string; date: string; start: string; end: string; room: string; studentIds: string[]; attendance: Record<string, string> }[],
  };
  const state = { data, failLoad: false, failSave: false, saves: 0, attendanceSaves: 0, role: "ADMIN" };
  await page.addInitScript(seed => { localStorage.setItem("duongan.demo.cleared.v1", "1"); localStorage.setItem("duongan.demo.v1", JSON.stringify(seed)); }, createSeed());
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/csrf") return route.fulfill({ json: { token: "csrf" } });
    if (path === "/api/auth/me") return route.fulfill({ json: { id: "actor", displayName: "Giáo viên phụ trách thật", role: state.role, email: "actor@example.com" } });
    if (path === "/api/teacher/portal") return route.fulfill({ json: { linkStatus: "READY", teacher: { id: "teacher-real", fullName: "Giáo viên phụ trách thật" }, students: [], classes: [], memberships: [], sessions: [], attendance: [], notices: [] } });
    if (path === "/api/schedule") return route.fulfill(state.failLoad ? { status: 503, json: { message: "Không thể tải lịch từ máy chủ." } } : { json: state.data });
    if (path.endsWith("/attendance")) {
      expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
      state.attendanceSaves++;state.data.sessions[0].attendance = route.request().postDataJSON().attendance;
      return route.fulfill({ status: 204 });
    }
    if (path.startsWith("/api/schedule/sessions")) {
      expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
      state.saves++;
      if (state.failSave) return route.fulfill({ status: 409, json: { message: "Giáo viên đã có lịch dạy trùng giờ." } });
      const input = route.request().postDataJSON();
      state.data.sessions = [{ id: "session-persisted", ...input, teacherId: "teacher-real", studentIds: ["student-real"], attendance: state.data.sessions[0]?.attendance ?? {} }];
      return route.fulfill({ json: { id: "session-persisted" } });
    }
    return route.fulfill({ json: [] });
  });
  return state;
}
async function fillLesson(page: Page) {
  await page.getByRole("button", { name: "Tạo lịch học", exact: true }).click();
  await page.getByRole("combobox", { name: /Lớp học$/ }).press("Enter");
  await page.getByTitle("Lớp đã lưu trong CSDL · Giáo viên phụ trách thật", { exact: true }).click();
  await page.getByLabel(/Bắt đầu$/).fill("08:00");
  await page.getByLabel(/Kết thúc$/).fill("09:00");
  await page.getByLabel(/Phòng học$/).fill("Phòng học đã chọn");
}

test("real class options, create, reload and reschedule all use the API without local schedule data", async ({ page }) => {
  const state = await calendar(page);
  await page.goto("/#/schedule");
  await fillLesson(page);
  await expect(page.getByRole("dialog").getByRole("textbox", { name: "Giáo viên phụ trách", exact: true })).toHaveValue("Giáo viên phụ trách thật");
  await page.getByRole("button", { name: "Lưu lịch học", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Lớp đã lưu trong CSDL", exact: true })).toBeVisible();
  expect(state.saves).toBe(1);expect(state.data.sessions[0].date).toBe(today());
  await page.reload();
  await expect(page.getByRole("heading", { name: "Lớp đã lưu trong CSDL", exact: true })).toBeVisible();
  await page.getByRole("button", { name: `Đổi lịch Lớp đã lưu trong CSDL ${today()}`, exact: true }).click();
  await page.getByLabel(/Bắt đầu$/).fill("10:00");await page.getByLabel(/Kết thúc$/).fill("11:00");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.locator(".calendar-event")).toContainText("10:00 – 11:00");expect(state.saves).toBe(2);
});

test("conflict preserves entered values and a successful retry saves", async ({ page }) => {
  const state = await calendar(page);state.failSave = true;
  await page.goto("/#/schedule");await fillLesson(page);
  await page.getByRole("button", { name: "Lưu lịch học", exact: true }).click();
  await expect(page.getByText("Giáo viên đã có lịch dạy trùng giờ.", { exact: true })).toBeVisible();
  await expect(page.getByLabel(/Phòng học$/)).toHaveValue("Phòng học đã chọn");
  state.failSave = false;
  await page.getByRole("button", { name: "Lưu lịch học", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("teacher attendance saves to API and is retained after page reload", async ({ page }) => {
  const state = await calendar(page);state.role = "TEACHER";
  state.data.sessions = [{ id: "session-persisted", classId: "class-real", teacherId: "teacher-real", date: today(), start: "08:00:00", end: "09:00:00", room: "Phòng học đã chọn", studentIds: ["student-real"], attendance: {} }];
  await page.goto("/#/attendance");
  await expect(page.getByRole("button", { name: "Tạo lịch học", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Điểm danh", exact: true }).click();
  await page.getByRole("button", { name: "Đánh dấu tất cả có mặt", exact: true }).click();
  await page.getByRole("button", { name: "Lưu điểm danh", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);expect(state.attendanceSaves).toBe(1);
  expect(state.data.sessions[0].attendance).toEqual({ "student-real": "PRESENT" });
  await page.reload();await page.getByRole("button", { name: "Điểm danh", exact: true }).click();
  await expect(page.getByRole("dialog").locator(".ant-select-selection-item")).toHaveText("Có mặt");
});

test("API failure offers retry and never falls back to demo class options", async ({ page }) => {
  const state = await calendar(page);state.failLoad = true;
  await page.goto("/#/schedule");
  await expect(page.getByText("Không thể tải lịch từ máy chủ.", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Tạo lịch học", exact: true })).toBeDisabled();
  state.failLoad = false;
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await fillLesson(page);
  await expect(page.getByRole("dialog").getByRole("combobox", { name: /Lớp học$/ })).toBeVisible();
});
