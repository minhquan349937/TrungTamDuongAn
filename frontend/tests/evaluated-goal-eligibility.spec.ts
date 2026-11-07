import { test, expect } from "@playwright/test";
import { mockIntervention } from "./intervention-fixture";

test("evaluated targets have no replace action and are absent from the add-goal catalog", async ({ page }) => {
  const { state, studentId } = await mockIntervention(page);
  const planId = "eligibility-plan";
  state.plans.push({ plan: { id: planId, studentId, name: "Kế hoạch kiểm tra", startDate: "2026-11-07", status: "ACTIVE" }, goals: [
    { id: "failed", planId, goalId: "GL000001", specificGoalId: null, targetType: "GOAL", targetId: "GL000001", status: "NOT_ACHIEVED", achievementPercent: 60, hasEvaluations: true, criteriaSnapshot: {}, goalSnapshot: { code: "D.1", name: "Đã đánh giá không đạt", description: null, target_type: "GOAL" } },
    { id: "unscored", planId, goalId: "GL000002", specificGoalId: null, targetType: "GOAL", targetId: "GL000002", status: "NOT_STARTED", achievementPercent: null, hasEvaluations: true, criteriaSnapshot: {}, goalSnapshot: { code: "D.2", name: "Đã lưu đánh giá", description: null, target_type: "GOAL" } },
    { id: "untouched", planId, goalId: "GL000003", specificGoalId: null, targetType: "GOAL", targetId: "GL000003", status: "NOT_STARTED", achievementPercent: null, hasEvaluations: false, criteriaSnapshot: {}, goalSnapshot: { code: "D.3", name: "Chưa đánh giá", description: null, target_type: "GOAL" } },
  ] });
  await page.goto(`/#/individual/plan/${studentId}?tab=current`);
  await expect(page.getByRole("button", { name: "Thay mục tiêu", exact: true })).toHaveCount(1);
  await page.getByRole("button", { name: "Thêm mục tiêu vào kế hoạch", exact: true }).click();
  const form = page.locator(".ip-add-goal-form");
  for (let index = 0; index < 3; index++) {
    const select = form.locator(".ant-select").nth(index);
    await select.click();
    await expect(page.locator(".ant-select-dropdown:visible .ant-select-item-option").first()).toBeVisible();
    await select.getByRole("combobox").press("Enter");
    await expect(page.locator(".ant-select-dropdown:visible")).toHaveCount(0);
  }
  await form.getByRole("combobox", { name: "Mục tiêu thêm vào kế hoạch", exact: true }).click();
  await expect(page.getByText("Không còn mục tiêu chưa đánh giá", { exact: true })).toBeVisible();
  await expect(page.locator(".ant-select-dropdown:visible .ant-select-item-option")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Lưu mục tiêu", exact: true })).toBeDisabled();
});
