import { expect, test, type Locator, type Page } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

const primaryNavigationHrefs = [
  "/",
  "/market",
  "/calls",
  "/institutions",
  "/analysts",
  "/maps/sp500",
  "/screener",
  "/methodology",
];

async function tabTo(page: Page, target: Locator, attempts = 24) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    await page.keyboard.press("Tab");
    if (await target.evaluate((element) => element === document.activeElement)) return;
  }
}

async function expectPrimaryNavigationUnchanged(page: Page) {
  const navigation = page.getByRole("navigation", { name: "주요 탐색" });
  await expect(navigation.getByRole("link")).toHaveCount(primaryNavigationHrefs.length);
  expect(
    await navigation.getByRole("link").evaluateAll((links) =>
      links.map((link) => link.getAttribute("href")),
    ),
  ).toEqual(primaryNavigationHrefs);
  return navigation;
}

test.describe("recorded S&P 500 call-event history", () => {
  test("renders one canonical DEMO event without price, chart, or completeness claims", async ({
    page,
  }, testInfo) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const response = await page.goto("/markets/sp500");

    expect(response?.ok()).toBe(true);
    await expect(page.getByRole("heading", {
      name: "기록된 S&P 500 전망 콜 이벤트",
    })).toBeVisible();
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");
    await expect(page.getByText(
      "원본 애널리스트 콜 기록의 시점 기준 일부입니다. 지수 가격 이력, 현재 전망, 컨센서스, 시장 추세 또는 성과 시계열이 아닙니다.",
      { exact: true },
    )).toBeVisible();

    const navigation = await expectPrimaryNavigationUnchanged(page);
    await expect(navigation.getByRole("link", { name: "시장", exact: true }))
      .toHaveAttribute("aria-current", "page");

    const provenance = page.getByLabel("S&P 500 콜 이력 출처 정보");
    const catalogAsOf = provenance.getByText("2026-08-18T00:00:00Z", { exact: true });
    await expect(catalogAsOf).toHaveAttribute("datetime", "2026-08-18T00:00:00Z");
    await expect(provenance.getByText("fixture-analyst-calls-v1", { exact: true }))
      .toBeVisible();
    await expect(provenance.getByText("SPX", { exact: true })).toBeVisible();
    await expect(provenance.getByText("DEMO", { exact: true })).toBeVisible();

    const history = page.getByRole("region", {
      name: "S&P 500 콜 이벤트 이력",
      exact: true,
    });
    await expect(history.getByRole("heading", { name: "S&P 500 콜 이벤트 이력" }))
      .toBeVisible();
    await expect(history.getByText(
      "1개 행 표시 · 일치하는 DEMO 이벤트 1건 · 불완전한 픽스처 범위",
      { exact: true },
    )).toBeVisible();

    const policy = history.getByLabel("S&P 500 콜 이력 정책");
    await expect(policy.getByText("표시 정책 · 픽스처 증거 아님", { exact: true }))
      .toBeVisible();
    await expect(policy).toContainText(
      "정정이나 개정 내용을 현재 유효 상태로 합치지 않습니다.",
    );
    await expect(policy).toContainText(
      "현재 추천, 가격, 컨센서스 또는 성과가 아닙니다.",
    );
    await expect(policy).toContainText(
      "S&P 500 범위, 신뢰도, 완전성 또는 시장 추세를 주장하지 않습니다.",
    );

    const queryEvidence = history.getByLabel("S&P 500 이력 쿼리 증거");
    await expect(queryEvidence.getByText("S&P 500 Index", { exact: true })).toBeVisible();
    await expect(queryEvidence.getByText("asset-spx", { exact: true })).toBeVisible();
    await expect(queryEvidence.getByText("SPX · INDEX", { exact: true })).toBeVisible();
    await expect(queryEvidence.getByText("asset-spx · page 0 · size 25", { exact: true }))
      .toBeVisible();
    await expect(queryEvidence.getByText(
      "이벤트 시각 내림차순 · 동일 시각은 콜 ID 오름차순",
      { exact: true },
    )).toBeVisible();
    await expect(queryEvidence.getByText("1 / 1", { exact: true })).toBeVisible();

    await expect(history.getByText(
      "Synthetic DEMO events only; no record represents a real JPMorgan or Goldman Sachs analyst statement.",
      { exact: true },
    )).toBeVisible();

    const tableRegion = history.getByRole("region", {
      name: "S&P 500 콜 이벤트 이력 표",
      exact: true,
    });
    const table = tableRegion.getByRole("table", {
      name: "원본 확정 S&P 500 DEMO 애널리스트 콜 이벤트",
    });
    const rows = table.getByRole("row");
    await expect(rows).toHaveCount(2);
    await expect(table.getByRole("columnheader")).toHaveCount(8);
    await expect(table.getByRole("columnheader", {
      name: /market price|chart|return|alpha|hit|accuracy|rank|consensus|outcome|performance|current|complete|시장 가격|차트|수익|알파|적중|정확도|순위|컨센서스|성과|현재|완전/i,
    })).toHaveCount(0);

    const row = rows.nth(1);
    const eventLink = row.getByRole("link", { name: "2026-08-10T12:00:00Z" });
    await expect(eventLink).toHaveAttribute("href", "/calls/demo-call-001");
    await expect(eventLink.locator("time"))
      .toHaveAttribute("datetime", "2026-08-10T12:00:00Z");
    await expect(row.getByText("demo-call-001", { exact: true })).toBeVisible();
    await expect(row.getByText("JPMorgan", { exact: true })).toBeVisible();
    await expect(row.getByText("Demo Analyst A", { exact: true })).toBeVisible();
    await expect(row.getByText("BULLISH", { exact: true })).toBeVisible();
    await expect(row.getByText("DEMO Bullish", { exact: true })).toBeVisible();
    await expect(row.getByText("$7,800.00 → $8,000.00", { exact: true })).toBeVisible();
    await expect(row.getByText("통화: USD", { exact: true })).toBeVisible();
    await expect(row.getByText("NA", { exact: true })).toBeVisible();
    await expect(row.getByText("ACTIVE", { exact: true })).toBeVisible();
    await expect(row.getByRole("link", { name: "DEMO index outlook" }))
      .toHaveAttribute("href", "/calls/demo-call-001#source");
    await expect(row.getByText("DEMO Publisher · 검증 여부: false", { exact: true }))
      .toBeVisible();
    await expect(row.locator('time[datetime="2026-08-10T12:03:00Z"]')).toHaveCount(2);
    await expect(row.getByText("DEMO · fixture-analyst-calls-v1", { exact: true }))
      .toBeVisible();

    await expect(history.locator("canvas, svg")).toHaveCount(0);
    await expect(history.getByRole("img")).toHaveCount(0);
    await expect(history.locator(".metric-grid, .market-map, .treemap-canvas")).toHaveCount(0);

    const containment = await tableRegion.evaluate((element) => {
      const bounds = element.getBoundingClientRect();
      const tableBounds = element.querySelector("table")!.getBoundingClientRect();
      return {
        clientWidth: element.clientWidth,
        left: bounds.left,
        overflowX: getComputedStyle(element).overflowX,
        right: bounds.right,
        scrollWidth: element.scrollWidth,
        tableWidth: tableBounds.width,
        viewportWidth: document.documentElement.clientWidth,
      };
    });
    expect(containment.left).toBeGreaterThanOrEqual(-1);
    expect(containment.right).toBeLessThanOrEqual(containment.viewportWidth + 1);
    expect(containment.tableWidth).toBeLessThanOrEqual(containment.scrollWidth + 1);
    if (testInfo.project.use.viewport?.width === 390) {
      expect(containment.scrollWidth).toBeLessThanOrEqual(containment.clientWidth + 1);
    } else {
      expect(["auto", "scroll"]).toContain(containment.overflowX);
      expect(containment.scrollWidth).toBeGreaterThan(containment.clientWidth);
    }

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("keeps Dashboard and Market navigation, source evidence, and the exact filter reachable", async ({
    page,
  }, testInfo) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const dashboardResponse = await page.goto("/");

    expect(dashboardResponse?.ok()).toBe(true);
    const dashboardNavigation = await expectPrimaryNavigationUnchanged(page);
    await expect(dashboardNavigation.getByRole("link", { name: "대시보드" }))
      .toHaveAttribute("aria-current", "page");
    const marketEntry = dashboardNavigation.getByRole("link", { name: "시장", exact: true });
    await expect(marketEntry).toHaveAttribute("href", "/market");
    await marketEntry.click();
    await expect(page).toHaveURL(/\/market$/);

    const marketNavigation = await expectPrimaryNavigationUnchanged(page);
    await expect(marketNavigation.getByRole("link", { name: "시장", exact: true }))
      .toHaveAttribute("aria-current", "page");
    const historyEntry = page.getByRole("region", { name: "시장 보드 게시 상태" })
      .getByRole("link", { name: "기록된 S&P 500 콜 이벤트 이력 열기" });
    await expect(historyEntry).toHaveAttribute("href", "/markets/sp500");

    await page.locator("body").focus();
    await tabTo(page, historyEntry, 16);
    await expectVisibleKeyboardFocus(historyEntry);
    await Promise.all([
      page.waitForURL((url) => url.pathname === "/markets/sp500"),
      historyEntry.press("Enter"),
    ]);

    await expect(page.getByRole("heading", {
      name: "기록된 S&P 500 전망 콜 이벤트",
    })).toBeVisible();
    const historyNavigation = await expectPrimaryNavigationUnchanged(page);
    await expect(historyNavigation.getByRole("link", { name: "시장", exact: true }))
      .toHaveAttribute("aria-current", "page");

    const history = page.getByRole("region", {
      name: "S&P 500 콜 이벤트 이력",
      exact: true,
    });
    const tableRegion = history.getByRole("region", {
      name: "S&P 500 콜 이벤트 이력 표",
      exact: true,
    });
    await page.locator("body").focus();
    await tabTo(page, tableRegion, 16);
    await expectVisibleKeyboardFocus(tableRegion);
    const tableScrollBefore = await tableRegion.evaluate((element) => ({
      clientWidth: element.clientWidth,
      scrollLeft: element.scrollLeft,
      scrollWidth: element.scrollWidth,
    }));
    if (testInfo.project.use.viewport?.width === 390) {
      expect(tableScrollBefore.scrollWidth).toBeLessThanOrEqual(
        tableScrollBefore.clientWidth + 1,
      );
    } else {
      await tableRegion.press("ArrowRight");
      await expect.poll(() => tableRegion.evaluate((element) => element.scrollLeft))
        .toBeGreaterThan(tableScrollBefore.scrollLeft);
    }

    const sourceLink = tableRegion.getByRole("link", { name: "DEMO index outlook" });
    await expect(sourceLink).toHaveAttribute("href", "/calls/demo-call-001#source");
    await tabTo(page, sourceLink, 4);
    await expectVisibleKeyboardFocus(sourceLink);
    await Promise.all([
      page.waitForURL((url) =>
        url.pathname === "/calls/demo-call-001" && url.hash === "#source"
      ),
      sourceLink.press("Enter"),
    ]);

    await expect(page.locator("#source")).toBeVisible();
    await expect(page.getByRole("heading", { name: "출처 추적 정보" })).toBeVisible();
    expect(await page.evaluate(() => window.location.hash)).toBe("#source");
    await expect.poll(() => page.locator("#source").evaluate((element) => {
      const bounds = element.getBoundingClientRect();
      return bounds.bottom > 0 && bounds.top < window.innerHeight;
    }), { timeout: 15_000 }).toBe(true);

    await page.goto("/markets/sp500");
    const filterLink = page.getByRole("link", { name: "필터링된 콜 원장 열기" });
    await expect(filterLink).toHaveAttribute("href", "/calls?assetId=asset-spx");
    await Promise.all([
      page.waitForURL((url) =>
        url.pathname === "/calls" && url.search === "?assetId=asset-spx"
      ),
      filterLink.click(),
    ]);

    const filteredUrl = new URL(page.url());
    expect([...filteredUrl.searchParams.entries()]).toEqual([["assetId", "asset-spx"]]);
    await expect(page.getByLabel("자산 ID (대소문자 정확히 일치)")).toHaveValue("asset-spx");
    await expect(page.getByRole("heading", { name: "이벤트 1건" })).toBeVisible();
    const filteredTable = page.getByRole("table", { name: "필터링된 애널리스트 콜 이벤트" });
    await expect(filteredTable.getByRole("row")).toHaveCount(2);
    const filteredRow = filteredTable.getByRole("row").nth(1);
    await expect(filteredRow).toContainText("SPX");
    await expect(filteredRow.getByRole("link", { name: "Aug 10, 2026, 12:00 PM UTC" }))
      .toHaveAttribute("href", "/calls/demo-call-001");

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
