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
    await expect(page.getByRole("heading", { name: "S&P 500 지도 증거." })).toBeVisible();
    await expect(page.getByRole("heading", { name: "S&P 500 가격 변동 트리맵" })).toBeVisible();
    await expect(page.getByRole("link", { name: "가격 변동" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    await expect(page.getByLabel("S&P 500 지도 출처")).toContainText(
      "fixture-market-treemap-sp500-v1",
    );
    await expect(page.getByText("DEMO 셀 표본 3개", { exact: true })).toBeVisible();
    await expect(page.getByRole("note")).toContainText(
      "외부 섹터 1개와 중첩 산업 3개를 시연합니다",
    );
    await expect(page.getByRole("note")).toContainText(
      "합성 프록시이며 공식 또는 현재 시가총액 값이 아닙니다",
    );
    await expect(page.getByLabel(/-5%.*\+5%/i)).toContainText(
      "표시값은 제한하지 않습니다",
    );

    const cellList = page.getByRole("list", { name: "S&P 500 중첩 DEMO 트리맵 셀" });
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

    const nvda = cellList.getByRole("article", { name: "NVDA 트리맵 증거: +1.25%" });
    const nvdaTooltip = nvda.getByRole("tooltip");
    await expect(nvdaTooltip).toBeHidden();
    await nvda.hover();
    await expect(nvdaTooltip).toBeVisible();
    await expect(nvdaTooltip).toContainText("Semiconductors");
    await expect(nvdaTooltip).toContainText("144 상대 단위");
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

    const aapl = cellList.getByRole("article", { name: "AAPL 트리맵 증거: NA" });
    await expect(aapl).toHaveClass(/treemap-metric-unavailable/);
    await expect(aapl).not.toHaveClass(/treemap-metric-positive/);
    await expect(aapl).not.toHaveClass(/treemap-metric-negative/);
    await expect(aapl.locator(".treemap-cell-copy")).toContainText("NA");
    await expect(page.locator('a[href^="/stocks/"]')).toHaveCount(0);
    await expect(cellList.getByRole("link")).toHaveCount(0);

    const evidenceSummary = page.getByText("접근 가능한 증거 인덱스 · 셀 3개", { exact: true });
    await tabTo(page, evidenceSummary, 5);
    await expectVisibleKeyboardFocus(evidenceSummary);
    await page.keyboard.press("Enter");
    await expect(page.locator(".treemap-evidence-index")).toHaveAttribute("open", "");

    const evidenceIndex = page.getByRole("table", {
      name: "S&P 500 접근 가능한 트리맵 증거 인덱스",
    });
    await expect(evidenceIndex).toBeVisible();
    const aaplEvidence = evidenceIndex.getByRole("row").filter({ hasText: "AAPL" });
    await expect(aaplEvidence).toContainText("asset-aapl");
    await expect(aaplEvidence).toContainText("Consumer Electronics");
    await expect(aaplEvidence).toContainText("NA");
    await expect(aaplEvidence).toContainText("100 상대 단위");
    await expect(aaplEvidence).toContainText("fixture-market-treemap-sp500-v1");

    const indexScrollState = await page.getByLabel("S&P 500 접근 가능한 트리맵 증거 스크롤 영역")
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

    const scrollState = await page.getByLabel("S&P 500 트리맵 스크롤 영역").evaluate((element) => ({
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
    await expect(page.getByRole("heading", { name: "Nasdaq 100 가격 변동 트리맵" })).toBeVisible();
    await expect(page.getByLabel("Nasdaq 100 지도 출처")).toContainText(
      "fixture-market-treemap-nasdaq100-v1",
    );
    await expect(page.getByRole("list", { name: "Nasdaq 100 중첩 DEMO 트리맵 셀" }))
      .toContainText("NVDA");

    const modeNavigation = page.getByRole("navigation", { name: "시장 지도 모드" });
    const analystLink = modeNavigation.getByRole("link", { name: "애널리스트 컨센서스" });
    await tabTo(page, analystLink);
    await expectVisibleKeyboardFocus(analystLink);
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/\/maps\/nasdaq100\?mode=analyst-consensus$/);
    await expect(page.getByLabel("Nasdaq 100 지도 출처")).toContainText(
      "fixture-market-map-nasdaq100-v1",
    );
    await expect(page.getByRole("status")).toContainText(
      "편입, 가중치, 지표 또는 콜 수를 추론하지 않았으며",
    );

    const universeNavigation = page.getByRole("navigation", { name: "시장 지도 유니버스" });
    const sp500Link = universeNavigation.getByRole("link", { name: "S&P 500" });
    await expect(sp500Link).toHaveAttribute("href", "/maps/sp500?mode=analyst-consensus");
    await sp500Link.click();
    await expect(page).toHaveURL(/\/maps\/sp500\?mode=analyst-consensus$/);
    await expect(page.getByRole("heading", { name: "S&P 500 애널리스트 컨센서스 표본" }))
      .toBeVisible();
    await expect(page.getByRole("list", { name: "S&P 500 제한 DEMO 표본 셀" }))
      .toContainText("NVDA");

    const priceChangeLink = page.getByRole("navigation", { name: "시장 지도 모드" })
      .getByRole("link", { name: "가격 변동" });
    await priceChangeLink.click();
    await expect(page).toHaveURL(/\/maps\/sp500$/);
    await expect(page.getByRole("heading", { name: "S&P 500 가격 변동 트리맵" })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "시장 지도 유니버스" })
      .getByRole("link", { name: "Nasdaq 100" })).toHaveAttribute("href", "/maps/nasdaq100");

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("fails closed for unknown and repeated mode query input", async ({ page }) => {
    await page.goto("/maps/sp500?mode=live");
    await expect(page.getByRole("heading", { name: "이 시장 지도는 게시되지 않았습니다." }))
      .toBeVisible();
    await expect(page.locator('meta[name="robots"]').first()).toHaveAttribute("content", /noindex/i);
    await expect(page.locator(".treemap-canvas")).toHaveCount(0);
    await expect(page.locator(".market-map-cells")).toHaveCount(0);

    await page.goto("/maps/sp500?mode=price-change&mode=analyst-consensus");
    await expect(page.getByRole("heading", { name: "이 시장 지도는 게시되지 않았습니다." }))
      .toBeVisible();
    await expect(page.locator('meta[name="robots"]').first()).toHaveAttribute("content", /noindex/i);
    await expect(page.locator(".treemap-canvas")).toHaveCount(0);
    await expect(page.locator(".market-map-cells")).toHaveCount(0);
    await expectNoPageOverflow(page);
  });
});
