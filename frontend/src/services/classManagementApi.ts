import { goalRequest } from "./goalBankApi";
import type { SchoolClass } from "../types";

type ClassRecord = Omit<SchoolClass, "type" | "status"> & { type: "GROUP" | "DAYCARE"; status: "ACTIVE" | "PAUSED" };
const fromApi = (row: ClassRecord): SchoolClass => ({ ...row, type: row.type === "GROUP" ? "Lớp nhóm" : "Bán trú", status: row.status === "ACTIVE" ? "Đang hoạt động" : "Tạm dừng" });
const toApi = (row: SchoolClass): ClassRecord => ({ ...row, type: row.type === "Lớp nhóm" ? "GROUP" : "DAYCARE", status: row.status === "Đang hoạt động" ? "ACTIVE" : "PAUSED" });
export const classManagementApi = {
  list: async (type: "Lớp nhóm" | "Bán trú") => (await goalRequest<ClassRecord[]>(`/api/admin/classes?type=${type === "Lớp nhóm" ? "GROUP" : "DAYCARE"}`)).map(fromApi),
  save: async (row: SchoolClass, existing: boolean) => fromApi(await goalRequest<ClassRecord>(existing ? `/api/admin/classes/${encodeURIComponent(row.id)}` : "/api/admin/classes", toApi(row), existing ? "PUT" : "POST")),
  close: (id: string) => goalRequest<void>(`/api/admin/classes/${encodeURIComponent(id)}`, undefined, "DELETE"),
};
