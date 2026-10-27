import { test, expect } from "@playwright/test";
import { mockIntervention } from "./intervention-fixture";

test("VB loads children on demand and a lost finish response retries the same plan", async ({
  page,
}) => {
  const { state, studentId } = await mockIntervention(page);
  const base = `/#/individual/plan/${studentId}`;
  await page.goto(`${base}?tab=bank`);
  await page
    .getByRole("button", { name: "Gợi ý mục tiêu", exact: true })
    .click();
  await page
    .getByRole("radio", { name: "Ngôn ngữ và kỹ năng xã hội", exact: true })
    .check();
  await page
    .getByRole("button", { name: "Tiếp tục: Lĩnh vực can thiệp" })
    .click();
  await page
    .getByRole("combobox", { name: "Độ tuổi Giao tiếp" })
    .selectOption("LD0004");
  expect(state.childRequests).toBe(0);
  await expect(page.getByRole("checkbox", { name: /VB\.1 / })).toHaveCount(0);
  await page
    .getByRole("button", { name: "Chọn mục tiêu cụ thể · VB.1", exact: true })
    .click();
  await page.getByRole("checkbox", { name: /VB\.1A/ }).check();
  await page.getByRole("checkbox", { name: /VB\.1B/ }).check();
  await page.getByRole("checkbox", { name: /VB\.2/ }).check();
  expect(state.childRequests).toBe(1);
  await expect(
    page.getByText("3 mục tiêu đã chọn", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Kiểm tra và hoàn tất" }).click();
  await expect(page.locator(".ip-outline-target")).toHaveCount(3);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(() =>
      page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    )
    .toBe(true);
  await page.screenshot({
    path: "test-results/plan-vb-mobile-review.png",
    fullPage: true,
  });
  state.loseNextResponse = true;
  await page.getByRole("button", { name: "Hoàn thành", exact: true }).click();
  await expect(
    page.getByText("Thử lại yêu cầu hoàn thành.", { exact: true }),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/new\/review$/);
  await page.getByRole("button", { name: "Hoàn thành", exact: true }).click();
  await expect(page).toHaveURL(/\?tab=current$/);
  expect(state.completeCalls).toBe(2);
  expect(state.plans).toHaveLength(1);
  await expect(page.locator(".ip-outline-target")).toHaveCount(3);
  await page.getByRole("button", { name: "Chỉnh sửa kế hoạch", exact: true }).click();
  await page.getByLabel("Tên kế hoạch", { exact: true }).fill("Kế hoạch VB đã sửa");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Kế hoạch VB đã sửa", exact: true })).toBeVisible();
  const observationsLoaded = page.waitForResponse(response => response.url().endsWith("/progress") && response.request().method() === "GET");
  await page.getByRole("button", { name: "Theo dõi tiến độ", exact: true }).first().click();
  await observationsLoaded;
  await page.getByRole("textbox", { name: "Ghi chú buổi học", exact: true }).fill("Trẻ đã chủ động giao tiếp bằng mắt.");
  await page.getByRole("button", { name: "Lưu quan sát", exact: true }).click();
  await expect(page.locator(".ip-observation")).toContainText("Trẻ đã chủ động giao tiếp bằng mắt.");
  await page.locator(".ant-modal-close").click();
  await page
    .locator(".ip-tabs")
    .getByRole("button", { name: "Lịch sử kế hoạch", exact: true })
    .click();
  await expect(page.locator(".ip-plan")).toHaveCount(1);
  await page
    .locator(".ip-tabs")
    .getByRole("button", { name: "Mục tiêu đã hoàn thành", exact: true })
    .click();
  await expect(
    page.getByText("Chưa có mục tiêu trong mục này", { exact: true }),
  ).toBeVisible();
  await page.goto(`${base}/new/review`);
  await expect(page).toHaveURL(/\/new\/program$/);
});
