import type { Database, Role } from "../types";
export const actorId = (role: Role) =>
  role === "TEACHER" ? "NV001" : role === "PARENT" ? "PH001" : "AD001";
export function visibleData(
  db: Database,
  role: Role,
  child?: string,
): Database {
  if (role === "ADMIN") return db;
  const actor = actorId(role);
  const ownClasses = db.classes.filter((c) => c.teacherId === actor);
  const ids =
    role === "PARENT"
      ? (db.parents.find((p) => p.id === actor)?.studentIds ?? []).filter(
          (id) => !child || id === child,
        )
      : [
          ...new Set([
            ...db.students
              .filter((s) => s.teacherId === actor)
              .map((s) => s.id),
            ...ownClasses.flatMap((c) => c.studentIds),
          ]),
        ];
  const classes =
    role === "TEACHER"
      ? ownClasses
      : db.classes
          .filter((c) => c.studentIds.some((id) => ids.includes(id)))
          .map((c) => ({
            ...c,
            studentIds: c.studentIds.filter((id) => ids.includes(id)),
          }));
  return {
    ...db,
    studentGoals:
      role === "PARENT"
        ? []
        : db.studentGoals.filter((g) => ids.includes(g.studentId)),
    students: db.students.filter((s) => ids.includes(s.id)),
    parents: db.parents.filter((p) =>
      role === "PARENT"
        ? p.id === actor
        : p.studentIds.some((id) => ids.includes(id)),
    ),
    staff: db.staff.filter((s) =>
      role === "TEACHER"
        ? s.id === actor
        : classes.some((c) => c.teacherId === s.id),
    ),
    classes,
    sessions: db.sessions
      .filter((s) =>
        role === "TEACHER"
          ? s.teacherId === actor
          : classes.some((c) => c.id === s.classId),
      )
      .map((s) => ({
        ...s,
        attendance: Object.fromEntries(
          Object.entries(s.attendance).filter(([id]) => ids.includes(id)),
        ),
      })),
    fees:
      role === "PARENT" ? db.fees.filter((f) => ids.includes(f.studentId)) : [],
    leaves:
      role === "TEACHER" ? db.leaves.filter((l) => l.teacherId === actor) : [],
    admins: [],
    checkins: role === "TEACHER" ? { [actor]: db.checkins[actor] ?? [] } : {},
  };
}
export function canVisit(role: Role, path: string) {
  if (role === "ADMIN") return true;
  const base = path.split("/")[1];
  return (
    role === "TEACHER"
      ? [
          "",
          "dashboard",
          "students",
          "individual",
          "groups",
          "daycare",
          "schedule",
          "attendance",
          "work",
          "leave",
          "library",
        ]
      : [
          "",
          "dashboard",
          "students",
          "schedule",
          "attendance",
          "notifications",
          "fees",
          "payments",
          "daycare",
        ]
  ).includes(base);
}
export function requireAdmin(role: Role) {
  if (role !== "ADMIN")
    throw new Error("Bạn không có quyền thực hiện thao tác này.");
}
