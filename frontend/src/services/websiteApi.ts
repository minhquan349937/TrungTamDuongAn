export type PublicMedia = { type: "image" | "video"; url: string; title: string | null; altText: string | null };
export type PublicContent = {
  settings: {
    centerName: string; logoUrl: string | null; address: string | null; hotline: string | null; workingHours: string | null;
    zaloUrl: string | null; facebookUrl: string | null; mapEmbedUrl: string | null;
  } | null;
  sections: Record<string, { title: string | null; summary: string | null; body: string | null; imageUrl: string | null; buttonLabel: string | null }>;
  items: { sectionKey: string; label: string | null; title: string; description: string | null }[];
  services: { id: string; title: string; summary: string | null; details: string | null; audience: string | null; format: string | null; imageUrl: string | null }[];
  team: { fullName: string; jobTitle: string | null; qualifications: string | null; experience: string | null; bio: string | null; photoUrl: string | null }[];
  albums: { id: string; title: string; category: "SPACE" | "ACTIVITY"; description: string | null; media: PublicMedia[] }[];
  faqs: { question: string; answer: string }[];
};
export type ConsultationInput = { parentName: string; phone: string; childAge: string; message: string; consent: boolean; website: string };

export type AdminRecord = { id: string; sample: boolean; updatedAt: string | null; [field: string]: unknown };
export type AdminMedia = {
  id: string; kind: "IMAGE" | "VIDEO"; contentType: string; sizeBytes: number; originalName: string; albumId: string | null;
  title: string | null; altText: string | null; sortOrder: number; visible: boolean; publicConsent: boolean; createdAt: string;
};
export type WebsiteStatus = { published: boolean; publishedAt: string | null; publishedBy: string | null; hasChanges: boolean; sampleCount: number };
export type ConsultationStatus = "NEW" | "CONTACTING" | "CONSULTED" | "CLOSED";
export type Consultation = {
  id: string; parentName: string; phone: string; childAge: string | null; message: string | null; status: ConsultationStatus;
  assignedUserId: string | null; assignedName: string | null; internalNote: string | null; updatedByName: string | null;
  consentedAt: string; createdAt: string; updatedAt: string;
};
export type ConsultationQuery = { q?: string; status?: string; from?: string; to?: string; page: number; size: number };

export const consultationStatusLabel: Record<ConsultationStatus, string> = {
  NEW: "Mới nhận", CONTACTING: "Đang liên hệ", CONSULTED: "Đã tư vấn", CLOSED: "Đã đóng",
};

/** Accepts 0xxxxxxxxx, +84xxxxxxxxx, 84xxxxxxxxx; returns 0xxxxxxxxx or null. Mirrors ConsultationService.normalizePhone. */
export function normalizePhone(raw: string): string | null {
  let s = raw.replace(/[\s.\-()]/g, "");
  if (s.startsWith("+84")) s = "0" + s.slice(3);
  else if (s.startsWith("84") && s.length === 11) s = "0" + s.slice(2);
  return /^0[235789]\d{8}$/.test(s) ? s : null;
}

export const adminMediaUrl = (id: string) => `/api/admin/website/media/${encodeURIComponent(id)}/file`;

async function csrfToken() {
  const response = await fetch("/api/auth/csrf", { credentials: "include" });
  if (!response.ok) throw new Error("Không lấy được mã bảo vệ phiên đăng nhập.");
  return ((await response.json()) as { token: string }).token;
}

async function failure(response: Response): Promise<Error> {
  const detail = (await response.json().catch(() => null)) as { message?: string } | null;
  if (detail?.message) return new Error(detail.message);
  if (response.status === 401) return new Error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
  if (response.status === 403) return new Error("Bạn không có quyền thực hiện thao tác này.");
  return new Error(`Không thể xử lý yêu cầu (${response.status}).`);
}

async function admin<T>(method: string, url: string, body?: unknown, form?: FormData): Promise<T> {
  const headers: Record<string, string> = {};
  if (method !== "GET") headers["X-CSRF-TOKEN"] = await csrfToken();
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(url, {
    method, credentials: "include", headers,
    body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
  });
  if (!response.ok) throw await failure(response);
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

const base = "/api/admin/website";
const query = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => value !== undefined && value !== "" && search.set(key, String(value)));
  const text = search.toString();
  return text ? "?" + text : "";
};

export const websiteApi = {
  /** null when nothing has been published yet. */
  async published(): Promise<PublicContent | null> {
    const response = await fetch("/api/public/website");
    if (response.status === 404) return null;
    if (!response.ok) throw await failure(response);
    return (await response.json()) as PublicContent;
  },
  async submitConsultation(input: ConsultationInput): Promise<string> {
    const response = await fetch("/api/public/consultations", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input),
    });
    if (!response.ok) throw await failure(response);
    return ((await response.json()) as { message: string }).message;
  },

  status: () => admin<WebsiteStatus>("GET", `${base}/status`),
  preview: () => admin<PublicContent>("GET", `${base}/preview`),
  publish: () => admin<WebsiteStatus>("POST", `${base}/publish`),
  list: (resource: string, section?: string) => admin<AdminRecord[]>("GET", `${base}/${resource}${query({ section })}`),
  create: (resource: string, data: object) => admin<AdminRecord>("POST", `${base}/${resource}`, data),
  update: (resource: string, id: string, data: object) => admin<AdminRecord>("PUT", `${base}/${resource}/${encodeURIComponent(id)}`, data),
  remove: (resource: string, id: string) => admin<void>("DELETE", `${base}/${resource}/${encodeURIComponent(id)}`),
  reorder: (resource: string, ids: string[]) => admin<void>("PUT", `${base}/${resource}/order`, { ids }),

  media: (albumId?: string) => admin<AdminMedia[]>("GET", `${base}/media${query({ albumId })}`),
  mediaGet: (id: string) => admin<AdminMedia>("GET", `${base}/media/${encodeURIComponent(id)}`),
  upload(file: File, options: { albumId?: string; publicConsent: boolean }) {
    const form = new FormData();
    form.set("file", file);
    form.set("publicConsent", String(options.publicConsent));
    if (options.albumId) form.set("albumId", options.albumId);
    return admin<AdminMedia>("POST", `${base}/media`, undefined, form);
  },
  mediaUpdate: (id: string, meta: Partial<Pick<AdminMedia, "title" | "altText" | "visible" | "publicConsent">>) =>
    admin<AdminMedia>("PUT", `${base}/media/${encodeURIComponent(id)}`, meta),
  mediaRemove: (id: string) => admin<void>("DELETE", `${base}/media/${encodeURIComponent(id)}`),
  mediaReorder: (ids: string[]) => admin<void>("PUT", `${base}/media/order`, { ids }),

  consultations: (params: ConsultationQuery) =>
    admin<{ items: Consultation[]; total: number; page: number; size: number }>("GET", `${base}/consultations${query(params)}`),
  updateConsultation: (id: string, data: { status: ConsultationStatus; assignedUserId: string | null; internalNote: string }) =>
    admin<Consultation>("PUT", `${base}/consultations/${encodeURIComponent(id)}`, data),
  assignees: () => admin<{ id: string; name: string; role: string }[]>("GET", `${base}/consultations/assignees`),
};
