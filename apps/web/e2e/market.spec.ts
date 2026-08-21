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
      name: "글로벌 시장 보드는 게시되지 않았습니다.",
    })).toBeVisible();
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");

    const navigation = page.getByRole("navigation", { name: "주요 탐색" });
    const marketLink = navigation.getByRole("link", { name: "시장", exact: true });
    await expect(marketLink).toHaveAttribute("aria-current", "page");
    await expect(marketLink).toHaveAttribute("href", "/market");
    await expect(navigation.getByRole("link", { name: "대시보드" }))
      .toHaveAttribute("href", "/");

    const provenance = page.getByLabel("시장 보드 픽스처 출처");
    await expect(provenance.getByText("1.0.0", { exact: true })).toBeVisible();
    await expect(provenance.getByText("v1", { exact: true })).toBeVisible();
    await expect(provenance.getByText("fixture-market-board-v1", { exact: true })).toBeVisible();
    await expect(provenance.getByText("Aug 19, 2026, 2:00 AM UTC", { exact: true }))
      .toHaveCount(2);

    const publication = page.getByRole("region", { name: "시장 보드 게시 상태" });
    const policy = publication.getByLabel("시장 보드 게시 정책");
    await expect(policy.getByText("게시 정책 · 시장 증거 아님", { exact: true }))
      .toBeVisible();
    await expect(policy).toContainText("지연, 장마감 또는 현재 호가 화면이 아닙니다");
    await expect(policy).toContainText("콜 이벤트 스냅샷과 합성 지도 표본");

    const status = publication.getByLabel("게시되지 않은 시장 보드 상태");
    await expect(status).toContainText("NOT_PUBLISHED");
    await expect(status).toContainText("GLOBAL_MARKET_OVERVIEW");
    await expect(status).toContainText("NO_CANONICAL_GLOBAL_QUOTE_CATALOG");
    await expect(status.getByText("NA", { exact: true })).toHaveCount(2);
    await expect(status).toContainText("게시된 항목 없음");

    const metadata = publication.getByLabel("시장 보드 정책 메타데이터");
    await expect(metadata).toContainText("시장 기준 시각");
    await expect(metadata).toContainText("LOCAL_SPECIFICATION");
    await expect(metadata).toContainText("INTERNAL_DEMO");

    const paths = publication.getByLabel("시장 보드 소스 경로");
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

    const dashboardNavigation = page.getByRole("navigation", { name: "주요 탐색" });
    const marketEntry = dashboardNavigation.getByRole("link", { name: "시장", exact: true });
    await expect(marketEntry).toHaveAttribute("href", "/market");
    await marketEntry.click();
    await expect(page).toHaveURL(/\/market$/);
    await page.locator("body").focus();

    const navigation = page.getByRole("navigation", { name: "주요 탐색" });
    const marketLink = navigation.getByRole("link", { name: "시장", exact: true });
    for (let attempt = 0; attempt < 8; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await marketLink.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(marketLink);

    const publication = page.getByRole("region", { name: "시장 보드 게시 상태" });
    await expect(publication).toHaveAttribute("tabindex", "0");
    const dashboardLink = publication.getByRole("link", { name: "대시보드 증거로 돌아가기" });
    await dashboardLink.focus();
    await page.keyboard.press("Shift+Tab");
    await expectVisibleKeyboardFocus(publication);

    await page.keyboard.press("Tab");
    await expectVisibleKeyboardFocus(dashboardLink);
    await Promise.all([
      page.waitForURL((url) => url.pathname === "/"),
      dashboardLink.press("Enter"),
    ]);

    await expect(page.getByRole("heading", { name: "추론으로 빈칸을 채우지 않은 시장 증거." }))
      .toBeVisible();
    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
