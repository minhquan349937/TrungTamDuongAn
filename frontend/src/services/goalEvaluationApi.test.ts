import { afterEach, describe, expect, it, vi } from "vitest";
import { evaluationStatus, goalEvaluationApi } from "./goalEvaluationApi";
afterEach(() => vi.unstubAllGlobals());
describe("goal evaluations", () => {
  it.each([[null, "NOT_STARTED"], [0, "NOT_ACHIEVED"], [79.99, "NOT_ACHIEVED"], [80, "ACHIEVED"], [100, "ACHIEVED"]])
    ("previews the server threshold for %s", (percent, status) => {
      expect(evaluationStatus(percent as number | null)).toBe(status);
    });
  it("sends only percentage, retry decision and notes with CSRF, retaining attempt identity for edits", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ token: "csrf" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => [] });
    vi.stubGlobal("fetch", fetchMock);
    const input = { achievementPercent: 80, restartDecision: "RETRY" as const, evaluationNotes: "Notes" };
    await goalEvaluationApi.update("plan", "goal", "attempt", input);
    expect(fetchMock.mock.calls[1][0]).toBe("/api/plans/plan/goals/goal/evaluations/attempt");
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: "PUT", credentials: "include", body: JSON.stringify(input),
      headers: { "X-CSRF-TOKEN": "csrf" } });
  });
});
