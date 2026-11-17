import { describe, expect, it } from "vitest";
import { parentPortalDatabase, type ParentPortal } from "./parentPortalApi";
import { visibleData } from "./authorization";

function portal(): ParentPortal {
  return {
    linkStatus: "READY", guardian: { id: "PH-not-demo", fullName: "Phụ huynh giả lập", phone: null, email: null },
    children: [
      { id: "HS-early", code: "HS1", fullName: "Con lớn giả lập", birthDate: "2020-01-01", admittedOn: "2024-01-01", status: "ACTIVE" },
      { id: "HS-later", code: "HS2", fullName: "Con nhỏ giả lập", birthDate: "2022-01-01", admittedOn: "2026-11-10", status: "PAUSED" },
    ],
    memberships: ["HS-early", "HS-later"].map((studentId, index) => ({ classId: "CLASS-own", code: "C1", name: "Lớp nhóm giả lập", type: "GROUP", teacherId: "GV-own", capacity: 10, status: "ACTIVE", studentId, startedOn: index ? "2026-11-10" : "2024-01-01", endedOn: null })),
    teachers: [{ id: "GV-own", fullName: "Giáo viên giả lập" }],
    sessions: [{ id: "SESSION-own", classId: "CLASS-own", teacherId: "GV-own", startsAt: "2026-11-09T17:30:00", endsAt: "2026-11-09T18:30:00", location: null }],
    attendance: [{ sessionId: "SESSION-own", studentId: "HS-later", status: "LATE" }], notices: [],
  };
}
describe("parent portal database adapter", () => {
  it("uses actual guardian IDs, converts UTC to Vietnam time and preserves late attendance", () => {
    const db=parentPortalDatabase(portal());
    expect(db.sessions[0]).toMatchObject({ date: "2026-11-10", start: "00:30", end: "01:30", attendance: { "HS-later": "Đi muộn" } });
    expect(visibleData(db,"PARENT","HS-later","PH-not-demo").students.map(s=>s.id)).toEqual(["HS-later"]);
    expect(db.classes).toHaveLength(1); expect(db.classes[0].studentIds).toEqual(["HS-early","HS-later"]);
    expect(db.parents[0].email).toBe(""); expect(db.students[0].note).toBe("");
  });
  it("does not show a sibling's session before the selected child's enrollment begins", () => {
    const input=portal(); input.sessions[0].startsAt="2026-11-09T02:00:00";input.sessions[0].endsAt="2026-11-09T03:00:00";
    const db=parentPortalDatabase(input);
    expect(db.sessions[0].studentIds).toEqual(["HS-early"]); expect(db.sessions[0].attendance).toEqual({});
    expect(visibleData(db,"PARENT","HS-later","PH-not-demo").sessions).toEqual([]);
    expect(visibleData(db,"PARENT","HS-early","PH-not-demo").sessions).toHaveLength(1);
  });
});
