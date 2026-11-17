import { goalRequest } from "./goalBankApi";
export type FeeStudent = { id: string; code: string; fullName: string };
export type FeeRecord = {
  id: string; code: string; studentId: string; studentName: string; title: string; month: string;
  amount: number; paidAmount: number; outstandingAmount: number; dueOn: string; paidAt: string | null;
};
export type CreateFee = { studentId: string; title: string; month: string; amount: number; dueOn?: string };
export const feeApi = {
  list: () => goalRequest<FeeRecord[]>("/api/fees"),
  students: () => goalRequest<FeeStudent[]>("/api/fees/students"),
  create: (input: CreateFee) => goalRequest<FeeRecord>("/api/fees", input, "POST"),
  pay: (id: string) => goalRequest<FeeRecord>(`/api/fees/${encodeURIComponent(id)}/payment`, {}, "POST"),
};
