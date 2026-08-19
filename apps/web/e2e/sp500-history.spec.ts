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
  "/methodology",
];

async function tabTo(page: Page, target: Locator, attempts = 24) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    await page.keyboard.press("Tab");
    if (await target.evaluate((element) => element === document.activeElement)) return;
  }
}

async function expectPrimaryNavigationUnchanged(page: Page) {
  const navigation = page.getByRole("navigation", { name: "Primary navigation" });
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
      name: "Recorded S&P 500 forecast-call events.",
    })).toBeVisible();
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");
    await expect(page.getByText(
      "This is a point-in-time subset of original analyst-call records, not index-price history, a current forecast, consensus, market trend, or performance series.",
      { exact: true },
    )).toBeVisible();

    const navigation = await expectPrimaryNavigationUnchanged(page);
    await expect(navigation.getByRole("link", { name: "Market" }))
      .toHaveAttribute("aria-current", "page");

    const provenance = page.getByLabel("S&P 500 call-history provenance");
    const catalogAsOf = provenance.getByText("2026-08-18T00:00:00Z", { exact: true });
    await expect(catalogAsOf).toHaveAttribute("datetime", "2026-08-18T00:00:00Z");
    await expect(provenance.getByText("fixture-analyst-calls-v1", { exact: true }))
      .toBeVisible();
    await expect(provenance.getByText("SPX", { exact: true })).toBeVisible();
    await expect(provenance.getByText("DEMO", { exact: true })).toBeVisible();

    const history = page.getByRole("region", {
      name: "S&P 500 call-event history",
      exact: true,
    });
    await expect(history.getByRole("heading", { name: "S&P 500 call-event history" }))
      .toBeVisible();
    await expect(history.getByText(
      "1 row shown · 1 matching DEMO event · incomplete fixture coverage",
      { exact: true },
    )).toBeVisible();

    const policy = history.getByLabel("S&P 500 call-history policy");
    await expect(policy.getByText("Presentation policy · not fixture evidence", { exact: true }))
      .toBeVisible();
    await expect(policy).toContainText(
      "No correction or revision is folded into a current effective view.",
    );
    await expect(policy).toContainText(
      "They are not current recommendations, prices, consensus, or performance.",
    );
    await expect(policy).toContainText(
      "they do not assert S&P 500 coverage, confidence, completeness, or market trend.",
    );

    const queryEvidence = history.getByLabel("S&P 500 history query evidence");
    await expect(queryEvidence.getByText("S&P 500 Index", { exact: true })).toBeVisible();
    await expect(queryEvidence.getByText("asset-spx", { exact: true })).toBeVisible();
    await expect(queryEvidence.getByText("SPX · INDEX", { exact: true })).toBeVisible();
    await expect(queryEvidence.getByText("asset-spx · page 0 · size 25", { exact: true }))
      .toBeVisible();
    await expect(queryEvidence.getByText(
      "Event time descending · call ID ascending tie break",
      { exact: true },
    )).toBeVisible();
    await expect(queryEvidence.getByText("1 / 1", { exact: true })).toBeVisible();

    await expect(history.getByText(
      "Synthetic DEMO events only; no record represents a real JPMorgan or Goldman Sachs analyst statement.",
      { exact: true },
    )).toBeVisible();

    const tableRegion = history.getByRole("region", {
      name: "S&P 500 call-event history table",
      exact: true,
    });
    const table = tableRegion.getByRole("table", {
      name: "Original committed S&P 500 DEMO analyst-call events",
    });
    const rows = table.getByRole("row");
    await expect(rows).toHaveCount(2);
    await expect(table.getByRole("columnheader")).toHaveCount(8);
    await expect(table.getByRole("columnheader", {
      name: /market price|chart|return|alpha|hit|accuracy|rank|consensus|outcome|performance|current|complete/i,
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
    await expect(row.getByText("Currency: USD", { exact: true })).toBeVisible();
    await expect(row.getByText("NA", { exact: true })).toBeVisible();
    await expect(row.getByText("ACTIVE", { exact: true })).toBeVisible();
    await expect(row.getByRole("link", { name: "DEMO index outlook" }))
      .toHaveAttribute("href", "/calls/demo-call-001#source");
    await expect(row.getByText("DEMO Publisher · Verified: false", { exact: true }))
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
    await expect(dashboardNavigation.getByRole("link", { name: "Dashboard" }))
      .toHaveAttribute("aria-current", "page");
    const marketEntry = dashboardNavigation.getByRole("link", { name: "Market" });
    await expect(marketEntry).toHaveAttribute("href", "/market");
    await marketEntry.click();
    await expect(page).toHaveURL(/\/market$/);

    const marketNavigation = await expectPrimaryNavigationUnchanged(page);
    await expect(marketNavigation.getByRole("link", { name: "Market" }))
      .toHaveAttribute("aria-current", "page");
    const historyEntry = page.getByRole("region", { name: "Market board publication state" })
      .getByRole("link", { name: "Open recorded S&P 500 call-event history" });
    await expect(historyEntry).toHaveAttribute("href", "/markets/sp500");

    await page.locator("body").focus();
    await tabTo(page, historyEntry, 16);
    await expectVisibleKeyboardFocus(historyEntry);
    await Promise.all([
      page.waitForURL((url) => url.pathname === "/markets/sp500"),
      historyEntry.press("Enter"),
    ]);

    await expect(page.getByRole("heading", {
      name: "Recorded S&P 500 forecast-call events.",
    })).toBeVisible();
    const historyNavigation = await expectPrimaryNavigationUnchanged(page);
    await expect(historyNavigation.getByRole("link", { name: "Market" }))
      .toHaveAttribute("aria-current", "page");

    const history = page.getByRole("region", {
      name: "S&P 500 call-event history",
      exact: true,
    });
    const tableRegion = history.getByRole("region", {
      name: "S&P 500 call-event history table",
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
    await expect(page.getByRole("heading", { name: "Source provenance" })).toBeVisible();
    expect(await page.evaluate(() => window.location.hash)).toBe("#source");
    await expect.poll(() => page.locator("#source").evaluate((element) => {
      const bounds = element.getBoundingClientRect();
      return bounds.bottom > 0 && bounds.top < window.innerHeight;
    })).toBe(true);

    await page.goto("/markets/sp500");
    const filterLink = page.getByRole("link", { name: "Open filtered call ledger" });
    await expect(filterLink).toHaveAttribute("href", "/calls?assetId=asset-spx");
    await Promise.all([
      page.waitForURL((url) =>
        url.pathname === "/calls" && url.search === "?assetId=asset-spx"
      ),
      filterLink.click(),
    ]);

    const filteredUrl = new URL(page.url());
    expect([...filteredUrl.searchParams.entries()]).toEqual([["assetId", "asset-spx"]]);
    await expect(page.getByLabel("Asset")).toHaveValue("asset-spx");
    await expect(page.getByRole("heading", { name: "1 event" })).toBeVisible();
    const filteredTable = page.getByRole("table", { name: "Filtered analyst call events" });
    await expect(filteredTable.getByRole("row")).toHaveCount(2);
    const filteredRow = filteredTable.getByRole("row").nth(1);
    await expect(filteredRow).toContainText("SPX");
    await expect(filteredRow.getByRole("link", { name: "Aug 10, 2026, 12:00 PM UTC" }))
      .toHaveAttribute("href", "/calls/demo-call-001");

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
