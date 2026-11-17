import { test, expect, type Page } from "@playwright/test";
import { mockIntervention } from "./intervention-fixture";
import type { IndividualReport } from "../src/services/individualReportApi";

async function mockReports(page: Page) {
  const { studentId } = await mockIntervention(page);
  const detail: IndividualReport = {
    report: { id: "NX000001", learningPlanId: "KH000001", planName: "Kế hoạch tháng chín", studentId,
      studentName: "Học sinh giả lập", evaluationNotes: "I. Tiến bộ\nNội dung gốc\nII. Khó khăn cần hỗ trợ\nCần tập trung\nIII. Định hướng hỗ trợ\nĐề xuất thực tế",
      evaluatedBy: "actor", evaluatedByName: "Giáo viên giả lập", evaluatedAt: "2026-10-15T00:00:00", sentToParent: false, sentAt: null, updatedAt: "2026-10-15T00:00:00" },
    studentCode: "HS01", planStartDate: "2026-09-01", planEndDate: "2026-09-30", resultsAsOf: "2026-09-30T16:59:59.999999", editable: true,
    sections: { structured: true, strengths: "Nội dung gốc", difficulties: "Cần tập trung", supportDirections: "Đề xuất thực tế", originalText: "Nhận xét gốc" },
    goals: [{ id: "MTKH000001", domain: "Giao tiếp", code: "D.1", name: "Giao tiếp bằng lời", achievementPercent: 80, status: "ACHIEVED", notes: "Ghi nhận giả lập" }], warnings: [],
  };
  const state = { detail, saves: 0, pdfCalls: 0, failSave: false, failList: false, search: "" };
  await page.route("**/api/individual-reports/**", async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === "/api/individual-reports/students") {
      state.search = url.searchParams.get("q") ?? "";
      return route.fulfill(state.failList ? { status: 503, json: { message: "Không thể tải danh sách báo cáo." } }
        : { json: state.search === "không có" ? [] : [{ id: studentId, code: "HS01", fullName: "Học sinh giả lập", reportCount: 2 }] });
    }
    if (path === `/api/individual-reports/students/${studentId}`) return route.fulfill({ json: { id: studentId, code: "HS01", fullName: "Học sinh giả lập", reportCount: 2 } });
    if (path.endsWith("/reports")) return route.fulfill({ json: ["NX000001", "NX000002"].map(id => ({ id, planId: "KH000001", planName: detail.report.planName,
      planStartDate: detail.planStartDate, planEndDate: detail.planEndDate, evaluatedAt: detail.report.evaluatedAt, evaluatedByName: detail.report.evaluatedByName, editable: true })) });
    if (path.endsWith("/sections")) {
      state.saves++;
      if (state.failSave) return route.fulfill({ status: 409, json: { message: "Báo cáo đã được thay đổi. Hãy tải lại và đối chiếu nội dung trước khi lưu." } });
      const input = route.request().postDataJSON();
      expect(input.expectedUpdatedAt).toBe(detail.report.updatedAt);
      expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
      detail.sections = { ...detail.sections, ...input, structured: true };
      detail.report.updatedAt = "2026-11-12T01:00:00";
      return route.fulfill({ json: detail });
    }
    if (path.endsWith("/pdf")) { state.pdfCalls++; return route.fulfill({ contentType: "application/pdf", body: "%PDF-1.4\n% synthetic test PDF\n%%EOF" }); }
    if (path === "/api/individual-reports/NX000001") return route.fulfill({ json: detail });
    return route.fulfill({ status: 404, json: { message: "Không tìm thấy báo cáo." } });
  });
  return { state, studentId };
}

