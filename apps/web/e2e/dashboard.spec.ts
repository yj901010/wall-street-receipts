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
      page.getByRole("heading", { name: "추론으로 빈칸을 채우지 않은 시장 증거." }),
    ).toBeVisible();
    await expect(page.getByRole("navigation", { name: "주요 탐색" })
      .getByRole("link", { name: "대시보드" })).toHaveAttribute("aria-current", "page");
    await expect(page.getByRole("navigation", { name: "주요 탐색" })
      .getByRole("link", { name: "시장", exact: true })).toHaveAttribute("href", "/market");
    await expect(page.getByText(/하나의 공통 기준 시각이나 소스를 합성하지 않습니다/i))
      .toBeVisible();

    const marketBoard = page.locator("#market-board");
    await expect(marketBoard.getByText("게시되지 않음", { exact: true })).toBeVisible();
    await expect(marketBoard.getByLabel("시장 보드 제공 상태")).toContainText(
      "NOT_PUBLISHED",
    );
    await expect(marketBoard.getByLabel("시장 보드 제공 상태")).toContainText("NA");
    await expect(marketBoard).toContainText("현재 시세로 승격하지 않습니다");
    await expect(marketBoard.getByRole("row")).toHaveCount(0);

    const callSection = page.locator("#calls");
    await expect(callSection.getByRole("heading", {
      name: "이 픽스처의 최신 콜",
    })).toBeVisible();
    const callProvenance = callSection.getByLabel("대시보드 콜 섹션 출처");
    await expect(callProvenance).toContainText(
      "fixture-analyst-calls-v1",
    );
    await expect(callProvenance.getByText("기준 시각", { exact: true })).toBeVisible();
    await expect(callProvenance).toContainText("2026-08-18 09:00:00 KST");
    await expect(callProvenance).toContainText(
      "원본 이벤트 시각 내림차순",
    );
    const callRows = callSection.getByRole("table", {
      name: "커밋된 DEMO 픽스처의 최신 애널리스트 콜",
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
      const heading = page.getByRole("heading", { name: `${label} 지도 미리보기` });
      const preview = heading.locator("xpath=ancestor::article");
      await expect(heading).toBeVisible();
      const previewProvenance = preview.getByLabel(`${label} 대시보드 지도 미리보기 출처`);
      await expect(previewProvenance).toContainText(provenance);
      await expect(previewProvenance.getByText("기준 시각", { exact: true })).toBeVisible();
      await expect(previewProvenance.getByText("생성 시각", { exact: true })).toBeVisible();
      await expect(previewProvenance.getByText("수집 시각", { exact: true })).toBeVisible();
      await expect(previewProvenance).toContainText("2026-08-19 09:30:00 KST");
      await expect(previewProvenance).toContainText("2026-08-19 10:00:00 KST");
      await expect(preview).toContainText("SAMPLE · 셀 3개");
      await expect(preview).toContainText("외부 섹터 1개 · 산업 3개");
      await expect(preview).toContainText("SYNTHETIC_MARKET_CAP_PROXY");
      await expect(preview).toContainText("144 relative");
      await expect(preview.getByRole("list", {
        name: `${label} 대시보드 PRICE_CHANGE 미리보기 셀`,
      })).toContainText("AAPL");
      await expect(preview.getByRole("list", {
        name: `${label} 대시보드 PRICE_CHANGE 미리보기 셀`,
      })).toContainText("NA");
      await expect(preview.getByRole("link", { name: `${label} 지도 열기` })).toHaveAttribute(
        "href",
        `/maps/${universe}`,
      );
    }
    await expect(page.getByRole("note")).toContainText(
      "저장된 합성 티커 셀 3개",
    );

    const eventCalendar = page.locator("#event-calendar");
    await expect(eventCalendar.getByText("게시되지 않음", { exact: true })).toBeVisible();
    await expect(eventCalendar.getByLabel("예정 이벤트 제공 상태")).toContainText(
      "NOT_PUBLISHED",
    );
    await expect(eventCalendar.getByRole("row")).toHaveCount(0);

    const ranking = page.locator("#ranking-preview");
    await expect(ranking.getByText("P3로 연기", { exact: true })).toBeVisible();
    await expect(ranking.getByLabel("순위 미리보기 제공 상태")).toContainText("P3_DEFERRED");
    await expect(ranking.getByLabel("순위 미리보기 제공 상태")).toContainText("NA");
    await expect(ranking.getByRole("row")).toHaveCount(0);
    await expect(ranking.getByRole("table")).toHaveCount(0);

    await expect(page.getByText("5,278.52", { exact: true })).toHaveCount(0);
    await expect(page.getByText("18,752.34", { exact: true })).toHaveCount(0);
    await expect(page.getByText("13.72", { exact: true })).toHaveCount(0);

    const callsScroll = page.getByLabel("스크롤 가능한 대시보드 최신 콜 표");
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

    const sp500Link = page.getByRole("link", { name: "S&P 500 지도 열기" });
    await tabTo(page, sp500Link, 20);
    await expectVisibleKeyboardFocus(sp500Link);

    const methodologyLink = ranking.getByRole("link", { name: "방법론 증거 검토" });
    await tabTo(page, methodologyLink, 40);
    await expectVisibleKeyboardFocus(methodologyLink);

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
