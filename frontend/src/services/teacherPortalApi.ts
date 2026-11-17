import { goalRequest } from "./goalBankApi";
import { parentPortalDatabase, type ParentPortal } from "./parentPortalApi";
import type { Database, SchoolClass } from "../types";

export type TeacherPortal = {
  linkStatus: "READY" | "NO_ACTIVE_STAFF_PROFILE";
  teacher: { id: string; fullName: string } | null;
  students: ParentPortal["children"];
  classes: { id: string; code: string; name: string; type: string; teacherId: string; capacity: number; status: string }[];
  memberships: ParentPortal["memberships"];
  sessions: ParentPortal["sessions"];
  attendance: ParentPortal["attendance"];
  notices: ParentPortal["notices"];
};
export const teacherPortalApi = { get: () => goalRequest<TeacherPortal>("/api/teacher/portal") };

export function teacherPortalDatabase(portal: TeacherPortal): Database {
  const db = parentPortalDatabase({ ...portal, linkStatus: "READY", guardian: null,
    children: portal.students, teachers: portal.teacher ? [portal.teacher] : [] });
  const types: Record<string, SchoolClass["type"]> = { INDIVIDUAL: "Cá nhân", GROUP: "Lớp nhóm", DAYCARE: "Bán trú" };
  // Include assigned classes with no enrollments, which a membership-only map would lose.
  db.classes = portal.classes.map(cls => ({ id: cls.id, name: cls.name, type: types[cls.type], teacherId: cls.teacherId,
    capacity: cls.capacity, status: cls.status === "ACTIVE" ? "Đang hoạt động" : "Tạm dừng",
    studentIds: [...new Set(portal.memberships.filter(m => m.classId === cls.id).map(m => m.studentId))] }));
  return db;
}
