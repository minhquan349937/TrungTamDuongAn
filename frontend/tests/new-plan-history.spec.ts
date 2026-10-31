import { test, expect } from "@playwright/test";
import { mockIntervention } from "./intervention-fixture";

test("new plans annotate failed, unperformed and new targets and hide achieved main and child goals", async ({ page }) => {
  const { state, studentId } = await mockIntervention(page);
  state.history = [
    { goalId: "GL000001", specificGoalId: null, resultStatus: "NOT_ACHIEVED", achievementPercent: 55, evaluatedAt: "2026-10-28T08:00:00" },
    { goalId: "GL000002", specificGoalId: null, resultStatus: "NOT_STARTED", achievementPercent: null, evaluatedAt: null },
    { goalId: "GL000003", specificGoalId: null, resultStatus: "ACHIEVED", achievementPercent: 80, evaluatedAt: "2026-10-28T08:00:00" },
    { goalId: "GL000005", specificGoalId: null, resultStatus: "ACHIEVED", achievementPercent: 90, evaluatedAt: "2026-10-28T08:00:00" },
    { goalId: "GL000005", specificGoalId: "SG000001", resultStatus: "NOT_ACHIEVED", achievementPercent: 50, evaluatedAt: "2026-10-28T08:00:00" },
    { goalId: "GL000005", specificGoalId: "SG000002", resultStatus: "ACHIEVED", achievementPercent: 85, evaluatedAt: "2026-10-28T08:00:00" },
  ];
  await page.goto(`/#/individual/plan/${studentId}/new/program`);
  await page.getByRole("radio", { name: "Denver", exact: true }).check();
  await page.getByRole("button", { name: "Tiếp tục: Lĩnh vực can thiệp" }).click();
  await page.getByRole("combobox", { name: "Độ tuổi Ngôn ngữ", exact: true }).selectOption("LD0001");
  await expect(page.getByText("Đã học · Tỷ lệ hoàn thành 55%", { exact: true })).toBeVisible();
  await expect(page.getByText("Chưa thực hiện", { exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: "Độ tuổi Ngôn ngữ", exact: true }).selectOption("LD0002");
  await expect(page.getByRole("checkbox", { name: /D\.3/ })).toHaveCount(0);
  await expect(page.getByText("Không còn mục tiêu phù hợp trong độ tuổi này", { exact: true })).toBeVisible();
  await page.getByRole("combobox", { name: "Độ tuổi Nhận thức", exact: true }).selectOption("LD0003");
  await expect(page.getByText("Mục tiêu mới", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Chọn thêm từ chương trình khác" }).click();
  await page.getByRole("radio", { name: "Ngôn ngữ và kỹ năng xã hội", exact: true }).check();
  await page.getByRole("button", { name: "Tiếp tục: Lĩnh vực can thiệp" }).click();
  await page.getByRole("combobox", { name: "Độ tuổi Giao tiếp" }).selectOption("LD0004");
  await expect(page.getByRole("checkbox", { name: /^VB\.1 ·/ })).toHaveCount(0);
  await page.getByRole("button", { name: "Chọn mục tiêu cụ thể · VB.1", exact: true }).click();
  await expect(page.getByRole("checkbox", { name: /VB\.1B/ })).toHaveCount(0);
  await expect(page.getByText("Đã học · Tỷ lệ hoàn thành 50%", { exact: true })).toBeVisible();
  await page.getByRole("checkbox", { name: /VB\.1A/ }).check();
  await page.getByRole("checkbox", { name: /VB\.2/ }).check();
  await page.getByRole("button", { name: "Kiểm tra và hoàn tất" }).click();
  await expect(page.locator(".ip-outline-target")).toHaveCount(2);
});
