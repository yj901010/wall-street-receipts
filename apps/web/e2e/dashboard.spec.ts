import { expect, test, type Locator, type Page } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

async function tabTo(page: Page, target: Locator, attempts = 40) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    await page.keyboard.press("Tab");
    if (await target.evaluate((element) => element === document.activeElement)) return;
  }
}

test.describe("dashboard responsive regression", () => {
  test("renders canonical section evidence and closed unavailable states", async ({
    page,
  }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const response = await page.goto("/");

    expect(response?.ok()).toBe(true);
    await expect(
      page.getByRole("heading", { name: "Market evidence, without inferred gaps." }),
    ).toBeVisible();
    await expect(page.getByRole("navigation", { name: "Primary navigation" })
      .getByRole("link", { name: "Dashboard" })).toHaveAttribute("aria-current", "page");
    await expect(page.getByRole("navigation", { name: "Primary navigation" })
      .getByRole("link", { name: "Market" })).toHaveAttribute("href", "/market");
    await expect(page.getByText(/does not synthesize one global as-of time or source/i))
      .toBeVisible();

    const marketBoard = page.locator("#market-board");
    await expect(marketBoard.getByText("Not published", { exact: true })).toBeVisible();
    await expect(marketBoard.getByLabel("Market board availability")).toContainText(
      "NOT_PUBLISHED",
    );
    await expect(marketBoard.getByLabel("Market board availability")).toContainText("NA");
    await expect(marketBoard).toContainText("not promoted to current quotes");
    await expect(marketBoard.getByRole("row")).toHaveCount(0);

    const callSection = page.locator("#calls");
    await expect(callSection.getByRole("heading", {
      name: "Latest calls within this fixture",
    })).toBeVisible();
    const callProvenance = callSection.getByLabel("Dashboard call section provenance");
    await expect(callProvenance).toContainText(
      "fixture-analyst-calls-v1",
    );
    await expect(callProvenance.getByText("As of", { exact: true })).toBeVisible();
    await expect(callProvenance).toContainText("Aug 18, 2026, 12:00 AM UTC");
    await expect(callProvenance).toContainText(
      "Original event time, descending",
    );
    const callRows = callSection.getByRole("table", {
      name: "Latest analyst calls within the committed DEMO fixture",
    }).getByRole("row").filter({ has: page.locator("td") });
    await expect(callRows).toHaveCount(3);
    expect(await callRows.locator("a.row-link").evaluateAll((links) =>
      links.map((link) => link.getAttribute("href"))
    )).toEqual([
      "/calls/demo-call-002",
      "/calls/demo-call-001",
      "/calls/demo-call-003",
    ]);
    await expect(callRows.nth(2)).toContainText("MSFT");
    await expect(callRows.nth(2)).toContainText("NA → NA");
    await expect(callRows.nth(2)).toContainText("NA · ACTIVE · DEMO");
    await expect(callRows.nth(2).getByRole("link", {
      name: "DEMO unattributed neutral outlook",
    })).toHaveAttribute("href", "/calls/demo-call-003#source");

    for (const [universe, label, provenance] of [
      ["sp500", "S&P 500", "fixture-market-treemap-sp500-v1"],
      ["nasdaq100", "Nasdaq 100", "fixture-market-treemap-nasdaq100-v1"],
    ] as const) {
      const heading = page.getByRole("heading", { name: `${label} map preview` });
      const preview = heading.locator("xpath=ancestor::article");
      await expect(heading).toBeVisible();
      const previewProvenance = preview.getByLabel(`${label} dashboard map preview provenance`);
      await expect(previewProvenance).toContainText(provenance);
      await expect(previewProvenance.getByText("As of", { exact: true })).toBeVisible();
      await expect(previewProvenance.getByText("Generated", { exact: true })).toBeVisible();
      await expect(previewProvenance.getByText("Captured", { exact: true })).toBeVisible();
      await expect(previewProvenance).toContainText("Aug 19, 2026, 12:30 AM UTC");
      await expect(previewProvenance).toContainText("Aug 19, 2026, 1:00 AM UTC");
      await expect(preview).toContainText("SAMPLE · 3 cells");
      await expect(preview).toContainText("1 outer sector · 3 industries");
      await expect(preview).toContainText("SYNTHETIC_MARKET_CAP_PROXY");
      await expect(preview).toContainText("144 relative units");
      await expect(preview.getByRole("list", {
        name: `${label} dashboard PRICE_CHANGE preview cells`,
      })).toContainText("AAPL");
      await expect(preview.getByRole("list", {
        name: `${label} dashboard PRICE_CHANGE preview cells`,
      })).toContainText("NA");
      await expect(preview.getByRole("link", { name: `Open ${label} map` })).toHaveAttribute(
        "href",
        `/maps/${universe}`,
      );
    }
    await expect(page.getByRole("note")).toContainText(
      "reuse 3 stored synthetic ticker cells",
    );

    const eventCalendar = page.locator("#event-calendar");
    await expect(eventCalendar.getByText("Not published", { exact: true })).toBeVisible();
    await expect(eventCalendar.getByLabel("Scheduled events availability")).toContainText(
      "NOT_PUBLISHED",
    );
    await expect(eventCalendar.getByRole("row")).toHaveCount(0);

    const ranking = page.locator("#ranking-preview");
    await expect(ranking.getByText("P3 deferred", { exact: true })).toBeVisible();
    await expect(ranking.getByLabel("Ranking preview availability")).toContainText("P3_DEFERRED");
    await expect(ranking.getByLabel("Ranking preview availability")).toContainText("NA");
    await expect(ranking.getByRole("row")).toHaveCount(0);
    await expect(ranking.getByRole("table")).toHaveCount(0);

    await expect(page.getByText("5,278.52", { exact: true })).toHaveCount(0);
    await expect(page.getByText("18,752.34", { exact: true })).toHaveCount(0);
    await expect(page.getByText("13.72", { exact: true })).toHaveCount(0);

    const callsScroll = page.getByLabel("Scrollable dashboard latest calls table");
    const callsScrollState = await callsScroll.evaluate((element) => ({
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      overflowX: getComputedStyle(element).overflowX,
    }));
    if (test.info().project.use.viewport?.width === 390) {
      expect(callsScrollState.scrollWidth).toBeLessThanOrEqual(callsScrollState.clientWidth + 1);
    } else {
      expect(callsScrollState.overflowX).toBe("auto");
    }

    const callDetailLink = callRows.nth(0).locator("a.row-link");
    await page.locator("body").focus();
    await tabTo(page, callDetailLink);
    await expectVisibleKeyboardFocus(callDetailLink);

    const sp500Link = page.getByRole("link", { name: "Open S&P 500 map" });
    await tabTo(page, sp500Link, 20);
    await expectVisibleKeyboardFocus(sp500Link);

    const methodologyLink = ranking.getByRole("link", { name: "Review methodology evidence" });
    await tabTo(page, methodologyLink, 40);
    await expectVisibleKeyboardFocus(methodologyLink);

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
