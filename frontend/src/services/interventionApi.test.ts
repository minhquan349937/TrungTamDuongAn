import { afterEach, describe, expect, it, vi } from "vitest";
import { interventionApi, type PlanReview } from "./interventionApi";
afterEach(() => vi.unstubAllGlobals());
describe("intervention workflow API", () => {
  it("finishes the reviewed plan in one request without sending client goal text", async () => {
    const review: PlanReview = {
      requestId: "request",
      programId: "PR0001",
      name: "My plan",
      startDate: "2026-10-27",
      total: 1,
      reviewToken: "a".repeat(64),
      items: [
        {
          goalId: "GL000001",
          specificGoalId: null,
          targetType: "GOAL",
          code: "D1",
          name: "Goal",
          description: null,
          ageMinMonths: 12,
          ageMaxMonths: 24,
          location: {
            programId: "PR0001",
            programName: "Denver",
            levelId: "LV0001",
            levelName: "Age1",
            domainId: "DM0001",
            domainName: "Language",
            ageMinMonths: 12,
            ageMaxMonths: 24,
          },
        },
      ],
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ token: "csrf" }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ alreadySaved: false }),
      });
    vi.stubGlobal("fetch", fetchMock);
    await interventionApi.complete("student", review);
    expect(fetchMock.mock.calls[1][0]).toBe(
      "/api/intervention/students/student/complete",
    );
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      requestId: "request",
      programId: "PR0001",
      name: "My plan",
      startDate: "2026-10-27",
      reviewToken: review.reviewToken,
      selections: [{ goalId: "GL000001", specificGoalId: null }],
    });
    expect(fetchMock.mock.calls[1][1].headers["X-CSRF-TOKEN"]).toBe("csrf");
  });
});
