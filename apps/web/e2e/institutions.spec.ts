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
      name: "Institutions as recorded evidence, not a leaderboard.",
    })).toBeVisible();
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");

    const provenance = page.getByLabel("Institution identity fixture provenance");
    await expect(provenance.getByText("1.0.0", { exact: true })).toBeVisible();
    await expect(provenance.getByText("v1", { exact: true })).toBeVisible();
    await expect(provenance.getByText("fixture-master-data-v1", { exact: true })).toBeVisible();
    await expect(provenance.getByText("Aug 18, 2026, 12:00 AM UTC", { exact: true })).toHaveCount(2);

    const sourceEvidence = page.getByLabel("Institution source evidence");
    await expect(sourceEvidence.getByText("LOCAL_SPECIFICATION", { exact: true })).toBeVisible();
    await expect(sourceEvidence.getByText("INTERNAL_DEMO", { exact: true })).toBeVisible();
    await expect(sourceEvidence.getByText("docs/fixtures/institutions.json", { exact: true }))
      .toBeVisible();
    await expect(sourceEvidence.getByText("docs/docs/DOMAIN_MODEL.md", { exact: true }))
      .toBeVisible();

    const policy = page.getByLabel("Institution directory policy");
    await expect(policy.getByText("Product policy · not fixture evidence", { exact: true }))
      .toBeVisible();
    await expect(policy).toContainText("Not ranked.");
    await expect(policy).toContainText("not a live operating-status claim");
    await expect(policy).toContainText("not an endorsement or investment advice");
    await expect(page.getByText("2 DEMO fixture records · coverage not asserted", { exact: true }))
      .toBeVisible();

    const region = page.getByRole("region", { name: "Institution identity table" });
    const table = region.getByRole("table", {
      name: "Canonical institution identities and their captured evidence",
    });
    const rows = table.getByRole("row");
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(1).locator('[data-label="Institution"] strong')).toHaveText("Goldman Sachs");
    await expect(rows.nth(2).locator('[data-label="Institution"] strong')).toHaveText("JPMorgan");
    await expect(rows.nth(1).getByText("inst-gs", { exact: true })).toBeVisible();
    await expect(rows.nth(2).getByText("inst-jpm", { exact: true })).toBeVisible();
    await expect(table.getByRole("columnheader", { name: "Recorded active" })).toBeAttached();
    await expect(table.getByRole("columnheader", { name: "Call ledger" })).toBeAttached();
    await expect(table.getByRole("columnheader", {
      name: /rank|score|accuracy|performance|call count/i,
    })).toHaveCount(0);
    await expect(page.locator('a[href^="/institutions/"]')).toHaveCount(0);
    await expect(page.getByText("Demo Analyst A", { exact: true })).toHaveCount(0);
    await expect(page.getByText("Demo Analyst B", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("link", {
      name: "Filter call ledger for Goldman Sachs",
    })).toHaveAttribute("href", "/calls?institutionId=inst-gs");
    await expect(page.getByRole("link", {
      name: "Filter call ledger for JPMorgan",
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

    const navigation = page.getByRole("navigation", { name: "Primary navigation" });
    const institutionsLink = navigation.getByRole("link", { name: "Institutions" });
    await expect(institutionsLink).toHaveAttribute("aria-current", "page");

    for (let attempt = 0; attempt < 8; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await institutionsLink.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(institutionsLink);

    const region = page.getByRole("region", { name: "Institution identity table" });
    for (let attempt = 0; attempt < 8; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await region.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(region);

    const filterLink = page.getByRole("link", {
      name: "Filter call ledger for Goldman Sachs",
    });
    for (let attempt = 0; attempt < 4; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await filterLink.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(filterLink);
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/\/calls\?institutionId=inst-gs$/);
    await expect(page.getByLabel("Institution")).toHaveValue("inst-gs");
    await expect(page.getByRole("heading", { name: "Analyst calls" }))
      .toBeVisible();
    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
