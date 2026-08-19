import { expect, test } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

test.describe("known-unavailable market board", () => {
  test("renders exact DEMO publication evidence without quote rows or page overflow", async ({
    page,
  }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const response = await page.goto("/market");

    expect(response?.ok()).toBe(true);
    await expect(page.getByRole("heading", {
      name: "A global market board is not published.",
    })).toBeVisible();
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");

    const navigation = page.getByRole("navigation", { name: "Primary navigation" });
    const marketLink = navigation.getByRole("link", { name: "Market" });
    await expect(marketLink).toHaveAttribute("aria-current", "page");
    await expect(marketLink).toHaveAttribute("href", "/market");
    await expect(navigation.getByRole("link", { name: "Dashboard" }))
      .toHaveAttribute("href", "/");

    const provenance = page.getByLabel("Market board fixture provenance");
    await expect(provenance.getByText("1.0.0", { exact: true })).toBeVisible();
    await expect(provenance.getByText("v1", { exact: true })).toBeVisible();
    await expect(provenance.getByText("fixture-market-board-v1", { exact: true })).toBeVisible();
    await expect(provenance.getByText("Aug 19, 2026, 2:00 AM UTC", { exact: true }))
      .toHaveCount(2);

    const publication = page.getByRole("region", { name: "Market board publication state" });
    const policy = publication.getByLabel("Market board publication policy");
    await expect(policy.getByText("Publication policy · not market evidence", { exact: true }))
      .toBeVisible();
    await expect(policy).toContainText("not a delayed, end-of-day, or current quote surface");
    await expect(policy).toContainText("Call-event snapshots and synthetic map samples");

    const status = publication.getByLabel("Known-unavailable market board status");
    await expect(status).toContainText("NOT_PUBLISHED");
    await expect(status).toContainText("GLOBAL_MARKET_OVERVIEW");
    await expect(status).toContainText("NO_CANONICAL_GLOBAL_QUOTE_CATALOG");
    await expect(status.getByText("NA", { exact: true })).toHaveCount(2);
    await expect(status).toContainText("None published");

    const metadata = publication.getByLabel("Market board policy metadata");
    await expect(metadata).toContainText("not a market as-of time");
    await expect(metadata).toContainText("LOCAL_SPECIFICATION");
    await expect(metadata).toContainText("INTERNAL_DEMO");

    const paths = publication.getByLabel("Market board source paths");
    const sourcePathItems = paths.getByRole("list").getByRole("listitem");
    await expect(sourcePathItems).toHaveCount(2);
    await expect(sourcePathItems.nth(0)).toHaveText("schemas/market-board.schema.json");
    await expect(sourcePathItems.nth(1)).toHaveText("quality/P2_ACCEPTANCE.md");
    await expect(publication.locator(".market-board-disclaimer"))
      .toContainText("No price, change, session status, freshness, or coverage was observed");
    await expect(publication.locator(".market-board-disclaimer"))
      .toContainText("Not investment advice.");

    await expect(publication.getByRole("table")).toHaveCount(0);
    await expect(publication.getByRole("row")).toHaveCount(0);
    await expect(page.getByText("5278.52", { exact: true })).toHaveCount(0);
    await expect(page.getByText("183.42", { exact: true })).toHaveCount(0);
    await expect(page.getByText("SPX", { exact: true })).toHaveCount(0);
    await expect(page.getByText("NVDA", { exact: true })).toHaveCount(0);

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("keeps the active route, publication evidence, and dashboard return keyboard reachable", async ({
    page,
  }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    await page.goto("/");

    const dashboardNavigation = page.getByRole("navigation", { name: "Primary navigation" });
    const marketEntry = dashboardNavigation.getByRole("link", { name: "Market" });
    await expect(marketEntry).toHaveAttribute("href", "/market");
    await marketEntry.click();
    await expect(page).toHaveURL(/\/market$/);
    await page.locator("body").focus();

    const navigation = page.getByRole("navigation", { name: "Primary navigation" });
    const marketLink = navigation.getByRole("link", { name: "Market" });
    for (let attempt = 0; attempt < 8; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await marketLink.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(marketLink);

    const publication = page.getByRole("region", { name: "Market board publication state" });
    for (let attempt = 0; attempt < 12; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await publication.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(publication);

    const dashboardLink = publication.getByRole("link", { name: "Return to dashboard evidence" });
    await page.keyboard.press("Tab");
    await expectVisibleKeyboardFocus(dashboardLink);
    await Promise.all([
      page.waitForURL((url) => url.pathname === "/"),
      dashboardLink.press("Enter"),
    ]);

    await expect(page.getByRole("heading", { name: "Market evidence, without inferred gaps." }))
      .toBeVisible();
    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
