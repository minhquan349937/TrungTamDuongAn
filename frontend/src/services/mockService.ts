import type {
  Database,
  Role,
  Collection,
  Entity,
  Session,
  SchoolClass,
  Leave,
  Student,
} from "../types";
import { actorId, requireAdmin, visibleData } from "./authorization";
import { today, uid } from "../utils/format";
const KEY = "duongan.demo.v1";
const CLEARED_DEMO_KEY = "duongan.demo.cleared.v1";
function emptyDatabase(): Database {
  return {
    version: 1,
    students: [],
    staff: [],
    parents: [],
    classes: [],
    sessions: [],
    fees: [],
    leaves: [],
    notices: [],
    checkins: {},
    admins: [],
    studentGoals: [],
  };
}
export function loadDatabase(): Database {
  const raw = localStorage.getItem(KEY);
  if (localStorage.getItem(CLEARED_DEMO_KEY) !== "1") {
    localStorage.removeItem(KEY);
    localStorage.setItem(CLEARED_DEMO_KEY, "1");
    return emptyDatabase();
  }
  if (!raw) return emptyDatabase();
  try {
    const db = JSON.parse(raw);
    if (
      db.version !== 1 ||
      ![
        "students",
        "staff",
        "parents",
        "classes",
        "sessions",
        "fees",
        "leaves",
        "notices",
        "admins",
      ].every((key) => Array.isArray(db[key])) ||
      !db.checkins ||
      typeof db.checkins !== "object"
    )
      throw Error();
    // Add the new feature without replacing previously saved demo records.
    if (db.studentGoals === undefined) db.studentGoals = [];
    if (!Array.isArray(db.studentGoals)) throw Error();
    return db;
  } catch {
    throw new Error(
      "Dữ liệu demo đã lưu không hợp lệ. Hãy khôi phục dữ liệu mẫu.",
    );
  }
}
export function persistDatabase(db: Database) {
  try {
    localStorage.setItem(KEY, JSON.stringify(db));
  } catch {
    throw new Error(
      "Không thể lưu dữ liệu trên trình duyệt. Vui lòng kiểm tra dung lượng hoặc quyền lưu trữ.",
    );
  }
}
export function validateSession(db: Database, s: Session) {
  const c = db.classes.find((c) => c.id === s.classId);
  if (!c) throw Error("Vui lòng chọn lớp học hợp lệ.");
  if (s.end <= s.start) throw Error("Giờ kết thúc phải sau giờ bắt đầu.");
  if (!db.staff.some((t) => t.id === s.teacherId))
    throw Error("Giáo viên không hợp lệ.");
  const overlap = db.sessions.find(
    (x) =>
      x.id !== s.id &&
      x.date === s.date &&
      x.start < s.end &&
      s.start < x.end &&
      (x.teacherId === s.teacherId ||
        x.classId === s.classId ||
        x.room.trim().toLowerCase() === s.room.trim().toLowerCase() ||
        db.classes
          .find((k) => k.id === x.classId)
          ?.studentIds.some((id) => c.studentIds.includes(id))),
  );
  if (overlap)
    throw Error(
      "Trùng lịch: giáo viên, học sinh, lớp hoặc phòng đã có buổi học trong khung giờ này.",
    );
}
export const mockService = {
  save(
    db: Database,
    role: Role,
    collection: Collection,
    entity: Entity,
  ): Database {
    if (collection === "leaves" && role === "TEACHER") {
      const leave = entity as Leave;
      if (
        leave.teacherId !== actorId(role) ||
        leave.status !== "Chờ duyệt" ||
        db.leaves.some((l) => l.id === leave.id)
      )
        throw Error("Không có quyền sửa đơn nghỉ phép.");
    } else requireAdmin(role);
    if (collection === "sessions") validateSession(db, entity as Session);
    if (collection === "classes") {
      const c = entity as SchoolClass;
      if (c.studentIds.length > c.capacity || c.capacity < 1)
        throw Error("Sĩ số vượt quá sức chứa của lớp.");
      if (c.type === "Cá nhân" && c.studentIds.length > 1)
        throw Error("Lớp cá nhân chỉ được gán một học sinh.");
      if (
        !db.staff.some((t) => t.id === c.teacherId) ||
        c.studentIds.some((id) => !db.students.some((s) => s.id === id))
      )
        throw Error("Phân công không hợp lệ.");
    }
    if (collection === "leaves") {
      const l = entity as Leave;
      if (l.to < l.from)
        throw Error("Ngày kết thúc phải từ ngày bắt đầu trở đi.");
    }
    const entries = db[collection] as Entity[];
    const next = {
      ...db,
      [collection]: entries.some((e) => e.id === entity.id)
        ? entries.map((e) => (e.id === entity.id ? entity : e))
        : [entity, ...entries],
    };
    if (collection === "students") {
      const student = entity as Student;
      const old = db.students.find((s) => s.id === student.id);
      next.parents = db.parents.map((p) => ({
        ...p,
        studentIds:
          p.id === student.parentId
            ? [...new Set([...p.studentIds, student.id])]
            : old?.parentId === p.id
              ? p.studentIds.filter((id) => id !== student.id)
              : p.studentIds,
      }));
    }
    return next;
  },
  remove(
    db: Database,
    role: Role,
    collection: Collection,
    id: string,
  ): Database {
    requireAdmin(role);
    const next = structuredClone(db);
    if (collection === "students") {
      next.classes.forEach(
        (c) => (c.studentIds = c.studentIds.filter((s) => s !== id)),
      );
      next.parents.forEach(
        (p) => (p.studentIds = p.studentIds.filter((s) => s !== id)),
      );
      next.sessions.forEach((s) => delete s.attendance[id]);
      next.fees = next.fees.filter((f) => f.studentId !== id);
      next.studentGoals = next.studentGoals.filter((g) => g.studentId !== id);
    }
    if (
      collection === "staff" &&
      (db.classes.some((c) => c.teacherId === id) ||
        db.students.some((s) => s.teacherId === id) ||
        db.sessions.some((s) => s.teacherId === id))
    )
      throw Error(
        "Nhân viên đang được phân công. Hãy chuyển phân công trước khi xóa.",
      );
    if (collection === "classes")
      next.sessions = next.sessions.filter((s) => s.classId !== id);
    return {
      ...next,
      [collection]: (next[collection] as Entity[]).filter((e) => e.id !== id),
    };
  },
  attendance(
    db: Database,
    role: Role,
    id: string,
    attendance: Session["attendance"],
  ): Database {
    const session = db.sessions.find((s) => s.id === id);
    if (
      !session ||
      role === "PARENT" ||
      (role === "TEACHER" && session.teacherId !== actorId(role))
    )
      throw Error("Bạn không có quyền điểm danh buổi học này.");
    const c = db.classes.find((c) => c.id === session.classId);
    if (Object.keys(attendance).some((id) => !c?.studentIds.includes(id)))
      throw Error("Học sinh không thuộc lớp này.");
    return {
      ...db,
      sessions: db.sessions.map((s) =>
        s.id === id ? { ...s, attendance } : s,
      ),
    };
  },
  pay(db: Database, role: Role, id: string): Database {
    requireAdmin(role);
    return {
      ...db,
      fees: db.fees.map((f) => (f.id === id ? { ...f, paidAt: today() } : f)),
    };
  },
  approve(
    db: Database,
    role: Role,
    id: string,
    status: Leave["status"],
  ): Database {
    requireAdmin(role);
    return {
      ...db,
      leaves: db.leaves.map((l) => (l.id === id ? { ...l, status } : l)),
    };
  },
  checkin(db: Database, role: Role): Database {
    if (role !== "TEACHER")
      throw Error("Chỉ giáo viên được chấm công cá nhân.");
    const id = actorId(role),
      dates = db.checkins[id] ?? [];
    if (dates.includes(today())) throw Error("Bạn đã chấm công hôm nay.");
    return { ...db, checkins: { ...db.checkins, [id]: [...dates, today()] } };
  },
  reset(role: Role) {
    requireAdmin(role);
    return emptyDatabase();
  },
  view: visibleData,
  newId: uid,
};
