import { afterEach, describe, expect, it, vi } from "vitest";
import { studentProfileApi } from "./studentProfileApi";

afterEach(() => vi.unstubAllGlobals());

describe("guardian profile API", () => {
  it("saves editable guardian fields with the session CSRF token", async () => {
    const guardian = { fullName: "Nguyễn Thị Mai", phone: "0912345678", email: null, address: "Hà Nội" };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ token: "csrf-token" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: "guardian-id", ...guardian }) });
    vi.stubGlobal("fetch", fetchMock);

    await studentProfileApi.updateGuardian("guardian-id", guardian);

    expect(fetchMock.mock.calls[1]).toEqual([
      "/api/guardians/guardian-id",
      { method: "PUT", credentials: "include",
        headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": "csrf-token" },
        body: JSON.stringify(guardian) },
    ]);
  });
});
