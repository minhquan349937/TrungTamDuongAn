import { afterEach, describe, expect, it, vi } from "vitest";
import { createSeed } from "../mocks/seed";
import {
  goalCatalog,
  goalDomains,
  goalPrograms,
  goalAges,
} from "../mocks/goalLibrary";
import { addStudentGoals } from "./interventionService";
import { canVisit, visibleData } from "./authorization";
import { loadDatabase, mockService } from "./mockService";

afterEach(() => vi.unstubAllGlobals());
describe("Student intervention goal bank", () => {
  it("saves independent snapshots per student and deduplicates retries", () => {
    const original = createSeed();
    const goal = goalCatalog[0];
    let db = addStudentGoals(original, "ADMIN", "HS001", [goal.id, goal.id]);
    db = addStudentGoals(db, "ADMIN", "HS001", [goal.id]);
    db = addStudentGoals(db, "ADMIN", "HS002", [goal.id]);
    expect(original.studentGoals).toEqual([]);
    expect(db.studentGoals).toHaveLength(2);
    expect(db.studentGoals[0].goal).toEqual(goal);
    expect(db.studentGoals[0].goal).not.toBe(goal);
    expect(db.studentGoals.map((g) => g.studentId)).toEqual(["HS001", "HS002"]);
    expect(db.studentGoals[0].addedBy).toBe("AD001");
  });
  it("enforces teacher scope and denies parents at the service boundary", () => {
    let db = createSeed();
    const allowed = visibleData(db, "TEACHER").students[0].id;
    const other = db.students.find(
      (s) => !visibleData(db, "TEACHER").students.some((v) => v.id === s.id),
    )!.id;
    const id = goalCatalog[0].id;
    db = addStudentGoals(db, "TEACHER", allowed, [id]);
    expect(db.studentGoals[0].addedBy).toBe("NV001");
    expect(() => addStudentGoals(db, "TEACHER", other, [id])).toThrow("quyền");
    expect(() => addStudentGoals(db, "PARENT", "HS001", [id])).toThrow("quyền");
    db = addStudentGoals(db, "ADMIN", other, [id]);
    expect(visibleData(db, "TEACHER").studentGoals).toHaveLength(1);
    expect(visibleData(db, "PARENT").studentGoals).toEqual([]);
    expect(canVisit("PARENT", "/individual/plan/HS001")).toBe(false);
  });
  it("rejects an empty or invalid selection and unknown students without partial changes", () => {
    const db = createSeed();
    expect(() => addStudentGoals(db, "ADMIN", "HS001", [])).toThrow("ít nhất");
    expect(() =>
      addStudentGoals(db, "ADMIN", "HS001", [goalCatalog[0].id, "missing"]),
    ).toThrow("thư viện");
    expect(() =>
      addStudentGoals(db, "ADMIN", "unknown", [goalCatalog[0].id]),
    ).toThrow("quyền");
    expect(db.studentGoals).toEqual([]);
  });
  it("clears old demo browser data and starts with empty records", () => {
    const legacy = { ...createSeed(), studentGoals: undefined };
    const store = new Map<string, string>([["duongan.demo.v1", JSON.stringify(legacy)]]);
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
      removeItem: (key: string) => store.delete(key),
    });
    const loaded = loadDatabase();
    expect(loaded.studentGoals).toEqual([]);
    expect(loaded.students).toEqual([]);
    expect(store.has("duongan.demo.v1")).toBe(false);
  });  it("removes goal bank records with demo student deletion and reset", () => {
    let db = addStudentGoals(createSeed(), "ADMIN", "HS001", [
      goalCatalog[0].id,
    ]);
    db = addStudentGoals(db, "ADMIN", "HS002", [goalCatalog[1].id]);
    db = mockService.remove(db, "ADMIN", "students", "HS001");
    expect(db.studentGoals.map((g) => g.studentId)).toEqual(["HS002"]);
    expect(mockService.reset("ADMIN").studentGoals).toEqual([]);
  });
  it("provides age-specific content and unique IDs throughout all selectable libraries", () => {
    expect(new Set(goalCatalog.map((g) => g.id)).size).toBe(goalCatalog.length);
    for (const program of goalPrograms) {
      for (const domainId of program.domainIds) {
        expect(goalDomains.some((d) => d.id === domainId)).toBe(true);
        for (const age of goalAges) {
          expect(
            goalCatalog.filter(
              (g) =>
                g.programId === program.id &&
                g.domainId === domainId &&
                g.ageId === age.id,
            ),
          ).toHaveLength(2);
        }
      }
    }
    const first = goalCatalog.filter(
      (g) => g.programId === "denver" && g.domainId === "imitation",
    );
    expect(new Set(first.map((g) => g.description)).size).toBe(first.length);
  });
});
