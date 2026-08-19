import { expect, test, type Locator, type Page } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

async function tabTo(page: Page, target: Locator, attempts = 10) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    await page.keyboard.press("Tab");
    if (await target.evaluate((element) => element === document.activeElement)) {
      return;
    }
  }
}

test.describe("market map shells", () => {
  test("renders the limited S&P 500 DEMO sample without page overflow", async ({ page }, testInfo) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const response = await page.goto("/maps/sp500");

    expect(response?.ok()).toBe(true);
    await expect(page.getByRole("heading", { name: "S&P 500 map evidence." })).toBeVisible();
    await expect(page.getByText("3-cell DEMO sample", { exact: true })).toBeVisible();
    await expect(page.getByText(/completeUniverse is false/i)).toBeVisible();
    await expect(page.getByText("SYNTHETIC_RELATIVE", { exact: true })).toHaveCount(2);
    await expect(page.getByRole("note")).toContainText(
      "On wide layouts, tile area uses SYNTHETIC_RELATIVE fixture weights",
    );
    await expect(page.getByRole("note")).toContainText(
      "Small screens stack the same cells for readability",
    );

    const mapCells = page.getByRole("list", { name: "S&P 500 limited DEMO sample cells" });
    const articles = mapCells.getByRole("article");
    await expect(articles).toHaveCount(3);
    await expect(mapCells.getByText("NVDA", { exact: true })).toBeVisible();
    await expect(mapCells.getByText("MSFT", { exact: true })).toBeVisible();

    const aapl = mapCells.getByRole("article", { name: "AAPL map evidence" });
    await expect(aapl).toHaveClass(/map-metric-unavailable/);
    await expect(aapl.getByText("NA", { exact: true })).toBeVisible();
    await expect(aapl).not.toHaveClass(/map-metric-positive/);
    await expect(aapl).not.toHaveClass(/map-metric-negative/);
    await expect(page.locator('a[href^="/stocks/"]')).toHaveCount(0);
    await expect(mapCells.getByRole("link")).toHaveCount(0);

    const boxes = await articles.evaluateAll((elements) =>
      elements.map((element) => {
        const bounds = element.getBoundingClientRect();
        return { left: bounds.left, top: bounds.top, width: bounds.width };
      }),
    );

    if (testInfo.project.use.viewport?.width === 390) {
      expect(boxes[1].top).toBeGreaterThan(boxes[0].top);
      expect(boxes[2].top).toBeGreaterThan(boxes[1].top);
    } else {
      expect(Math.abs(boxes[1].top - boxes[0].top)).toBeLessThanOrEqual(1);
      expect(boxes[0].width).toBeGreaterThan(boxes[1].width);
      expect(boxes[1].width).toBeGreaterThan(boxes[2].width);
    }

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("keeps Nasdaq 100 canonically empty without S&P substitution", async ({ page }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const response = await page.goto("/maps/nasdaq100");

    expect(response?.ok()).toBe(true);
    await expect(page.getByRole("heading", { name: "Nasdaq 100 map evidence." })).toBeVisible();
    await expect(page.getByText("0-cell DEMO sample", { exact: true })).toBeVisible();
    await expect(page.getByRole("status")).toContainText(
      "No membership, weight, metric, or call count was inferred",
    );
    await expect(page.getByLabel("Nasdaq 100 map definition")).toContainText("analystConsensus");
    await expect(page.getByLabel("Nasdaq 100 map provenance")).toContainText(
      "fixture-market-map-nasdaq100-v1",
    );
    await expect(page.getByText("NVDA", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("list", { name: /limited DEMO sample cells/i })).toHaveCount(0);
    await expect(page.locator('a[href^="/stocks/"]')).toHaveCount(0);

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("keeps universe navigation keyboard reachable while cells remain read-only", async ({ page }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    await page.goto("/maps/sp500");

    const primaryNavigation = page.getByRole("navigation", { name: "Primary navigation" });
    const mapsLink = primaryNavigation.getByRole("link", { name: "Maps" });
    await expect(mapsLink).toHaveAttribute("aria-current", "page");
    await tabTo(page, mapsLink);
    await expectVisibleKeyboardFocus(mapsLink);

    const universeNavigation = page.getByRole("navigation", { name: "Market map universes" });
    const nasdaqLink = universeNavigation.getByRole("link", { name: "Nasdaq 100" });
    await tabTo(page, nasdaqLink);
    await expectVisibleKeyboardFocus(nasdaqLink);
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/\/maps\/nasdaq100$/);
    await expect(page.getByRole("heading", { name: "Nasdaq 100 map evidence." })).toBeVisible();
    await expect(page.locator('a[href^="/stocks/"]')).toHaveCount(0);
    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
