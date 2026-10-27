import type { Database, Role } from "../types";
import { actorId, visibleData } from "./authorization";
import { goalCatalog } from "../mocks/goalLibrary";
import { uid } from "../utils/format";

export function addStudentGoals(
  db: Database,
  role: Role,
  studentId: string,
  goalIds: string[],
): Database {
  if (
    role === "PARENT" ||
    !visibleData(db, role).students.some((s) => s.id === studentId)
  ) {
    throw Error("Bạn không có quyền quản lý mục tiêu của học sinh này.");
  }
  const ids = [...new Set(goalIds)];
  if (!ids.length) throw Error("Vui lòng chọn ít nhất một mục tiêu.");
  const goals = ids.map((id) => goalCatalog.find((goal) => goal.id === id));
  if (goals.some((goal) => !goal))
    throw Error("Mục tiêu không còn trong thư viện. Vui lòng chọn lại.");
  const existing = new Set(
    db.studentGoals
      .filter((row) => row.studentId === studentId)
      .map((row) => row.goal.id),
  );
  const addedAt = new Date().toISOString();
  const additions = goals
    .filter((goal) => !existing.has(goal!.id))
    .map((goal) => ({
      id: uid("MT"),
      studentId,
      goal: { ...goal! },
      addedAt,
      addedBy: actorId(role),
    }));
  return { ...db, studentGoals: [...db.studentGoals, ...additions] };
}
