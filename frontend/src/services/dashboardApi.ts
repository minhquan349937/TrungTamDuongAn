export type DashboardSummary = {
  totalStudents: number;
  activeStudents: number;
  totalStaff: number;
  activeClasses: number;
  studentsAdmittedThisMonth: number;
  staffJoinedThisMonth: number;
  activeClassTypes: number;
};

export const dashboardApi = {
  async summary(): Promise<DashboardSummary> {
    const response = await fetch("/api/admin/dashboard-summary", { credentials: "include" });
    if (!response.ok) throw new Error(`Không tải được thống kê tổng quan (${response.status}).`);
    return response.json() as Promise<DashboardSummary>;
  },
};
