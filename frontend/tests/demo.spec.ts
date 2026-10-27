import { test, expect } from "@playwright/test";
import { adminMenu } from "../src/routes/menu";
import { createSeed } from "../src/mocks/seed";
test.beforeEach(async ({ page }) => {
  let currentRole: "ADMIN" | "TEACHER" | "PARENT" | null = null;
  await page.route("**/api/auth/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/auth/csrf") {
      await route.fulfill({ json: { token: "test-csrf" } });
      return;
    }
    if (path === "/api/auth/login") {
      const email = route.request().postDataJSON().email as string;
      currentRole = email.startsWith("admin") ? "ADMIN" : email.startsWith("teacher") ? "TEACHER" : "PARENT";
    } else if (path === "/api/auth/logout") {
      currentRole = null;
      await route.fulfill({ status: 204 });
      return;
    }
    if (!currentRole) {
      await route.fulfill({ status: 401, json: { message: "Unauthenticated" } });
      return;
    }
    await route.fulfill({ json: {
      id: "00000000-0000-0000-0000-000000000101",
      email: `${currentRole.toLowerCase()}@duongan.local`,
      displayName: currentRole,
      role: currentRole,
    } });
  });
  await page.addInitScript((seed) => {
    if (localStorage.getItem("duongan.demo.cleared.v1") !== "1") {
      localStorage.setItem("duongan.demo.cleared.v1", "1");
      localStorage.setItem("duongan.demo.v1", JSON.stringify(seed));
    }
  }, createSeed());
  page.on("pageerror", (error) => {
    throw error;
  });
  await loginAs(page, "Quản trị viên");
});
const roleAccounts = {
  "Quản trị viên": ["admin@duongan.local", "Admin123!"],
  "Giáo viên": ["teacher@duongan.local", "Teacher123!"],
  "Phụ huynh": ["parent@duongan.local", "Parent123!"],
} as const;
async function loginAs(page: import("@playwright/test").Page, role: keyof typeof roleAccounts) {
  const [email, password] = roleAccounts[role];
  await page.goto("/#/login");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  const csrfRefresh = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/auth/csrf" && response.request().method() === "GET"
  );
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await csrfRefresh;
  await expect(page).toHaveURL(/#\/dashboard/);
}
async function selectRole(
  page: import("@playwright/test").Page,
  label: string,
) {
  await page.locator(".account-button").click();
  await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
  await loginAs(page, label as keyof typeof roleAccounts);
}
test("dashboard, menus, search, filters, pagination and responsive layout", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /Một ngày mới/ }),
  ).toBeVisible();
  await expect(page.locator(".nav-parent")).toHaveCount(8);
  await expect(page.locator(".stat-card")).toHaveCount(4);
  await page.screenshot({
    path: "test-results/dashboard-desktop.png",
    fullPage: true,
  });
  const panel = page.locator(".data-panel").first();
  await panel.getByRole("button", { name: "Trang sau" }).click();
  await expect(
    panel.getByText("Phạm Minh Khang", { exact: true }),
  ).toBeVisible();
  await panel.getByRole("textbox").fill("Minh An");
  await expect(panel.locator("tbody tr")).toHaveCount(1);
  await panel.getByRole("textbox").fill("không tồn tại");
  await expect(panel.getByText("Không tìm thấy dữ liệu phù hợp")).toBeVisible();
  await panel.getByRole("textbox").fill("");
  await panel.locator(".ant-select").click();
  await page
    .locator(".ant-select-item-option-content")
    .getByText("Tạm nghỉ", { exact: true })
    .click();
  await expect(panel.locator("tbody tr")).toHaveCount(2);
  await page.getByRole("button", { name: "Thu gọn hoặc mở sidebar" }).click();
  await expect(page.locator(".app-shell")).toHaveClass(/collapsed/);
  await page.getByRole("button", { name: "Thu gọn hoặc mở sidebar" }).click();
  await page
    .getByRole("button", { name: "Quản lý hồ sơ", exact: true })
    .click();
  await page.getByRole("link", { name: "Hồ sơ học sinh", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Hồ sơ học sinh", exact: true }).first(),
  ).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/#/dashboard");
  await page.getByRole("button", { name: "Mở menu", exact: true }).click();
  await expect(page.locator(".sidebar")).toBeInViewport();
  await page.locator(".sidebar-scrim").click({ position: { x: 380, y: 400 } });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await expect(page.locator(".sidebar")).not.toBeInViewport();
  await page.screenshot({
    path: "test-results/dashboard-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(errors).toEqual([]);
});
test("student CRUD persists and linked profile opens", async ({ page }) => {
  await page.goto("/#/students");
  await page
    .getByRole("button", { name: "Thêm học sinh", exact: true })
    .click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Học sinh kiểm thử");
  await page.getByLabel("Ngày sinh", { exact: true }).fill("2021-06-15");
  await page.getByRole("button", { name: "Tạo mới", exact: true }).click();
  await expect(
    page.getByText("Đã thêm học sinh", { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("textbox", { name: /Tìm kiếm/ })
    .fill("Học sinh kiểm thử");
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.reload();
  await page
    .getByRole("textbox", { name: /Tìm kiếm/ })
    .fill("Học sinh kiểm thử");
  await expect(
    page.getByRole("button", { name: "Học sinh kiểm thử", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Sửa Học sinh kiểm thử", exact: true })
    .click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Học sinh đã sửa");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await page.getByRole("textbox", { name: /Tìm kiếm/ }).fill("Học sinh đã sửa");
  await page
    .getByRole("button", { name: "Xem Học sinh đã sửa", exact: true })
    .click();
  await page.getByRole("tab", { name: "Phụ huynh liên kết" }).click();
  await expect(
    page.getByText("ph1@example.invalid", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await page
    .getByRole("button", { name: "Xóa Học sinh đã sửa", exact: true })
    .click();
  await page.getByRole("button", { name: "Xóa hồ sơ", exact: true }).click();
  await expect(page.getByText("Không tìm thấy dữ liệu phù hợp")).toBeVisible();
});
test("staff creation and class assignments", async ({ page }) => {
  await page.goto("/#/staff");
  await page.getByRole("button", { name: "Thêm nhân viên" }).click();
  await page
    .getByLabel("Họ và tên", { exact: true })
    .fill("Giáo viên kiểm thử");
  await page.getByLabel("Chuyên môn", { exact: true }).click();
  await page
    .locator(".ant-select-item-option-content")
    .getByText("Giáo dục đặc biệt", { exact: true })
    .click();
  await page.getByRole("button", { name: "Tạo mới", exact: true }).click();
  await expect(
    page.getByText("Đã thêm nhân viên", { exact: true }),
  ).toBeVisible();
  await page.goto("/#/groups");
  await page.getByRole("button", { name: "Thêm lớp học" }).click();
  await page.getByLabel("Tên lớp học").fill("Lớp kiểm thử");
  await page.getByLabel("Sức chứa tối đa").fill("5");
  await page.getByLabel("Học sinh tham gia").click();
  await page
    .locator(".ant-select-item-option-content")
    .getByText("Nguyễn Minh An", { exact: true })
    .click();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Tạo mới", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Lớp kiểm thử", exact: true }),
  ).toBeVisible();
  const card = page.locator(".class-card").filter({ hasText: "Lớp kiểm thử" });
  await card.getByRole("button", { name: "Xem chi tiết" }).click();
  await expect(page.getByText("Nguyễn Minh An", { exact: true })).toBeVisible();
});
test("schedule conflict, reschedule, attendance, leave approval and payment", async ({
  page,
}) => {
  await page.goto("/#/schedule");
  await page.getByRole("button", { name: "Tạo lịch học" }).click();
  await page.getByLabel("Lớp học (giáo viên theo phân công của lớp)").click();
  await page
    .locator(".ant-select-item-option-content")
    .filter({ hasText: "Mầm Xanh 01" })
    .click();
  await page.getByLabel("Ngày học", { exact: true }).fill("2026-12-01");
  await page.getByLabel("Phòng học", { exact: true }).fill("Phòng kiểm thử");
  await page.getByRole("button", { name: "Tạo mới", exact: true }).click();
  await expect(
    page.getByText("Đã thêm lịch học", { exact: true }),
  ).toBeVisible();
  await page.getByLabel("Ngày xem lịch", { exact: true }).fill("2026-12-01");
  await expect(
    page.getByRole("heading", { name: "Mầm Xanh 01", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Tạo lịch học" }).click();
  await page.getByLabel("Lớp học (giáo viên theo phân công của lớp)").click();
  await page
    .locator(".ant-select-item-option-content")
    .filter({ hasText: "Mầm Xanh 01" })
    .click();
  await page.getByLabel("Ngày học", { exact: true }).fill("2026-12-01");
  await page.getByLabel("Phòng học", { exact: true }).fill("Phòng khác");
  await page.getByRole("button", { name: "Tạo mới", exact: true }).click();
  await expect(page.getByText(/Trùng lịch: giáo viên/)).toBeVisible();
  await page.getByRole("button", { name: "Hủy", exact: true }).click();
  await page.getByRole("button", { name: /Đổi lịch Mầm Xanh 01/ }).click();
  await page.getByLabel("Bắt đầu", { exact: true }).fill("10:00");
  await page.getByLabel("Kết thúc", { exact: true }).fill("11:00");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page.getByText("10:00 – 11:00", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Điểm danh", exact: true }).click();
  await page.getByRole("button", { name: "Đánh dấu tất cả có mặt" }).click();
  await page.getByRole("button", { name: "Lưu điểm danh" }).click();
  await expect(
    page.getByText("Đã lưu điểm danh", { exact: true }),
  ).toBeVisible();
  await selectRole(page, "Giáo viên");
  await page.goto("/#/leave");
  await page
    .getByRole("button", { name: "Đăng ký nghỉ phép", exact: true })
    .click();
  await page
    .getByLabel("Lý do nghỉ phép", { exact: true })
    .fill("Đơn kiểm thử");
  await page.getByRole("button", { name: "Tạo mới", exact: true }).click();
  await expect(page.getByText("Đơn kiểm thử", { exact: true })).toBeVisible();
  await selectRole(page, "Quản trị viên");
  await page.goto("/#/leave");
  await page
    .locator("tr")
    .filter({ hasText: "Đơn kiểm thử" })
    .getByRole("button", { name: "Duyệt", exact: true })
    .click();
  await expect(
    page
      .locator("tr")
      .filter({ hasText: "Đơn kiểm thử" })
      .getByText("Đã duyệt"),
  ).toBeVisible();
  await page.goto("/#/fees");
  const fee = page.locator("tr").filter({ hasText: "Nguyễn Minh An" });
  await fee.getByRole("button", { name: "Ghi nhận thu" }).click();
  await page.getByRole("button", { name: "Xác nhận đã thu" }).click();
  await expect(fee.getByText("Đã thanh toán", { exact: true })).toBeVisible();
});
test("teacher and parent authorization, child switch and check-in", async ({
  page,
}) => {
  await page.goto("/");
  await selectRole(page, "Giáo viên");
  await expect(
    page.getByRole("heading", { name: /Chào cô Thu Hà/ }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Chấm công hôm nay", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Đã chấm công hôm nay" }),
  ).toBeDisabled();
  await page.goto("/#/fees");
  await expect(
    page.getByText("Bạn không có quyền truy cập", { exact: true }),
  ).toBeVisible();
  await selectRole(page, "Phụ huynh");
  await expect(
    page.getByRole("heading", { name: /Cùng con lớn lên mỗi ngày/ }),
  ).toBeVisible();
  await page.goto("/#/students");
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await expect(page.getByRole("button", { name: "Thêm học sinh" })).toHaveCount(
    0,
  );
  await page.locator(".child-selector .ant-select").click();
  await page
    .locator(".ant-select-item-option-content")
    .getByText("Trần Gia Bảo", { exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Trần Gia Bảo", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Nguyễn Minh An", exact: true }),
  ).toHaveCount(0);
  await page.goto("/#/staff");
  await expect(
    page.getByText("Bạn không có quyền truy cập", { exact: true }),
  ).toBeVisible();
});
test("all admin menu routes, chart rendering and tuition creation", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator(".recharts-pie-sector")).toHaveCount(3);
  await expect(page.locator(".recharts-area-curve")).toHaveCount(2);
  for (const group of adminMenu) {
    for (const item of group.items) {
      await page.goto("/#" + item.path);
      await expect(page.locator("main h1")).toBeVisible();
      await expect(
        page.getByText("Bạn không có quyền truy cập", { exact: true }),
      ).toHaveCount(0);
    }
  }
  await page.goto("/#/fees");
  await page
    .getByRole("button", { name: "Tạo khoản thu", exact: true })
    .click();
  await page.getByLabel("Học sinh", { exact: true }).click();
  await page
    .locator(".ant-select-item-option-content")
    .getByText("Nguyễn Minh An", { exact: true })
    .click();
  await page
    .getByLabel("Nội dung khoản thu", { exact: true })
    .fill("Khoản thu kiểm thử");
  await page.getByRole("button", { name: "Tạo mới", exact: true }).click();
  await expect(
    page.getByRole("cell", { name: /Khoản thu kiểm thử/ }),
  ).toBeVisible();
});
