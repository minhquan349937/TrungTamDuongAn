import { afterEach, describe, expect, it, vi } from "vitest";
import { goalBankApi } from "./goalBankApi";

afterEach(() => vi.unstubAllGlobals());

describe("goal bank API contract", () => {
  it("loads both program types and paged goals from the same endpoints", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => [{ id: "denver", code: "DENVER" }, { id: "vb", code: "VB" }] })
      .mockResolvedValueOnce({ ok: true, json: async () => ({
        content: [{ id: "goal", code: "VB.1.1", name: "Request" }],
        page: 1, size: 20, totalElements: 21, totalPages: 2,
      }) });
    vi.stubGlobal("fetch", fetchMock);
    expect((await goalBankApi.programs()).map((program) => program.code)).toEqual(["DENVER", "VB"]);
    expect((await goalBankApi.goals("domain-id", "VB 1", 1)).totalPages).toBe(2);
    expect(fetchMock.mock.calls[1][0]).toBe("/api/level-domains/domain-id/goals?q=VB%201&page=1&size=20");
    expect(fetchMock.mock.calls[1][1].credentials).toBe("include");
  });

  it("uses the session CSRF token when assigning a specific goal", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ token: "csrf-token" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: "assignment" }) });
    vi.stubGlobal("fetch", fetchMock);
    await goalBankApi.assign("plan-id", "GL000001", "SG000001");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/csrf");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/plans/plan-id/goals");
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: "POST", credentials: "include",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": "csrf-token" },
      body: JSON.stringify({ goalId: "GL000001", specificGoalId: "SG000001" }),
    });
  });

});
