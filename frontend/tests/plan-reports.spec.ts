import { test, expect } from "@playwright/test";
import { mockIntervention } from "./intervention-fixture";

test("draft monthly comment can be revised and then published without closing the plan", async ({ page }) => {
  const { state, studentId } = await mockIntervention(page);
  const planId = "00000000-0000-0000-0000-000000000099";
  state.plans.push({ plan: { id: planId, studentId, name: "Kế hoạch tháng 10", startDate: "2026-10-01", status: "ACTIVE" }, goals: [{
    id: "assignment-1", planId, goalId: "GL000001", specificGoalId: null, targetType: "GOAL", targetId: "GL000001",
    status: "NOT_ACHIEVED", achievementPercent: 55, criteriaSnapshot: {}, goalSnapshot: { code: "D.1", name: "Định vị âm thanh", description: null,
      target_type: "GOAL", program_name: "Denver", level_name: "Cấp độ 1", domain_name: "Ngôn ngữ" },
  }] });
  state.plans.push({ plan: { id: "old-plan", studentId, name: "Kế hoạch tháng 9", startDate: "2026-09-01", status: "COMPLETED" }, goals: [] });
  await page.goto(`/#/individual/plan/${studentId}?tab=current`);
  await expect(page.locator(".ip-progress-row")).toHaveCount(0);
  await expect(page.locator(".ip-outline-target")).toHaveCount(1);
  await expect(page.locator(".ip-outline-target")).toContainText("Không đạt");
  await expect(page.locator(".ip-outline-target")).toContainText("Tỷ lệ hoàn thành: 55%");
  await expect(page.getByRole("button", { name: "Đánh giá / Lịch sử" })).toHaveCount(0);
  await expect(page.locator(".ip-monthly-reports")).toHaveCount(0);
  await page.locator(".ip-tabs").getByRole("button", { name: "Lịch sử kế hoạch", exact: true }).click();
  await expect(page.locator(".ip-history-table tbody tr")).toHaveCount(2);
  await expect(page.getByRole("button", { name: "Đánh giá / Lịch sử" })).toHaveCount(0);
  await expect(page.locator(".ip-monthly-reports")).toHaveCount(0);
  await page.locator(".ip-tabs").getByRole("button", { name: "Đánh giá kế hoạch", exact: true }).click();
  await page.getByRole("combobox", { name: "Kế hoạch cần đánh giá" }).click();
  await expect(page.locator(".ant-select-dropdown:visible")).toContainText("Kế hoạch tháng 9");
  await page.locator(".ant-select-dropdown:visible").getByText(/Kế hoạch tháng 9/).click();
  await expect(page.locator(".ip-assessment-plan")).toContainText("Kế hoạch chưa có mục tiêu");
  await page.getByRole("combobox", { name: "Kế hoạch cần đánh giá" }).focus();
  await page.getByRole("combobox", { name: "Kế hoạch cần đánh giá" }).press("ArrowDown");
  await page.locator(".ant-select-dropdown:visible").getByText(/Kế hoạch tháng 10/).click();
  await expect(page.locator(".ip-progress-row")).toContainText("Tỷ lệ hoàn thành: 55%");
  await page.locator(".ip-plan-assessment-actions").getByRole("button", { name: "Đánh giá kế hoạch", exact: true }).click();
  await expect(page.getByRole("textbox", { name: "Tên kế hoạch đánh giá", exact: true })).toHaveValue("Kế hoạch tháng 10");
  await expect(page.getByRole("textbox", { name: "Tên học sinh đánh giá", exact: true })).toHaveValue("Học sinh kiểm thử An");
  await expect(page.getByLabel("Ngày áp dụng kế hoạch", { exact: true })).toHaveValue("2026-10-01");
  await expect(page.getByLabel("Ngày đánh giá gần nhất", { exact: true })).toHaveValue("Chưa có đánh giá trước đó");
  await expect(page.getByRole("textbox", { name: "Tên người đánh giá", exact: true })).toHaveValue("Test admin");
  await page.getByRole("textbox", { name: "Nhận xét giáo viên về kế hoạch" }).fill("Con tiến bộ trong giao tiếp.");
  await page.getByRole("button", { name: "Lưu đánh giá kế hoạch", exact: true }).click();
  await expect(page.locator(".ip-monthly-card")).toContainText("Bản nháp");
  await page.locator(".ip-plan-assessment-actions").getByRole("button", { name: "Chỉnh sửa đánh giá", exact: true }).click();
  await expect(page.getByLabel("Ngày đánh giá gần nhất", { exact: true })).not.toHaveValue("Chưa có đánh giá trước đó");
  await page.getByRole("textbox", { name: "Nhận xét giáo viên về kế hoạch" }).fill("Con tiến bộ trong giao tiếp và tương tác.");
  await page.getByRole("button", { name: "Lưu đánh giá kế hoạch", exact: true }).click();
  await page.getByRole("button", { name: "Công bố trong cổng phụ huynh" }).click();
  await page.getByRole("button", { name: "Công bố", exact: true }).click();
  await expect(page.locator(".ip-monthly-card")).toContainText("Đã công bố cho phụ huynh");
  await expect(page.locator(".ip-monthly-card")).toContainText("Con tiến bộ trong giao tiếp và tương tác.");
  await expect(page.getByRole("button", { name: "Sửa bản nháp" })).toHaveCount(0);
  expect(state.plans[0].plan.status).toBe("ACTIVE");
  await page.locator(".ip-tabs").getByRole("button", { name: "Kế hoạch hiện tại", exact: true }).click();
  await page.getByRole("button", { name: "Thay mục tiêu" }).click();
  await page.getByRole("combobox", { name: "Chương trình mục tiêu" }).click();
  await page.locator(".ant-select-dropdown:visible").getByText("Denver", { exact: true }).click();
  await page.getByRole("combobox", { name: "Cấp độ mục tiêu" }).click();
  await page.locator(".ant-select-dropdown:visible").getByText("Cấp độ 1", { exact: true }).click();
  await page.getByRole("combobox", { name: "Lĩnh vực mục tiêu" }).click();
  await page.locator(".ant-select-dropdown:visible").getByText("Ngôn ngữ", { exact: true }).click();
  await page.getByRole("combobox", { name: "Mục tiêu thêm vào kế hoạch" }).click();
  await page.locator(".ant-select-dropdown:visible").getByText("D.2 · Nhận biết đồ vật", { exact: true }).click();
  await page.getByRole("button", { name: "Lưu mục tiêu thay thế" }).click();
  await expect(page.locator(".ip-outline-domain")).toContainText("D.2");
  await expect(page.locator(".ip-outline-domain")).not.toContainText("D.1");
  await expect(page.locator(".ip-outline-target")).toContainText("Chưa thực hiện");
  await page.locator(".ip-tabs").getByRole("button", { name: "Đánh giá kế hoạch", exact: true }).click();
  await page.getByRole("combobox", { name: "Kế hoạch cần đánh giá" }).click();
  await page.locator(".ant-select-dropdown:visible").getByText(/Kế hoạch tháng 10/).click();
  await expect(page.locator(".ip-monthly-card")).toContainText("Con tiến bộ trong giao tiếp và tương tác.");
  await page.locator(".ip-plan-assessment-actions").getByRole("button", { name: "Chỉnh sửa đánh giá", exact: true }).click();
  await expect(page.getByText(/Bản đánh giá trước đã công bố/)).toBeVisible();
  await page.getByRole("textbox", { name: "Nhận xét giáo viên về kế hoạch" }).fill("Nhận xét bổ sung cho kế hoạch.");
  await page.getByRole("button", { name: "Lưu đánh giá kế hoạch", exact: true }).click();
  await expect(page.locator(".ip-monthly-card")).toHaveCount(2);
  expect(state.monthlyReports.find(row => row.sentToParent)?.evaluationNotes).toBe("Con tiến bộ trong giao tiếp và tương tác.");
});

