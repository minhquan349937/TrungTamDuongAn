import { afterEach, describe, expect, it, vi } from "vitest";
import { normalizePhone, websiteApi } from "./websiteApi";

afterEach(() => vi.unstubAllGlobals());

describe("normalizePhone", () => {
  it("accepts 0, +84 and 84 prefixes and ignores separators", () => {
    expect(normalizePhone("0912 345 678")).toBe("0912345678");
    expect(normalizePhone("+84 912.345.678")).toBe("0912345678");
    expect(normalizePhone("84912345678")).toBe("0912345678");
    expect(normalizePhone("(028) 7654 321")).toBe("0287654321");
  });
  it("rejects numbers that are not Vietnamese phone numbers", () => {
    for (const bad of ["", "12345", "0112345678", "09123456789", "abc", "+1 912345678"]) expect(normalizePhone(bad)).toBeNull();
  });
});

describe("website API", () => {
  it("reports not-yet-published as null instead of an error", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 404 }));
    await expect(websiteApi.published()).resolves.toBeNull();
  });

  it("only reports success after the backend confirms, and surfaces the backend error", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 429, json: async () => ({ message: "Bạn gửi quá nhiều lần." }) }));
    const input = { parentName: "An", phone: "0912345678", childAge: "", message: "", consent: true, website: "" };
    await expect(websiteApi.submitConsultation(input)).rejects.toThrow("Bạn gửi quá nhiều lần.");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 201, json: async () => ({ message: "Đã nhận" }) }));
    await expect(websiteApi.submitConsultation(input)).resolves.toBe("Đã nhận");
  });

  it("sends admin writes with the session CSRF token", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ token: "csrf-token" }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ published: true }) });
    vi.stubGlobal("fetch", fetchMock);
    await websiteApi.publish();
    expect(fetchMock.mock.calls[1][0]).toBe("/api/admin/website/publish");
    expect(fetchMock.mock.calls[1][1].headers["X-CSRF-TOKEN"]).toBe("csrf-token");
  });
});
