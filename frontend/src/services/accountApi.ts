import { goalRequest } from "./goalBankApi";
export type Account = { id: string; displayName: string; email: string | null; phone: string | null;
  role: "ADMIN" | "TEACHER" | "PARENT"; status: "ACTIVE" | "LOCKED" | "DISABLED";
  allowedRoles?: Account["role"][] };
export type AccountContact = Pick<Account, "email" | "phone">;
export type AccountUpdate = AccountContact & Pick<Account, "displayName" | "role" | "status"> & { password?: string };
export const accountApi = {
  list: () => goalRequest<Account[]>("/api/admin/accounts"),
  update: (id: string, input: AccountUpdate) => goalRequest<Account>(`/api/admin/accounts/${encodeURIComponent(id)}`, input, "PUT"),
};
