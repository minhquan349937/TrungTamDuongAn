import { goalRequest } from "./goalBankApi";
import type { PlanEvaluation } from "./planEvaluationApi";

export type ReportStudent = { id: string; code: string; fullName: string; reportCount: number };
export type ReportSummary = { id: string; planId: string; planName: string; planStartDate: string; planEndDate: string | null;
  evaluatedAt: string; evaluatedByName: string; editable: boolean };
export type ReportSections = { structured: boolean; strengths: string; difficulties: string; supportDirections: string; originalText: string };
export type ReportGoal = { id: string; domain: string; code: string; name: string; achievementPercent: number | null; status: string; notes: string | null };
export type IndividualReport = { report: PlanEvaluation; studentCode: string; planStartDate: string; planEndDate: string | null;
  resultsAsOf: string; editable: boolean; sections: ReportSections; goals: ReportGoal[]; warnings: string[] };
export type ReportEdit = Pick<ReportSections, "strengths" | "difficulties" | "supportDirections"> & { expectedUpdatedAt: string };
const base = "/api/individual-reports";
export const individualReportApi = {
  students: (q = "") => goalRequest<ReportStudent[]>(`${base}/students?q=${encodeURIComponent(q)}`),
  student: (id: string) => goalRequest<ReportStudent>(`${base}/students/${encodeURIComponent(id)}`),
  list: (id: string) => goalRequest<ReportSummary[]>(`${base}/students/${encodeURIComponent(id)}/reports`),
  get: (id: string) => goalRequest<IndividualReport>(`${base}/${encodeURIComponent(id)}`),
  update: (id: string, input: ReportEdit) => goalRequest<IndividualReport>(`${base}/${encodeURIComponent(id)}/sections`, input, "PUT"),
  pdf: async (id: string, download: boolean) => {
    const response = await fetch(`${base}/${encodeURIComponent(id)}/pdf?download=${download}`, { credentials: "include", cache: "no-store" });
    if (!response.ok) {
      const error = await response.json().catch(() => null) as { message?: string } | null;
      throw new Error(error?.message ?? (response.status === 403 ? "Bạn không có quyền tải báo cáo này." : "Không thể tải PDF. Vui lòng thử lại."));
    }
    if (!response.headers.get("Content-Type")?.includes("application/pdf")) throw new Error("Máy chủ không trả về PDF hợp lệ.");
    return response.blob();
  },
};
