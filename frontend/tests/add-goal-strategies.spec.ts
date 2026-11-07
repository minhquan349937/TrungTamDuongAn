import { test, expect } from "@playwright/test";
import { mockIntervention } from "./intervention-fixture";

for (const chooseStrategy of [false, true]) {
  test(`add-goal form displays months and saves ${chooseStrategy ? "selected strategies" : "null strategies"}`, async ({ page }) => {
    const { state, studentId } = await mockIntervention(page);
    const planId = "modal-plan";
    state.plans.push({ plan: { id: planId, studentId, name: "Kế hoạch", startDate: "2026-11-07", status: "ACTIVE" }, goals: [] });
    let body: { goalId: string; specificGoalId: string | null; strategies: { strategyId: string; description: string | null }[] | null } | undefined;
    await page.route(`**/api/plans/${planId}/goals`, async route => {
      body = route.request().postDataJSON();
      await route.fulfill({ status: 201, contentType: "application/json", body: "{}" });
    });
    await page.goto(`/#/individual/plan/${studentId}?tab=current`);
    await page.getByRole("button", { name: "Thêm mục tiêu vào kế hoạch", exact: true }).click();
    const form = page.locator(".ip-add-goal-form");
    for (let index = 0; index < 4; index++) {
      const select = form.locator(".ant-select").nth(index);
      await select.click();
      const option = page.locator(".ant-select-dropdown:visible .ant-select-item-option").first();
      await expect(option).toBeVisible();
      if (index === 1) {
        await expect(option).toHaveText("12–24 tháng");
        await expect(form.getByRole("combobox", { name: "Tháng tuổi mục tiêu", exact: true })).toBeVisible();
      }
      await select.getByRole("combobox").press("Enter");
      await expect(page.locator(".ant-select-dropdown:visible")).toHaveCount(0);
    }
    await expect(page.getByRole("button", { name: "Lưu mục tiêu", exact: true })).toBeEnabled();
    if (chooseStrategy) {
      await form.getByRole("combobox", { name: "Chiến lược mục tiêu", exact: true }).click();
      await page.locator(".ant-select-dropdown:visible").getByRole("option", { name: "Làm mẫu", exact: true }).click();
      await form.getByRole("combobox", { name: "Chiến lược mục tiêu", exact: true }).press("Escape");
      await form.getByRole("textbox", { name: "Cách áp dụng Làm mẫu", exact: true }).fill("Cô làm mẫu trước.");
    }
    await page.getByRole("button", { name: "Lưu mục tiêu", exact: true }).click();
    await expect(page.locator(".ip-add-goal-modal")).not.toBeVisible();
    expect(body?.strategies).toEqual(chooseStrategy ? [{ strategyId: "CL001", description: "Cô làm mẫu trước." }] : null);
  });
}
