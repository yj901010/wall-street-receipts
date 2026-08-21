import { expect, test } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

test.describe("analyst identity directory", () => {
  test("renders exact DEMO identity evidence without affiliation, counts, or leaderboard data", async ({
    page,
  }, testInfo) => {
    const runtimeErrors = collectRuntimeErrors(page);

    await page.goto("/analysts");

    await expect(page.getByRole("heading", {
      name: "애널리스트를 순위표가 아닌 기록된 증거로 봅니다.",
    })).toBeVisible();
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");

    const provenance = page.getByLabel("애널리스트 식별 픽스처 출처");
    await expect(provenance.getByText("1.0.0", { exact: true })).toBeVisible();
    await expect(provenance.getByText("v1", { exact: true })).toBeVisible();
    await expect(provenance.getByText("fixture-master-data-v1", { exact: true })).toBeVisible();
    await expect(provenance.getByText("Aug 18, 2026, 12:00 AM UTC", { exact: true })).toHaveCount(2);

    const sourceEvidence = page.getByLabel("애널리스트 소스 증거");
    await expect(sourceEvidence.getByText("LOCAL_SPECIFICATION", { exact: true })).toBeVisible();
    await expect(sourceEvidence.getByText("INTERNAL_DEMO", { exact: true })).toBeVisible();
    await expect(sourceEvidence.getByText("docs/fixtures/institutions.json", { exact: true }))
      .toBeVisible();
    await expect(sourceEvidence.getByText("docs/docs/DOMAIN_MODEL.md", { exact: true }))
      .toBeVisible();

    const policy = page.getByLabel("애널리스트 디렉터리 정책");
    await expect(policy.getByText("제품 정책 · 픽스처 증거 아님", { exact: true }))
      .toBeVisible();
    await expect(policy).toContainText("순위가 아닙니다.");
    await expect(policy).toContainText("현재 활동 상태를 주장하지 않습니다");
    await expect(policy).toContainText(
      "고용주나 소속, 보증, 성과 또는 투자 조언",
    );
    await expect(page.getByText("DEMO 식별 픽스처 · 범위를 주장하지 않음", { exact: true }))
      .toBeVisible();
    await expect(page.getByText(/\b[0-9]+ DEMO fixture records\b/)).toHaveCount(0);

    const region = page.getByRole("region", { name: "애널리스트 식별 정보 표" });
    const table = region.getByRole("table", {
      name: "정규 애널리스트 식별 정보와 수집된 증거",
    });
    const rows = table.getByRole("row");
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(1).locator('[data-label="애널리스트"] strong')).toHaveText("Demo Analyst A");
    await expect(rows.nth(2).locator('[data-label="애널리스트"] strong')).toHaveText("Demo Analyst B");
    await expect(rows.nth(1).getByText("analyst-demo-a", { exact: true })).toBeVisible();
    await expect(rows.nth(2).getByText("analyst-demo-b", { exact: true })).toBeVisible();
    await expect(table.getByRole("columnheader", { name: "기록된 active" })).toBeAttached();
    await expect(table.getByRole("columnheader", { name: "콜 원장" })).toBeAttached();
    await expect(table.getByRole("columnheader", {
      name: /institution|employer|affiliation|rank|score|accuracy|performance|call count|outcome/i,
    })).toHaveCount(0);
    await expect(page.getByText("JPMorgan", { exact: true })).toHaveCount(0);
    await expect(page.getByText("Goldman Sachs", { exact: true })).toHaveCount(0);
    await expect(page.getByText("demo-call-001", { exact: true })).toHaveCount(0);
    await expect(page.locator('a[href^="/analysts/"]')).toHaveCount(0);
    await expect(page.getByRole("link", {
      name: "다음 애널리스트로 콜 원장 필터링: Demo Analyst A",
    })).toHaveAttribute("href", "/calls?analystId=analyst-demo-a");
    await expect(page.getByRole("link", {
      name: "다음 애널리스트로 콜 원장 필터링: Demo Analyst B",
    })).toHaveAttribute("href", "/calls?analystId=analyst-demo-b");

    const overflow = await region.evaluate((element) => ({
      client: element.clientWidth,
      overflowX: getComputedStyle(element).overflowX,
      scroll: element.scrollWidth,
    }));
    const viewportWidth = testInfo.project.use.viewport?.width;
    if (viewportWidth === 390) {
      expect(overflow.scroll).toBeLessThanOrEqual(overflow.client + 1);
    } else {
      expect(["auto", "scroll"]).toContain(overflow.overflowX);
      expect(overflow.scroll).toBeGreaterThanOrEqual(overflow.client);
    }

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("keeps nav, identity evidence, and the existing ledger filter keyboard reachable", async ({
    page,
  }) => {
    const runtimeErrors = collectRuntimeErrors(page);

    await page.goto("/analysts");

    const navigation = page.getByRole("navigation", { name: "주요 탐색" });
    const analystsLink = navigation.getByRole("link", { name: "애널리스트" });
    await expect(analystsLink).toHaveAttribute("aria-current", "page");

    for (let attempt = 0; attempt < 10; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await analystsLink.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(analystsLink);

    const region = page.getByRole("region", { name: "애널리스트 식별 정보 표" });
    for (let attempt = 0; attempt < 8; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await region.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(region);

    const filterLink = page.getByRole("link", {
      name: "다음 애널리스트로 콜 원장 필터링: Demo Analyst A",
    });
    for (let attempt = 0; attempt < 4; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await filterLink.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(filterLink);
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/\/calls\?analystId=analyst-demo-a$/);
    await expect(page.locator('select[name="analystId"]')).toHaveValue(
      "analyst-demo-a",
    );
    await expect(page.getByRole("heading", { name: "애널리스트 콜" })).toBeVisible();
    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
