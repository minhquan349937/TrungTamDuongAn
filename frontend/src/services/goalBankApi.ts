export type Student = {
  id: string;
  code: string;
  fullName: string;
  birthDate: string;
  admittedOn: string;
  status: string;
};
export type Plan = { id: string; studentId: string; name: string; startDate: string; status: string;
  endDate?: string | null; createdAt?: string; hasEvaluation?: boolean; sentToParent?: boolean };
export type Program = { id: string; code: string; name: string; description: string | null };
export type ProgramLevel = {
  id: string; programId: string; levelNumber: number; name: string;
  ageMinMonths: number; ageMaxMonths: number; displayOrder: number;
};
export type Domain = {
  levelDomainId: string;
  id: string; programLevelId: string; code: string; name: string;
  description: string | null; displayOrder: number;
};
export type Goal = {
  id: string; code: string; name: string; description: string | null; displayOrder: number;
};
export type SpecificGoal = {
  id: string; code: string; description: string; ageMinMonths: number;
  ageMaxMonths: number; inheritedAge: boolean; displayOrder: number;
};
export type GoalLocation = {
  programId: string; programName: string; levelId: string; levelName: string;
  domainId: string; domainName: string; ageMinMonths: number; ageMaxMonths: number;
};
export type GoalDetail = Omit<Goal, "displayOrder"> & {
  specificGoals: SpecificGoal[]; supportsSpecificGoals: boolean; canSelectGeneralGoal: boolean; location: GoalLocation;
};
export type GoalTarget = { goalId: string; specificGoalId: string | null };
export type GoalSelection = GoalTarget & {
  targetType: "GOAL" | "SPECIFIC_GOAL"; code: string; name: string; description: string | null;
  location: GoalLocation; ageMinMonths: number; ageMaxMonths: number;
};
export type Page<T> = {
  content: T[]; page: number; size: number; totalElements: number; totalPages: number;
};
export type AssignedGoal = {
  goalId: string; specificGoalId: string | null;
  id: string; planId: string; targetType: "GOAL" | "SPECIFIC_GOAL"; targetId: string;
  goalSnapshot: { code: string; name: string; description: string | null; target_type: string;
    goal_name?: string; goal_code?: string; program_id?: string; program_name?: string;
    domain_id?: string; domain_name?: string; domain_code?: string; program_level_id?: string;
    level_name?: string; level_number?: number; age_min_months?: number; age_max_months?: number };
  criteriaSnapshot: Record<string, unknown>; status: string; achievementPercent?: number | null;
};

async function request<T>(url: string, body?: unknown, method = body === undefined ? "GET" : "POST"): Promise<T> {
  const csrf = body === undefined ? null : await fetch("/api/auth/csrf", { credentials: "include" });
  if (csrf && !csrf.ok) throw new Error("Không lấy được mã bảo vệ phiên đăng nhập.");
  const token = csrf ? ((await csrf.json()) as { token: string }).token : "";
  const response = await fetch(url, {
    method,
    credentials: "include",
    headers: body === undefined ? undefined : { "Content-Type": "application/json", "X-CSRF-TOKEN": token },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const status = response.status;
    const body = await response.json().catch(() => null) as { message?: string } | null;
    const message = status === 409 ? "Dữ liệu đã tồn tại hoặc đã được chọn cho kế hoạch này."
      : status === 403 ? "Bạn không có quyền thực hiện thao tác này."
      : status === 404 ? "Không tìm thấy dữ liệu được yêu cầu."
      : status === 400 ? "Dữ liệu nhập chưa hợp lệ."
      : "Không thể tải hoặc lưu dữ liệu từ máy chủ.";
    throw new Error(body?.message && /[À-ỹ]/.test(body.message) ? body.message : message);
  }
  return response.status === 204 ? undefined as T : (await response.json()) as T;
}

export { request as goalRequest };

export const goalBankApi = {
  students: (q = "") => request<Student[]>(`/api/students?q=${encodeURIComponent(q)}`),
  createStudent: (body: { code: string; fullName: string; birthDate: string; admittedOn: string }) =>
    request<Student>("/api/students", body),
  plans: (studentId: string) => request<Plan[]>(`/api/students/${studentId}/plans`),
  createPlan: (studentId: string, body: { name: string; startDate: string }) =>
    request<Plan>(`/api/students/${studentId}/plans`, body),
  programs: () => request<Program[]>("/api/programs"),
  levels: (programId: string) => request<ProgramLevel[]>(`/api/programs/${programId}/levels`),
  domains: (levelId: string) => request<Domain[]>(`/api/levels/${levelId}/domains`),
  goals: (domainId: string, q: string, page: number) =>
    request<Page<Goal>>(`/api/level-domains/${domainId}/goals?q=${encodeURIComponent(q)}&page=${page}&size=20`),
  goal: (goalId: string) => request<GoalDetail>(`/api/goals/${goalId}`),
  assigned: (planId: string) => request<AssignedGoal[]>(`/api/plans/${planId}/goals`),
  assign: (planId: string, goalId: string, specificGoalId: string | null = null) =>
    request<AssignedGoal>(`/api/plans/${planId}/goals`, { goalId, specificGoalId }),
  replace: (planId: string, assignedId: string, goalId: string, specificGoalId: string | null = null) =>
    request<AssignedGoal>(`/api/plans/${planId}/goals/${assignedId}/replace`, { goalId, specificGoalId }, "PUT"),
};
