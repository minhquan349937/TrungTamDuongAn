import { test, expect } from "@playwright/test";
import { mockIntervention } from "./intervention-fixture";

test("five pages select multiple goals across fields and ages, then save one plan", async ({
  page,
}) => {
  const { state, studentId } = await mockIntervention(page);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("/#/individual/plan");
  await expect(
    page.getByRole("heading", { name: "Kế hoạch can thiệp", exact: true }),
  ).toBeVisible();
  await expect(page.locator("tbody tr")).toHaveCount(2);
  await expect(page.getByText("3 tuổi 9 tháng")).toBeVisible();
  await page.screenshot({
    path: "test-results/plan-01-students.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: /Học sinh kiểm thử An/ }).click();
  await expect(page).toHaveURL(new RegExp(`${studentId}$`));
  await expect(page.locator(".ip-tabs button")).toHaveCount(5);
  await page.getByRole("button", { name: "Tạo kế hoạch học tập", exact: true }).click();
  await expect(page).toHaveURL(/\/new\/program$/);
  await expect(
    page.getByRole("button", { name: "Tiếp tục: Lĩnh vực can thiệp" }),
  ).toBeDisabled();
  await page.getByRole("radio", { name: "Denver", exact: true }).check();
  await page.screenshot({
    path: "test-results/plan-03-programs.png",
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Tiếp tục: Lĩnh vực can thiệp" })
    .click();
  await expect(page).toHaveURL(/\/new\/targets$/);
  await expect(
    page.getByRole("button", { name: "Kiểm tra và hoàn tất" }),
  ).toBeDisabled();
  await page
    .getByRole("combobox", { name: "Độ tuổi Ngôn ngữ", exact: true })
    .selectOption("LD0001");
  await page.getByRole("checkbox", { name: /D\.1/ }).check();
  await page.getByRole("checkbox", { name: /D\.2/ }).check();
  await page
    .getByRole("combobox", { name: "Độ tuổi Ngôn ngữ", exact: true })
    .selectOption("LD0002");
  await page.getByRole("checkbox", { name: /D\.3/ }).check();
  await page
    .getByRole("combobox", { name: "Độ tuổi Nhận thức", exact: true })
    .selectOption("LD0003");
  await page.getByRole("checkbox", { name: /D\.4/ }).check();
  await expect(
    page.getByText("4 mục tiêu đã chọn", { exact: true }),
  ).toBeVisible();
  expect(state.completeCalls).toBe(0);
  expect(state.childRequests).toBe(0);
  await expect(page.getByText("Mức độ ưu tiên", { exact: true })).toHaveCount(
    0,
  );
  await expect(
    page.getByRole("button", { name: /Chọn mục tiêu cụ thể/ }),
  ).toHaveCount(0);
  await page.screenshot({
    path: "test-results/plan-04-fields.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Kiểm tra và hoàn tất" }).click();
  await expect(page).toHaveURL(/\/new\/review$/);
  await expect(page.locator(".ip-outline-domain")).toHaveCount(2);
  await expect(page.locator(".ip-outline-target")).toHaveCount(4);
  expect(state.plans).toHaveLength(0);
  await page.getByRole("button", { name: "Quay lại lựa chọn" }).click();
  await expect(
    page.getByRole("combobox", { name: "Độ tuổi Ngôn ngữ", exact: true }),
  ).toHaveValue("LD0002");
  await expect(page.getByRole("checkbox", { name: /D\.3/ })).toBeChecked();
  await page
    .getByRole("combobox", { name: "Độ tuổi Ngôn ngữ", exact: true })
    .selectOption("LD0001");
  await expect(page.getByRole("checkbox", { name: /D\.1/ })).toBeChecked();
  await expect(page.getByRole("checkbox", { name: /D\.2/ })).toBeChecked();
  await page.getByRole("checkbox", { name: /D\.1/ }).check();
  await page.getByRole("checkbox", { name: /D\.2/ }).check();
  await page.getByRole("button", { name: "Kiểm tra và hoàn tất" }).click();
  await expect(
    page.getByRole("button", { name: "Hoàn thành", exact: true }),
  ).toBeEnabled();
  await page.screenshot({
    path: "test-results/plan-05-review.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Hoàn thành", exact: true }).click();
  await expect(page).toHaveURL(/\?tab=current$/);
  await expect(
    page.getByText(/Đã lưu Kế hoạch học tập với 4 mục tiêu/),
  ).toBeVisible();
  expect(state.plans).toHaveLength(1);
  expect(state.plans[0].goals).toHaveLength(4);
  expect(state.completeCalls).toBe(1);
  await page.reload();
  await expect(page.locator(".ip-outline-target")).toHaveCount(4);
  await page.getByRole("button", { name: "Danh sách học sinh" }).click();
  await expect(page.locator("tbody tr").first()).toContainText("4 mục tiêu");
  await page
    .getByRole("textbox", { name: "Tìm học sinh", exact: true })
    .fill("Bình");
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await expect(page.locator("tbody tr")).toContainText("0 mục tiêu");
});
