import { describe, it, expect } from "vitest";
import { createSeed } from "../mocks/seed";
import { mockService, validateSession } from "./mockService";
import { canVisit, visibleData } from "./authorization";
import type { Session } from "../types";
describe("Demo data and authorization", () => {
  it("creates the requested fictional dataset", () => {
    const d = createSeed();
    expect([
      d.admins.length,
      d.staff.length,
      d.students.length,
      d.parents.length,
      d.classes.length,
    ]).toEqual([2, 10, 30, 30, 8]);
    expect(d.parents.every((p) => p.email.endsWith("@example.invalid"))).toBe(
      true,
    );
  });
  it("scopes teacher and parent reads and denies protected routes", () => {
    const d = createSeed(),
      t = visibleData(d, "TEACHER"),
      p = visibleData(d, "PARENT", "HS001");
    expect(t.classes.every((c) => c.teacherId === "NV001")).toBe(true);
    expect(t.sessions.every((s) => s.teacherId === "NV001")).toBe(true);
    expect(t.fees).toHaveLength(0);
    expect(p.students.map((s) => s.id)).toEqual(["HS001"]);
    expect(p.fees.every((f) => f.studentId === "HS001")).toBe(true);
    expect(
      p.classes.every((c) => c.studentIds.every((id) => id === "HS001")),
    ).toBe(true);
    expect(visibleData(d, "PARENT", "HS003").students).toHaveLength(0);
    expect(canVisit("PARENT", "/staff")).toBe(false);
    expect(canVisit("TEACHER", "/fees")).toBe(false);
  });
  it("checks writes at the service boundary", () => {
    const d = createSeed();
    expect(() =>
      mockService.save(d, "PARENT", "students", d.students[0]),
    ).toThrow("quyền");
    expect(() => mockService.pay(d, "TEACHER", d.fees[0].id)).toThrow("quyền");
    expect(() => mockService.approve(d, "TEACHER", "NP1", "Đã duyệt")).toThrow(
      "quyền",
    );
    expect(() =>
      mockService.attendance(d, "TEACHER", d.sessions[1].id, {}),
    ).toThrow("quyền");
    expect(() => mockService.reset("PARENT")).toThrow("quyền");
  });
  it("updates linked parent records and cascades student deletion", () => {
    let d = createSeed();
    d = mockService.save(d, "ADMIN", "students", {
      ...d.students[0],
      id: "HS-NEW",
      name: "Học sinh kiểm thử",
      parentId: "PH003",
    });
    expect(d.parents.find((p) => p.id === "PH003")?.studentIds).toContain(
      "HS-NEW",
    );
    d = mockService.remove(d, "ADMIN", "students", "HS001");
    expect(d.classes.every((c) => !c.studentIds.includes("HS001"))).toBe(true);
    expect(d.fees.some((f) => f.studentId === "HS001")).toBe(false);
    expect(d.parents.every((p) => !p.studentIds.includes("HS001"))).toBe(true);
  });
  it("enforces capacity and individual enrollment", () => {
    const d = createSeed();
    expect(() =>
      mockService.save(d, "ADMIN", "classes", {
        ...d.classes[0],
        studentIds: ["HS001", "HS002"],
      }),
    ).toThrow("Sĩ số");
    expect(() =>
      mockService.save(d, "ADMIN", "classes", {
        ...d.classes[0],
        capacity: 3,
        studentIds: ["HS001", "HS002"],
      }),
    ).toThrow("cá nhân");
  });
  it("records attendance, leave decisions and payment", () => {
    let d = createSeed();
    const s = d.sessions.find((s) => s.teacherId === "NV001")!;
    d = mockService.attendance(d, "TEACHER", s.id, { HS001: "Có mặt" });
    expect(d.sessions.find((x) => x.id === s.id)?.attendance.HS001).toBe(
      "Có mặt",
    );
    d = mockService.save(d, "TEACHER", "leaves", {
      id: "NP-TEST",
      teacherId: "NV001",
      from: "2026-12-01",
      to: "2026-12-02",
      reason: "Kiểm thử",
      status: "Chờ duyệt",
    });
    d = mockService.approve(d, "ADMIN", "NP-TEST", "Đã duyệt");
    expect(d.leaves.find((l) => l.id === "NP-TEST")?.status).toBe("Đã duyệt");
    d = mockService.pay(d, "ADMIN", d.fees[0].id);
    expect(d.fees[0].paidAt).toBeTruthy();
  });
});
describe("Schedule overlap validation", () => {
  const fresh = () => {
    const d = createSeed();
    d.sessions = [
      {
        id: "existing",
        classId: "LH001",
        teacherId: "NV001",
        date: "2026-12-01",
        start: "08:00",
        end: "09:00",
        room: "Phòng 1",
        attendance: {},
      },
    ];
    return d;
  };
  const base: Session = {
    id: "new",
    classId: "LH002",
    teacherId: "NV002",
    date: "2026-12-01",
    start: "08:30",
    end: "09:30",
    room: "Phòng 2",
    attendance: {},
  };
  it("allows unrelated simultaneous and adjacent sessions", () => {
    const d = fresh();
    expect(() => validateSession(d, base)).not.toThrow();
    expect(() =>
      validateSession(d, {
        ...base,
        classId: "LH001",
        teacherId: "NV001",
        start: "09:00",
        end: "10:00",
      }),
    ).not.toThrow();
  });
  it("rejects teacher, room, class and shared-student conflicts", () => {
    const d = fresh();
    for (const s of [
      { ...base, teacherId: "NV001" },
      { ...base, room: "Phòng 1" },
      { ...base, classId: "LH001" },
      { ...base, classId: "LH004" },
    ])
      expect(() => validateSession(d, s)).toThrow("Trùng lịch");
  });
  it("excludes current record during rescheduling and rejects reversed times", () => {
    const d = fresh();
    expect(() => validateSession(d, { ...d.sessions[0] })).not.toThrow();
    expect(() => validateSession(d, { ...base, end: "08:00" })).toThrow(
      "kết thúc",
    );
  });
});
