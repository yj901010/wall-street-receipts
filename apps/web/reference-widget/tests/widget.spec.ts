import { expect, test, type Page } from "@playwright/test";
import { WIDGET_SCRIPT, SCRIPT_TIMEOUT_MS, widgetAttributes } from "../../src/app/market/reference/AAPL/config";
import { collectRuntimeErrors, expectNoRuntimeErrors, expectNoPageOverflow, expectVisibleKeyboardFocus } from "../../e2e/runtime-assertions";

// Transport stub only. No vendor code, market data, numeric quote or live claim.
const STUB = `customElements.define("vunelix-symbol-overview", class extends HTMLElement {
  connectedCallback() { this.attachShadow({mode:"open"}).innerHTML =
    '<p>TEST TRANSPORT ONLY — NO QUOTE</p>'; }
});`;

async function networkBoundary(page: Page, outcome: "loaded" | "error" | "pending" | "empty") {
  const requested: string[] = [];
  const unexpected: string[] = [];
  await page.route("**/*", async route => {
    const url = route.request().url();
    if (new URL(url).origin === "http://127.0.0.1:3118") return route.continue();
    requested.push(url);
    if (url !== WIDGET_SCRIPT) { unexpected.push(url); return route.abort(); }
    if (outcome === "error") return route.abort("failed");
    if (outcome === "pending") return; // Test context disposal cancels the held request.
    return route.fulfill({ contentType: "application/javascript", body: outcome === "empty" ? "/* empty test module */" : STUB });
  });
  return { requested, unexpected };
}

test("explicit keyboard consent, one request, unverified transport and full document reset", async ({ page }, info) => {
  const network = await networkBoundary(page, "loaded");
  const errors = collectRuntimeErrors(page);
  await page.goto("/market/reference/AAPL?symbol=UNTRUSTED");
  const button = page.getByRole("button", { name: "외부 Vunelix 위젯 불러오기" });
  await expect(button).toBeVisible();
  await expect(page.locator('script[data-wsr-reference]')).toHaveCount(0);
  expect(network.requested).toEqual([]);
  await page.keyboard.press("Tab");
  await page.keyboard.press("Tab");
  await expectVisibleKeyboardFocus(button);
  await button.press("Enter");
  await expect(page.getByRole("status")).toContainText("시세는 미검증");
  await expect(page.getByText("TEST TRANSPORT ONLY — NO QUOTE")).toBeVisible();
  const widget = page.locator("vunelix-symbol-overview");
  for (const [key, value] of Object.entries(widgetAttributes)) await expect(widget).toHaveAttribute(key, value);
  await expect(page.getByRole("button")).toHaveCount(0);
  await expectNoPageOverflow(page);
  await page.screenshot({ path: info.outputPath("reference-transport-only.png"), fullPage: true });
  expect(network.requested).toEqual([WIDGET_SCRIPT]);
  expect(network.unexpected).toEqual([]);
  expectNoRuntimeErrors(errors);
  await page.evaluate(() => { document.documentElement.dataset.oldDocument = "true"; });
  await page.getByRole("link", { name: "외부 코드 종료 후 이 페이지 새로고침" }).click();
  await expect(button).toBeVisible();
  await expect(page.locator("html")).not.toHaveAttribute("data-old-document");
  expect(await page.evaluate(() => Boolean(customElements.get("vunelix-symbol-overview")))).toBe(false);
  await expect(page.locator('script[data-wsr-reference]')).toHaveCount(0);
  expect(network.requested).toEqual([WIDGET_SCRIPT]);
});

test("English empty module is not proof that prices arrived", async ({ context, page }) => {
  const network = await networkBoundary(page, "empty");
  await context.addCookies([{ name: "wsr_locale", value: "en", url: "http://127.0.0.1:3118", httpOnly: true }]);
  await page.goto("/market/reference/AAPL");
  await page.getByRole("button", { name: "Load external Vunelix widget" }).click();
  await expect(page.getByRole("status")).toHaveText("Widget script loaded · quotes remain unverified");
  await expect(page.getByText(/does not prove that any quote arrived/)).toBeVisible();
  await expect(page.locator("vunelix-symbol-overview")).toBeEmpty();
  await expect(page.locator("time, table")).toHaveCount(0);
  await expectNoPageOverflow(page);
  expect(network.requested).toEqual([WIDGET_SCRIPT]);
  expect(network.unexpected).toEqual([]);
});

test("script error is bounded with no numeric fallback or automatic retry", async ({ page }) => {
  const network = await networkBoundary(page, "error");
  await page.goto("/market/reference/AAPL");
  await page.getByRole("button").click();
  await expect(page.getByRole("status")).toContainText("불러오지 못했습니다");
  await expect(page.getByText(/대체 가격은 표시하지 않습니다/)).toBeVisible();
  await expect(page.locator("table, time")).toHaveCount(0);
  await expectNoPageOverflow(page);
  expect(network.requested).toEqual([WIDGET_SCRIPT]);
  expect(network.unexpected).toEqual([]);
});

test("hung script deadline does not claim a price or termination of external code", async ({ page }) => {
  const network = await networkBoundary(page, "pending");
  await page.goto("/market/reference/AAPL");
  await expect(page.getByRole("button")).toBeVisible();
  await page.clock.install();
  await page.getByRole("button").click();
  await expect(page.getByRole("status")).toContainText("요청하고 있습니다");
  await expect.poll(() => network.requested.length).toBe(1);
  await page.clock.fastForward(SCRIPT_TIMEOUT_MS);
  await expect(page.getByRole("status")).toContainText("불러오지 못했습니다");
  await expect(page.getByText(/뒤늦게 로드될 수 있으므로/)).toBeVisible();
  await expect(page.locator("table, time")).toHaveCount(0);
  await expectNoPageOverflow(page);
  expect(network.requested).toEqual([WIDGET_SCRIPT]);
  expect(network.unexpected).toEqual([]);
});
