import { expect, test, type Page } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

const KOREAN_NAVIGATION = [
  "대시보드",
  "시장",
  "콜 기록",
  "기관",
  "애널리스트",
  "시장 지도",
  "스크리너",
  "방법론",
] as const;

const ENGLISH_NAVIGATION = [
  "Dashboard",
  "Market",
  "Calls",
  "Institutions",
  "Analysts",
  "Maps",
  "Screener",
  "Methodology",
] as const;

function collectExternalRequests(page: Page) {
  const external: string[] = [];
  page.on("request", (request) => {
    const url = new URL(request.url());
    if (url.hostname !== "localhost" && url.hostname !== "127.0.0.1") {
      external.push(request.url());
    }
  });
  return external;
}

async function expectNavigation(page: Page, label: string, items: readonly string[]) {
  const navigation = page.getByRole("navigation", { name: label });
  await expect(navigation.getByRole("link")).toHaveText([...items]);
  return navigation;
}

test.describe("Korean-default bilingual SSR", () => {
  test("renders Korean in raw SSR for missing or invalid preferences without browser-language inference", async ({
    context,
    page,
  }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const externalRequests = collectExternalRequests(page);
    await context.clearCookies();

    const rawDefault = await page.request.get("/", {
      headers: { "accept-language": "en-US,en;q=0.9" },
    });
    expect(rawDefault.ok()).toBe(true);
    expect(rawDefault.headers()["cache-control"] ?? "").not.toMatch(/\bpublic\b|s-maxage/i);
    expect(await rawDefault.text()).toContain('<html lang="ko"');

    await context.addCookies([{
      name: "wsr_locale",
      value: "fr",
      url: new URL(rawDefault.url()).origin,
    }]);
    const rawInvalid = await page.request.get("/");
    expect(rawInvalid.ok()).toBe(true);
    expect(await rawInvalid.text()).toContain('<html lang="ko"');

    await context.addCookies([{
      name: "wsr_locale",
      value: "ko",
      url: new URL(rawDefault.url()).origin,
    }]);
    const rawKorean = await page.request.get("/");
    expect(rawKorean.ok()).toBe(true);
    expect(await rawKorean.text()).toContain('<html lang="ko"');

    await page.goto("/");
    await expect(page.locator("html")).toHaveAttribute("lang", "ko");
    await expectNavigation(page, "주요 탐색", KOREAN_NAVIGATION);
    await expect(page.getByRole("heading", {
      name: "추론으로 빈칸을 채우지 않은 시장 증거.",
    })).toBeVisible();
    await expect.poll(() => page.locator(".mode-badge").count()).toBe(1);
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");
    await expectNoPageOverflow(page);
    expect(externalRequests).toEqual([]);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("persists English through the real server action, reload, navigation, and a revisited context", async ({
    browser,
    context,
    page,
  }, testInfo) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const externalRequests = collectExternalRequests(page);
    await context.clearCookies();
    const initialUrl = "/calls?assetId=asset-spx&order=desc#calls-page-title";
    await page.goto(initialUrl);
    await expect(page.locator("html")).toHaveAttribute("lang", "ko");

    const canonicalLinksBefore = await page.locator('a[href^="/calls/demo-call-"]')
      .evaluateAll((links) => links.map((link) => link.getAttribute("href")));
    const englishButton = page.getByRole("button", { name: "English" });
    await englishButton.focus();
    await expectVisibleKeyboardFocus(englishButton);
    await englishButton.press("Enter");

    await expect(page.locator("html")).toHaveAttribute("lang", "en");
    await expect(page).toHaveURL(new RegExp(
      "/calls\\?assetId=asset-spx&order=desc#calls-page-title$",
    ));
    await expectNavigation(page, "Primary navigation", ENGLISH_NAVIGATION);
    const activeEnglishButton = page.getByRole("button", { name: "English" });
    await expect(activeEnglishButton).toHaveAttribute("aria-pressed", "true");
    await expect(activeEnglishButton).toBeFocused();

    const preference = (await context.cookies()).find((cookie) => cookie.name === "wsr_locale");
    expect(preference).toMatchObject({
      httpOnly: true,
      path: "/",
      sameSite: "Lax",
      value: "en",
    });
    expect(preference?.expires ?? 0).toBeGreaterThan(Date.now() / 1000 + 300 * 24 * 60 * 60);

    const rawEnglish = await page.request.get("/methodology");
    expect(rawEnglish.ok()).toBe(true);
    expect(await rawEnglish.text()).toContain('<html lang="en"');

    const canonicalLinksAfter = await page.locator('a[href^="/calls/demo-call-"]')
      .evaluateAll((links) => links.map((link) => link.getAttribute("href")));
    expect(canonicalLinksAfter).toEqual(canonicalLinksBefore);
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");

    await page.reload();
    await expect(page.locator("html")).toHaveAttribute("lang", "en");
    const methodologyLink = (await expectNavigation(page, "Primary navigation", ENGLISH_NAVIGATION))
      .getByRole("link", { name: "Methodology" });
    await methodologyLink.click();
    await expect(page).toHaveURL(/\/methodology$/);
    await expect(page.locator("html")).toHaveAttribute("lang", "en");

    const storageState = await context.storageState();
    const revisitedContext = await browser.newContext({
      baseURL: testInfo.project.use.baseURL as string,
      storageState,
      viewport: testInfo.project.use.viewport,
    });
    try {
      const revisitedPage = await revisitedContext.newPage();
      const revisitedErrors = collectRuntimeErrors(revisitedPage);
      await revisitedPage.goto("/market");
      await expect(revisitedPage.locator("html")).toHaveAttribute("lang", "en");
      await expectNavigation(revisitedPage, "Primary navigation", ENGLISH_NAVIGATION);
      expectNoRuntimeErrors(revisitedErrors);
    } finally {
      await revisitedContext.close();
    }

    const koreanButton = page.getByRole("button", { name: "한국어" });
    await koreanButton.click();
    await expect(page.locator("html")).toHaveAttribute("lang", "ko");
    await expectNavigation(page, "주요 탐색", KOREAN_NAVIGATION);
    await expectNoPageOverflow(page);
    expect(externalRequests).toEqual([]);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("keeps the locale control keyboard reachable without overflowing the responsive header", async ({
    context,
    page,
  }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    await context.clearCookies();
    await page.goto("/");

    const switcher = page.getByRole("form", { name: "언어 선택" });
    const koreanButton = switcher.getByRole("button", { name: "한국어" });
    const englishButton = switcher.getByRole("button", { name: "English" });
    await expect(koreanButton).toHaveAttribute("aria-pressed", "true");
    await expect(englishButton).toHaveAttribute("aria-pressed", "false");
    await expect(koreanButton).toHaveAttribute("lang", "ko");
    await expect(englishButton).toHaveAttribute("lang", "en");
    await englishButton.focus();
    await expectVisibleKeyboardFocus(englishButton);

    const geometry = await switcher.evaluate((element) => {
      const bounds = element.getBoundingClientRect();
      return {
        left: bounds.left,
        right: bounds.right,
        optionHeights: Array.from(element.querySelectorAll("button"), (button) =>
          button.getBoundingClientRect().height),
        viewport: document.documentElement.clientWidth,
      };
    });
    expect(geometry.left).toBeGreaterThanOrEqual(-1);
    expect(geometry.right).toBeLessThanOrEqual(geometry.viewport + 1);
    expect(geometry.optionHeights.every((height) => height >= 24)).toBe(true);
    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("localizes unknown routes without inventing a data mode or evidence", async ({
    context,
    page,
  }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    await context.clearCookies();

    await page.goto("/route-that-is-not-published");
    await expect(page.locator("html")).toHaveAttribute("lang", "ko");
    await expect(page.getByRole("heading", { name: "페이지를 찾을 수 없습니다." }))
      .toBeVisible();
    await expectNavigation(page, "주요 탐색", KOREAN_NAVIGATION);
    await expect(page.locator(".mode-badge")).toHaveCount(0);
    await expect(page.getByRole("link", { name: "대시보드로 돌아가기" }))
      .toHaveAttribute("href", "/");

    await context.addCookies([{
      name: "wsr_locale",
      value: "en",
      url: new URL(page.url()).origin,
    }]);
    await page.goto("/another-unpublished-route");
    await expect(page.locator("html")).toHaveAttribute("lang", "en");
    await expect(page.getByRole("heading", { name: "Page not found." })).toBeVisible();
    await expectNavigation(page, "Primary navigation", ENGLISH_NAVIGATION);
    await expect(page.locator(".mode-badge")).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Return to dashboard" }))
      .toHaveAttribute("href", "/");
    await expectNoPageOverflow(page);
    const expectedNotFoundConsoleError =
      "console error: Failed to load resource: the server responded with a status of 404 (Not Found)";
    expect(runtimeErrors).toHaveLength(2);
    expect(runtimeErrors.every((error) => error === expectedNotFoundConsoleError)).toBe(true);
  });
});
