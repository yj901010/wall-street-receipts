import { expect, test } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

const DEFINITION_HASHES = [
  "03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2",
  "256056d7cb2b292a1ec0bd7b905f856134bb38851a65b8a2fceaca41489db3e8",
] as const;

test.describe("methodology registry", () => {
  test("renders the governed DEMO registry without responsive overflow", async ({ page }, testInfo) => {
    const runtimeErrors = collectRuntimeErrors(page);

    await page.goto("/methodology");

    await expect(
      page.getByRole("heading", { name: "Methodology definitions, before performance claims." }),
    ).toBeVisible();
    await expect(page.getByText("DEMO", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("MODEL_ONLY", { exact: true })).toHaveCount(2);

    const registry = page.getByRole("region", { name: "Methodology registry table" });
    const table = registry.getByRole("table");
    await expect(registry).toBeVisible();
    await expect(table.getByRole("row")).toHaveCount(3);
    await expect(
      page
        .getByLabel("Methodology dataset provenance")
        .getByText("fixture-call-outcomes-v1", { exact: true }),
    ).toBeVisible();
    await expect(table.getByText("fixture-call-outcomes-v1", { exact: true })).toHaveCount(2);
    for (const definitionHash of DEFINITION_HASHES) {
      await expect(table.getByText(definitionHash, { exact: true })).toBeVisible();
    }

    const registryOverflow = await registry.evaluate((element) => {
      const bounds = element.getBoundingClientRect();
      const style = getComputedStyle(element);

      return {
        clientWidth: element.clientWidth,
        left: bounds.left,
        overflowX: style.overflowX,
        right: bounds.right,
        scrollWidth: element.scrollWidth,
        viewport: document.documentElement.clientWidth,
      };
    });

    expect(["auto", "scroll"]).toContain(registryOverflow.overflowX);
    expect(registryOverflow.left).toBeGreaterThanOrEqual(-1);
    expect(registryOverflow.right).toBeLessThanOrEqual(registryOverflow.viewport + 1);

    const viewportWidth = testInfo.project.use.viewport?.width;
    if (viewportWidth === 390) {
      expect(registryOverflow.scrollWidth).toBeLessThanOrEqual(registryOverflow.clientWidth + 1);
    } else {
      expect(registryOverflow.scrollWidth).toBeGreaterThan(registryOverflow.clientWidth);
    }

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });

  test("keeps navigation and the dense registry keyboard reachable", async ({ page }, testInfo) => {
    const runtimeErrors = collectRuntimeErrors(page);

    await page.goto("/methodology");

    const navigation = page.getByRole("navigation", { name: "Primary navigation" });
    const methodologyLink = navigation.getByRole("link", { name: "Methodology" });
    await expect(navigation).toBeVisible();
    await expect(methodologyLink).toBeVisible();
    await expect(methodologyLink).toHaveAttribute("aria-current", "page");

    for (let attempt = 0; attempt < 9; attempt += 1) {
      await page.keyboard.press("Tab");
      if (await methodologyLink.evaluate((element) => element === document.activeElement)) {
        break;
      }
    }

    await expectVisibleKeyboardFocus(methodologyLink);

    const registry = page.getByRole("region", { name: "Methodology registry table" });
    await page.keyboard.press("Tab");
    await expectVisibleKeyboardFocus(registry);

    const viewportWidth = testInfo.project.use.viewport?.width;
    if (viewportWidth === 390) {
      const widths = await registry.evaluate((element) => ({
        client: element.clientWidth,
        scroll: element.scrollWidth,
      }));
      expect(widths.scroll).toBeLessThanOrEqual(widths.client + 1);
    } else {
      await registry.evaluate((element) => {
        element.scrollLeft = 0;
      });
      await page.keyboard.press("ArrowRight");
      await expect.poll(() => registry.evaluate((element) => element.scrollLeft)).toBeGreaterThan(0);
    }

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
