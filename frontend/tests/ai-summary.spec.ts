import { test, expect } from "@playwright/test";
import { mockIntervention } from "./intervention-fixture";

test("AI suggestion requires explicit use and replacement confirmation, remains editable and saves through the existing API", async ({ page }) => {
  const { state, studentId } = await mockIntervention(page);
  const planId = "KH000001";
  state.plans.push({ plan: { id: planId, studentId, name: "Kế hoạch AI giả lập", startDate: "2026-10-01", status: "ACTIVE" }, goals: [] });
  let calls = 0;
  await page.route("**/api/learning-plans/*/ai-summary", async route => {
    calls++;
    expect(route.request().method()).toBe("POST");
    expect(route.request().postDataJSON()).toEqual({ keywords: "Hợp tác tốt" });
    await new Promise(resolve => setTimeout(resolve, 300));
    await route.fulfill({ json: { generatedText: "Tiến bộ\nGhi nhận giả lập.\nKhó khăn cần hỗ trợ\nChưa có đánh giá.\nĐịnh hướng hỗ trợ\nĐề xuất để giáo viên xem xét." } });
  });
  await page.goto(`/#/individual/plan/${studentId}?tab=evaluation`);
  await page.getByRole("combobox", { name: "Kế hoạch cần đánh giá" }).click();
  await page.locator(".ant-select-dropdown:visible").getByText(/Kế hoạch AI giả lập/).click();
  await page.locator(".ip-plan-assessment-actions").getByRole("button", { name: "Đánh giá kế hoạch", exact: true }).click();
  const notes = page.getByRole("textbox", { name: "Nhận xét giáo viên về kế hoạch" });
  await notes.fill("Nội dung giáo viên đang viết");
  await page.getByRole("textbox", { name: "Từ khóa / Ghi nhận của giáo viên" }).fill("Hợp tác tốt");
  await page.getByRole("button", { name: "AI viết nhận xét" }).click();
  await expect(page.getByRole("status")).toContainText("AI đang viết nhận xét");
  await expect(page.getByRole("button", { name: "Lưu đánh giá kế hoạch", exact: true })).toBeDisabled();
  const suggestion = page.getByRole("textbox", { name: "Nhận xét AI đề xuất" });
  await expect(suggestion).toHaveValue(/Định hướng hỗ trợ/);
  await suggestion.fill("");
  await expect(suggestion).toBeVisible();
  await expect(page.getByRole("button", { name: "Sử dụng nhận xét này" })).toBeDisabled();
  await suggestion.fill("Gợi ý AI đã được giáo viên sửa trực tiếp.");
  await expect(notes).toHaveValue("Nội dung giáo viên đang viết");
  expect(state.monthlyReports).toHaveLength(0);
  await page.getByRole("button", { name: "Sử dụng nhận xét này" }).click();
  await page.getByRole("button", { name: "Giữ nhận xét hiện tại" }).click();
  await expect(notes).toHaveValue("Nội dung giáo viên đang viết");
  await page.getByRole("button", { name: "Sử dụng nhận xét này" }).click();
  await page.getByRole("button", { name: "Thay thế nhận xét", exact: true }).click();
  await expect(notes).toHaveValue("Gợi ý AI đã được giáo viên sửa trực tiếp.");
  await notes.fill("Giáo viên đã chỉnh sửa nhận xét AI.");
  await page.getByRole("button", { name: "Lưu đánh giá kế hoạch", exact: true }).click();
  await expect(page.locator(".ip-monthly-card")).toContainText("Giáo viên đã chỉnh sửa nhận xét AI.");
  expect(calls).toBe(1);
  expect(state.monthlyReports[0].evaluationNotes).toBe("Giáo viên đã chỉnh sửa nhận xét AI.");
  expect(state.plans[0].plan.status).toBe("ACTIVE");
  expect(state.plans[0].goals).toEqual([]);
});

test("AI errors preserve existing notes and allow a retry with blank keywords", async ({ page }) => {
  const { state, studentId } = await mockIntervention(page);
  state.plans.push({ plan: { id: "KH000002", studentId, name: "Kế hoạch lỗi giả lập", startDate: "2026-10-01", status: "ACTIVE" }, goals: [] });
  let fail = true;
  await page.route("**/api/learning-plans/*/ai-summary", async route => {
    expect(route.request().postDataJSON()).toEqual({ keywords: "" });
    await route.fulfill(fail ? { status: 429, json: { message: "Gemini đã hết hạn mức. Vui lòng thử lại sau." } }
      : { json: { generatedText: "Gợi ý thử lại bằng dữ liệu giả lập" } });
  });
  await page.goto(`/#/individual/plan/${studentId}?tab=evaluation`);
  await page.getByRole("combobox", { name: "Kế hoạch cần đánh giá" }).click();
  await page.locator(".ant-select-dropdown:visible").getByText(/Kế hoạch lỗi giả lập/).click();
  await page.locator(".ip-plan-assessment-actions").getByRole("button", { name: "Đánh giá kế hoạch", exact: true }).click();
  const notes = page.getByRole("textbox", { name: "Nhận xét giáo viên về kế hoạch" });
  await notes.fill("Nhận xét giữ nguyên");
  await page.getByRole("button", { name: "AI viết nhận xét" }).click();
  await expect(page.getByRole("alert")).toContainText("Gemini đã hết hạn mức");
  await expect(notes).toHaveValue("Nhận xét giữ nguyên");
  fail = false;
  await page.getByRole("button", { name: "AI viết nhận xét" }).click();
  await expect(page.getByTestId("ai-suggestion")).toHaveValue(/Gợi ý thử lại/);
  await expect(notes).toHaveValue("Nhận xét giữ nguyên");
  expect(state.monthlyReports).toHaveLength(0);
});
