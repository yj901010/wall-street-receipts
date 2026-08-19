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
      name: "Historical equity screening is deferred.",
    })).toBeVisible();
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");

    const navigation = page.getByRole("navigation", { name: "Primary navigation" });
    await expect(navigation.getByRole("link")).toHaveText([
      "Dashboard",
      "Market",
      "Calls",
      "Institutions",
      "Analysts",
      "Maps",
      "Screener",
      "Methodology",
    ]);
    const screenerLink = navigation.getByRole("link", { name: "Screener" });
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
      name: "Historical screening publication state",
    });
    const policy = region.getByLabel("Screener product availability policy");
    await expect(policy.getByText(
      "Product availability policy · not fixture evidence",
      { exact: true },
    )).toBeVisible();
    await expect(policy).toContainText("historical bars, a point-in-time feature catalog");
    await expect(policy).toContainText("Performance outcomes and rankings remain P3 work");
    await expect(policy).toContainText("licensed observed-provider integration remains P5 work");
    await expect(region.getByRole("note")).toContainText(
      "NA records an unpublished capability state; it never means zero matches",
    );

    const state = region.getByRole("status", { name: "Deferred screener state" });
    await expect(state.locator("dt")).toHaveText([
      "Data mode",
      "Scope",
      "Status",
      "Reason",
      "Missing display",
    ]);
    await expect(state.locator("dd")).toHaveText(STATE_VALUES);
    await expect(state.getByText("As of", { exact: true })).toHaveCount(0);
    await expect(state.getByText("Source", { exact: true })).toHaveCount(0);
    await expect(state.getByText("Provenance", { exact: true })).toHaveCount(0);

    const adjacent = region.getByRole("navigation", { name: "Adjacent evidence routes" });
    await expect(adjacent.getByText(
      "Separate evidence surfaces · not screener output",
      { exact: true },
    )).toBeVisible();
    await expect(adjacent.getByRole("link", { name: "Open recorded call evidence" }))
      .toHaveAttribute("href", "/calls");
    await expect(adjacent.getByRole("link", { name: "Open methodology definitions" }))
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
      name: "Historical screening publication state",
    });
    await expect(region).toBeVisible();
    await page.locator("body").focus();

    const navigation = page.getByRole("navigation", { name: "Primary navigation" });
    const screenerLink = navigation.getByRole("link", { name: "Screener" });
    for (let attempt = 0; attempt < 9; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await screenerLink.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(screenerLink);

    for (let attempt = 0; attempt < 3; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await region.evaluate((element) => element === document.activeElement)) break;
    }
    await expectVisibleKeyboardFocus(region);

    const callsLink = region.getByRole("link", { name: "Open recorded call evidence" });
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
      await expect(main.getByText("Unsupported screener request", { exact: true })).toBeVisible();
      await expect(main.getByRole("heading", {
        name: "This screener request is not published.",
      })).toBeVisible();
      const robotsDirectives = await page.locator('meta[name="robots"]').evaluateAll((elements) =>
        elements.map((element) => element.getAttribute("content")),
      );
      expect(robotsDirectives.length).toBeGreaterThan(0);
      expect(robotsDirectives.every((content) => /noindex/i.test(content ?? ""))).toBe(true);
      await expect(main.locator(".mode-badge")).toHaveCount(0);
      await expect(main.getByRole("navigation", { name: "Primary navigation" })).toHaveCount(0);
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
