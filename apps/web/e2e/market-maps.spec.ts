import { expect, test, type Locator, type Page } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

async function tabTo(page: Page, target: Locator, attempts = 20) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    await page.keyboard.press("Tab");
    if (await target.evaluate((element) => element === document.activeElement)) return;
  }
}

function overlapArea(
  left: { left: number; top: number; right: number; bottom: number },
  right: { left: number; top: number; right: number; bottom: number },
) {
  return Math.max(0, Math.min(left.right, right.right) - Math.max(left.left, right.left)) *
    Math.max(0, Math.min(left.bottom, right.bottom) - Math.max(left.top, right.top));
}

test.describe("market map modes and nested treemap", () => {
  test("renders proportional PRICE_CHANGE hierarchy with local mobile containment", async ({
    page,
  }, testInfo) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const response = await page.goto("/maps/sp500");

    expect(response?.ok()).toBe(true);
    await expect(page.getByRole("heading", { name: "S&P 500 map evidence." })).toBeVisible();
    await expect(page.getByRole("heading", { name: "S&P 500 price-change treemap" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Price change" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    await expect(page.getByLabel("S&P 500 map provenance")).toContainText(
      "fixture-market-treemap-sp500-v1",
    );
    await expect(page.getByText("3-cell DEMO sample", { exact: true })).toBeVisible();
    await expect(page.getByRole("note")).toContainText(
      "demonstrates 1 outer sector and 3 nested industries",
    );
    await expect(page.getByRole("note")).toContainText(
      "synthetic proxy, never an official or current market-cap value",
    );
    await expect(page.getByLabel(/palette saturates at -5% and \+5%/i)).toContainText(
      "displayed values are never clamped",
    );

    const cellList = page.getByRole("list", { name: "S&P 500 nested DEMO treemap cells" });
    const cells = cellList.getByRole("article");
    await expect(cells).toHaveCount(3);
    await expect(page.locator(".treemap-sector-outline > span")).toHaveText("Technology");
    await expect(page.locator(".treemap-industry-outline > span")).toHaveText([
      "Semiconductors",
      "Software",
      "Consumer Electronics",
    ]);
    expect(
      await page.locator(".treemap-industry-outline > span, .treemap-sector-outline > span")
        .evaluateAll((labels) => labels.every((label) => {
          const bounds = label.getBoundingClientRect();
          const parentBounds = label.parentElement!.getBoundingClientRect();
          return (
            bounds.left >= parentBounds.left - 1 &&
            bounds.top >= parentBounds.top - 1 &&
            bounds.right <= parentBounds.right + 1 &&
            bounds.bottom <= parentBounds.bottom + 1
          );
        })),
    ).toBe(true);
    expect(
      await page.locator(".treemap-industry-outline, .treemap-sector-outline")
        .evaluateAll((groups) => groups.every((group) => {
          const style = getComputedStyle(group);
          return style.overflowX === "hidden" && style.overflowY === "hidden";
        })),
    ).toBe(true);

    const canvas = page.locator(".treemap-canvas");
    const geometry = await canvas.evaluate((element) => {
      const canvasBounds = element.getBoundingClientRect();
      const cellBounds = [...element.querySelectorAll<HTMLElement>(".treemap-cell-position")].map(
        (cell) => {
          const bounds = cell.getBoundingClientRect();
          return {
            ticker: cell.querySelector(".treemap-cell-copy strong")?.textContent ?? "",
            proxy: Number(cell.dataset.proxy),
            left: bounds.left,
            top: bounds.top,
            right: bounds.right,
            bottom: bounds.bottom,
            area: bounds.width * bounds.height,
          };
        },
      );
      const groupBounds = [...element.querySelectorAll<HTMLElement>(".treemap-industry-outline")].map(
        (group) => {
          const bounds = group.getBoundingClientRect();
          return {
            weight: Number(group.dataset.groupWeight),
            area: bounds.width * bounds.height,
          };
        },
      );
      return {
        canvas: {
          left: canvasBounds.left,
          top: canvasBounds.top,
          right: canvasBounds.right,
          bottom: canvasBounds.bottom,
          area: canvasBounds.width * canvasBounds.height,
        },
        cells: cellBounds,
        groups: groupBounds,
      };
    });

    const renderedLeafArea = geometry.cells.reduce((sum, cell) => sum + cell.area, 0);
    expect(Math.abs(renderedLeafArea / geometry.canvas.area - 1)).toBeLessThan(5e-5);
    for (const [index, cell] of geometry.cells.entries()) {
      expect(cell.left).toBeGreaterThanOrEqual(geometry.canvas.left - 1);
      expect(cell.top).toBeGreaterThanOrEqual(geometry.canvas.top - 1);
      expect(cell.right).toBeLessThanOrEqual(geometry.canvas.right + 1);
      expect(cell.bottom).toBeLessThanOrEqual(geometry.canvas.bottom + 1);
      for (const other of geometry.cells.slice(index + 1)) {
        expect(overlapArea(cell, other)).toBeLessThanOrEqual(1);
      }
    }

    const byTicker = Object.fromEntries(geometry.cells.map((cell) => [cell.ticker, cell]));
    expect(Math.abs((byTicker.NVDA.area / byTicker.MSFT.area) / (144 / 121) - 1))
      .toBeLessThan(0.002);
    expect(Math.abs((byTicker.MSFT.area / byTicker.AAPL.area) / (121 / 100) - 1))
      .toBeLessThan(0.002);
    expect(geometry.groups.map(({ weight }) => weight)).toEqual([144, 121, 100]);
    expect(
      Math.abs((geometry.groups[0].area / geometry.groups[2].area) / (144 / 100) - 1),
    ).toBeLessThan(0.002);

    const nvda = cellList.getByRole("article", { name: "NVDA treemap evidence: +1.25%" });
    const nvdaTooltip = nvda.getByRole("tooltip");
    await expect(nvdaTooltip).toBeHidden();
    await nvda.hover();
    await expect(nvdaTooltip).toBeVisible();
    await expect(nvdaTooltip).toContainText("Semiconductors");
    await expect(nvdaTooltip).toContainText("144 relative units");
    expect(await nvdaTooltip.evaluate((element) => {
      const bounds = element.getBoundingClientRect();
      const topmost = document.elementFromPoint(bounds.left + 20, bounds.top + 28);
      const cell = element.closest(".treemap-cell");
      const positionedCell = element.closest(".treemap-cell-position");
      return (
        topmost !== null &&
        cell?.contains(topmost) === true &&
        positionedCell !== null &&
        getComputedStyle(positionedCell).zIndex === "10"
      );
    })).toBe(true);
    await page.mouse.move(0, 0);
    await expect(nvdaTooltip).toBeHidden();

    await page.locator("body").focus();
    await tabTo(page, nvda);
    await expectVisibleKeyboardFocus(nvda);
    await expect(nvdaTooltip).toBeVisible();
    expect(await nvdaTooltip.evaluate((element) => {
      const bounds = element.getBoundingClientRect();
      const topmost = document.elementFromPoint(bounds.left + 20, bounds.top + 28);
      const cell = element.closest(".treemap-cell");
      const positionedCell = element.closest(".treemap-cell-position");
      return (
        topmost !== null &&
        cell?.contains(topmost) === true &&
        positionedCell !== null &&
        getComputedStyle(positionedCell).zIndex === "10"
      );
    })).toBe(true);

    const aapl = cellList.getByRole("article", { name: "AAPL treemap evidence: NA" });
    await expect(aapl).toHaveClass(/treemap-metric-unavailable/);
    await expect(aapl).not.toHaveClass(/treemap-metric-positive/);
    await expect(aapl).not.toHaveClass(/treemap-metric-negative/);
    await expect(aapl.locator(".treemap-cell-copy")).toContainText("NA");
    await expect(page.locator('a[href^="/stocks/"]')).toHaveCount(0);
    await expect(cellList.getByRole("link")).toHaveCount(0);

    const evidenceSummary = page.getByText("Accessible evidence index · 3 cells", { exact: true });
    await tabTo(page, evidenceSummary, 5);
    await expectVisibleKeyboardFocus(evidenceSummary);
    await page.keyboard.press("Enter");
    await expect(page.locator(".treemap-evidence-index")).toHaveAttribute("open", "");

    const evidenceIndex = page.getByRole("table", {
      name: "S&P 500 accessible treemap evidence index",
    });
    await expect(evidenceIndex).toBeVisible();
    const aaplEvidence = evidenceIndex.getByRole("row").filter({ hasText: "AAPL" });
    await expect(aaplEvidence).toContainText("asset-aapl");
    await expect(aaplEvidence).toContainText("Consumer Electronics");
    await expect(aaplEvidence).toContainText("NA");
    await expect(aaplEvidence).toContainText("100 relative units");
    await expect(aaplEvidence).toContainText("fixture-market-treemap-sp500-v1");

    const indexScrollState = await page.getByLabel("S&P 500 accessible treemap evidence scroll region")
      .evaluate((element) => ({
        clientWidth: element.clientWidth,
        scrollWidth: element.scrollWidth,
        overflowX: getComputedStyle(element).overflowX,
      }));
    expect(indexScrollState.overflowX).toBe("auto");
    if ((testInfo.project.use.viewport?.width ?? 0) < 1440) {
      expect(indexScrollState.scrollWidth).toBeGreaterThan(indexScrollState.clientWidth);
    } else {
      expect(indexScrollState.scrollWidth).toBeLessThanOrEqual(indexScrollState.clientWidth + 1);
    }

    const scrollState = await page.getByLabel("S&P 500 treemap scroll region").evaluate((element) => ({
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      overflowX: getComputedStyle(element).overflowX,
    }));
    expect(scrollState.overflowX).toBe("auto");
    if (testInfo.project.use.viewport?.width === 390) {
      expect(scrollState.scrollWidth).toBeGreaterThan(scrollState.clientWidth);
    } else {
      expect(scrollState.scrollWidth).toBeLessThanOrEqual(scrollState.clientWidth + 1);
    }

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("preserves both canonical universes and the legacy analyst-consensus mode", async ({ page }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const response = await page.goto("/maps/nasdaq100?mode=price-change");

    expect(response?.ok()).toBe(true);
    await expect(page.getByRole("heading", { name: "Nasdaq 100 price-change treemap" })).toBeVisible();
    await expect(page.getByLabel("Nasdaq 100 map provenance")).toContainText(
      "fixture-market-treemap-nasdaq100-v1",
    );
    await expect(page.getByRole("list", { name: "Nasdaq 100 nested DEMO treemap cells" }))
      .toContainText("NVDA");

    const modeNavigation = page.getByRole("navigation", { name: "Market map modes" });
    const analystLink = modeNavigation.getByRole("link", { name: "Analyst consensus" });
    await tabTo(page, analystLink);
    await expectVisibleKeyboardFocus(analystLink);
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/\/maps\/nasdaq100\?mode=analyst-consensus$/);
    await expect(page.getByLabel("Nasdaq 100 map provenance")).toContainText(
      "fixture-market-map-nasdaq100-v1",
    );
    await expect(page.getByRole("status")).toContainText(
      "No membership, weight, metric, or call count was inferred",
    );

    const universeNavigation = page.getByRole("navigation", { name: "Market map universes" });
    const sp500Link = universeNavigation.getByRole("link", { name: "S&P 500" });
    await expect(sp500Link).toHaveAttribute("href", "/maps/sp500?mode=analyst-consensus");
    await sp500Link.click();
    await expect(page).toHaveURL(/\/maps\/sp500\?mode=analyst-consensus$/);
    await expect(page.getByRole("heading", { name: "S&P 500 analyst-consensus sample" }))
      .toBeVisible();
    await expect(page.getByRole("list", { name: "S&P 500 limited DEMO sample cells" }))
      .toContainText("NVDA");

    const priceChangeLink = page.getByRole("navigation", { name: "Market map modes" })
      .getByRole("link", { name: "Price change" });
    await priceChangeLink.click();
    await expect(page).toHaveURL(/\/maps\/sp500$/);
    await expect(page.getByRole("heading", { name: "S&P 500 price-change treemap" })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "Market map universes" })
      .getByRole("link", { name: "Nasdaq 100" })).toHaveAttribute("href", "/maps/nasdaq100");

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("fails closed for unknown and repeated mode query input", async ({ page }) => {
    await page.goto("/maps/sp500?mode=live");
    await expect(page.getByRole("heading", { name: "This market map is not published." }))
      .toBeVisible();
    await expect(page.locator('meta[name="robots"]').first()).toHaveAttribute("content", /noindex/i);
    await expect(page.locator(".treemap-canvas")).toHaveCount(0);
    await expect(page.locator(".market-map-cells")).toHaveCount(0);

    await page.goto("/maps/sp500?mode=price-change&mode=analyst-consensus");
    await expect(page.getByRole("heading", { name: "This market map is not published." }))
      .toBeVisible();
    await expect(page.locator('meta[name="robots"]').first()).toHaveAttribute("content", /noindex/i);
    await expect(page.locator(".treemap-canvas")).toHaveCount(0);
    await expect(page.locator(".market-map-cells")).toHaveCount(0);
    await expectNoPageOverflow(page);
  });
});
