import { expect, test } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

const STATE_VALUES = [
  "DEMO",
  "HISTORICAL_EQUITY_SCREENING",
  "P8_DEFERRED",
  "NO_CANONICAL_HISTORICAL_SCREENING_FEATURE_CATALOG",
  "NA",
] as const;

test.describe("known-deferred screener shell", () => {
  test("renders the exact application policy without filters, results, or responsive overflow", async ({
    page,
  }, testInfo) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const response = await page.goto("/screener");

    expect(response?.ok()).toBe(true);
    await expect(page.getByRole("heading", {
      name: "과거 주식 스크리닝은 연기됐습니다.",
    })).toBeVisible();
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");

    const navigation = page.getByRole("navigation", { name: "주요 탐색" });
    await expect(navigation.getByRole("link")).toHaveText([
      "대시보드",
      "시장",
      "콜 기록",
      "기관",
      "애널리스트",
      "시장 지도",
      "스크리너",
      "방법론",
    ]);
    const screenerLink = navigation.getByRole("link", { name: "스크리너" });
    await expect(screenerLink).toHaveAttribute("href", "/screener");
    await expect(screenerLink).toHaveAttribute("aria-current", "page");

    const navGeometry = await navigation.evaluate((element) => {
      const bounds = element.getBoundingClientRect();
      return {
        clientWidth: element.clientWidth,
        left: bounds.left,
        overflowX: getComputedStyle(element).overflowX,
        right: bounds.right,
        scrollWidth: element.scrollWidth,
        viewport: document.documentElement.clientWidth,
      };
    });
    expect(navGeometry.left).toBeGreaterThanOrEqual(-1);
    expect(navGeometry.right).toBeLessThanOrEqual(navGeometry.viewport + 1);
    if (testInfo.project.use.viewport?.width === 390) {
      expect(["auto", "scroll"]).toContain(navGeometry.overflowX);
      expect(navGeometry.scrollWidth).toBeGreaterThan(navGeometry.clientWidth);
    }

    const region = page.getByRole("region", {
      name: "과거 스크리닝 게시 상태",
    });
    const policy = region.getByLabel("스크리너 제품 제공 정책");
    await expect(policy.getByText(
      "제품 제공 정책 · 픽스처 증거 아님",
      { exact: true },
    )).toBeVisible();
    await expect(policy).toContainText("과거 가격 바, 시점 기준 기능 카탈로그");
    await expect(policy).toContainText("P3 작업");
    await expect(policy).toContainText("P5 작업");
    await expect(region.getByRole("note")).toContainText(
      "NA는 게시되지 않은 기능 상태를 기록합니다",
    );

    const state = region.getByRole("status", { name: "연기된 스크리너 상태" });
    await expect(state.locator("dt")).toHaveText([
      "데이터 모드",
      "범위",
      "상태",
      "사유",
      "누락 표시",
    ]);
    await expect(state.locator("dd")).toHaveText(STATE_VALUES);
    await expect(state.getByText("기준 시각", { exact: true })).toHaveCount(0);
    await expect(state.getByText("소스", { exact: true })).toHaveCount(0);
    await expect(state.getByText("출처 식별자", { exact: true })).toHaveCount(0);

    const adjacent = region.getByRole("navigation", { name: "인접 증거 경로" });
    await expect(adjacent.getByText(
      "별도 증거 화면 · 스크리너 출력 아님",
      { exact: true },
    )).toBeVisible();
    await expect(adjacent.getByRole("link", { name: "기록된 콜 증거 열기" }))
      .toHaveAttribute("href", "/calls");
    await expect(adjacent.getByRole("link", { name: "방법론 정의 열기" }))
      .toHaveAttribute("href", "/methodology");

    await expect(region.locator(
      "time, [datetime], form, button, input, select, textarea, table, [role=row], "
      + "[role=grid], canvas, svg, ol, ul, .metric-grid",
    )).toHaveCount(0);
    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("keeps the shell and adjacent evidence routes keyboard reachable", async ({ page }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    await page.goto("/screener");
    const region = page.getByRole("region", {
      name: "과거 스크리닝 게시 상태",
    });
    await expect(region).toBeVisible();
    await page.locator("body").focus();

    const navigation = page.getByRole("navigation", { name: "주요 탐색" });
    const screenerLink = navigation.getByRole("link", { name: "스크리너" });
    for (let attempt = 0; attempt < 9; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await screenerLink.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(screenerLink);

    for (let attempt = 0; attempt < 5; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await region.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(region);

    const callsLink = region.getByRole("link", { name: "기록된 콜 증거 열기" });
    await page.keyboard.press("Tab");
    await expectVisibleKeyboardFocus(callsLink);
    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("fails closed and emits noindex for any unsupported query shape", async ({ page }) => {
    const runtimeErrors = collectRuntimeErrors(page);

    for (const url of [
      "/screener?filter=",
      "/screener?filter=value&filter=other",
      "/screener?status=P8_DEFERRED",
      "/screener?unknown=value",
    ]) {
      await page.goto(url);
      const main = page.getByRole("main");
      await expect(main.getByText("지원하지 않는 스크리너 요청", { exact: true })).toBeVisible();
      await expect(main.getByRole("heading", {
        name: "이 스크리너 요청은 게시되지 않았습니다.",
      })).toBeVisible();
      const robotsDirectives = await page.locator('meta[name="robots"]').evaluateAll((elements) =>
        elements.map((element) => element.getAttribute("content")),
      );
      expect(robotsDirectives.length).toBeGreaterThan(0);
      expect(robotsDirectives.every((content) => /noindex/i.test(content ?? ""))).toBe(true);
      await expect(main.locator(".mode-badge")).toHaveCount(0);
      await expect(main.getByRole("navigation", { name: "주요 탐색" })).toHaveCount(0);
      for (const value of STATE_VALUES) {
        await expect(main.getByText(value, { exact: true })).toHaveCount(0);
      }
      await expect(main.locator("form, input, select, table, [role=row], canvas, svg, .metric-grid"))
        .toHaveCount(0);
      const actions = main.locator(".state-actions");
      await expect(actions.getByRole("link")).toHaveCount(2);
      await expect(actions.getByRole("link").nth(0)).toHaveAttribute("href", "/calls");
      await expect(actions.getByRole("link").nth(1)).toHaveAttribute("href", "/methodology");
      await expectNoPageOverflow(page);
    }

    expectNoRuntimeErrors(runtimeErrors);
  });
});
