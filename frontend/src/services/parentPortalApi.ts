import { goalRequest } from "./goalBankApi";
import type { Database, SchoolClass, Session } from "../types";

export type ParentPortal = {
  linkStatus: "READY" | "NO_GUARDIAN_PROFILE" | "NO_LINKED_STUDENT";
  guardian: { id: string; fullName: string; phone: string | null; email: string | null } | null;
  children: { id: string; code: string; fullName: string; birthDate: string; admittedOn: string; status: string }[];
  memberships: { classId: string; code: string; name: string; type: string; teacherId: string; capacity: number; status: string; studentId: string; startedOn: string; endedOn: string | null }[];
  teachers: { id: string; fullName: string }[];
  sessions: { id: string; classId: string; teacherId: string; startsAt: string; endsAt: string; location: string | null }[];
  attendance: { sessionId: string; studentId: string; status: string }[];
  notices: { id: string; title: string; body: string; publishedAt: string }[];
};
export const parentPortalApi = { get: () => goalRequest<ParentPortal>("/api/parent/portal") };

// The shared screens consume this shape; parent data stays in memory and is never
// merged with localStorage or demo profiles. The server defines the family scope.
export function parentPortalDatabase(portal: ParentPortal): Database {
  const classes = new Map<string, SchoolClass>();
  for (const row of portal.memberships) {
    const cls = classes.get(row.classId) ?? {
      id: row.classId, name: row.name, type: row.type === "INDIVIDUAL" ? "Cá nhân" : row.type === "GROUP" ? "Lớp nhóm" : "Bán trú",
      teacherId: row.teacherId, capacity: row.capacity, studentIds: [], status: row.status === "ACTIVE" ? "Đang hoạt động" : "Tạm dừng",
    };
    if (!cls.studentIds.includes(row.studentId)) cls.studentIds.push(row.studentId);
    classes.set(row.classId, cls);
  }
  const localParts = (utc: string) => {
    const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).formatToParts(new Date(`${utc}Z`));
    const get = (type: string) => parts.find(part => part.type === type)?.value;
    return { date: `${get("year")}-${get("month")}-${get("day")}`, time: `${get("hour")}:${get("minute")}` };
  };
  const sessions: Session[] = portal.sessions.map(row => {
    const start = localParts(row.startsAt), end = localParts(row.endsAt);
    const studentIds = [...new Set(portal.memberships.filter(m => m.classId === row.classId && m.startedOn <= start.date && (!m.endedOn || m.endedOn > start.date)).map(m => m.studentId))];
    const statuses: Record<string, Session["attendance"][string]> = { PRESENT: "Có mặt", ABSENT: "Vắng", EXCUSED: "Có phép", LATE: "Đi muộn" };
    return { id: row.id, classId: row.classId, teacherId: row.teacherId, date: start.date, start: start.time, end: end.time,
      room: row.location ?? "", studentIds,
      attendance: Object.fromEntries(portal.attendance.filter(a => a.sessionId === row.id && studentIds.includes(a.studentId) && statuses[a.status]).map(a => [a.studentId, statuses[a.status]])),
    };
  });
  return {
    version: 1,
    students: portal.children.map(row => {
      const memberships = portal.memberships.filter(m => m.studentId === row.id);
      return { id: row.id, code: row.code, name: row.fullName, birthDate: row.birthDate, joined: row.admittedOn,
        status: row.status === "ACTIVE" ? "Đang học" : row.status === "PAUSED" ? "Tạm nghỉ" : "Ngừng học",
        parentId: portal.guardian?.id ?? "", teacherId: (memberships.find(m => m.type === "INDIVIDUAL") ?? memberships[0])?.teacherId ?? "", note: "" };
    }),
    parents: portal.guardian ? [{ id: portal.guardian.id, name: portal.guardian.fullName, phone: portal.guardian.phone ?? "", email: portal.guardian.email ?? "", studentIds: portal.children.map(row => row.id) }] : [],
    staff: portal.teachers.map(row => ({ id: row.id, name: row.fullName, specialty: "", certificate: "", status: "Đang làm việc", salary: 0 })),
    classes: [...classes.values()], sessions,
    notices: portal.notices.map(row => ({ id: row.id, title: row.title, body: row.body, date: localParts(row.publishedAt).date })),
    fees: [], leaves: [], checkins: {}, admins: [], studentGoals: [],
  };
}
