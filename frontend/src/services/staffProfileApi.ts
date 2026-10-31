export type StaffProfile = {
  id: string;
  code: string;
  fullName: string;
  position: string | null;
  specialty: string | null;
  phone: string | null;
  email: string | null;
  joinedOn: string;
  status: "ACTIVE" | "ON_LEAVE" | "TERMINATED";
};
export type StaffInput = Omit<StaffProfile, "id" | "code">;

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  let token = "";
  if (method !== "GET") {
    const csrf = await fetch("/api/auth/csrf", { credentials: "include" });
    if (!csrf.ok) throw new Error("Không lấy được mã bảo vệ phiên đăng nhập.");
    token = ((await csrf.json()) as { token: string }).token;
  }
  const response = await fetch(url, {
    method, credentials: "include",
    headers: method === "GET" ? undefined : { "Content-Type": "application/json", "X-CSRF-TOKEN": token },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const detail = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(detail?.message || `Không thể xử lý hồ sơ nhân viên (${response.status}).`);
  }
  return response.json() as Promise<T>;
}

export const staffProfileApi = {
  list: () => request<StaffProfile[]>("GET", "/api/staff-profiles"),
  create: (data: StaffInput) => request<StaffProfile>("POST", "/api/staff-profiles", data),
  update: (id: string, data: StaffInput) => request<StaffProfile>("PUT", `/api/staff-profiles/${encodeURIComponent(id)}`, data),
};
