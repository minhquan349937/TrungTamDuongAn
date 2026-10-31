import { test, expect } from "@playwright/test";
import { mockIntervention } from "./intervention-fixture";

test("one plan keeps Denver selections when adding VB targets", async ({ page }) => {
  const { state, studentId } = await mockIntervention(page);
  await page.goto(`/#/individual/plan/${studentId}`);
  await page.getByRole("button", { name: "Tạo kế hoạch học tập", exact: true }).click();
  await page.getByRole("radio", { name: "Denver", exact: true }).check();
  await page.getByRole("button", { name: "Tiếp tục: Lĩnh vực can thiệp" }).click();
  await page.getByRole("combobox", { name: "Độ tuổi Ngôn ngữ", exact: true }).selectOption("LD0001");
  await page.getByRole("checkbox", { name: /D\.1/ }).check();
  await page.getByRole("button", { name: "Chọn thêm từ chương trình khác" }).click();
  await page.getByRole("radio", { name: "Ngôn ngữ và kỹ năng xã hội", exact: true }).check();
  await page.getByRole("button", { name: "Tiếp tục: Lĩnh vực can thiệp" }).click();
  await page.getByRole("combobox", { name: "Độ tuổi Giao tiếp" }).selectOption("LD0004");
  await page.getByRole("checkbox", { name: /VB\.2/ }).check();
  await expect(page.getByText("2 mục tiêu đã chọn", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Kiểm tra và hoàn tất" }).click();
  await expect(page.locator(".ip-outline-domain")).toHaveCount(2);
  await page.getByRole("button", { name: "Hoàn thành", exact: true }).click();
  await expect(page).toHaveURL(/\?tab=current$/);
  expect(state.plans).toHaveLength(1);
  expect(state.plans[0].goals.map(g => g.goalSnapshot.program_name)).toEqual(["Denver", "Ngôn ngữ và kỹ năng xã hội"]);
});

test("VB loads children on demand and a lost finish response retries the same plan", async ({
  page,
}) => {
  const { state, studentId } = await mockIntervention(page);
  const base = `/#/individual/plan/${studentId}`;
  await page.goto(`${base}?tab=current`);
  await page
    .getByRole("button", { name: "Tạo kế hoạch học tập", exact: true })
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
  await expect(page.getByRole("checkbox", { name: /VB\.1 / })).toHaveCount(1);
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
  await expect(page.getByRole("button", { name: "Đánh giá / Lịch sử", exact: true })).toHaveCount(0);
  await page.locator(".ip-tabs").getByRole("button", { name: "Đánh giá kế hoạch", exact: true }).click();
  await page.getByRole("combobox", { name: "Kế hoạch cần đánh giá" }).click();
  await page.locator(".ant-select-dropdown:visible").getByText(/Kế hoạch VB đã sửa/).click();
  await page.getByRole("button", { name: "Đánh giá / Lịch sử", exact: true }).first().click();
  await expect(page.getByRole("heading", { name: "Lịch sử đánh giá (0)", exact: true })).toBeVisible();
  await page.getByRole("spinbutton", { name: "Tỷ lệ hoàn thành", exact: true }).fill("79.99");
  await page.getByRole("textbox", { name: "Nhận xét giáo viên", exact: true }).fill("Trẻ đã chủ động giao tiếp bằng mắt.");
  await page.getByRole("button", { name: "Lưu đánh giá", exact: true }).click();
  await expect(page.locator(".ip-observation")).toContainText("Không đạt · 79.99%");
  await page.getByRole("button", { name: "Đánh giá lại", exact: true }).click();
  await page.getByRole("spinbutton", { name: "Tỷ lệ hoàn thành", exact: true }).fill("80");
  await page.getByRole("button", { name: "Lưu đánh giá", exact: true }).click();
  await expect(page.locator(".ip-observation")).toHaveCount(2);
  await expect(page.locator(".ip-observation").first()).toContainText("Lần 2: Đạt · 80%");
  await page.locator(".ant-modal:visible .ant-modal-close").click();
  await expect(page.locator(".ip-progress-row").first()).toContainText("Tỷ lệ hoàn thành: 80%");
  await page
    .locator(".ip-tabs")
    .getByRole("button", { name: "Lịch sử kế hoạch", exact: true })
    .click();
  await expect(page.locator(".ip-history-table tbody tr")).toHaveCount(1);
  await expect(page.getByRole("button", { name: "Đánh giá / Lịch sử", exact: true })).toHaveCount(0);
  await page
    .locator(".ip-tabs")
    .getByRole("button", { name: "Mục tiêu đã hoàn thành", exact: true })
    .click();
  await expect(page.locator(".ip-outline-target")).toHaveCount(1);
  await page.goto(`${base}/new/review`);
  await expect(page).toHaveURL(/\/new\/program$/);
});