test("student search, separate reports in the same month, edit-save-preview-download flow", async ({ page }) => {
  const { state, studentId } = await mockReports(page);
  await page.goto("/#/individual/report");
  await expect(page.getByRole("heading", { name: "Báo cáo cá nhân", exact: true })).toBeVisible();
  await expect(page.getByRole("cell", { name: "Học sinh giả lập", exact: true })).toBeVisible();
  await page.getByRole("searchbox", { name: "Tìm kiếm học sinh có báo cáo" }).fill("HS01");
  await expect.poll(() => state.search).toBe("HS01");
  await page.getByRole("button", { name: "Xem báo cáo", exact: true }).click();
  await expect(page.locator(".ir-panel .ant-table-row")).toHaveCount(2);
  await expect(page.locator(".ir-panel .ant-table-row").first()).toContainText("01/09/2026 – 30/09/2026");
  await expect(page.locator(".ir-panel .ant-table-row").first()).toContainText("tháng 10");
  await page.getByRole("button", { name: "Chỉnh sửa báo cáo", exact: true }).first().click();
  await expect(page.locator(".ant-descriptions")).toContainText("15/10/2026 · 07:00:00");
  const strengths = page.getByRole("textbox", { name: "I. Tiến bộ", exact: true });
  await expect(strengths).toHaveValue("Nội dung gốc");
  await expect(page.getByRole("cell", { name: "Đạt", exact: true })).toBeVisible();
  await strengths.fill("Giáo viên đã chỉnh sửa.\nGiữ đầy đủ nội dung.");
  await expect(page.getByRole("button", { name: "Xem trước PDF" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Tải xuống PDF" })).toBeDisabled();
  expect(state.saves).toBe(0);expect(state.pdfCalls).toBe(0);
  await page.getByRole("button", { name: "Lưu chỉnh sửa", exact: true }).click();
  await expect(page.getByRole("button", { name: "Lưu chỉnh sửa", exact: true })).toBeDisabled();
  expect(state.saves).toBe(1);expect(state.detail.sections.strengths).toContain("Giữ đầy đủ nội dung");
  await page.getByRole("button", { name: "Xem trước PDF" }).click();
  await expect(page.getByTitle("Bản xem trước báo cáo PDF")).toBeVisible();
  await page.locator(".ant-modal-close").click();
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Tải xuống PDF" }).click();
  expect((await download).suggestedFilename()).toBe("bao-cao-NX000001.pdf");
  expect(state.pdfCalls).toBe(2);
  await page.getByRole("button", { name: "Quay lại", exact: true }).first().click();
  await expect(page).toHaveURL(new RegExp(`${studentId}$`));
});

test("legacy original is preserved on read/export and explicit conversion retains the entire text", async ({ page }) => {
  const { state, studentId } = await mockReports(page);
  const original = "Nhận xét cũ không có tiêu đề.\nKhông làm mất dòng này.";
  state.detail.sections = { structured: false, strengths: "", difficulties: "", supportDirections: "", originalText: original };
  await page.goto(`/#/individual/report/${studentId}/NX000001`);
  await expect(page.getByRole("textbox", { name: "Văn bản nhận xét gốc" })).toHaveValue(original);
  await expect(page.getByRole("textbox", { name: "I. Tiến bộ", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Xem trước PDF" }).click();
  await expect(page.getByTitle("Bản xem trước báo cáo PDF")).toBeVisible();
  await page.locator(".ant-modal-close").click();
  expect(state.saves).toBe(0);
  await page.getByRole("button", { name: "Đưa văn bản gốc vào phần I để phân chia, chỉnh sửa" }).click();
  await expect(page.getByRole("textbox", { name: "I. Tiến bộ", exact: true })).toHaveValue(original);
  await expect(page.getByRole("button", { name: "Xem trước PDF" })).toBeDisabled();
  await page.getByRole("button", { name: "Lưu chỉnh sửa", exact: true }).click();
  await expect(page.getByRole("button", { name: "Xem trước PDF" })).toBeEnabled();
  expect(state.detail.sections.strengths).toBe(original);
});

test("save errors retain unsaved content and locked reports stay read-only", async ({ page }) => {
  const { state, studentId } = await mockReports(page);
  state.failSave = true;
  await page.goto(`/#/individual/report/${studentId}/NX000001`);
  const strengths = page.getByRole("textbox", { name: "I. Tiến bộ", exact: true });
  await strengths.fill("Nội dung chưa lưu cần giữ lại");
  await page.getByRole("button", { name: "Lưu chỉnh sửa", exact: true }).click();
  await expect(page.getByRole("alert").first()).toContainText("Báo cáo đã được thay đổi");
  await expect(strengths).toHaveValue("Nội dung chưa lưu cần giữ lại");
  await expect(page.getByRole("button", { name: "Tải xuống PDF" })).toBeDisabled();
  page.once("dialog", dialog => dialog.dismiss());
  await page.getByRole("button", { name: "Quay lại", exact: true }).first().click();
  await expect(strengths).toBeVisible();
  state.detail.editable = false; state.detail.report.sentToParent = true;
  page.once("dialog", dialog => dialog.accept());
  await page.reload();
  await expect(strengths).toBeDisabled();
  await expect(page.getByRole("button", { name: "Lưu chỉnh sửa", exact: true })).toHaveCount(0);
  await expect(page.getByText("Báo cáo lịch sử đã khóa chỉnh sửa.")).toBeVisible();
});

test("empty search and API errors have clear states", async ({ page }) => {
  const { state } = await mockReports(page);
  state.failList = true;
  await page.goto("/#/individual/report");
  await expect(page.getByRole("alert")).toContainText("Không thể tải danh sách báo cáo.");
  state.failList = false;
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.getByRole("button", { name: "Xem báo cáo", exact: true })).toBeVisible();
  await page.getByRole("searchbox", { name: "Tìm kiếm học sinh có báo cáo" }).fill("không có");
  await expect(page.getByText("Chưa có học sinh có báo cáo phù hợp.")).toBeVisible();
});
