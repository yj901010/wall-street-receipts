import { expect, test } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

test.describe("institution identity directory", () => {
  test("renders exact DEMO identity evidence without a leaderboard or page overflow", async ({
    page,
  }, testInfo) => {
    const runtimeErrors = collectRuntimeErrors(page);

    await page.goto("/institutions");

    await expect(page.getByRole("heading", {
      name: "기관을 순위표가 아닌 기록된 증거로 봅니다.",
    })).toBeVisible();
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");

    const provenance = page.getByLabel("기관 식별 픽스처 출처");
    await expect(provenance.getByText("1.0.0", { exact: true })).toBeVisible();
    await expect(provenance.getByText("v1", { exact: true })).toBeVisible();
    await expect(provenance.getByText("fixture-master-data-v1", { exact: true })).toBeVisible();
    await expect(provenance.getByText("Aug 18, 2026, 12:00 AM UTC", { exact: true })).toHaveCount(2);

    const sourceEvidence = page.getByLabel("기관 소스 증거");
    await expect(sourceEvidence.getByText("LOCAL_SPECIFICATION", { exact: true })).toBeVisible();
    await expect(sourceEvidence.getByText("INTERNAL_DEMO", { exact: true })).toBeVisible();
    await expect(sourceEvidence.getByText("docs/fixtures/institutions.json", { exact: true }))
      .toBeVisible();
    await expect(sourceEvidence.getByText("docs/docs/DOMAIN_MODEL.md", { exact: true }))
      .toBeVisible();

    const policy = page.getByLabel("기관 디렉터리 정책");
    await expect(policy.getByText("제품 정책 · 픽스처 증거 아님", { exact: true }))
      .toBeVisible();
    await expect(policy).toContainText("순위가 아닙니다.");
    await expect(policy).toContainText("현재 운영 상태를 주장하지 않습니다");
    await expect(policy).toContainText("보증이나 투자 조언이 아닙니다");
    await expect(page.getByText("2 DEMO 픽스처 레코드 · 범위를 주장하지 않음", { exact: true }))
      .toBeVisible();

    const region = page.getByRole("region", { name: "기관 식별 정보 표" });
    const table = region.getByRole("table", {
      name: "정규 기관 식별 정보와 수집된 증거",
    });
    const rows = table.getByRole("row");
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(1).locator('[data-label="기관"] strong')).toHaveText("Goldman Sachs");
    await expect(rows.nth(2).locator('[data-label="기관"] strong')).toHaveText("JPMorgan");
    await expect(rows.nth(1).getByText("inst-gs", { exact: true })).toBeVisible();
    await expect(rows.nth(2).getByText("inst-jpm", { exact: true })).toBeVisible();
    await expect(table.getByRole("columnheader", { name: "기록된 active" })).toBeAttached();
    await expect(table.getByRole("columnheader", { name: "콜 원장" })).toBeAttached();
    await expect(table.getByRole("columnheader", {
      name: /rank|score|accuracy|performance|call count/i,
    })).toHaveCount(0);
    await expect(page.locator('a[href^="/institutions/"]')).toHaveCount(0);
    await expect(page.getByText("Demo Analyst A", { exact: true })).toHaveCount(0);
    await expect(page.getByText("Demo Analyst B", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("link", {
      name: "다음 기관으로 콜 원장 필터링: Goldman Sachs",
    })).toHaveAttribute("href", "/calls?institutionId=inst-gs");
    await expect(page.getByRole("link", {
      name: "다음 기관으로 콜 원장 필터링: JPMorgan",
    })).toHaveAttribute("href", "/calls?institutionId=inst-jpm");

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
      expect(overflow.scroll).toBeGreaterThan(overflow.client);
    }

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("keeps nav, evidence, and the existing call-ledger filter keyboard reachable", async ({
    page,
  }) => {
    const runtimeErrors = collectRuntimeErrors(page);

    await page.goto("/institutions");

    const navigation = page.getByRole("navigation", { name: "주요 탐색" });
    const institutionsLink = navigation.getByRole("link", { name: "기관" });
    await expect(institutionsLink).toHaveAttribute("aria-current", "page");

    for (let attempt = 0; attempt < 8; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await institutionsLink.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(institutionsLink);

    const region = page.getByRole("region", { name: "기관 식별 정보 표" });
    for (let attempt = 0; attempt < 8; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await region.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(region);

    const filterLink = page.getByRole("link", {
      name: "다음 기관으로 콜 원장 필터링: Goldman Sachs",
    });
    for (let attempt = 0; attempt < 4; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await filterLink.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(filterLink);
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/\/calls\?institutionId=inst-gs$/);
    await expect(page.getByLabel("기관 ID (대소문자 정확히 일치)")).toHaveValue("inst-gs");
    await expect(page.getByRole("heading", { name: "애널리스트 콜" }))
      .toBeVisible();
    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
