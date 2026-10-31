export type GuardianProfile = {
  id: string;
  code: string;
  fullName: string;
  phone: string;
  email: string | null;
  address: string | null;
  relationship: "MOTHER" | "FATHER" | "GRANDFATHER" | "GRANDMOTHER" | "GUARDIAN" | "OTHER";
};
export type StudentProfile = {
  id: string;
  code: string;
  fullName: string;
  birthDate: string;
  admittedOn: string;
  status: "ACTIVE" | "PAUSED" | "WITHDRAWN";
  notes: string | null;
  guardian: GuardianProfile | null;
};
export type ProfileInput = Omit<StudentProfile, "id" | "guardian"> & {
  guardian: Omit<GuardianProfile, "id">;
};
export type CreateProfileInput = Omit<ProfileInput, "code" | "status">;
export type GuardianUpdateInput = Pick<GuardianProfile, "fullName" | "phone" | "email" | "address">;

async function request<T>(url: string, method = "GET", body?: unknown): Promise<T> {
  let token = "";
  if (method !== "GET") {
    const csrf = await fetch("/api/auth/csrf", { credentials: "include" });
    if (!csrf.ok) throw new Error("Không lấy được mã bảo vệ phiên đăng nhập.");
    token = (await csrf.json() as { token: string }).token;
  }
  const response = await fetch(url, {
    method,
    credentials: "include",
    headers: method === "GET" ? undefined : { "Content-Type": "application/json", "X-CSRF-TOKEN": token },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const detail = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(detail?.message || `Không thể xử lý hồ sơ học sinh (${response.status}).`);
  }
  return response.json() as Promise<T>;
}
export const studentProfileApi = {
  list: () => request<StudentProfile[]>("/api/student-profiles"),
  create: (profile: CreateProfileInput) => request<StudentProfile>("/api/student-profiles", "POST", profile),
  update: (id: string, profile: ProfileInput) => request<StudentProfile>(`/api/student-profiles/${encodeURIComponent(id)}`, "PUT", profile),
  updateGuardian: (id: string, guardian: GuardianUpdateInput) =>
    request<GuardianUpdateInput & { id: string }>(`/api/guardians/${encodeURIComponent(id)}`, "PUT", guardian),
};
