import { goalRequest } from "./goalBankApi";
import type { SchoolClass, Session } from "../types";

export type ScheduleClass = SchoolClass & { startsOn: string; endsOn: string | null; teacherAvailable: boolean };
export type ScheduleData = { classes: ScheduleClass[]; staff: { id: string; name: string }[]; students: { id: string; name: string }[]; sessions: Session[] };
type RawCalendar = Omit<ScheduleData, "classes"> & { classes: (Omit<ScheduleClass, "type" | "status" | "studentIds"> & { type: string; status: string })[] };
export type ScheduleInput = { classId: string; date: string; start: string; end: string; room: string; changeReason?: string };
const types: Record<string, SchoolClass["type"]> = { INDIVIDUAL: "Cá nhân", GROUP: "Lớp nhóm", DAYCARE: "Bán trú" };
const statusLabels: Record<string, Session["attendance"][string]> = { PRESENT: "Có mặt", ABSENT: "Vắng", EXCUSED: "Có phép", LATE: "Đi muộn" };
const statusCodes = Object.fromEntries(Object.entries(statusLabels).map(([key, value]) => [value, key]));
export const scheduleApi = {
  get: async (from: string, to: string): Promise<ScheduleData> => {
    const raw = await goalRequest<RawCalendar>(`/api/schedule?${new URLSearchParams({ from, to })}`);
    return { ...raw, classes: raw.classes.map(cls => ({ ...cls, type: types[cls.type], status: cls.status === "ACTIVE" ? "Đang hoạt động" : "Tạm dừng", studentIds: [] })),
      sessions: raw.sessions.map(row => ({ ...row, start: row.start.slice(0, 5), end: row.end.slice(0, 5),
        attendance: Object.fromEntries(Object.entries(row.attendance).filter(([, value]) => statusLabels[value]).map(([id, value]) => [id, statusLabels[value]])) })) };
  },
  save: (input: ScheduleInput, id?: string) => goalRequest<{ id: string }>(id ? `/api/schedule/sessions/${encodeURIComponent(id)}` : "/api/schedule/sessions", input, id ? "PUT" : "POST"),
  attendance: (id: string, marks: Session["attendance"]) => goalRequest<void>(`/api/schedule/sessions/${encodeURIComponent(id)}/attendance`, { attendance: Object.fromEntries(Object.entries(marks).map(([student, status]) => [student, statusCodes[status]])) }, "PUT"),
};
