import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

// The API is mocked, so these tests need neither the database nor published content.
const content = {
  settings: { centerName: "Trung tâm Đường An", logoUrl: null, address: "1 Đường Thử", hotline: "0912 345 678", workingHours: "8:00 – 17:00", zaloUrl: null, facebookUrl: null, mapEmbedUrl: null },
  sections: {
    HERO: { title: "Mỗi bước nhỏ, một niềm vui lớn.", summary: "Mô tả.", body: null, imageUrl: null, buttonLabel: "Đăng ký tư vấn" },
    STORY: { title: "Về trung tâm", summary: null, body: "Đoạn 1.\n\nĐoạn 2.", imageUrl: null, buttonLabel: null },
    SERVICES: { title: "Dịch vụ", summary: null, body: null, imageUrl: null, buttonLabel: null },
    FAQ: { title: "Hỏi đáp", summary: null, body: null, imageUrl: null, buttonLabel: null },
    CONTACT: { title: "Liên hệ", summary: null, body: null, imageUrl: null, buttonLabel: null },
  },
  items: [],
  services: [{ id: "s1", title: "Một_từ_rất_dài_không_có_khoảng_trắng_để_thử_tràn_ngang_".repeat(3), summary: "Tóm tắt", details: "<b>Không phải HTML</b>", audience: null, format: null, imageUrl: null }],
  team: [],
  albums: [],
  faqs: [{ question: "Hỏi?", answer: "Đáp." }],
};

async function open(page: Page) {
  await page.route("**/api/auth/**", (route) => (route.request().url().endsWith("/csrf") ? route.fulfill({ json: { token: "t" } }) : route.fulfill({ status: 401, json: {} })));
  await page.route("**/api/public/website", (route) => route.fulfill({ json: content }));
  await page.goto("/#/gioi-thieu");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Mỗi bước nhỏ, một niềm vui lớn.");
}

test("public site shows only sections that have content and never overflows on a phone", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await open(page);
  for (const id of ["about", "services", "faq", "contact"]) await expect(page.locator("#" + id)).toBeVisible();
  for (const id of ["audience", "team", "progress", "gallery"]) await expect(page.locator("#" + id)).toHaveCount(0);
  await page.getByRole("button", { name: "Xem chi tiết" }).click();
  await expect(page.getByText("<b>Không phải HTML</b>")).toBeVisible(); // rendered as text, never as markup
  expect(await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(0);
});

test("consultation form validates first and reports success only after the backend confirms", async ({ page }) => {
  let posts = 0;
  await open(page);
  await page.route("**/api/public/consultations", async (route) => {
    posts++;
    if (posts === 1) return route.fulfill({ status: 429, json: { message: "Bạn gửi quá nhiều lần." } });
    return route.fulfill({ status: 201, json: { message: "Đã nhận yêu cầu." } });
  });
  await page.getByRole("button", { name: "Gửi yêu cầu tư vấn" }).click();
  await expect(page.locator(".g-error")).toHaveCount(3);
  expect(posts).toBe(0);

  await page.locator("#consult-parentName").fill("Nguyễn An");
  await page.locator("#consult-phone").fill("+84 912 345 678");
  await page.locator("#consult-consent").check();
  await page.getByRole("button", { name: "Gửi yêu cầu tư vấn" }).click();
  await expect(page.getByRole("alert")).toHaveText("Bạn gửi quá nhiều lần.");
  await expect(page.getByText("Cảm ơn bạn đã tin tưởng")).toHaveCount(0);

  await page.getByRole("button", { name: "Gửi yêu cầu tư vấn" }).click();
  await expect(page.getByText("Cảm ơn bạn đã tin tưởng")).toBeVisible();
  expect(posts).toBe(2);
});