test("parent reports page lists only published narratives", async ({ page }) => {
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = [];
    if (path === "/api/auth/me") data = { id: "parent", email: "parent@test.invalid", displayName: "Phụ huynh", role: "PARENT" };
    else if (path === "/api/auth/csrf") data = { token: "csrf" };
    else if (path === "/api/parent/plan-evaluations") data = [{
      id: "published", learningPlanId: "plan", planName: "Kế hoạch tháng 10", studentId: "student",
      studentName: "Bé An", evaluationNotes: "Báo cáo đã công bố", evaluatedBy: "teacher",
      evaluatedByName: "Giáo viên A", evaluatedAt: "2026-10-28T08:00:00", sentToParent: true,
      sentAt: "2026-10-29T08:00:00", updatedAt: "2026-10-29T08:00:00",
    }];
    else return route.fulfill({ status: 404, body: "{}" });
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(data) });
  });
  await page.goto("/#/reports");
  await expect(page.getByRole("heading", { name: "Nhận xét kế hoạch học tập" })).toBeVisible();
  await expect(page.locator(".ip-monthly-card")).toContainText("Báo cáo đã công bố");
  await expect(page.getByRole("button", { name: /Sửa|Công bố/ })).toHaveCount(0);
});

test("pause and completion require confirmation and history shows assessment and goal decisions", async ({ page }) => {
  const { state, studentId } = await mockIntervention(page);
  const planId="00000000-0000-0000-0000-000000000088";
  state.plans.push({ plan: { id: planId, studentId, name: "Kế hoạch kiểm tra trạng thái", startDate: "2026-10-01", status: "ACTIVE" },
    goals: [55,80,null].map((percent,index) => ({ id: `status-goal-${index}`,planId,goalId:`GL00000${index+1}`,
      specificGoalId:null,targetType:"GOAL",targetId:`GL00000${index+1}`,status:percent==null?"NOT_STARTED":percent>=80?"ACHIEVED":"NOT_ACHIEVED",
      achievementPercent:percent,criteriaSnapshot:{},goalSnapshot:{code:`D.${index+1}`,name:`Mục tiêu ${index+1}`,description:`Nội dung mục tiêu ${index+1}`,
        target_type:"GOAL",program_name:"Denver",level_name:"Cấp độ 1",domain_name:"Giao tiếp"} })) });
  await page.goto(`/#/individual/plan/${studentId}?tab=evaluation`);
  await page.getByRole("combobox", { name: "Kế hoạch cần đánh giá" }).click();
  await page.locator(".ant-select-dropdown:visible").getByText(/Kế hoạch kiểm tra trạng thái/).click();
  await expect(page.getByRole("button",{name:"Hoàn thành kế hoạch",exact:true})).toBeDisabled();
  await page.getByRole("button",{name:"Tạm dừng kế hoạch",exact:true}).click();
  await page.getByRole("button",{name:"Hủy",exact:true}).click();
  expect(state.plans[0].plan.status).toBe("ACTIVE");
  await page.getByRole("button",{name:"Tạm dừng kế hoạch",exact:true}).click();
  await page.getByRole("button",{name:"Xác nhận tạm dừng",exact:true}).click();
  await expect(page.locator(".ip-assessment-plan > .ip-plan-heading")).toContainText("Tạm dừng");
  await page.locator(".ip-plan-assessment-actions").getByRole("button",{name:"Đánh giá kế hoạch",exact:true}).click();
  await page.getByRole("textbox",{name:"Nhận xét giáo viên về kế hoạch"}).fill("Con đã tiến bộ; tiếp tục luyện những mục tiêu chưa đạt.");
  await page.getByRole("button",{name:"Lưu đánh giá kế hoạch",exact:true}).click();
  await expect(page.getByRole("button",{name:"Hoàn thành kế hoạch",exact:true})).toBeEnabled();
  await page.getByRole("button",{name:"Hoàn thành kế hoạch",exact:true}).click();
  await page.getByRole("button",{name:"Hủy",exact:true}).click();
  expect(state.plans[0].plan.status).toBe("PAUSED");
  await page.getByRole("button",{name:"Hoàn thành kế hoạch",exact:true}).click();
  await page.getByRole("button",{name:"Xác nhận hoàn thành",exact:true}).click();
  await expect(page.locator(".ip-assessment-plan > .ip-plan-heading")).toContainText("Đã hoàn thành");
  await page.locator(".ip-tabs").getByRole("button",{name:"Lịch sử kế hoạch",exact:true}).click();
  await expect(page.locator(".ip-history-table tbody tr")).toContainText("29 ngày");
  await expect(page.locator(".ip-history-table tbody tr")).toContainText("Đã hoàn thành");
  await page.getByRole("button",{name:"Xem đánh giá",exact:true}).click();
  await expect(page.locator(".ip-history-report-summary")).toContainText("Học sinh kiểm thử An");
  await expect(page.locator(".ip-history-report-summary")).toContainText("Test admin");
  await expect(page.locator(".ip-history-report-summary")).toContainText("Con đã tiến bộ; tiếp tục luyện những mục tiêu chưa đạt.");
  await expect(page.locator(".ip-history-goals-table tbody tr").nth(0)).toContainText("Lặp lại mục tiêu");
  await expect(page.locator(".ip-history-goals-table tbody tr").nth(1)).toContainText("Đã hoàn thành");
  await expect(page.locator(".ip-history-goals-table tbody tr").nth(2)).toContainText("Chưa thực hiện");
  await page.screenshot({path:"test-results/plan-history-report.png",fullPage:true});
  await page.setViewportSize({width:390,height:844});
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
