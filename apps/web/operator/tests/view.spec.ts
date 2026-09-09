import { test, expect } from "@playwright/test";
const TOKEN = Buffer.alloc(32, 7).toString("base64"); // Offline DEMO only; artifacts disabled.
test("DEMO: real local transport, KST, responsive/keyboard, selection and failure recovery", async ({ page, context }, info) => {
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", event => { if (event.type() === "error" && !event.text().includes("404 (Not Found)")) errors.push(event.text()); });
  const requests: string[] = []; page.on("request", request => requests.push(request.url()));
  const response = await page.goto("/operator/cpi");
  expect(response!.status()).toBe(200);
  expect(response!.headers()["cache-control"]).toContain("no-store");
  await expect(page.getByRole("heading", { name: "CPI 수집 이력" })).toBeVisible();
  await expect(page.getByText("아직 조회하지 않았습니다.")).toBeVisible();
  expect(requests.some(url => url.includes("/query"))).toBe(false);
  await page.keyboard.press("Tab"); await expect(page.getByLabel("운영자 토큰")).toBeFocused();
  expect(await page.getByLabel("운영자 토큰").evaluate(el => getComputedStyle(el).outlineStyle)).not.toBe("none");
  // The single-process limiter also applies across browser contexts/projects.
  await page.waitForTimeout(1100);
  await page.getByLabel("운영자 토큰").fill(TOKEN);
  await page.getByRole("button", { name: "이력 조회" }).click();
  await expect(page.locator("tbody tr")).toHaveCount(20);
  await expect(page.getByLabel("운영자 토큰")).toHaveValue("");
  await expect(page.getByText(/더 오래된 기록이 있습니다/)).toBeVisible();
  await expect(page.getByText("2026-09-08 23:59:59.123456 KST").first()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
  expect(await context.cookies()).toEqual([]);
  await page.screenshot({ path: info.outputPath("demo-credential-cleared.png"), fullPage: true });
  // Explicit pacing in the rehearsal only; the production client never retries automatically.
  await page.waitForTimeout(1100);
  await page.getByLabel("수집 시도 UUID (선택)").fill("00000000-0000-0000-0000-000000000001");
  await page.getByLabel("운영자 토큰").fill(TOKEN);
  await page.getByRole("button", { name: "이력 조회" }).click();
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await expect(page.locator("tbody tr td").nth(2)).toContainText("저장 기록");
  await expect(page.locator("tbody tr td").nth(2)).toContainText("SAVED");
  await expect(page.getByText("00000000-0000-0000-0000-000000000090", { exact: true })).toBeVisible();
  await page.waitForTimeout(1100);
  await page.getByLabel("수집 시도 UUID (선택)").fill("00000000-0000-0000-0000-000000000099");
  await page.getByLabel("운영자 토큰").fill(TOKEN);
  await page.getByRole("button", { name: "이력 조회" }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("기록을 찾을 수 없거나");
  await expect(page.locator("tbody tr")).toHaveCount(0);
  await page.getByRole("button", { name: "입력·결과 지우기" }).click();
  await expect(page.getByLabel("수집 시도 UUID (선택)")).toHaveValue("");
  expect(requests.every(url => url.startsWith("http://127.0.0.1:3470/"))).toBe(true);
  expect(requests.some(url => url.includes(TOKEN) || url.includes("token="))).toBe(false);
  expect(errors).toEqual([]);
});
